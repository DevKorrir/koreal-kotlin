package dev.korryr.koreal.data.model

import java.util.UUID

data class NetworkPacketInfo(
    val id: String = UUID.randomUUID().toString(),
    val sourceIp: String,
    val destinationIp: String,
    val protocol: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isOutbound: Boolean = true,
    val uid: Int? = null,
    val appName: String? = null
)
