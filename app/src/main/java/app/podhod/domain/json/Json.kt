package app.podhod.domain.json

/**
 * Минимальный JSON без внешних зависимостей: чистый Kotlin, поэтому формат резервной копии
 * (разбор, проверка структуры, обработка повреждённых файлов) тестируется на обычной JVM.
 */
sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = fields[key]
    }
}

class JsonException(message: String) : Exception(message)

object JsonParser {
    private const val MAX_DEPTH = 64

    fun parse(text: String): JsonValue {
        val p = Parser(text.removePrefix("﻿"))
        val v = p.readValue(0)
        p.skipWs()
        if (!p.atEnd()) p.fail("лишние данные после конца JSON")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length

        fun fail(msg: String): Nothing = throw JsonException("Ошибка JSON в позиции $i: $msg")

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) fail("слишком большая вложенность")
            skipWs()
            if (atEnd()) fail("неожиданный конец файла")
            return when (val c = s[i]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("неожиданный символ '$c'")
            }
        }

        fun literal(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) fail("ожидалось $word")
            i += word.length
            return value
        }

        fun readObject(depth: Int): JsonValue {
            i++ // {
            val map = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return JsonValue.Obj(map)
            }
            while (true) {
                skipWs()
                if (atEnd() || s[i] != '"') fail("ожидался ключ в кавычках")
                val key = readString()
                skipWs()
                if (atEnd() || s[i] != ':') fail("ожидалось ':'")
                i++
                map[key] = readValue(depth + 1)
                skipWs()
                if (atEnd()) fail("объект не закрыт")
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return JsonValue.Obj(map)
                    }
                    else -> fail("ожидалось ',' или '}'")
                }
            }
        }

        fun readArray(depth: Int): JsonValue {
            i++ // [
            val list = ArrayList<JsonValue>()
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return JsonValue.Arr(list)
            }
            while (true) {
                list += readValue(depth + 1)
                skipWs()
                if (atEnd()) fail("массив не закрыт")
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return JsonValue.Arr(list)
                    }
                    else -> fail("ожидалось ',' или ']'")
                }
            }
        }

        fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("строка не закрыта")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) fail("незавершённая escape-последовательность")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("неверный \\u")
                                val hex = s.substring(i, i + 4)
                                val code = hex.toIntOrNull(16) ?: fail("неверный \\u$hex")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("неизвестный escape '\\$e'")
                        }
                    }
                    c < ' ' -> fail("управляющий символ в строке")
                    else -> sb.append(c)
                }
            }
        }

        fun readNumber(): JsonValue {
            val start = i
            if (s[i] == '-') i++
            if (atEnd() || s[i] !in '0'..'9') fail("неверное число")
            if (s[i] == '0') {
                i++
            } else {
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && s[i] == '.') {
                i++
                if (atEnd() || s[i] !in '0'..'9') fail("неверное число")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (atEnd() || s[i] !in '0'..'9') fail("неверное число")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val v = s.substring(start, i).toDoubleOrNull() ?: fail("неверное число")
            if (!v.isFinite()) fail("число вне диапазона")
            return JsonValue.Num(v)
        }
    }
}

object JsonWriter {
    fun write(value: JsonValue, pretty: Boolean = false): String {
        val sb = StringBuilder()
        writeValue(sb, value, pretty, 0)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, v: JsonValue, pretty: Boolean, depth: Int) {
        when (v) {
            JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(if (v.value) "true" else "false")
            is JsonValue.Num -> sb.append(number(v.value))
            is JsonValue.Str -> writeString(sb, v.value)
            is JsonValue.Arr -> {
                if (v.items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                v.items.forEachIndexed { idx, item ->
                    if (idx > 0) sb.append(',')
                    newline(sb, pretty, depth + 1)
                    writeValue(sb, item, pretty, depth + 1)
                }
                newline(sb, pretty, depth)
                sb.append(']')
            }
            is JsonValue.Obj -> {
                if (v.fields.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                var first = true
                for ((k, item) in v.fields) {
                    if (!first) sb.append(',')
                    first = false
                    newline(sb, pretty, depth + 1)
                    writeString(sb, k)
                    sb.append(if (pretty) ": " else ":")
                    writeValue(sb, item, pretty, depth + 1)
                }
                newline(sb, pretty, depth)
                sb.append('}')
            }
        }
    }

    private fun newline(sb: StringBuilder, pretty: Boolean, depth: Int) {
        if (!pretty) return
        sb.append('\n')
        repeat(depth) { sb.append("  ") }
    }

    private fun number(d: Double): String =
        if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
