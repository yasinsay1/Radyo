package com.m3uradio.auto

import java.net.URI

object M3uParser {

    private val attrRegex = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")

    fun parse(text: String): List<Station> {
        val raw = mutableListOf<Station>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var extGroup: String? = null

        for (rawLine in text.removePrefix("﻿").lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val body = line.substringAfter(':', "")
                    val comma = firstCommaOutsideQuotes(body)
                    val attrPart = if (comma >= 0) body.substring(0, comma) else body
                    val title = if (comma >= 0) body.substring(comma + 1).trim() else ""
                    val attrs = attrRegex.findAll(attrPart)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = title.ifBlank { attrs["tvg-name"] }
                    logo = attrs["tvg-logo"]
                    group = attrs["group-title"]
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substringAfter(':').trim().ifBlank { null }
                }
                line.startsWith("#") -> Unit
                isHttpUrl(line) -> {
                    raw += Station(
                        id = "",
                        name = name?.takeIf { it.isNotBlank() } ?: hostOf(line),
                        url = line,
                        logo = logo?.takeIf { it.isNotBlank() },
                        group = (group ?: extGroup)?.takeIf { it.isNotBlank() },
                    )
                    name = null
                    logo = null
                    group = null
                }
                else -> Unit
            }
        }
        return raw.mapIndexed { i, s -> s.copy(id = "s${i}_${s.url.hashCode().toUInt()}") }
    }

    private fun isHttpUrl(s: String): Boolean =
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    private fun hostOf(url: String): String =
        try {
            URI(url).host ?: url
        } catch (e: Exception) {
            url
        }

    private fun firstCommaOutsideQuotes(s: String): Int {
        var inQuote = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> inQuote = !inQuote
                ',' -> if (!inQuote) return i
            }
        }
        return -1
    }
}
