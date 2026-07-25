# i18n log messages plan

## Top-Level Overview
日本語固定になっているユーザー向けログ出力を、実行環境ロケールが日本語なら日本語、それ以外なら英語で出し分ける。対象はユーザー向けのログ出力と対話入力プロンプトのみとし、[`log.debug`](src/main/java/com/ibm/jp/automation/e2m/Main.java:92) などのデバッグ情報と例外メッセージ文字列は対象外とする。実装は Java 標準のリソース管理を使って、既存の出力箇所を最小限の差分で置き換える。

## Sub-Tasks

### 1. メッセージ基盤を追加する
- **Intent** — ロケール判定とメッセージ取得を一箇所に集約し、既存コードの変更を最小化する。
- **Expected Outcomes** — 日本語と英語のメッセージ定義が追加され、呼び出し側はキー指定でユーザー向け文言を取得できる。
- **Todo List**
  1. 新規 i18n ユーティリティクラスを追加する
  2. 実行環境ロケールから日本語かどうかを判定する
  3. 日本語用と英語用のメッセージリソースを追加する
  4. プレースホルダー付きメッセージを取得できる形にする
- **Relevant Context** — [`Main`](src/main/java/com/ibm/jp/automation/e2m/Main.java:39), [`DependencyResolver`](src/main/java/com/ibm/jp/automation/e2m/DependencyResolver.java:44), [`ProjectCopier`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:50), [`PomGenerator`](src/main/java/com/ibm/jp/automation/e2m/PomGenerator.java:37)
- **Status** — [x] done

### 2. ユーザー向けログ出力をi18n化する
- **Intent** — 既存のユーザー向け進捗ログと警告エラーをロケールに応じて切り替えられるようにする。
- **Expected Outcomes** — [`Main`](src/main/java/com/ibm/jp/automation/e2m/Main.java:99), [`DependencyResolver`](src/main/java/com/ibm/jp/automation/e2m/DependencyResolver.java:132), [`ProjectCopier`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:89), [`PomGenerator`](src/main/java/com/ibm/jp/automation/e2m/PomGenerator.java:218) のユーザー向けログが日本語または英語で表示される。
- **Todo List**
  1. ユーザー向けの [`log.info`](src/main/java/com/ibm/jp/automation/e2m/Main.java:120) をメッセージキー参照に置き換える
  2. ユーザー向けの [`log.warn`](src/main/java/com/ibm/jp/automation/e2m/DependencyResolver.java:135) と [`log.error`](src/main/java/com/ibm/jp/automation/e2m/Main.java:101) を置き換える
  3. デバッグ出力と例外メッセージ文字列が変更されていないことを確認する
- **Relevant Context** — [`Main.call()`](src/main/java/com/ibm/jp/automation/e2m/Main.java:86), [`DependencyResolver.resolveJar()`](src/main/java/com/ibm/jp/automation/e2m/DependencyResolver.java:111), [`DependencyResolver.parseResponse()`](src/main/java/com/ibm/jp/automation/e2m/DependencyResolver.java:190), [`ProjectCopier.copy()`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:80), [`ProjectCopier.extractJspPageEncoding()`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:312), [`PomGenerator.generate()`](src/main/java/com/ibm/jp/automation/e2m/PomGenerator.java:60)
- **Status** — [x] done

### 3. 対話入力プロンプトと確認を行う
- **Intent** — CLI の対話入力でも同じロケール方針を適用し、回帰がないことを確認する。
- **Expected Outcomes** — [`Main.promptIfAbsent()`](src/main/java/com/ibm/jp/automation/e2m/Main.java:270) のプロンプトと入力再要求メッセージがロケールに応じて切り替わり、ビルドまたはテストで変更が妥当と確認できる。
- **Todo List**
  1. [`System.out.print`](src/main/java/com/ibm/jp/automation/e2m/Main.java:277) のプロンプト文言をi18n化する
  2. 空入力時の案内文言をi18n化する
  3. 関連するビルドまたはテストを実行して変更を検証する
- **Relevant Context** — [`Main.promptIfAbsent()`](src/main/java/com/ibm/jp/automation/e2m/Main.java:270), [`pom.xml`](pom.xml)
- **Status** — [x] done
