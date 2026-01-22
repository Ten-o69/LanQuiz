package io.github.ten_o69.lanquiz.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class WsMsg {

    @Serializable @SerialName("join_req")
    data class JoinReq(
        val roomCode: String,
        val name: String,
        val password: String? = null,
        val playerId: String? = null
    ) : WsMsg()

    @Serializable @SerialName("join_ok")
    data class JoinOk(
        val playerId: String,
        val players: List<PlayerDto>
    ) : WsMsg()

    @Serializable @SerialName("join_denied")
    data class JoinDenied(val reason: String) : WsMsg()

    @Serializable @SerialName("players")
    data class Players(val players: List<PlayerDto>) : WsMsg()

    @Serializable @SerialName("start_game")
    data class StartGame(val questionCount: Int) : WsMsg()

    @Serializable @SerialName("game_cancelled")
    data class GameCancelled(val reason: String = "Отменено ведущим") : WsMsg()

    @Serializable @SerialName("question")
    data class Question(
        val index: Int,
        val total: Int,
        val text: String,
        val kind: QuestionKind,
        val options: List<String> = emptyList(),
        val durationMs: Long = 15_000
    ) : WsMsg()

    @Serializable @SerialName("answer")
    data class Answer(
        val questionIndex: Int,
        val answer: AnswerPayload
    ) : WsMsg()

    @Serializable @SerialName("clear_answer")
    data class ClearAnswer(
        val questionIndex: Int
    ) : WsMsg()

    @Serializable
    sealed class AnswerPayload {
        @Serializable @SerialName("bool")
        data class Bool(val value: Boolean) : AnswerPayload()

        @Serializable @SerialName("index")
        data class Index(val value: Int) : AnswerPayload()
    }

    @Serializable @SerialName("reveal")
    data class Reveal(
        val questionIndex: Int,
        val correct: CorrectPayload,
        val scoreboard: List<ScoreDto>
    ) : WsMsg()

    @Serializable
    sealed class CorrectPayload {
        @Serializable @SerialName("bool")
        data class Bool(val value: Boolean) : CorrectPayload()

        @Serializable @SerialName("index")
        data class Index(val value: Int) : CorrectPayload()
    }

    @Serializable @SerialName("game_over")
    data class GameOver(val scoreboard: List<ScoreDto>) : WsMsg()

    @Serializable @SerialName("error")
    data class Error(val message: String) : WsMsg()
}

@Serializable
data class PlayerDto(val id: String, val name: String)

@Serializable
data class ScoreDto(
    val playerId: String,
    val name: String,
    val correct: Int,
    val wrong: Int
)

object WireCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(msg: WsMsg): String = json.encodeToString(WsMsg.serializer(), msg)
    fun decode(text: String): WsMsg = json.decodeFromString(WsMsg.serializer(), text)
}
