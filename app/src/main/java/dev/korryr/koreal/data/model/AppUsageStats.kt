package dev.korryr.koreal.data.model

import android.graphics.drawable.Drawable

data class AppUsageStats(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val totalBytesRecv: Long,
    val totalBytesSent: Long,
    val totalBytes: Long
)
