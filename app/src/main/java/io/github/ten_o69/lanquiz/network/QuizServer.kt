package io.github.ten_o69.lanquiz.network

import io.github.ten_o69.lanquiz.data.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ServerSocket
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class QuizServer(
    private val nsd: NsdHelper,
    private val onHostEvent: (HostEvent) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var engine: ApplicationEngine? = null
    private var gameJob: Job? = null

    private var roomCode: String = ""
    private var password: String? = null
    private var questions: List<QuizQuestion> = emptyList()

    private val sessions = LinkedHashMap<String, DefaultWebSocketServerSession>()
    private val players = LinkedHashMap<String, Player>() // id -> player
    private val score = LinkedHashMap<String, Pair<Int, Int>>() // id -> (correct, wrong)

    private var acceptingAnswersFor: Int = -1
    private val answers = LinkedHashMap<String, WsMsg.AnswerPayload>() // playerId -> answer for current question

    fun start(code: String, password: String?, questions: List<QuizQuestion>) {
        this.roomCode = code
        this.password = password
        this.questions = questions

        val port = pickFreePort()

        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(WebSockets) {
                pingPeriod = 15.seconds.toJavaDuration()
                timeout = 30.seconds.toJavaDuration()
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/ws") { handleSession(this) }
            }
        }.start(wait = false)

        nsd.registerRoom(serviceName = "QUIZ-$code", port = port,
            onResult = { realName -> onHostEvent(HostEvent.RoomStarted(realName, port)) },
            onError = { err -> onHostEvent(HostEvent.Error(err)) }
        )
    }

    fun stop() {
        cancelGame("Сервер остановлен")
        nsd.unregister()
        scope.cancel()
        runCatching { engine?.stop(500, 1500) }
        engine = null
    }

    fun startGame(durationMs: Long = 15_000) {
        if (questions.isEmpty()) {
            onHostEvent(HostEvent.Error("Нет вопросов"))
            return
        }
        if (gameJob?.isActive == true) return

        gameJob = scope.launch {
            try {
                resetScores()
                broadcast(WsMsg.StartGame(questions.size))
                onHostEvent(HostEvent.GameStarted(questions.size))

                for (i in questions.indices) {
                    ensureActive()
                    askQuestion(i, durationMs)
                }

                val final = buildScoreboard()
                broadcast(WsMsg.GameOver(final))
                onHostEvent(HostEvent.GameOver(final))
            } catch (_: CancellationException) {
                // отменено через cancelGame()
            } finally {
                mutex.withLock {
                    acceptingAnswersFor = -1
                    answers.clear()
                }
            }
        }
    }

    fun cancelGame(reason: String = "Отменено ведущим") {
        gameJob?.cancel()
        gameJob = null

        scope.launch {
            mutex.withLock {
                acceptingAnswersFor = -1
                answers.clear()
            }
            resetScores()
            broadcast(WsMsg.GameCancelled(reason))
            onHostEvent(HostEvent.GameCancelled(reason))
        }
    }

    private suspend fun askQuestion(index: Int, durationMs: Long) {
        val q = questions[index]

        mutex.withLock {
            acceptingAnswersFor = index
            answers.clear()
        }

        broadcast(
            WsMsg.Question(
                index = index,
                total = questions.size,
                text = q.text,
                kind = q.kind,
                options = q.options,
                durationMs = durationMs
            )
        )
        onHostEvent(HostEvent.QuestionStarted(index, questions.size, q))

        delay(durationMs)

        val reveal = mutex.withLock { applyScoringAndBuildReveal(index, q) }
        broadcast(reveal)
        onHostEvent(HostEvent.Reveal(reveal))

        delay(1500)
    }

    private fun applyScoringAndBuildReveal(index: Int, q: QuizQuestion): WsMsg.Reveal {
        val correctPayload: WsMsg.CorrectPayload =
            when (q.kind) {
                QuestionKind.YESNO -> WsMsg.CorrectPayload.Bool(q.correctBool ?: false)
                QuestionKind.MULTI -> WsMsg.CorrectPayload.Index(q.correctIndex ?: 0)
            }

        for ((playerId, _) in players) {
            val ans = answers[playerId]
            val isCorrect = isCorrect(q, ans)
            val (c, w) = score[playerId] ?: (0 to 0)
            score[playerId] = if (isCorrect) (c + 1 to w) else (c to w + 1)
        }

        return WsMsg.Reveal(
            questionIndex = index,
            correct = correctPayload,
            scoreboard = buildScoreboard()
        )
    }

    private fun isCorrect(q: QuizQuestion, ans: WsMsg.AnswerPayload?): Boolean {
        if (ans == null) return false
        return when {
            q.kind == QuestionKind.YESNO && ans is WsMsg.AnswerPayload.Bool ->
                q.correctBool == ans.value
            q.kind == QuestionKind.MULTI && ans is WsMsg.AnswerPayload.Index ->
                q.correctIndex == ans.value
            else -> false
        }
    }

    private fun buildScoreboard(): List<ScoreDto> {
        return players.values.map { p ->
            val (c, w) = score[p.id] ?: (0 to 0)
            ScoreDto(p.id, p.name, c, w)
        }.sortedWith(
            compareByDescending<ScoreDto> { it.correct }
                .thenBy { it.wrong }
                .thenBy { it.name.lowercase() }
        )
    }

    private suspend fun resetScores() {
        mutex.withLock {
            for (p in players.values) {
                score[p.id] = 0 to 0
            }
        }
    }

    private suspend fun handleSession(ws: DefaultWebSocketServerSession) {
        val sessionId = UUID.randomUUID().toString()
        var playerId: String? = null

        try {
            for (frame in ws.incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val msg = runCatching { WireCodec.decode(text) }.getOrNull()
                    ?: run {
                        ws.send(Frame.Text(WireCodec.encode(WsMsg.Error("Bad message"))))
                        continue
                    }

                when (msg) {
                    is WsMsg.JoinReq -> {
                        if (!msg.roomCode.equals(roomCode, ignoreCase = true)) {
                            ws.send(Frame.Text(WireCodec.encode(WsMsg.JoinDenied("Неверный код"))))
                            ws.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Bad code"))
                            return
                        }
                        if (!password.isNullOrBlank()) {
                            if ((msg.password ?: "") != password) {
                                ws.send(Frame.Text(WireCodec.encode(WsMsg.JoinDenied("Неверный пароль"))))
                                ws.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Bad password"))
                                return
                            }
                        }

                        val id = UUID.randomUUID().toString()
                        playerId = id

                        mutex.withLock {
                            sessions[sessionId] = ws
                            val player = Player(id, msg.name.take(24))
                            players[id] = player
                            score.putIfAbsent(id, 0 to 0)
                        }

                        val dto = mutex.withLock { players.values.map { PlayerDto(it.id, it.name) } }
                        ws.send(Frame.Text(WireCodec.encode(WsMsg.JoinOk(id, dto))))

                        broadcastPlayers()
                        onHostEvent(HostEvent.PlayerJoined(id, msg.name))
                    }

                    is WsMsg.Answer -> {
                        val pid = playerId ?: continue
                        mutex.withLock {
                            // теперь можно менять ответ сколько угодно, пока вопрос активен
                            if (msg.questionIndex == acceptingAnswersFor) {
                                answers[pid] = msg.answer
                            }
                        }
                    }

                    is WsMsg.ClearAnswer -> {
                        val pid = playerId ?: continue
                        mutex.withLock {
                            if (msg.questionIndex == acceptingAnswersFor) {
                                answers.remove(pid)
                            }
                        }
                    }

                    else -> {}
                }
            }
        } finally {
            val pid = playerId
            mutex.withLock {
                sessions.remove(sessionId)
                if (pid != null) {
                    players.remove(pid)
                    score.remove(pid)
                }
            }
            if (pid != null) {
                broadcastPlayers()
                onHostEvent(HostEvent.PlayerLeft(pid))
            }
        }
    }

    private suspend fun broadcastPlayers() {
        val dto = mutex.withLock { players.values.map { PlayerDto(it.id, it.name) } }
        broadcast(WsMsg.Players(dto))
    }

    private suspend fun broadcast(msg: WsMsg) {
        val payload = Frame.Text(WireCodec.encode(msg))
        val dead = mutableListOf<String>()
        mutex.withLock {
            sessions.forEach { (sid, s) ->
                runCatching { s.send(payload) }.onFailure { dead += sid }
            }
            dead.forEach { sessions.remove(it) }
        }
    }

    private fun pickFreePort(): Int = ServerSocket(0).use { it.localPort }
}

sealed class HostEvent {
    data class RoomStarted(val serviceName: String, val port: Int) : HostEvent()
    data class Error(val message: String) : HostEvent()
    data class PlayerJoined(val id: String, val name: String) : HostEvent()
    data class PlayerLeft(val id: String) : HostEvent()
    data class GameStarted(val count: Int) : HostEvent()
    data class GameCancelled(val reason: String) : HostEvent()
    data class QuestionStarted(val index: Int, val total: Int, val q: QuizQuestion) : HostEvent()
    data class Reveal(val reveal: WsMsg.Reveal) : HostEvent()
    data class GameOver(val scoreboard: List<ScoreDto>) : HostEvent()
}