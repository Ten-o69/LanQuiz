package io.github.ten_o69.lanquiz.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ten_o69.lanquiz.data.*
import io.github.ten_o69.lanquiz.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

enum class AppRole { NONE, HOST, CLIENT }
enum class AppStage { HOME, HOST_SETUP, JOIN, LOBBY, GAME, RESULTS }

data class UiState(
    val role: AppRole = AppRole.NONE,
    val stage: AppStage = AppStage.HOME,

    val nickname: String = "Player-${(1000..9999).random()}",
    val roomCode: String = randomCode(),
    val password: String = "",

    val questions: List<QuizQuestion> = emptyList(),
    val players: List<PlayerDto> = emptyList(),

    val currentQuestion: WsMsg.Question? = null,
    val lastReveal: WsMsg.Reveal? = null,
    val scoreboard: List<ScoreDto> = emptyList(),

    // чтобы Undo работал на ответы
    val answeredForIndex: Int? = null,
    val answeredPayload: WsMsg.AnswerPayload? = null,

    val hostServiceName: String? = null,
    val hostPort: Int? = null,

    val resolvedHost: String? = null,
    val resolvedPort: Int? = null,

    val error: String? = null
)

private sealed class UndoEffect {
    data object None : UndoEffect()
    data object StopHosting : UndoEffect()
    data object StopClientJoin : UndoEffect()
    data object CancelGame : UndoEffect()
    data class ClearMyAnswer(val questionIndex: Int) : UndoEffect()
}

private data class UndoEntry(
    val prev: UiState,
    val effect: UndoEffect
)

class QuizViewModel(app: Application) : AndroidViewModel(app) {

    private val nsd = NsdHelper(app.applicationContext)

    private var server: QuizServer? = null
    private var client: QuizClient? = null
    private var discoveryJob: Job? = null

    private val undoStack = ArrayDeque<UndoEntry>()
    val canUndo = MutableStateFlow(false)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    fun go(stage: AppStage) = pushUndoAndUpdate(UndoEffect.None) {
        it.copy(stage = stage, error = null)
    }

    fun setNickname(v: String) = pushUndoAndUpdate(UndoEffect.None) { it.copy(nickname = v) }
    fun setRoomCode(v: String) = pushUndoAndUpdate(UndoEffect.None) { it.copy(roomCode = v.uppercase()) }
    fun setPassword(v: String) = pushUndoAndUpdate(UndoEffect.None) { it.copy(password = v) }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        canUndo.value = undoStack.isNotEmpty()

