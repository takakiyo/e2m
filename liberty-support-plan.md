# Liberty Support Plan

## 概要

e2m が生成する Maven プロジェクトに Liberty 対応機能を追加する。
具体的には `liberty-maven-plugin` を pom.xml に追加し、`src/main/liberty/config/server.xml` を
プロジェクトに合わせた内容で生成する。

`-n/--noLiberty` オプションが指定された場合は従来どおり単純な war 出力プロジェクトを生成する。
Liberty 対応は **webProject=true の場合のみ** 行う（Java プロジェクトはスキップ）。

---

## サブタスク

### Sub-task 1: `-n/--noLiberty` オプションを Main.java に追加

**Intent**  
picocli の `@Option` として `--noLiberty` フラグを追加し、後続の生成処理に渡せるようにする。

**Expected Outcomes**
- `e2m -n ...` または `e2m --noLiberty ...` でオプションが認識される
- `noLiberty` フラグが `PomGenerator.generate()` および `LibertyServerXmlGenerator.generate()`
  の呼び出し制御に使われる

**Todo List**
1. `Main.java` に `@Option(names = {"-n", "--noLiberty"})` フィールドを追加
2. `PomGenerator.generate()` の呼び出し引数に `noLiberty` を追加（Sub-task 2 で署名変更後）
3. `LibertyServerXmlGenerator.generate()` の呼び出しを追加
   （条件: `!noLiberty && eclipseProject.webProject()`）

**Relevant Context**
- [`Main.java`](src/main/java/com/ibm/jp/automation/e2m/Main.java:49) — picocli `@Option` フィールド定義パターン（例: `--convertToUtf8`, `--debug`）
- [`Main.java`](src/main/java/com/ibm/jp/automation/e2m/Main.java:240) — `PomGenerator.generate()` 呼び出し箇所（L240-241）

**Status**: [x] done

---

### Sub-task 2: `PomGenerator.java` に liberty-maven-plugin を追加

**Intent**  
`noLiberty=false` かつ `webProject=true` の場合に `liberty-maven-plugin` を pom.xml の
`<build><plugins>` に追加する。

**Expected Outcomes**
- Liberty 対応時: `liberty-maven-plugin` (groupId=`io.openliberty.tools`, version=`3.12.0`) が出力される
- `--noLiberty` 指定時または Java プロジェクト時: 従来どおり liberty-maven-plugin は出力されない
- 既存テスト（`PomGeneratorTest`）が引数変更に追従してパスする

**Todo List**
1. `PomGenerator.generate()` の引数に `boolean noLiberty` を追加
2. バージョン定数 `LIBERTY_MAVEN_PLUGIN_VERSION = "3.12.0"` を追加
3. `if (!noLiberty && eclipseProject.webProject())` の条件で liberty-maven-plugin 要素を追加
   （maven-war-plugin の直後に追加する）
4. `PomGeneratorTest.java` の全テストの `generateNoConvert()` ヘルパーを修正
   （`noLiberty` 引数を追加、既存テストには `noLiberty=true` を渡す）

**Relevant Context**
- [`PomGenerator.java`](src/main/java/com/ibm/jp/automation/e2m/maven/PomGenerator.java:47) — バージョン定数の定義パターン
- [`PomGenerator.java`](src/main/java/com/ibm/jp/automation/e2m/maven/PomGenerator.java:166) — webProject 条件で maven-war-plugin を追加するパターン
- [`PomGeneratorTest.java`](src/test/java/com/ibm/jp/automation/e2m/maven/PomGeneratorTest.java:61) — `generateNoConvert()` ヘルパー

**Status**: [x] done

---

### Sub-task 3: `LibertyServerXmlGenerator.java` を新規作成

**Intent**  
`src/main/liberty/config/server.xml` を生成する新規クラスを作成する。
server.xml の雛形 XML をプログラム内に保持し、プロジェクトの Servlet バージョンから
`<feature>` と `<webApplication>` を書き換えて出力する。

**Expected Outcomes**
- `outputDir/src/main/liberty/config/server.xml` が生成される
- `<feature>` が Servlet バージョンに対応する Liberty プラットフォーム feature になる
- `<webApplication location="artifactId.war" contextRoot="/artifactId" />` が出力される

**Servlet バージョン → Liberty feature マッピング**

