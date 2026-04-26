# AGENTS.md

## Purpose
本ドキュメントは `sound-mode-widget` の開発作業におけるエージェント行動規範の定義。
目的は、変更の安全性・再現性・可読性の担保。

## Project Scope
- 対象: `sound-mode-widget` の実装・修正・テスト・ドキュメント更新
- 非対象: 明示依頼のない大規模リファクタリング、仕様変更、依存追加

## Working Principles
- 変更は依頼範囲に限定
- 既存仕様の維持を優先
- 推測を含む判断は「推測」と明示
- 変更前に「現状挙動」「原因仮説」「変更方針」を簡潔に提示
- 反映前に、対象ファイル・変更要点・影響範囲を明記

## Implementation Rules
- 既存命名・既存構造を尊重
- 曖昧変数名（`data`, `payload`, `context`, `params`）の横断的使い回しを回避
- 1変数1責務を原則
- 不要なログ追加、不要なバリデーション追加を禁止
- ASCII 基本。既存ファイルで必要な場合のみ非ASCII許容

## Change Boundaries
- 指定ファイル・指定関数・指定処理ブロック外は変更しない
- 追加変更が必要な場合、先に理由と影響を提示し合意を取得
- 既存の未関連差分は巻き戻さない

## Review Checklist
- 仕様逸脱の有無
- 既存動作の回帰有無
- 例外系・境界値の破綻有無
- 命名の責務整合性
- 依存関係増加の妥当性

## Test Policy
- 可能な範囲で再現手順を最小化
- 変更箇所に近い単位から確認
- 実行不能時は未実施理由を記録
- 必要最小限の確認結果を報告

## Output Format
- 先頭に結論
- 次に変更ファイル一覧
- 次に理由と影響
- 最後に未実施事項と残課題

## Prohibitions
- 依頼外の設計変更
- 破壊的コマンド実行（明示依頼なし）
- 根拠不明の最適化
- 無断での依存追加

## Windows Notes
- 書き込み不可時は ACL を確認
- パス操作は `-LiteralPath` 優先
- 権限不足時は反映を停止し、権限調整後に再実行

## Local Build Environment
- Android SDK は `local.properties` の `sdk.dir=C\:/Users/kani/AppData/Local/Android/Sdk` を使用
- `adb` は PATH にないため、`C:\Users\kani\AppData\Local\Android\Sdk\platform-tools\adb.exe` を直接実行
- Android Gradle Plugin は Java 17 必須。`JAVA_HOME` は `C:\Program Files\Android\Android Studio\jbr` を使用
- Gradle wrapper はユーザーホーム外へ書き込まないよう、`GRADLE_USER_HOME` にプロジェクト直下の `.gradle-user-home` を指定
- Android tooling のユーザーディレクトリは、`ANDROID_USER_HOME` にプロジェクト直下の `.android-home` を指定
- 実機更新時は既存インストールと署名を合わせるため、`C:\Users\kani\.android\debug.keystore` を使用

### Device Update Command
```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME=(Resolve-Path .\.gradle-user-home).Path
$env:ANDROID_USER_HOME=(Resolve-Path .\.android-home).Path

.\gradlew.bat :app:installDebug --no-daemon `
  "-Pandroid.injected.signing.store.file=C:\Users\kani\.android\debug.keystore" `
  "-Pandroid.injected.signing.store.password=android" `
  "-Pandroid.injected.signing.key.alias=androiddebugkey" `
  "-Pandroid.injected.signing.key.password=android"
```

### Device Check Command
```powershell
& 'C:\Users\kani\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
```
