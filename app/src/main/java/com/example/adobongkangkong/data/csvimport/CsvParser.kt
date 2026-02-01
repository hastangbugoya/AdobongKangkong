package com.example.adobongkangkong.data.csvimport

import android.util.Log

object CsvParser {

    /**
     * Parses a single CSV line into fields.
     * - Handles quoted fields with commas.
     * - Handles double quotes inside quoted values ("").
     */
    fun parseLine(line: String): List<String> {
        val out = ArrayList<String>(32)
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        out.forEach { s ->
            Log.d("Meow","Parsed: $s")
        }
        return out
    }
}
