package dev.korryr.koreal.data.repository

import dev.korryr.koreal.data.model.NetworkPacketInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PacketRepository {
    private val _packetFlow = MutableSharedFlow<NetworkPacketInfo>(extraBufferCapacity = 100)
    val packetFlow: SharedFlow<NetworkPacketInfo> = _packetFlow.asSharedFlow()

    fun emitPacket(packet: NetworkPacketInfo) {
        _packetFlow.tryEmit(packet)
    }
}
