package dev.korryr.koreal.data.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.RemoteException
import dev.korryr.koreal.data.model.AppUsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class NetworkStatsRepository(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager = context.packageManager

    suspend fun getUsageStatsForToday(): List<AppUsageStats> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        val usageMap = mutableMapOf<Int, AppUsageRecord>()

        collectStats(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap)
        collectStats(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap)

        val resultList = mutableListOf<AppUsageStats>()

        usageMap.forEach { (uid, record) ->
            val packages = packageManager.getPackagesForUid(uid)
            val packageName = packages?.firstOrNull() ?: "Unknown UID: $uid"

            val appName = try {
                if (packages != null) {
                    val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    packageManager.getApplicationLabel(appInfo).toString()
                } else packageName
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }

            val icon = try {
                if (packages != null) packageManager.getApplicationIcon(packageName) else null
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            resultList.add(
                AppUsageStats(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    totalBytesRecv = record.rxBytes,
                    totalBytesSent = record.txBytes,
                    totalBytes = record.rxBytes + record.txBytes
                )
            )
        }

        resultList.sortedByDescending { it.totalBytes }
    }

    private fun collectStats(networkType: Int, startTime: Long, endTime: Long, usageMap: MutableMap<Int, AppUsageRecord>) {
        try {
            val bucket = NetworkStats.Bucket()
            networkStatsManager.querySummary(networkType, null, startTime, endTime).use { query ->
                while (query.hasNextBucket()) {
                    query.getNextBucket(bucket)
                    val uid = bucket.uid
                    val rx = bucket.rxBytes
                    val tx = bucket.txBytes

                    val record = usageMap.getOrPut(uid) { AppUsageRecord() }
                    record.rxBytes += rx
                    record.txBytes += tx
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private data class AppUsageRecord(
        var rxBytes: Long = 0,
        var txBytes: Long = 0
    )
}
