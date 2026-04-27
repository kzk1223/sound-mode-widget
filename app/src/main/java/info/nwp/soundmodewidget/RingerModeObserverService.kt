package info.nwp.soundmodewidget

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
import android.os.PowerManager

/**
 * 着信モード監視フォアグラウンドサービス。
 *
 * 画面表示中は RINGER_MODE_CHANGED_ACTION を動的レシーバーで受信し、
 * ウィジェットのアイコン・ラベル・背景色を同期する。
 * 画面消灯中は着信モード監視を停止し、画面点灯またはロック解除時に現在値を同期する。
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

        /**
         * 監視サービスの開始処理。
         */
        fun start(context: Context) {
            val intent = Intent(context, RingerModeObserverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 監視サービスの停止処理。
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, RingerModeObserverService::class.java))
        }
    }

    private var isRingerModeReceiverRegistered = false
    private var isScreenStateReceiverRegistered = false

    // ---------------------------------------------
    // 動的レシーバー
    // ---------------------------------------------

    /**
     * 着信モード変更通知の受信処理。
     */
    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (
                intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION ||
                intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED
            ) {
                refreshOwnAppWidgetInstances(context)
            }
        }
    }

    /**
     * 画面点灯状態変更通知の受信処理。
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> unregisterRingerModeReceiver()
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    registerRingerModeReceiver()
                    refreshOwnAppWidgetInstances(context)
                }
            }
        }
    }

    // ---------------------------------------------
    // Service ライフサイクル
    // ---------------------------------------------

    /**
     * サービス生成時の通知開始と監視レシーバー登録処理。
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        registerScreenStateReceiver()
        if (isDeviceInteractive()) {
            registerRingerModeReceiver()
            refreshOwnAppWidgetInstances(this)
        }
    }

    /**
     * OS によるサービス停止後の再生成方針。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // サービスがOSに強制終了された場合に自動再起動
        return START_STICKY
    }

    /**
     * サービス破棄時のレシーバー解除処理。
     */
    override fun onDestroy() {
        unregisterRingerModeReceiver()
        unregisterScreenStateReceiver()
        super.onDestroy()
    }

    /**
     * バインド非対応の明示。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------
    // 通知チャネル & フォアグラウンド通知
    // ---------------------------------------------

    /**
     * フォアグラウンド通知チャネルの生成処理。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN   // 最小限の通知
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * フォアグラウンドサービス通知の開始処理。
     */
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
            setContentTitle(getString(R.string.notification_title))
            setContentText(getString(R.string.notification_text))
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

    // ---------------------------------------------
    // 動的レシーバー登録
    // ---------------------------------------------

    /**
     * 端末の画面表示状態。
     */
    private fun isDeviceInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    /**
     * 着信モード変更レシーバーの登録。
     */
    private fun registerRingerModeReceiver() {
        if (isRingerModeReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ では一部のシステムブロードキャスト受信に exported 指定が必要
            registerReceiver(ringerModeReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(ringerModeReceiver, filter)
        }
        isRingerModeReceiverRegistered = true
    }

    /**
     * 着信モード変更レシーバーの解除。
     */
    private fun unregisterRingerModeReceiver() {
        if (!isRingerModeReceiverRegistered) return

        try {
            unregisterReceiver(ringerModeReceiver)
        } catch (_: Exception) {
        } finally {
            isRingerModeReceiverRegistered = false
        }
    }

    /**
     * 画面状態レシーバーの登録。
     */
    private fun registerScreenStateReceiver() {
        if (isScreenStateReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
        isScreenStateReceiverRegistered = true
    }

    /**
     * 画面状態レシーバーの解除。
     */
    private fun unregisterScreenStateReceiver() {
        if (!isScreenStateReceiverRegistered) return

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
        } finally {
            isScreenStateReceiverRegistered = false
        }
    }

    // ---------------------------------------------
    // 自 AppWidget 更新
    // ---------------------------------------------

    /**
     * 自アプリに属する AppWidget 全インスタンスの再描画要求処理。
     */
    private fun refreshOwnAppWidgetInstances(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SoundModeWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            val intent = Intent(context, SoundModeWidgetProvider::class.java).apply {
                action = SoundModeWidgetProvider.ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
