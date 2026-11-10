package dev.termos

import android.content.Context

object Config {
    fun theme(c: Context) = Store.get(c, "theme") ?: "green"
    fun vim(c: Context) = Store.get(c, "vim") == "on"
    fun font(c: Context) = Store.get(c, "font")?.toIntOrNull() ?: 13
    fun autolock(c: Context) = Store.get(c, "autolock") != "off"
    fun user(c: Context) = Store.get(c, "user") ?: "user"
    fun keepalive(c: Context) = Store.get(c, "keepalive") != "off"
}
