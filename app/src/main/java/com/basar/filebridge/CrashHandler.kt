package com.basar.filebridge

import android.content.Context
import android.content.Intent
import android.os.Process
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val message = throwable.stackTraceToString()
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra("error", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }
}
