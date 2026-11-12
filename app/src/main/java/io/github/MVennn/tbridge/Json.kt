package io.github.MVennn.tbridge

/**
 * 极简 JSON —— 只做两件事：拼输出、解析一行请求。
 *
 * 不引 Gson / kotlinx.serialization 的理由：这两种都会被 R8 折腾（反射 / 序列化器生成），
 * 而本模块的协议形状是固定的、极小的。手写两百行比引依赖更可控，APK 也更小。
 *
 * 支持的输入：object / array / string / number / true / false / null。
 * 不支持：\u 转义以外的一切花活（够用，协议是自己定的）。
 */
object Json {

    // ------------------------------------------------------------ 输出

    fun obj(vararg kv: Pair<String, Any?>): String = buildString {
        append('{')
        var first = true
        for ((k, v) in kv) {
            if (v === Unit) continue
            if (!first) append(',')
            first = false
            append(str(k)); append(':'); append(value(v))
        }
        append('}')
    }

    fun objOf(m: Map<String, Any?>): String = obj(*m.entries.map { it.key to it.value }.toTypedArray())

    fun arr(items: List<Any?>): String = buildString {
        append('[')
        items.forEachIndexed { i, v -> if (i > 0) append(','); append(value(v)) }
        append(']')
    }

    private fun value(v: Any?) = when (v) {
        null -> "null"
        is String -> str(v)
        is Boolean, is Int, is Long, is Short, is Byte -> v.toString()
        is Double -> if (v.isFinite()) v.toString() else "null"
        is Float -> if (v.isFinite()) v.toString() else "null"
        is Map<*, *> -> objOf(v.entries.associate { it.key.toString() to it.value })
        is Iterable<*> -> arr(v.toList())
        is Array<*> -> arr(v.toList())
        else -> str(v.toString())
    }

    private fun str(s: String) = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
        }
        append('"')
    }

    // ------------------------------------------------------------ 解析

    fun parse(s: String): Map<String, Any?> {
        val p = P(s)
        val v = p.nextValue()
        p.skipWs()
        return (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
    }

    private class P(val s: String) {
        var i = 0
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun nextValue(): Any? {
            skipWs()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> nextObj()
                '[' -> nextArr()
                '"' -> nextStr()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> nextNum()
            }
        }
        fun nextObj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (i < s.length) {
                skipWs()
                val k = nextStr()
                skipWs(); if (i < s.length && s[i] == ':') i++
                m[k] = nextValue()
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                if (i < s.length && s[i] == '}') { i++; break }
                break
            }
            return m
        }
        fun nextArr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (i < s.length) {
                l.add(nextValue())
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                if (i < s.length && s[i] == ']') { i++; break }
                break
            }
            return l
        }
        fun nextStr(): String {
            val sb = StringBuilder()
            i++ // "
            while (i < s.length && s[i] != '"') {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i + 1, minOf(i + 5, s.length)).toInt(16).toChar()); i += 4 }
                        else -> sb.append(s[i])
                    }
                } else sb.append(c)
                i++
            }
            i++ // closing quote
            return sb.toString()
        }
        fun nextNum(): Any {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: 0
        }
    }
}