        viewModelScope.launch {
            // сначала компенсируем побочный эффект
            when (val eff = entry.effect) {
                UndoEffect.None -> {}
                UndoEffect.StopHosting -> {
                    runCatching { server?.stop() }
                    server = null
                }
                UndoEffect.StopClientJoin -> {
                    discoveryJob?.cancel(); discoveryJob = null
                    runCatching { client?.close() }
                    client = null
                }
                UndoEffect.CancelGame -> {
                    server?.cancelGame("Отменено (Undo)")
                }
                is UndoEffect.ClearMyAnswer -> {
                    client?.clearAnswer(eff.questionIndex)
                }
            }
            // потом возвращаем UI-состояние
            _ui.emit(entry.prev.copy(error = null))
        }
    }

    private fun pushUndo(effect: UndoEffect) {
        undoStack.addLast(UndoEntry(_ui.value, effect))
        canUndo.value = true
    }

    private fun pushUndoAndUpdate(effect: UndoEffect, reducer: (UiState) -> UiState) {
        pushUndo(effect)
        _ui.value = reducer(_ui.value)
    }

    fun importQuestions(uri: Uri) {
        pushUndo(UndoEffect.None)
        viewModelScope.launch {
            try {
                val qs = ImportParsers.importAny(getApplication<Application>().contentResolver, uri)
                _ui.emit(_ui.value.copy(questions = qs, error = null))
            } catch (e: Exception) {
                _ui.emit(_ui.value.copy(error = "Импорт: ${e.message}"))
            }
        }
    }

    // HOST
    fun startHosting() {
        val state = _ui.value
        if (state.questions.isEmpty()) {
            _ui.value = state.copy(error = "Сначала импортируй вопросы")
            return
        }

        pushUndo(UndoEffect.StopHosting)
        stopAllInternal(clearUndo = false)

        _ui.value = state.copy(role = AppRole.HOST, stage = AppStage.LOBBY, players = emptyList(), error = null)

        server = QuizServer(nsd) { evt ->
            when (evt) {
                is HostEvent.RoomStarted ->
                    _ui.value = _ui.value.copy(hostServiceName = evt.serviceName, hostPort = evt.port)
                is HostEvent.Error ->
                    _ui.value = _ui.value.copy(error = evt.message)
                is HostEvent.Players ->
                    _ui.value = _ui.value.copy(players = evt.players)

                is HostEvent.GameStarted -> {
                    _ui.value = _ui.value.copy(
                        stage = AppStage.GAME,
                        currentQuestion = null,
                        lastReveal = null,
                        scoreboard = emptyList(),
                        answeredForIndex = null,
                        answeredPayload = null
                    )
                }

                is HostEvent.GameCancelled -> {
                    _ui.value = _ui.value.copy(
                        stage = AppStage.LOBBY,
                        currentQuestion = null,
                        lastReveal = null,
                        scoreboard = emptyList(),
                        answeredForIndex = null,
                        answeredPayload = null,
                        error = evt.reason
                    )
                }

                is HostEvent.QuestionStarted -> {
                    _ui.value = _ui.value.copy(stage = AppStage.GAME)
                }

                is HostEvent.Reveal -> {
                    _ui.value = _ui.value.copy(lastReveal = evt.reveal, scoreboard = evt.reveal.scoreboard)
                }

                is HostEvent.GameOver -> {
                    _ui.value = _ui.value.copy(stage = AppStage.RESULTS, scoreboard = evt.scoreboard)
                }

                else -> {}
            }
        }.also {
            val port = it.start(state.roomCode, state.password.takeIf { p -> p.isNotBlank() }, state.questions)
            _ui.value = _ui.value.copy(hostPort = port)
            connectLocalHostClient(port)
        }
    }

    fun hostStartGame() {
        pushUndo(UndoEffect.CancelGame)
        _ui.value.hostPort?.let { connectLocalHostClient(it) }
        server?.startGame(durationMs = 15_000)
    }

    fun hostCancelGame() {
        pushUndo(UndoEffect.None)
        server?.cancelGame("Отменено ведущим")
        _ui.value = _ui.value.copy(stage = AppStage.LOBBY)
    }

    // CLIENT
    fun startJoinDiscovery() {
        pushUndo(UndoEffect.StopClientJoin)
        stopClientOnly(clearUndo = false)

        val state = _ui.value
        _ui.value = state.copy(role = AppRole.CLIENT, stage = AppStage.JOIN, error = null)

        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            nsd.discoverRoomByCode(state.roomCode).collect { r ->
                if (r.error != null) {
                    _ui.value = _ui.value.copy(error = r.error)
                    return@collect
                }
                if (r.host != null && r.port != null) {
                    _ui.value = _ui.value.copy(resolvedHost = r.host, resolvedPort = r.port, error = null)
                    connectClient(r.host, r.port)
                    this.cancel()
                }
            }
        }
    }

    private fun connectClient(host: String, port: Int) {
        val state = _ui.value
        client = QuizClient { evt ->
            when (evt) {
                is ClientEvent.Joined -> {
                    val currentStage = _ui.value.stage
                    val nextStage = if (currentStage == AppStage.GAME || currentStage == AppStage.RESULTS) {
                        currentStage
                    } else {
                        AppStage.LOBBY
                    }
                    _ui.value = _ui.value.copy(stage = nextStage, players = evt.players, error = null)
                }
                is ClientEvent.Players -> _ui.value = _ui.value.copy(players = evt.players)
                is ClientEvent.GameStarted -> _ui.value = _ui.value.copy(stage = AppStage.GAME, error = null)

                is ClientEvent.GameCancelled -> {
                    _ui.value = _ui.value.copy(
                        stage = AppStage.LOBBY,
                        currentQuestion = null,
                        lastReveal = null,
                        scoreboard = emptyList(),
                        answeredForIndex = null,
                        answeredPayload = null,
                        error = evt.reason
                    )
                }

                is ClientEvent.Question -> _ui.value = _ui.value.copy(
                    currentQuestion = evt.q,
                    lastReveal = null,
                    stage = AppStage.GAME,
                    answeredForIndex = null,
                    answeredPayload = null
                )
                is ClientEvent.Reveal -> _ui.value = _ui.value.copy(lastReveal = evt.r, scoreboard = evt.r.scoreboard)
                is ClientEvent.GameOver -> _ui.value = _ui.value.copy(stage = AppStage.RESULTS, scoreboard = evt.scoreboard)
                is ClientEvent.Error -> _ui.value = _ui.value.copy(error = evt.message)
            }
        }.also {
            it.connect(host, port, state.roomCode, state.nickname, state.password.takeIf { p -> p.isNotBlank() })
        }
    }

    private fun connectLocalHostClient(port: Int) {
        val state = _ui.value
        val host = "127.0.0.1"
        if (client == null) {
            connectClient(host, port)
        } else {
            client?.connect(host, port, state.roomCode, state.nickname, state.password.takeIf { p -> p.isNotBlank() })
        }
    }

    fun answerYesNo(value: Boolean) {
        val q = _ui.value.currentQuestion ?: return
        pushUndo(UndoEffect.ClearMyAnswer(q.index))
        client?.sendAnswer(q.index, WsMsg.AnswerPayload.Bool(value))
        _ui.value = _ui.value.copy(answeredForIndex = q.index, answeredPayload = WsMsg.AnswerPayload.Bool(value))
    }

    fun answerIndex(index: Int) {
        val q = _ui.value.currentQuestion ?: return
        pushUndo(UndoEffect.ClearMyAnswer(q.index))
        client?.sendAnswer(q.index, WsMsg.AnswerPayload.Index(index))
        _ui.value = _ui.value.copy(answeredForIndex = q.index, answeredPayload = WsMsg.AnswerPayload.Index(index))
    }

    fun stopAll() {
        pushUndo(UndoEffect.None)
        stopAllInternal(clearUndo = false)
        _ui.value = _ui.value.copy(role = AppRole.NONE, stage = AppStage.HOME)
    }

    private fun stopAllInternal(clearUndo: Boolean) {
        discoveryJob?.cancel(); discoveryJob = null
        runCatching { server?.stop() }
        server = null
        runCatching { client?.close() }
        client = null
        if (clearUndo) {
            undoStack.clear()
            canUndo.value = false
        }
    }

    private fun stopClientOnly(clearUndo: Boolean) {
        discoveryJob?.cancel(); discoveryJob = null
        runCatching { client?.close() }
        client = null
        if (clearUndo) {
            undoStack.clear()
            canUndo.value = false
        }
    }
}

private fun randomCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..4).map { alphabet.random() }.joinToString("")
}
