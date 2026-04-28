package dev.bikram.remember.diagnostics

import android.content.Context
import android.os.Build
import dev.bikram.remember.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

object DiagnosticLog {
    private const val DIAGNOSTICS_DIR = "diagnostics"
    private const val LOG_FILE_NAME = "remember-diagnostics.log"
    private const val SHARE_FILE_NAME = "remember-diagnostics.txt"
    private const val MAX_LOG_BYTES = 256 * 1024

    @Volatile
    private var crashHandlerInstalled = false

    fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(appContext, "Uncaught exception on ${thread.name}", throwable)
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(2)
            }
        }
        crashHandlerInstalled = true
    }

    fun record(
        context: Context,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching {
            val logFile = logFile(context)
            logFile.parentFile?.mkdirs()
            trimIfNeeded(logFile)
            logFile.appendText(
                buildString {
                    append(Instant.now())
                    append(" | ")
                    append(message)
                    append('\n')
                    if (throwable != null) {
                        append(stackTraceText(throwable))
                        append('\n')
                    }
                },
            )
        }
    }

    fun createShareFile(context: Context): File {
        val shareFile = File(File(context.cacheDir, DIAGNOSTICS_DIR), SHARE_FILE_NAME)
        shareFile.parentFile?.mkdirs()
        val logText = runCatching { logFile(context).readText() }.getOrDefault("")
        shareFile.writeText(
            buildString {
                appendLine("Remember diagnostics")
                appendLine("Generated: ${Instant.now()}")
                appendLine("Package: ${context.packageName}")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Flavor: ${BuildConfig.FLAVOR}")
                appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine()
                appendLine("App log")
                appendLine("=======")
                append(logText.ifBlank { "No app log entries captured yet.\n" })
            },
        )
        return shareFile
    }

    private fun logFile(context: Context): File = File(File(context.filesDir, DIAGNOSTICS_DIR), LOG_FILE_NAME)

    private fun trimIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        val text = logFile.readText()
        val keepFrom = (text.length / 2).coerceAtLeast(0)
        logFile.writeText(text.substring(keepFrom))
    }

    private fun stackTraceText(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
