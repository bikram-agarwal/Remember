package dev.bikram.remember

import com.mohamedrejeb.richeditor.model.RichTextState

fun main() {
    val methods = RichTextState::class.java.methods
    for (m in methods) {
        if (m.name.contains("Link") || m.name.contains("Text") || m.name.contains("List") || m.name.contains("config")) {
            println(m.name + " -> " + m.parameterTypes.joinToString { it.simpleName })
        }
    }
}
