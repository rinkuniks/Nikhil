package com.example.testingcast.util

object HostValidator {
    private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")
    private val HOSTNAME = Regex("""^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""")
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    fun isValidHost(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return false
        val looksNumeric = trimmed.all { it.isDigit() || it == '.' }
        return if (looksNumeric) IPV4.matches(trimmed) else HOSTNAME.matches(trimmed)
    }

    fun parsePort(port: String): Int? = port.trim().toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
}
