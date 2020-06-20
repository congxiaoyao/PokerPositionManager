package com.congxiaoyao.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class PokerConfig(
    val pokers: List<Char>,
    val selected: List<Char>,
    val highlights: List<Char>
){
    companion object {
        val pokerConfigFile get() = File("res/pokerConfig")

        fun load(): PokerConfig {
            val file = pokerConfigFile
            val json = file.readText()
            return Gson().fromJson(json, PokerConfig::class.java)
        }
    }

    fun save() {
        val file = pokerConfigFile
        file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(this))
    }
}

