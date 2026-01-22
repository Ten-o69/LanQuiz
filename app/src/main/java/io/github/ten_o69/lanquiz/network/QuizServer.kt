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
    private val sessionByPlayerId = LinkedHashMap<String, String>() // playerId -> sessionId
    private val players = LinkedHashMap<String, Player>() // id -> player
    private val score = LinkedHashMap<String, Pair<Int, Int>>() // id -> (correct, wrong)

    private var acceptingAnswersFor: Int = -1
    private val answers = LinkedHashMap<String, WsMsg.AnswerPayload>() // playerId -> answer for current question
    private var questionStartedAtMs: Long = 0L
    private var questionDurationMs: Long = 0L
    private var gameInProgress: Boolean = false
    private var gameFinished: Boolean = false
    private var lastScoreboard: List<ScoreDto> = emptyList()

    fun start(code: String, password: String?, questions: List<QuizQuestion>): Int {
        this.roomCode = code
        this.password = password
        this.questions = questions

        val port = pickFreePort()

        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(WebSockets) {
                pingPeriod = 30.seconds.toJavaDuration()
                timeout = 120.seconds.toJavaDuration()
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
        return port
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
                mutex.withLock {
                    gameInProgress = true
                    gameFinished = false
                    lastScoreboard = emptyList()
                }
                broadcast(WsMsg.StartGame(questions.size))
                onHostEvent(HostEvent.GameStarted(questions.size))

                for (i in questions.indices) {
                    ensureActive()
                    askQuestion(i, durationMs)
                }

                val final = mutex.withLock { buildScoreboard() }
                broadcast(WsMsg.GameOver(final))
                onHostEvent(HostEvent.GameOver(final))
                mutex.withLock {
                    lastScoreboard = final
                    gameInProgress = false
                    gameFinished = true
                }
                dropDisconnectedPlayers()
            } catch (_: CancellationException) {
                // отменено через cancelGame()
            } finally {
                mutex.withLock {
                    acceptingAnswersFor = -1
                    answers.clear()
                    questionStartedAtMs = 0L
                    questionDurationMs = 0L
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
                questionStartedAtMs = 0L
                questionDurationMs = 0L
                gameInProgress = false
                gameFinished = false
                lastScoreboard = emptyList()
            }
            resetScores()
            broadcast(WsMsg.GameCancelled(reason))
            onHostEvent(HostEvent.GameCancelled(reason))
            dropDisconnectedPlayers()
            broadcastPlayers()
        }
    }

    private suspend fun askQuestion(index: Int, durationMs: Long) {
        val q = questions[index]

        mutex.withLock {
            acceptingAnswersFor = index
            answers.clear()
            questionStartedAtMs = System.currentTimeMillis()
            questionDurationMs = durationMs
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
        var kickedSession: DefaultWebSocketServerSession? = null

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
                        kickedSession = null
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

                        val requestedId = msg.playerId?.takeIf { it.isNotBlank() }
                        val id = mutex.withLock {
                            val existing = requestedId?.takeIf { players.containsKey(it) }
                            val finalId = existing ?: UUID.randomUUID().toString()
                            sessions[sessionId] = ws
                            players[finalId] = Player(finalId, msg.name.take(24))
                            score.putIfAbsent(finalId, 0 to 0)
                            val oldSid = sessionByPlayerId.put(finalId, sessionId)
                            if (oldSid != null && oldSid != sessionId) {
                                kickedSession = sessions.remove(oldSid)
                            }
                            finalId
                        }
                        playerId = id

                        kickedSession?.let {
                            runCatching {
                                it.close(CloseReason(CloseReason.Codes.NORMAL, "Reconnected"))
                            }
                        }
                        kickedSession = null

                        val dto = mutex.withLock { players.values.map { PlayerDto(it.id, it.name) } }
                        ws.send(Frame.Text(WireCodec.encode(WsMsg.JoinOk(id, dto))))

                        broadcastPlayers()
                        onHostEvent(HostEvent.PlayerJoined(id, msg.name))

                        val snapshot = snapshotGameState()
                        if (snapshot.gameFinished) {
                            if (snapshot.scoreboard.isNotEmpty()) {
                                ws.send(Frame.Text(WireCodec.encode(WsMsg.GameOver(snapshot.scoreboard))))
                            }
                            continue
                        }
                        if (snapshot.gameInProgress) {
                            ws.send(Frame.Text(WireCodec.encode(WsMsg.StartGame(questions.size))))
                            val current = buildCurrentQuestion(snapshot)
                            if (current != null) {
                                ws.send(Frame.Text(WireCodec.encode(current)))
                            }
                        }
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
                if (pid != null && sessionByPlayerId[pid] == sessionId) {
                    sessionByPlayerId.remove(pid)
                }
                if (pid != null && !gameInProgress && sessionByPlayerId[pid] == null) {
                    players.remove(pid)
                    score.remove(pid)
                    answers.remove(pid)
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
        onHostEvent(HostEvent.Players(dto))
    }

    private suspend fun broadcast(msg: WsMsg) {
        val payload = WireCodec.encode(msg)
        val snapshot = mutex.withLock { sessions.toList() }
        val dead = mutableListOf<String>()
        for ((sid, s) in snapshot) {
            runCatching { s.send(Frame.Text(payload)) }.onFailure { dead += sid }
        }
        if (dead.isNotEmpty()) {
            mutex.withLock { dead.forEach { sessions.remove(it) } }
        }
    }

    private suspend fun dropDisconnectedPlayers() {
        mutex.withLock {
            val online = sessionByPlayerId.filterValues { sessions.containsKey(it) }.keys.toSet()
            val stale = sessionByPlayerId.keys.filter { it !in online }
            for (id in stale) {
                sessionByPlayerId.remove(id)
            }
            val toRemove = players.keys.filter { it !in online }
            for (id in toRemove) {
                players.remove(id)
                score.remove(id)
                answers.remove(id)
            }
        }
    }

    private suspend fun snapshotGameState(): GameSnapshot {
        return mutex.withLock {
            val finalBoard =
                if (gameFinished) {
                    if (lastScoreboard.isNotEmpty()) lastScoreboard else buildScoreboard()
                } else {
                    emptyList()
                }
            GameSnapshot(
                gameInProgress = gameInProgress,
                gameFinished = gameFinished,
                acceptingIndex = acceptingAnswersFor,
                questionStartedAtMs = questionStartedAtMs,
                questionDurationMs = questionDurationMs,
                scoreboard = finalBoard
            )
        }
    }

    private fun buildCurrentQuestion(snapshot: GameSnapshot): WsMsg.Question? {
        if (snapshot.acceptingIndex < 0) return null
        val q = questions.getOrNull(snapshot.acceptingIndex) ?: return null
        val now = System.currentTimeMillis()
        val elapsed = now - snapshot.questionStartedAtMs
        val remaining = (snapshot.questionDurationMs - elapsed).coerceAtLeast(0L)
        if (remaining <= 0L) return null
        return WsMsg.Question(
            index = snapshot.acceptingIndex,
            total = questions.size,
            text = q.text,
            kind = q.kind,
            options = q.options,
            durationMs = remaining
        )
    }

    private fun pickFreePort(): Int = ServerSocket(0).use { it.localPort }
}

private data class GameSnapshot(
    val gameInProgress: Boolean,
    val gameFinished: Boolean,
    val acceptingIndex: Int,
    val questionStartedAtMs: Long,
    val questionDurationMs: Long,
    val scoreboard: List<ScoreDto>
)

sealed class HostEvent {
    data class RoomStarted(val serviceName: String, val port: Int) : HostEvent()
    data class Error(val message: String) : HostEvent()
    data class PlayerJoined(val id: String, val name: String) : HostEvent()
    data class PlayerLeft(val id: String) : HostEvent()
    data class Players(val players: List<PlayerDto>) : HostEvent()
    data class GameStarted(val count: Int) : HostEvent()
    data class GameCancelled(val reason: String) : HostEvent()
    data class QuestionStarted(val index: Int, val total: Int, val q: QuizQuestion) : HostEvent()
    data class Reveal(val reveal: WsMsg.Reveal) : HostEvent()
    data class GameOver(val scoreboard: List<ScoreDto>) : HostEvent()
}
