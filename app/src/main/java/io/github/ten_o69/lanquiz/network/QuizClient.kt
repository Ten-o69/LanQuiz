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

    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }

    private var session: DefaultClientWebSocketSession? = null

    fun connect(host: String, port: Int, code: String, name: String, password: String?) {
        scope.launch {
            try {
                client.webSocket(host = host, port = port, path = "/ws") {
                    session = this
                    send(Frame.Text(WireCodec.encode(WsMsg.JoinReq(code, name, password))))

                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val msg = WireCodec.decode(text)

                        when (msg) {
                            is WsMsg.JoinOk -> onEvent(ClientEvent.Joined(msg.playerId, msg.players))
                            is WsMsg.JoinDenied -> onEvent(ClientEvent.Error(msg.reason))
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
            } catch (e: Exception) {
                onEvent(ClientEvent.Error("Connect error: ${e.message}"))
            } finally {
                session = null
            }
        }
    }

    fun sendAnswer(questionIndex: Int, payload: WsMsg.AnswerPayload) {
        scope.launch {
            val s = session ?: return@launch
            runCatching {
                s.send(Frame.Text(WireCodec.encode(WsMsg.Answer(questionIndex, payload))))
            }
        }
    }

    fun clearAnswer(questionIndex: Int) {
        scope.launch {
            val s = session ?: return@launch
            runCatching {
                s.send(Frame.Text(WireCodec.encode(WsMsg.ClearAnswer(questionIndex))))
            }
        }
    }

    fun close() {
        scope.cancel()
        runCatching { client.close() }
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