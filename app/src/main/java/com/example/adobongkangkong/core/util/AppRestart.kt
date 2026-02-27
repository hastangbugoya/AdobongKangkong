package com.example.adobongkangkong.core.util

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

fun restartApp(context: Context) {
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            }

    context.startActivity(intent)
    exitProcess(0)
}