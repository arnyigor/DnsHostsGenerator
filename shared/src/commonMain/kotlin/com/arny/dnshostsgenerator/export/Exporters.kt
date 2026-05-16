package com.arny.dnshostsgenerator.export

import com.arny.dnshostsgenerator.domain.GenerationResult

object HostsExporter {
    fun toText(result: GenerationResult): String = result.outputText
}

object CsvExporter {
    fun toComparisonCsv(results: List<GenerationResult>): String {
        if (results.isEmpty()) return ""

        val minRows = results.minOf { it.lines.size }
        val header = results.joinToString(separator = ";") { escapeCsv(it.presetTitle) }
        val rows = (0 until minRows).map { rowIndex ->
            results.joinToString(separator = ";") { result ->
                escapeCsv(result.lines[rowIndex].toOutputLine())
            }
        }

        return (listOf(header) + rows).joinToString(separator = "\n")
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