| Servlet | Liberty feature |
|---------|----------------|
| 3.0 以前（または不明） | `javaee-7.0` |
| 3.1 | `javaee-7.0` |
| 4.0 | `javaee-8.0` |
| 5.0 | `jakartaee-9.1` |
| 6.0 | `jakartaee-10.0` |
| 6.1 | `jakartaee-11.0` |

**server.xml 雛形（プログラム内に String 定数として保持）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<server description="Liberty Server">
    <featureManager>
        <feature>LIBERTY_FEATURE</feature>
    </featureManager>
    <httpEndpoint id="defaultHttpEndpoint" host="*" httpPort="9080" httpsPort="9443"/>
    <webApplication location="ARTIFACT_ID.war" contextRoot="/ARTIFACT_ID"/>
</server>
```

**Todo List**
1. `src/main/java/com/ibm/jp/automation/e2m/maven/LibertyServerXmlGenerator.java` を新規作成
2. `generate(EclipseProject eclipseProject, String artifactId, Path outputDir)` static メソッドを実装
3. Servlet バージョン → feature のマッピングを実装
   （`eclipseProject.webVersion()` を使用、null/不明時は `javaee-7.0`）
4. 雛形 XML 文字列のプレースホルダーを置換して `outputDir/src/main/liberty/config/server.xml` に書き出す
5. `src/test/java/com/ibm/jp/automation/e2m/maven/LibertyServerXmlGeneratorTest.java` を新規作成
   - 各 Servlet バージョンに対応する feature が出力されることをテスト
   - `<webApplication>` の location/contextRoot が artifactId を使ってい確認するテスト

**Relevant Context**
- [`PomGenerator.java`](src/main/java/com/ibm/jp/automation/e2m/maven/PomGenerator.java:216) — ファイル書き出し処理のパターン（`Files.createDirectories`, `Files.write`）
- [`JavaEEVersion.java`](src/main/java/com/ibm/jp/automation/e2m/spec/JavaEEVersion.java:68) — Servlet バージョンに応じた switch パターン
- [`AppLogger.java`](src/main/java/com/ibm/jp/automation/e2m/util/AppLogger.java) — ログ取得パターン

**Status**: [x] done

---

### Sub-task 4: i18n メッセージを追加

**Intent**  
Liberty 関連の処理ログを i18n 対応で追加する。

**Expected Outcomes**
- `messages_ja.properties` と `messages_en.properties` に Liberty 関連メッセージが追加される
- `Main.java` のステップログが表示される

**追加するメッセージキー（案）**

| キー | 日本語 | 英語 |
|------|--------|------|
| `liberty.serverXmlGenerated` | `Generated: {0}` | `Generated: {0}` |
| `liberty.skippedNotWebProject` | `  Liberty対応をスキップ（Javaプロジェクトのため）` | `  Skipping Liberty support (not a web project)` |

**Todo List**
1. `messages_ja.properties` に Liberty 関連メッセージを追加
2. `messages_en.properties` に Liberty 関連メッセージを追加
3. `LibertyServerXmlGenerator.java` のログ呼び出しに上記キーを使用

**Relevant Context**
- [`messages_ja.properties`](src/main/resources/messages_ja.properties:55) — `pom.generated` のパターン
- [`Messages.java`](src/main/java/com/ibm/jp/automation/e2m/i18n/Messages.java)

**Status**: [x] done

---

## 変更ファイル一覧

| ファイル | 変更種別 |
|---------|---------|
| `src/main/java/com/ibm/jp/automation/e2m/Main.java` | 修正 |
| `src/main/java/com/ibm/jp/automation/e2m/maven/PomGenerator.java` | 修正 |
| `src/main/java/com/ibm/jp/automation/e2m/maven/LibertyServerXmlGenerator.java` | 新規 |
| `src/main/resources/messages_ja.properties` | 修正 |
| `src/main/resources/messages_en.properties` | 修正 |
| `src/test/java/com/ibm/jp/automation/e2m/maven/PomGeneratorTest.java` | 修正 |
| `src/test/java/com/ibm/jp/automation/e2m/maven/LibertyServerXmlGeneratorTest.java` | 新規 |

## 実装後の検証

- `mvn test` ですべてのテストがパスすること
- `mvn package -DskipTests` でビルドが通ること
