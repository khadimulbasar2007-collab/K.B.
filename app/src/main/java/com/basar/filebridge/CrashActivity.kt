package com.basar.filebridge

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent.getStringExtra("error") ?: "Unknown error"
        val textView = TextView(this).apply {
            text = "অ্যাপ ক্র্যাশ করেছে, এই লেখাটার স্ক্রিনশট দিন:\n\n$message"
            setPadding(32, 32, 32, 32)
            textSize = 12f
        }
        setContentView(ScrollView(this).apply { addView(textView) })
    }
}
