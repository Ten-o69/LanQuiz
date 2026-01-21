package io.github.ten_o69.lanquiz.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QuestionKind { YESNO, MULTI }

@Serializable
data class QuizQuestion(
    val text: String,
    val kind: QuestionKind,
    val options: List<String> = emptyList(), // для MULTI
    val correctIndex: Int? = null,           // для MULTI (0-based)
    val correctBool: Boolean? = null         // для YESNO
)

data class Player(
    val id: String,
    val name: String
)

data class PlayerScore(
    val playerId: String,
    val name: String,
    val correct: Int,
    val wrong: Int
)