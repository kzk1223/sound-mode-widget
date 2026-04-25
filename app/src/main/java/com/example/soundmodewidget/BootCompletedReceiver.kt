package com.example.soundmodewidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 端末再起動後に監視サービスを再開するレシーバー。
 *
 * BOOT_COMPLETED はマニフェスト登録の暗黙ブロードキャスト制限の
 * 例外リストに含まれているため、Android 8+ でも受信可能。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    /**
     * 端末起動またはアプリ更新後の監視サービス復帰処理。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // ---------------------------------------------
            // 配置済み AppWidget の存在確認
            // ---------------------------------------------

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SoundModeWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                RingerModeObserverService.start(context)
            }
        }
    }
}
