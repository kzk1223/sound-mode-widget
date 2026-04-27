package info.nwp.soundmodewidget

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 初期設定画面。
 *
 * サイレントモードアクセス権限と通知表示権限の状態を表示し、
 * 必要なシステム設定または権限要求へ誘導する。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_NOTIFICATION = 1001
    }

    /**
     * 初期表示と権限導線の設定処理。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnGrant = findViewById<Button>(R.id.btn_grant_permission)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val ivIcon   = findViewById<ImageView>(R.id.iv_icon)

        updateStatus(tvStatus, ivIcon)

        btnGrant.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    // システムの DND アクセス設定画面へ遷移
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } else {
                    Toast.makeText(this, R.string.toast_dnd_permission_already_granted, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.toast_dnd_permission_not_required, Toast.LENGTH_SHORT).show()
            }
        }

        // ---------------------------------------------
        // Android 13+ 通知権限要求
        // ---------------------------------------------

        requestNotificationPermissionIfNeeded()
    }

    /**
     * システム設定画面から戻った際の権限状態再描画処理。
     */
    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val ivIcon   = findViewById<ImageView>(R.id.iv_icon)
        updateStatus(tvStatus, ivIcon)
    }

    /**
     * 権限状態に応じたステータス表示更新処理。
     */
    private fun updateStatus(tvStatus: TextView, ivIcon: ImageView) {
        val messages = mutableListOf<String>()
        var allGranted = true

        // ---------------------------------------------
        // サイレントモードアクセス権限判定
        // ---------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                messages.add(getString(R.string.status_dnd_permission_ok))
            } else {
                messages.add(getString(R.string.status_dnd_permission_denied))
                allGranted = false
            }
        }

        // ---------------------------------------------
        // 通知表示権限判定
        // ---------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                messages.add(getString(R.string.status_notification_permission_ok))
            } else {
                messages.add(getString(R.string.status_notification_permission_denied))
                allGranted = false
            }
        }

        if (allGranted) {
            messages.add(getString(R.string.status_add_widget_instruction))
            tvStatus.setTextColor(0xFF2E7D32.toInt())
            ivIcon.setImageResource(R.drawable.ic_volume_normal)
        } else {
            tvStatus.setTextColor(0xFFD32F2F.toInt())
            ivIcon.setImageResource(R.drawable.ic_volume_silent)
        }

        tvStatus.text = messages.joinToString("\n")
    }

    /**
     * Android 13+ の通知表示権限要求処理。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION
                )
            }
        }
    }

    /**
     * 通知表示権限要求後の画面状態更新処理。
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION) {
            val tvStatus = findViewById<TextView>(R.id.tv_status)
            val ivIcon   = findViewById<ImageView>(R.id.iv_icon)
            updateStatus(tvStatus, ivIcon)
        }
    }
}
