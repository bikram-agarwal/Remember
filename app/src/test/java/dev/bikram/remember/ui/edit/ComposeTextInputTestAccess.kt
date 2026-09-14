@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * JVM-only access to Compose's real IME edit path. Programmatic TextFieldState.edit bypasses
 * InputTransformation and cannot create composition, so it would weaken these regressions.
 * Keep internal access here without unsupported Kotlin visibility suppressions. Compose upgrades
 * can still change this JVM API; method lookup then fails explicitly instead of skipping coverage.
 */
internal fun TextFieldState.editAsUserForTest(
    inputTransformation: InputTransformation,
    edit: TextFieldBuffer.() -> Unit,
) {
    ComposeTextInputTestAccess.editAsUser.invokeForTest(this, inputTransformation, true, ComposeTextInputTestAccess.mergeUndo, edit)
}

internal val TextFieldBuffer.imeComposition: TextRange?
    get() = ComposeTextInputTestAccess.composition.invokeForTest(this) as TextRange?

internal fun TextFieldBuffer.setImeComposition(
    start: Int,
    end: Int,
) {
    ComposeTextInputTestAccess.setComposition.invokeForTest(this, start, end, null)
}

internal fun TextFieldBuffer.commitImeComposition() {
    ComposeTextInputTestAccess.commitComposition.invokeForTest(this)
}

/** Compose's JVM range arguments pack the two public endpoints into one long. */
internal fun TextRange.toComposeTestRange(): Long {
    return (start.toLong() shl 32) or (end.toLong() and 0xFFFFFFFFL)
}

private object ComposeTextInputTestAccess {
    val editAsUser = TextFieldState::class.java.requireComposeMethod("editAsUser", 4)
    val mergeUndo =
        checkNotNull(editAsUser.parameterTypes[2].enumConstants?.singleOrNull { (it as Enum<*>).name == "MergeIfPossible" }) {
            "Compose JVM test API changed: update the editAsUser undo behavior in ComposeTextInputTestAccess."
        }
    val composition = TextFieldBuffer::class.java.requireComposeMethod("getComposition", 0)
    val setComposition = TextFieldBuffer::class.java.requireComposeMethod("setComposition", 3)
    val commitComposition = TextFieldBuffer::class.java.requireComposeMethod("commitComposition", 0)
}

private fun Class<*>.requireComposeMethod(
    name: String,
    parameterCount: Int,
): Method {
    return checkNotNull(methods.singleOrNull { it.name.substringBefore('$').substringBefore('-') == name && it.parameterCount == parameterCount }) {
        "Compose JVM test API changed: expected ${this.name}.$name with $parameterCount arguments. Update ComposeTextInputTestAccess."
    }
}

private fun Method.invokeForTest(
    receiver: Any,
    vararg arguments: Any?,
): Any? {
    try {
        return invoke(receiver, *arguments)
    } catch (failure: InvocationTargetException) {
        // Preserve the original assertion or edit failure instead of a reflection wrapper.
        throw failure.targetException
    }
}
