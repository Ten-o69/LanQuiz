package io.github.ten_o69.lanquiz.data

import android.content.ContentResolver
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ImportParsers {

    suspend fun importAny(resolver: ContentResolver, uri: Uri): List<QuizQuestion> {
        val name = resolver.getType(uri) ?: ""
        val fileName = resolver.query(uri, null, null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else ""
            } ?: ""

        val lower = (fileName.ifBlank { name }).lowercase()

        return when {
            lower.endsWith(".json") -> importJson(resolver, uri)
            lower.endsWith(".txt") -> importTxt(resolver, uri)
            lower.endsWith(".xlsx") -> importXlsx(resolver, uri)
            else -> throw IllegalArgumentException("Неизвестный формат файла: $fileName")
        }
    }

    private fun importJson(resolver: ContentResolver, uri: Uri): List<QuizQuestion> {
        val text = resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        return WireCodec.json.decodeFromString(text)
    }

    /**
     * TXT формат блоками по 3 строки, блоки разделяются пустой строкой:
     * 1) Вопрос
     * 2) "YESNO" или варианты через ';'   (пример: A;B;C;D)
     * 3) правильный: "да/нет/true/false" или индекс 1..N (или текст варианта)
     */
    private fun importTxt(resolver: ContentResolver, uri: Uri): List<QuizQuestion> {
        val lines = resolver.openInputStream(uri)!!.bufferedReader().readLines()
        val blocks = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        for (l in lines) {
            if (l.isBlank()) {
                if (cur.isNotEmpty()) { blocks += cur.toList(); cur = mutableListOf() }
            } else cur += l.trim()
        }
        if (cur.isNotEmpty()) blocks += cur

        return blocks.mapIndexed { i, b ->
            require(b.size >= 3) { "TXT: блок #${i + 1} должен иметь минимум 3 строки" }
            val q = b[0]
            val second = b[1]
            val third = b[2]

            if (second.equals("YESNO", ignoreCase = true) || second.contains("да", true) || second.contains("нет", true)) {
                val corr = parseBool(third)
                QuizQuestion(text = q, kind = QuestionKind.YESNO, correctBool = corr)
            } else {
                val options = second.split(';').map { it.trim() }.filter { it.isNotBlank() }
                require(options.size >= 2) { "TXT: у MULTI должно быть >=2 вариантов" }
                val correctIndex = parseCorrectIndex(third, options)
                QuizQuestion(text = q, kind = QuestionKind.MULTI, options = options, correctIndex = correctIndex)
            }
        }
    }

    /**
     * XLSX: 1 лист, строки:
     * Col A: question
     * Col B: "YESNO" или "A;B;C;D"
     * Col C: correct ("да/нет/true/false" или индекс 1..N или текст варианта)
     */
    private fun importXlsx(resolver: ContentResolver, uri: Uri): List<QuizQuestion> {
        resolver.openInputStream(uri)!!.use { input ->
            val wb = XSSFWorkbook(input)
            val sheet = wb.getSheetAt(0)

            val out = mutableListOf<QuizQuestion>()
            for (row in sheet) {
                val q = row.getCell(0)?.stringCellValue?.trim().orEmpty()
                val b = row.getCell(1)?.stringCellValue?.trim().orEmpty()
                val c = row.getCell(2)?.stringCellValue?.trim().orEmpty()
                if (q.isBlank()) continue

                if (b.equals("YESNO", ignoreCase = true)) {
                    out += QuizQuestion(text = q, kind = QuestionKind.YESNO, correctBool = parseBool(c))
                } else {
                    val options = b.split(';').map { it.trim() }.filter { it.isNotBlank() }
                    out += QuizQuestion(
                        text = q,
                        kind = QuestionKind.MULTI,
                        options = options,
                        correctIndex = parseCorrectIndex(c, options)
                    )
                }
            }
            wb.close()
            return out
        }
    }

    private fun parseBool(s: String): Boolean {
        val t = s.trim().lowercase()
        return when (t) {
            "да", "yes", "true", "1" -> true
            "нет", "no", "false", "0" -> false
            else -> throw IllegalArgumentException("Не могу распарсить YES/NO: '$s'")
        }
    }

    private fun parseCorrectIndex(s: String, options: List<String>): Int {
        val t = s.trim()
        // 1) индекс 1..N
        t.toIntOrNull()?.let { n ->
            val idx = n - 1
            require(idx in options.indices) { "Correct index вне диапазона: $n" }
            return idx
        }
        // 2) совпадение по тексту
        val found = options.indexOfFirst { it.equals(t, ignoreCase = true) }
        require(found >= 0) { "Correct value '$s' не найден среди вариантов" }
        return found
    }
}