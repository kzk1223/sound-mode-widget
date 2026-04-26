# Sound Mode Widget (Android)

1×1 サイズのホーム画面ウィジェットで、タップするたびに **通常 → バイブ → サイレント** と切り替わります。
サイレント状態は DND（通知の割り込み制限）も含めて判定します。
アプリ名は端末の言語にかかわらず **Sound Mode Widget** で表示されます。

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
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle
├── AGENTS.md
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle             # アプリレベル
    ├── proguard-rules.pro
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
            ├── values/
            │   ├── strings.xml                ← 既定の英語文言
            │   └── themes.xml
            └── values-ja/
                └── strings.xml                ← 日本語端末向け文言
```

---

## ビルド & インストール

### Android Studio を使う場合

1. Android Studio で `sound-mode-widget/` フォルダを開く
2. `Run > Run 'app'` で実機 or エミュレータにインストール
3. アプリ起動 → 「サイレントモード権限を許可する」をタップ
4. ホーム画面を長押し → ウィジェット → 「Sound Mode Widget」を追加

### コマンドラインの場合

```powershell
cd sound-mode-widget

$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME=(Resolve-Path .\.gradle-user-home).Path
$env:ANDROID_USER_HOME=(Resolve-Path .\.android-home).Path

.\gradlew.bat :app:assembleDebug --no-daemon
& 'C:\Users\kani\AppData\Local\Android\Sdk\platform-tools\adb.exe' install app/build/outputs/apk/debug/app-debug.apk
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
2. サービス内で `RINGER_MODE_CHANGED_ACTION` と `ACTION_INTERRUPTION_FILTER_CHANGED` の動的 BroadcastReceiver を登録
3. 音量ボタン・クイック設定・他アプリ等で着信モードや DND 状態が変わるとレシーバーが発火
4. `ACTION_REFRESH` で全ウィジェットの UI を現在の実効モードに合わせて更新
5. 画面消灯時は着信モード監視を解除し、画面点灯またはロック解除時に再登録して現在値を同期
6. 端末再起動時やアプリ更新時は `BootCompletedReceiver` が配置済みウィジェットの存在を確認してサービスを再開

> **なぜフォアグラウンドサービスが必要か？**
> Android 8+ ではマニフェスト登録の暗黙ブロードキャスト（`RINGER_MODE_CHANGED_ACTION` 含む）が
> 制限されており、動的レシーバーでしか受信できないため。

---

## 多言語対応

- 既定ロケールは英語です。
- 日本語端末では `values-ja/strings.xml` の日本語文言を表示します。
- アプリ名 `app_name` は全ロケールで `Sound Mode Widget` に統一しています。

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
