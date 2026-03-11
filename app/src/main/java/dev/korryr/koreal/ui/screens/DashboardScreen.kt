package dev.korryr.koreal.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import dev.korryr.koreal.data.model.AppUsageStats
import dev.korryr.koreal.data.model.NetworkPacketInfo
import dev.korryr.koreal.service.LocalVpnService
import dev.korryr.koreal.ui.viewmodel.NetworkMonitorViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: NetworkMonitorViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val usageStats by viewModel.usageStats.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val recentPackets by viewModel.recentPackets.collectAsState()

    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startVpnService(context)
            viewModel.setVpnState(true)
        }
    }

    LaunchedEffect(hasUsagePermission) {
        if (hasUsagePermission) {
            viewModel.loadUsageStats()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Koreal Network Monitor") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (isVpnActive) {
                        stopVpnService(context)
                        viewModel.setVpnState(false)
                    } else {
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnLauncher.launch(vpnIntent)
                        } else {
                            startVpnService(context)
                            viewModel.setVpnState(true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isVpnActive) "Stop VPN Monitor" else "Start VPN Monitor")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasUsagePermission) {
                Text("Needs Usage Stats Permission to see data usage.")
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) {
                    Text("Grant Permission")
                }
            } else {
                if (usageStats.isNotEmpty()) {
                    Text(
                        text = "Today's Data Usage",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Button(onClick = { viewModel.loadUsageStats() }) {
                        Text("Refresh Usage Data")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(usageStats) { stat ->
                            AppUsageItem(stat)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recent Packets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (recentPackets.isEmpty()) {
                    item {
                        Text("No packets captured yet. Start the VPN adapter.", modifier = Modifier.padding(8.dp))
                    }
                } else {
                    items(recentPackets) { packet ->
                        PacketInfoItem(packet)
                    }
                }
            }
        }
    }
}

private fun startVpnService(context: Context) {
    Intent(context, LocalVpnService::class.java).also { intent ->
        intent.action = LocalVpnService.ACTION_START
        context.startService(intent)
    }
}

private fun stopVpnService(context: Context) {
    Intent(context, LocalVpnService::class.java).also { intent ->
        intent.action = LocalVpnService.ACTION_STOP
        context.startService(intent)
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
fun AppUsageItem(stat: AppUsageStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stat.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap().asImageBitmap(),
                    contentDescription = stat.appName,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stat.appName, style = MaterialTheme.typography.bodyLarge)
                Text(text = stat.packageName, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stat.totalBytes / 1024 / 1024} MB",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Rx: ${stat.totalBytesRecv / 1024 / 1024} MB",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Tx: ${stat.totalBytesSent / 1024 / 1024} MB",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun PacketInfoItem(packet: NetworkPacketInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (packet.isOutbound) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (packet.isOutbound) "Outbound" else "Inbound",
                        modifier = Modifier.size(16.dp),
                        tint = if (packet.isOutbound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = packet.appName ?: "Unknown App",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(packet.timestampMs)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Protocol: ${packet.protocol}", style = MaterialTheme.typography.labelMedium)
                Text(text = if (packet.isOutbound) "Dst: ${packet.destinationIp}" else "Src: ${packet.sourceIp}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
