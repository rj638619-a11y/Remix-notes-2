package com.example.util

object HashUtil {
    /**
     * Replicates:
     * function hashStr(s){let h=5381;for(let i=0;i<s.length;i++)h=(h*33+s.charCodeAt(i))|0;return(h>>>0).toString(36)}
     * const noteHash=(t,c)=>hashStr(String(t).trim().toLowerCase()+'\u0000'+String(c).replace(/\s+/g,' ').trim());
     */
    fun hashStr(s: String): String {
        var h = 5381
        for (ch in s) {
            h = ((h * 33) + ch.code) or 0
        }
        val unsignedH = h.toLong() and 0xFFFFFFFFL
        return java.lang.Long.toString(unsignedH, 36)
    }

    fun noteHash(title: String, content: String): String {
        val cleanTitle = title.trim().lowercase()
        val cleanContent = content.replace(Regex("\\s+"), " ").trim()
        return hashStr("$cleanTitle\u0000$cleanContent")
    }

    fun sha256(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            hashStr(input)
        }
    }
}
