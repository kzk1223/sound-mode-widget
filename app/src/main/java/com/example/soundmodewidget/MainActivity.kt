package com.example.soundmodewidget

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
 * 初回起動時に必要な権限をユーザーに要求するための画面。
 *
 * 1. サイレントモード(DND)アクセス — Android 7+ で必須
 * 2. 通知表示権限 — Android 13+ でフォアグラウンドサービスの通知に必要
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_NOTIFICATION = 1001
    }

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
                    Toast.makeText(this, "DND権限は既に付与されています", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "この端末ではDND権限は不要です", Toast.LENGTH_SHORT).show()
            }
        }

        // Android 13+ の通知権限リクエスト
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val ivIcon   = findViewById<ImageView>(R.id.iv_icon)
        updateStatus(tvStatus, ivIcon)
    }

    private fun updateStatus(tvStatus: TextView, ivIcon: ImageView) {
        val messages = mutableListOf<String>()
        var allGranted = true

        // 1. DND 権限チェック
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                messages.add("✅ サイレントモード権限: OK")
            } else {
                messages.add("⚠️ サイレントモード権限: 未許可")
                allGranted = false
            }
        }

        // 2. 通知権限チェック (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                messages.add("✅ 通知権限: OK")
            } else {
                messages.add("⚠️ 通知権限: 未許可（監視サービスに必要）")
                allGranted = false
            }
        }

        if (allGranted) {
            messages.add("\nウィジェットをホーム画面に追加してください")
            tvStatus.setTextColor(0xFF2E7D32.toInt())
            ivIcon.setImageResource(R.drawable.ic_volume_normal)
        } else {
            tvStatus.setTextColor(0xFFD32F2F.toInt())
            ivIcon.setImageResource(R.drawable.ic_volume_silent)
        }

        tvStatus.text = messages.joinToString("\n")
    }

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
