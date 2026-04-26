package com.example.soundmodewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.app.NotificationManager
import android.os.Build
import android.widget.RemoteViews

/**
 * マナーモード切替 AppWidget。
 *
 * クリックごとに 通常 → バイブ → サイレント → 通常 … とトグル。
 * 各モードに応じてアイコンとラベルを切り替える。
 *
 * 外部からの着信モード変更（音量ボタン、クイック設定等）は
 * RingerModeObserverService が検知し、ウィジェットを自動更新する。
 */
class SoundModeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.example.soundmodewidget.ACTION_TOGGLE"
        const val ACTION_REFRESH = "com.example.soundmodewidget.ACTION_REFRESH"
    }

    // ---------------------------------------------
    // AppWidget ライフサイクル
    // ---------------------------------------------

    /**
     * 初回 AppWidget 配置時の監視サービス開始処理。
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
        ensureServiceRunning(context)
    }

    /**
     * 最終 AppWidget 削除時の監視サービス停止処理。
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 監視サービスを停止
        RingerModeObserverService.stop(context)
    }

    /**
     * AppWidget 表示更新処理。
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // 端末再起動後などにサービスが停止している場合に再開
        ensureServiceRunning(context)
    }

    // ---------------------------------------------
    // AppWidget 操作
    // ---------------------------------------------

    /**
     * AppWidget クリックイベント受信処理。
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE -> {
                toggleSoundMode(context)
                updateAllWidgets(context)
            }
            ACTION_REFRESH -> {
                updateAllWidgets(context)
            }
        }
    }

    /**
     * 端末の着信モード切替処理。
     */
    private fun toggleSoundMode(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> {
                audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                if (!canChangeDoNotDisturbMode(context)) return
                audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
            AudioManager.RINGER_MODE_SILENT  -> {
                if (!canChangeDoNotDisturbMode(context)) return
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        }
    }

    /**
     * サイレント系モード変更の許可状態。
     */
    private fun canChangeDoNotDisturbMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return notificationManager.isNotificationPolicyAccessGranted
    }

    // ---------------------------------------------
    // AppWidget 更新ユーティリティ
    // ---------------------------------------------

    /**
     * 自 AppWidget 全インスタンスの表示更新処理。
     */
    private fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SoundModeWidgetProvider::class.java)
        )
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    /**
     * ウィジェットが存在する限りサービスを動かす。
     * 端末再起動や OS によるプロセス強制停止後の復帰に対応。
     */
    private fun ensureServiceRunning(context: Context) {
        try {
            RingerModeObserverService.start(context)
        } catch (_: Exception) { }
    }

    /**
     * AppWidget 単一インスタンスの表示構築処理。
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val views = RemoteViews(context.packageName, R.layout.widget_sound_mode)

        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_volume_normal)
                views.setTextViewText(R.id.widget_label, "通常")
                views.setInt(R.id.widget_background, "setBackgroundResource", R.drawable.bg_normal)
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_volume_vibrate)
                views.setTextViewText(R.id.widget_label, "バイブ")
                views.setInt(R.id.widget_background, "setBackgroundResource", R.drawable.bg_vibrate)
            }
            AudioManager.RINGER_MODE_SILENT -> {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_volume_silent)
                views.setTextViewText(R.id.widget_label, "サイレント")
                views.setInt(R.id.widget_background, "setBackgroundResource", R.drawable.bg_silent)
            }
        }

        val toggleIntent = Intent(context, SoundModeWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_background, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_content, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_label, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
