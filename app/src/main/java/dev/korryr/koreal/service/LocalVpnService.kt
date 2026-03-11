package dev.korryr.koreal.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import dev.korryr.koreal.data.model.NetworkPacketInfo
import dev.korryr.koreal.data.repository.PacketRepository
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.io.FileInputStream
import java.net.InetSocketAddress
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
    private val appNameCache = mutableMapOf<Int, String>()

    private fun getAppName(uid: Int): String {
        if (uid <= 0) return "Unknown"
        return appNameCache.getOrPut(uid) {
            val pm = packageManager
            try {
                val packages = pm.getPackagesForUid(uid)
                if (!packages.isNullOrEmpty()) {
                    val appInfo = pm.getApplicationInfo(packages[0], 0)
                    pm.getApplicationLabel(appInfo).toString()
                } else "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }

    private fun formatIpPortToHex(ip: String, port: Int): String {
        val parts = ip.split(".")
        if (parts.size != 4) return ""
        return java.lang.String.format(java.util.Locale.US, "%02X%02X%02X%02X:%04X",
            parts[3].toInt(), parts[2].toInt(), parts[1].toInt(), parts[0].toInt(),
            port)
    }

    private fun getUidFromProcNet(protocol: Int, localIp: String, localPort: Int, remoteIp: String, remotePort: Int): Int {
        val localHex = formatIpPortToHex(localIp, localPort)
        val remoteHex = formatIpPortToHex(remoteIp, remotePort)
        val portHex = java.lang.String.format(java.util.Locale.US, ":%04X", localPort)
        val anyIpv4 = "00000000$portHex"
        val anyIpv6 = "00000000000000000000000000000000$portHex"

        val filesToSearch = if (protocol == 6) listOf("/proc/net/tcp", "/proc/net/tcp6") else listOf("/proc/net/udp", "/proc/net/udp6")

        for (file in filesToSearch) {
            try {
                java.io.File(file).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("sl")) continue

                        val columns = trimmed.split("\\s+".toRegex())
                        if (columns.size > 7) {
                            val colLocal = columns[1]
                            val colRemote = columns[2]

                            if (colLocal == localHex || colLocal == anyIpv4 || colLocal == anyIpv6) {
                                if (protocol == 6 && colRemote != remoteHex && colRemote != "00000000:0000") continue
                                return columns[7].toIntOrNull() ?: android.os.Process.INVALID_UID
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore file read issues
            }
        }
        return android.os.Process.INVALID_UID
    }

    private fun getUidForConnection(protocol: Int, localIp: String, localPort: Int, remoteIp: String, remotePort: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (protocol == 6 || protocol == 17)) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val osProtocol = if (protocol == 6) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP
            try {
                val uid = cm.getConnectionOwnerUid(
                    osProtocol,
                    InetSocketAddress(localIp, localPort),
                    InetSocketAddress(remoteIp, remotePort)
                )
                if (uid != android.os.Process.INVALID_UID) {
                    Log.d(TAG, "getUidForConnection API 29+ $localIp:$localPort -> $remoteIp:$remotePort (Proto $osProtocol) returned $uid")
                    return uid
                }
            } catch (e: Exception) {
                Log.d(TAG, "getUidForConnection API 29+ error: ${e.message}")
            }
        }

        // Fallback for Android 9 and older
        if (protocol == 6 || protocol == 17) {
            val procUid = getUidFromProcNet(protocol, localIp, localPort, remoteIp, remotePort)
            if (procUid != android.os.Process.INVALID_UID) {
                return procUid
            }
        }

        return android.os.Process.INVALID_UID
    }

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
                        val isOutbound = sourceAddress == "10.0.0.2"

                        var ownerUid: Int? = null
                        var appName: String? = null
                        
                        if (protocol == 6 || protocol == 17) {
                            val ipHeaderLen = (buffer[0].toInt() and 0x0F) * 4
                            if (read >= ipHeaderLen + 4) {
                                val srcPort = ((buffer[ipHeaderLen].toInt() and 0xFF) shl 8) or (buffer[ipHeaderLen + 1].toInt() and 0xFF)
                                val dstPort = ((buffer[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (buffer[ipHeaderLen + 3].toInt() and 0xFF)
                                
                                val localIp = if (isOutbound) sourceAddress else destAddress
                                val localPort = if (isOutbound) srcPort else dstPort
                                val remoteIp = if (isOutbound) destAddress else sourceAddress
                                val remotePort = if (isOutbound) dstPort else srcPort
                                
                                val uid = getUidForConnection(protocol, localIp, localPort, remoteIp, remotePort)
                                if (uid > 0 && uid != android.os.Process.INVALID_UID) {
                                    ownerUid = uid
                                    appName = getAppName(uid)
                                }
                            }
                        }
                        
                        Log.d(TAG, "Intercepted IPv4 Packet: $sourceAddress -> $destAddress ($protocolName) app: $appName")
                        packetRepository.emitPacket(
                            NetworkPacketInfo(
                                sourceIp = sourceAddress,
                                destinationIp = destAddress,
                                protocol = protocolName,
                                isOutbound = isOutbound,
                                uid = ownerUid,
                                appName = appName
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
