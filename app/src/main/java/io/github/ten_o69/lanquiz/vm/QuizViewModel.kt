package io.github.ten_o69.lanquiz.vm

import android.app.Application
import android.content.Context
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
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UiState(
    val role: AppRole = AppRole.NONE,
    val stage: AppStage = AppStage.HOME,
    val selectedRole: AppRole = AppRole.HOST,

    val nickname: String = "Player-${(1000..9999).random()}",
    val roomCode: String = randomCode(),
    val password: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    val questions: List<QuizQuestion> = emptyList(),
    val players: List<PlayerDto> = emptyList(),

    val currentQuestion: WsMsg.Question? = null,
    val lastReveal: WsMsg.Reveal? = null,
    val scoreboard: List<ScoreDto> = emptyList(),

    // чтобы Undo работал на ответы
    val answeredForIndex: Int? = null,
    val answeredPayload: WsMsg.AnswerPayload? = null,

    val timerEnabled: Boolean = true,
    val timerSeconds: Int = 15,
    val manualAdvance: Boolean = false,

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
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var server: QuizServer? = null
    private var client: QuizClient? = null
    private var discoveryJob: Job? = null
    private var hostEventsEnabled = true
    private var allowClientErrors = true

    private val undoStack = ArrayDeque<UndoEntry>()
    val canUndo = MutableStateFlow(false)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val mode = runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
        _ui.value = _ui.value.copy(themeMode = mode)
    }

    fun go(stage: AppStage) = pushUndoAndUpdate(UndoEffect.None) {
        it.copy(stage = stage, error = null)
    }

    fun setSelectedRole(v: AppRole) = pushUndoAndUpdate(UndoEffect.None) { it.copy(selectedRole = v) }
    fun setNickname(v: String) = pushUndoAndUpdate(UndoEffect.None) { it.copy(nickname = v) }
    fun setRoomCode(v: String) = pushUndoAndUpdate(UndoEffect.None) { it.copy(roomCode = v.uppercase()) }
    fun setPassword(v: String) = pushUndoAndUpdate(UndoEffect.None) { it.copy(password = v) }
    fun setThemeMode(v: ThemeMode) {
        pushUndoAndUpdate(UndoEffect.None) { it.copy(themeMode = v) }
        prefs.edit().putString("theme_mode", v.name).apply()
    }

    fun setTimerEnabled(v: Boolean) = pushUndoAndUpdate(UndoEffect.None) {
        val manual = if (!v) true else it.manualAdvance
        it.copy(timerEnabled = v, manualAdvance = manual)
    }

    fun setTimerSeconds(v: Int) = pushUndoAndUpdate(UndoEffect.None) {
        it.copy(timerSeconds = v.coerceIn(5, 60))
    }

    fun setManualAdvance(v: Boolean) = pushUndoAndUpdate(UndoEffect.None) {
        val timer = if (!v) true else it.timerEnabled
        it.copy(manualAdvance = v, timerEnabled = timer)
    }

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

    fun clearQuestions() {
        pushUndo(UndoEffect.None)
        _ui.value = _ui.value.copy(questions = emptyList(), error = null)
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

        hostEventsEnabled = true
        server = QuizServer(nsd) host@ { evt ->
            if (!hostEventsEnabled) return@host
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
                        questions = emptyList(),
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
                    _ui.value = _ui.value.copy(
                        stage = AppStage.RESULTS,
                        questions = emptyList(),
                        currentQuestion = null,
                        lastReveal = null,
                        answeredForIndex = null,
                        answeredPayload = null,
                        scoreboard = evt.scoreboard
                    )
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
        val state = _ui.value
        server?.startGame(
            durationMs = state.timerSeconds * 1000L,
            timerEnabled = state.timerEnabled,
            manualAdvance = state.manualAdvance
        )
    }

    fun hostCancelGame() {
        pushUndo(UndoEffect.None)
        hostEventsEnabled = false
        stopAllInternal(clearUndo = true, serverStopReason = "Отменено ведущим")
        resetSessionState(clearQuestions = true)
    }

    fun hostNext() {
        server?.hostNext()
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
        allowClientErrors = true
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
                    if (_ui.value.stage != AppStage.HOME) {
                        stopAllInternal(clearUndo = true)
                        resetSessionState(clearQuestions = false)
                    }
                }

                is ClientEvent.Question -> _ui.value = _ui.value.copy(
                    currentQuestion = evt.q,
                    lastReveal = null,
                    stage = AppStage.GAME,
                    answeredForIndex = null,
                    answeredPayload = null
                )
                is ClientEvent.Reveal -> _ui.value = _ui.value.copy(lastReveal = evt.r, scoreboard = evt.r.scoreboard)
                is ClientEvent.GameOver -> _ui.value = _ui.value.copy(
                    stage = AppStage.RESULTS,
                    currentQuestion = null,
                    lastReveal = null,
                    answeredForIndex = null,
                    answeredPayload = null,
                    scoreboard = evt.scoreboard
                )
                is ClientEvent.Error -> {
                    if (allowClientErrors) {
                        _ui.value = _ui.value.copy(error = evt.message)
                    }
                }
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
        hostEventsEnabled = false
        stopAllInternal(clearUndo = true, serverStopReason = "Сессия завершена")
        resetSessionState(clearQuestions = true)
    }

    private fun stopAllInternal(clearUndo: Boolean, serverStopReason: String? = null) {
        discoveryJob?.cancel(); discoveryJob = null
        allowClientErrors = false
        if (server != null) {
            hostEventsEnabled = false
        }
        if (serverStopReason != null) {
            runCatching { server?.stop(serverStopReason) }
        } else {
            runCatching { server?.stop() }
        }
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
        allowClientErrors = false
        runCatching { client?.close() }
        client = null
        if (clearUndo) {
            undoStack.clear()
            canUndo.value = false
        }
    }

    private fun resetSessionState(clearQuestions: Boolean) {
        val state = _ui.value
        _ui.value = state.copy(
            role = AppRole.NONE,
            stage = AppStage.HOME,
            roomCode = randomCode(),
            password = "",
            questions = if (clearQuestions) emptyList() else state.questions,
            players = emptyList(),
            currentQuestion = null,
            lastReveal = null,
            scoreboard = emptyList(),
            answeredForIndex = null,
            answeredPayload = null,
            hostServiceName = null,
            hostPort = null,
            resolvedHost = null,
            resolvedPort = null,
            error = null
        )
    }
}

private fun randomCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..4).map { alphabet.random() }.joinToString("")
}
