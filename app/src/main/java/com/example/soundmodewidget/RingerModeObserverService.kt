package com.example.soundmodewidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder

/**
 * フォアグラウンドサービス:
 * RINGER_MODE_CHANGED_ACTION を動的レシーバーで受信し、
 * ウィジェットのアイコン・ラベル・背景色をリアルタイムに同期する。
 *
 * Android 8+ ではマニフェスト登録の暗黙ブロードキャストが制限されるため、
 * サービス内で動的に registerReceiver() する必要がある。
 *
 * ライフサイクル:
 *   開始 → WidgetProvider.onEnabled()（最初のウィジェット配置時）
 *   停止 → WidgetProvider.onDisabled()（最後のウィジェット削除時）
 */
class RingerModeObserverService : Service() {

    companion object {
        private const val CHANNEL_ID = "ringer_mode_observer"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, RingerModeObserverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingerModeObserverService::class.java))
        }
    }

    // ===== 動的レシーバー: 着信モード変更を検知 =====

    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                refreshAllWidgets(context)
            }
        }
    }

    // ===== Service ライフサイクル =====

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        registerRingerModeReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // サービスがOSに強制終了された場合に自動再起動
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(ringerModeReceiver)
        } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ===== 通知チャネル & フォアグラウンド通知 =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "マナーモード監視",
                NotificationManager.IMPORTANCE_MIN   // 最小限の通知
            ).apply {
                description = "着信モードの変更を監視してウィジェットを更新します"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val tapIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, flags)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentTitle("マナーモード監視中")
            setContentText("着信モードの変更を検知してウィジェットを更新します")
            setSmallIcon(R.drawable.ic_volume_normal)
            setContentIntent(pendingIntent)
            setOngoing(true)
        }.build()

        // Android 14+ では foregroundServiceType を明示的に指定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ===== 動的レシーバー登録 =====

    private fun registerRingerModeReceiver() {
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ では RECEIVER_NOT_EXPORTED を指定（システムブロードキャスト）
            registerReceiver(ringerModeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(ringerModeReceiver, filter)
        }
    }

    // ===== 全ウィジェット更新 =====

    private fun refreshAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SoundModeWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            val intent = Intent(context, SoundModeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
