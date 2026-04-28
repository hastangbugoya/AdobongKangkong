package com.example.adobongkangkong.core.util

import android.content.Context
import android.content.Intent
import com.example.adobongkangkong.core.log.MeowLog
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
    MeowLog.d("restartApp>$intent")
    context.startActivity(intent)
    exitProcess(0)
}