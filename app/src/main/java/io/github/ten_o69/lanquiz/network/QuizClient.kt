package io.github.ten_o69.lanquiz.network

import io.github.ten_o69.lanquiz.data.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

class QuizClient(
    private val onEvent: (ClientEvent) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ConnectParams(
        val host: String,
        val port: Int,
        val code: String,
        val name: String,
        val password: String?
    )

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 15_000L
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var connectJob: Job? = null
    @Volatile private var closing = false
    private var params: ConnectParams? = null
    private var playerId: String? = null
    @Volatile private var reconnectEnabled = true
    @Volatile private var reportedDisconnect = false

    fun connect(host: String, port: Int, code: String, name: String, password: String?) {
        val next = ConnectParams(host, port, code, name, password)
        if (params == next && session?.isActive == true && connectJob?.isActive == true) {
            return
        }
        params = next
        closing = false
        reconnectEnabled = true
        reportedDisconnect = false
        startConnectLoop()
    }

    fun sendAnswer(questionIndex: Int, payload: WsMsg.AnswerPayload) {
        scope.launch {
            val s = session ?: return@launch
            if (!s.isActive) return@launch
            runCatching {
                s.send(Frame.Text(WireCodec.encode(WsMsg.Answer(questionIndex, payload))))
            }
        }
    }

    fun clearAnswer(questionIndex: Int) {
        scope.launch {
            val s = session ?: return@launch
            if (!s.isActive) return@launch
            runCatching {
                s.send(Frame.Text(WireCodec.encode(WsMsg.ClearAnswer(questionIndex))))
            }
        }
    }

    fun close() {
        closing = true
        reconnectEnabled = false
        connectJob?.cancel()
        connectJob = null
        scope.cancel()
        runCatching { client.close() }
    }

    private fun startConnectLoop() {
        connectJob?.cancel()
        connectJob = scope.launch {
            var attempt = 0
            while (isActive && !closing) {
                val p = params ?: return@launch
                if (session?.isActive == true) {
                    delay(500L)
                    continue
                }
                try {
                    client.webSocket(host = p.host, port = p.port, path = "/ws") {
                        session = this
                        send(Frame.Text(WireCodec.encode(WsMsg.JoinReq(p.code, p.name, p.password, playerId))))

                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            val msg = runCatching { WireCodec.decode(text) }.getOrNull() ?: continue

                            when (msg) {
                                is WsMsg.JoinOk -> {
                                    playerId = msg.playerId
                                    reportedDisconnect = false
                                    attempt = 0
                                    onEvent(ClientEvent.Joined(msg.playerId, msg.players))
                                }
                                is WsMsg.JoinDenied -> {
                                    reconnectEnabled = false
                                    onEvent(ClientEvent.Error(msg.reason))
                                    return@webSocket
                                }
                                is WsMsg.Players -> onEvent(ClientEvent.Players(msg.players))
                                is WsMsg.StartGame -> onEvent(ClientEvent.GameStarted(msg.questionCount))
                                is WsMsg.GameCancelled -> onEvent(ClientEvent.GameCancelled(msg.reason))
                                is WsMsg.Question -> onEvent(ClientEvent.Question(msg))
                                is WsMsg.Reveal -> onEvent(ClientEvent.Reveal(msg))
                                is WsMsg.GameOver -> onEvent(ClientEvent.GameOver(msg.scoreboard))
                                is WsMsg.Error -> onEvent(ClientEvent.Error(msg.message))
                                else -> {}
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    if (closing || !reconnectEnabled) return@launch
                } finally {
                    session = null
                }

                if (closing || !reconnectEnabled) return@launch
                if (!reportedDisconnect) {
                    reportedDisconnect = true
                    onEvent(ClientEvent.Error("Проблемы с подключением. Переподключаемся..."))
                }

                attempt = (attempt + 1).coerceAtMost(8)
                delay(400L * attempt)
            }
        }
    }
}

sealed class ClientEvent {
    data class Joined(val myId: String, val players: List<PlayerDto>) : ClientEvent()
    data class Players(val players: List<PlayerDto>) : ClientEvent()
    data class GameStarted(val count: Int) : ClientEvent()
    data class GameCancelled(val reason: String) : ClientEvent()
    data class Question(val q: WsMsg.Question) : ClientEvent()
    data class Reveal(val r: WsMsg.Reveal) : ClientEvent()
    data class GameOver(val scoreboard: List<ScoreDto>) : ClientEvent()
    data class Error(val message: String) : ClientEvent()
}
