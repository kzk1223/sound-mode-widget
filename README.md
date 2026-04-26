# マナーモード切替ウィジェット (Android)

1×1 サイズのホーム画面ウィジェットで、タップするたびに **通常 → バイブ → サイレント** と切り替わります。
サイレント状態は DND（通知の割り込み制限）も含めて判定します。

---

## 動作仕様

| モード | アイコン | 背景色 |
|--------|----------|--------|
| 通常   | 🔊 スピーカー＋音波 | 🟩 緑 `#4CAF50` |
| バイブ | 📳 振動するスマホ | 🟧 橙 `#FF9800` |
| サイレント | 🔇 ミュートスピーカー | 🟥 赤 `#F44336` |

---

## プロジェクト構成

```
sound-mode-widget/
├── build.gradle                 # プロジェクトレベル
├── settings.gradle
└── app/
    ├── build.gradle             # アプリレベル
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/soundmodewidget/
        │   ├── SoundModeWidgetProvider.kt   ← ウィジェット本体
        │   ├── RingerModeObserverService.kt ← 着信モード変更の監視サービス
        │   ├── BootCompletedReceiver.kt     ← 再起動後にサービスを再開
        │   └── MainActivity.kt              ← 権限リクエスト画面
        └── res/
            ├── layout/
            │   ├── widget_sound_mode.xml     ← ウィジェットレイアウト
            │   └── activity_main.xml         ← 権限画面レイアウト
            ├── drawable/
            │   ├── ic_volume_normal.xml      ← 通常アイコン (白)
            │   ├── ic_volume_vibrate.xml     ← バイブアイコン (白)
            │   ├── ic_volume_silent.xml      ← サイレントアイコン (白)
            │   ├── bg_normal.xml             ← 緑背景
            │   ├── bg_vibrate.xml            ← 橙背景
            │   └── bg_silent.xml             ← 赤背景
            ├── xml/
            │   └── sound_mode_widget_info.xml ← ウィジェット定義
            └── values/
                ├── strings.xml
                └── themes.xml
```

---

## ビルド & インストール

### Android Studio を使う場合

1. Android Studio で `sound-mode-widget/` フォルダを開く
2. `Run > Run 'app'` で実機 or エミュレータにインストール
3. アプリ起動 → 「サイレントモード権限を許可する」をタップ
4. ホーム画面を長押し → ウィジェット → 「マナーモード切替」を追加

### コマンドラインの場合

```bash
cd sound-mode-widget
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 必要な権限

| 権限 | 理由 |
|------|------|
| `ACCESS_NOTIFICATION_POLICY` | Android 7+ でサイレントモードに切り替えるために必要 |
| `FOREGROUND_SERVICE` | 着信モード変更を常時監視するサービスの実行に必要 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ でのフォアグラウンドサービスタイプ宣言 |
| `RECEIVE_BOOT_COMPLETED` | 端末再起動後に監視サービスを自動再開 |
| `POST_NOTIFICATIONS` | Android 13+ でサービスの常駐通知を表示するために必要 |

初回起動時に画面の指示に従って権限を許可してください。

---

## 仕組み

### ウィジェットタップ時
1. ウィジェットをタップすると `ACTION_TOGGLE` ブロードキャストが発行される
2. `SoundModeWidgetProvider.onReceive()` が `AudioManager.ringerMode` と DND 状態を読み取り、実効モードを判定
3. 通常 → バイブは `AudioManager.ringerMode`、バイブ → サイレントは `NotificationManager.setInterruptionFilter()` で切替
4. サイレント → 通常では DND を解除し、`AudioManager.ringerMode` を通常へ戻す
5. 新しいモードに応じてアイコン・背景色を `RemoteViews` で更新

### 外部からのモード変更を検知（自動同期）
1. ウィジェット配置時に `RingerModeObserverService`（フォアグラウンドサービス）が起動
2. サービス内で `RINGER_MODE_CHANGED_ACTION` の動的 BroadcastReceiver を登録
3. 音量ボタン・クイック設定・他アプリ等でモードが変わるとレシーバーが発火
4. 全ウィジェットの UI を現在の実効モードに合わせて即時更新
5. 端末再起動時は `BootCompletedReceiver` がサービスを再開

> **なぜフォアグラウンドサービスが必要か？**
> Android 8+ ではマニフェスト登録の暗黙ブロードキャスト（`RINGER_MODE_CHANGED_ACTION` 含む）が
> 制限されており、動的レシーバーでしか受信できないため。

---

## カスタマイズ

- **背景色**: `res/drawable/bg_*.xml` の `solid android:color` を変更
- **アイコン**: `res/drawable/ic_volume_*.xml` を差し替え
- **ウィジェットサイズ**: `sound_mode_widget_info.xml` の `minWidth`/`minHeight` を変更

---

## 対応バージョン

| 項目 | バージョン |
|------|-----------|
| Android Studio | Panda 2 \| 2025.3.2 Stable |
| AGP | 8.12.0 |
| Gradle | 8.13 |
| Kotlin | 2.1.20 |
| JDK | 17 |
| SDK Build Tools | 35.0.0 |
| compileSdk | 36 |
| targetSdk | 35 |
| minSdk | 24 |
