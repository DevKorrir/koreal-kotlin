package dev.korryr.koreal.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.korryr.koreal.data.model.NetworkPacketInfo
import dev.korryr.koreal.data.repository.PacketRepository
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.io.FileInputStream
import java.nio.ByteBuffer

class LocalVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    companion object {
        const val ACTION_START = "dev.korryr.koreal.START"
        const val ACTION_STOP = "dev.korryr.koreal.STOP"
        private const val TAG = "LocalVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        
        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setSession("KorealNetworkMonitor")
            
            vpnInterface = builder.establish()
            
            vpnInterface?.let {
                scope.launch {
                    processPackets(it)
                }
            }
            Log.d(TAG, "VPN Started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private val packetRepository: PacketRepository by inject()

    private suspend fun CoroutineScope.processPackets(vpnInterface: ParcelFileDescriptor) {
        val inputStream = FileInputStream(vpnInterface.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)
        
        while (isActive) {
            try {
                val read = inputStream.read(packet.array())
                if (read > 0) {
                    val buffer = packet.array()
                    val version = (buffer[0].toInt() shr 4) and 0x0F
                    
                    if (version == 4) { // IPv4
                        val protocol = buffer[9].toInt() and 0xFF
                        val protocolName = when (protocol) {
                            6 -> "TCP"
                            17 -> "UDP"
                            1 -> "ICMP"
                            else -> "Other ($protocol)"
                        }
                        
                        val sourceAddress = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"
                        val destAddress = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
                        
                        Log.d(TAG, "Intercepted IPv4 Packet: $sourceAddress -> $destAddress ($protocolName)")
                        packetRepository.emitPacket(
                            NetworkPacketInfo(
                                sourceIp = sourceAddress,
                                destinationIp = destAddress,
                                protocol = protocolName
                            )
                        )
                    } else if (version == 6) { // IPv6
                        Log.d(TAG, "Intercepted IPv6 Packet")
                    }
                    
                    packet.clear()
                } else {
                    delay(10)
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Packet processing error", e)
                break
            }
        }
        inputStream.close()
    }

    private fun stopVpn() {
        job.cancelChildren()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        Log.d(TAG, "VPN Stopped")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        job.cancel()
    }
}
