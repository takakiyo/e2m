# Eclipse to Maven 変換ツール (e2m) 実装計画

## 概要

EclipseのJavaプロジェクト（標準JavaおよびWTP Webプロジェクト）を、Mavenの標準ディレクトリ構造に変換するコマンドラインツール `e2m` を実装する。

- 入力：Eclipseプロジェクトディレクトリ（`.project` / `.classpath` を持つもの）
- 出力：別の出力ディレクトリに新規生成されるMavenプロジェクト
- 配布：GraalVM Native Image によるネイティブ実行ファイル（macOS / Windows / Linux）

### 使用方法

```
e2m --groupId <groupId> --artifactId <artifactId> --artifactVersion <version> \
    <入力Eclipseプロジェクトディレクトリ> <出力ディレクトリ>
```

---

## Sub-Task 1: プロジェクトスケルトンの作成

**Status**: [x] done

### Intent
ツール自体のMavenプロジェクト構造（ディレクトリ・pom.xml・エントリポイントクラス）を作成する。
GraalVM Native ImageビルドとpicocliによるCLIの基盤を整える。

### Expected Outcomes
- `pom.xml` が存在し、picocli・GraalVM Native Image Mavenプラグインが設定されている
- `src/main/java/com/example/e2m/Main.java` が存在し、CLIオプションの定義（--groupId, --artifactId, --version, 入力/出力パス）が実装されている
- `mvn package` が通り、Fat JARが生成できる状態になっている

### Todo List
1. ツールのルートディレクトリに `pom.xml` を作成する
   - groupId: `com.ibm.jp.automation`, artifactId: `e2m`, version: `1.0`
   - Java 17以上をターゲットに設定
   - 依存関係: `info.picocli:picocli`
   - プラグイン: `maven-shade-plugin`（Fat JAR生成）、`native-maven-plugin`（GraalVM Native Image）
2. `src/main/java/com/example/e2m/Main.java` を作成する
   - picocliの `@Command` アノテーションでCLIを定義
   - `--groupId`, `--artifactId`, `--version`（必須オプション）
   - 引数: `inputDir`（Eclipseプロジェクトのパス）、`outputDir`（出力先パス）
   - `call()` メソッドでメインの処理フローを呼び出す（後のサブタスクで実装する各クラスに委譲）

### Relevant Context
- picocli: https://picocli.info/ （GraalVM Native Image対応が優秀なCLIライブラリ）
- GraalVM Native Image Maven Plugin: `org.graalvm.buildtools:native-maven-plugin`

---

## Sub-Task 2: Eclipseプロジェクト情報のパース

**Status**: [x] done

### Intent
Eclipseプロジェクトのメタデータファイル（`.project`、`.classpath`、WTPの設定ファイル）を解析し、変換に必要な情報を構造体として取り出す。

### Expected Outcomes
- `EclipseProject` データクラスが存在し、以下の情報を保持する
  - プロジェクト名（`.project` の `<name>` 要素）
  - プロジェクト種別（Java / Webプロジェクト）
  - Javaソースフォルダのリスト（`.classpath` の `kind="src"` エントリ）
  - 出力フォルダ（`.classpath` の `kind="output"` エントリ）
  - JARファイルパスのリスト（`.classpath` の `kind="lib"` エントリの `path` 属性）
  - WebコンテンツのルートディレクトリパスとWEB-INFパス（WTPプロジェクトの場合）— **`WebContent` 等に決め打ちせず、`.settings/org.eclipse.wst.common.component` から動的に解決する**
- `EclipseProjectParser` クラスが存在し、入力ディレクトリを受け取って `EclipseProject` を返す

### Todo List
1. `EclipseProject.java` を作成する（データ保持用のレコードまたはクラス）
2. `EclipseProjectParser.java` を作成する
   - `.project` ファイルを解析してプロジェクト名とネイチャー（`org.eclipse.wst.common.project.facet.core.nature` の有無でWebプロジェクト判定）を取得
   - `.classpath` ファイルを解析して src / output / lib エントリを取得
   - WTPプロジェクトの場合は `.settings/org.eclipse.wst.common.component` を解析してWebコンテンツルートを特定する。具体的には：
     - `<wb-module>` 配下の `<wb-resource>` 要素のうち、`tag="defaultRootSource"` 属性を持つ要素の `source-path` 属性値をWebコンテンツルートとして取得する
     - 取得したWebコンテンツルートに `WEB-INF/web.xml` を結合したパスを `web.xml` の参照先とする

### Relevant Context
- Eclipseの `.project` ファイル: XML形式、`<natures>` 要素でプロジェクト種別を識別
- Eclipseの `.classpath` ファイル: XML形式、`<classpathentry kind="src|output|lib|con" .../>` の構造
- WTP設定ファイル `.settings/org.eclipse.wst.common.component` の構造例:
  ```xml
  <wb-module deploy-name="MyApp">
    <wb-resource deploy-path="/" source-path="/WebContent" tag="defaultRootSource"/>
    <wb-resource deploy-path="/WEB-INF/classes" source-path="/src"/>
  </wb-module>
  ```
  → `tag="defaultRootSource"` の `source-path="/WebContent"` がWebコンテンツルート
  → `web.xml` のパスは `<inputDir>/WebContent/WEB-INF/web.xml`（`source-path` の値を使用）

---

## Sub-Task 3: JARファイルのMaven Central検索

**Status**: [x] done

### Intent
`.classpath` に記載されたJARファイルのSHA1ハッシュを計算し、Maven Central REST APIで検索して `<dependency>` 情報を取得する。見つからない場合はsystemスコープの依存として記録する。

### Expected Outcomes
- `DependencyResolver` クラスが存在し、JARファイルパスのリストを受け取って `List<MavenDependency>` を返す
- `MavenDependency` データクラスが存在し、以下を保持する
  - `groupId`, `artifactId`, `version`
  - `scope`（通常は省略、systemの場合は "system"）
  - `systemPath`（systemスコープの場合のみ、絶対パス）
- Maven Central APIで見つかったJARは正しいgroupId/artifactId/versionが設定される
- 見つからなかったJARはsystemスコープとして元のJARファイルの絶対パスがsystemPathに設定される

### Todo List
1. `MavenDependency.java` を作成する（データ保持用レコード）
2. `DependencyResolver.java` を作成する
   - JARファイルのSHA1を計算するメソッド（`java.security.MessageDigest` を使用）
   - Maven Central Search API (`https://search.maven.org/solrsearch/select?q=1:<SHA1>&rows=1&wt=json`) を呼び出すメソッド（`java.net.http.HttpClient` を使用）
   - レスポンスのJSONをパースして `groupId`/`artifactId`/`version` を取得
   - 見つからない場合は systemスコープの `MavenDependency` を生成

### Relevant Context
- Maven Central SHA1検索API: `https://search.maven.org/solrsearch/select?q=1:{sha1}&rows=1&wt=json`
- レスポンスJSON: `response.docs[0].g`（groupId）, `response.docs[0].a`（artifactId）, `response.docs[0].latestVersion`（version）
- SHA1計算: `MessageDigest.getInstance("SHA-1")`
- JSONパース: Java標準ライブラリにはJSONパーサーがないため、軽量ライブラリ（`org.json` など）またはシンプルな正規表現/文字列パースを使用する

---

## Sub-Task 4: pom.xml の生成

**Status**: [x] done

### Intent
解析したEclipseプロジェクト情報と解決済み依存関係リストを元に、Mavenの `pom.xml` を生成する。

### Expected Outcomes
- `PomGenerator` クラスが存在し、`EclipseProject` と `List<MavenDependency>` と CLI引数（groupId/artifactId/version）を受け取り、出力ディレクトリに `pom.xml` を生成する
- 生成された `pom.xml` は以下を含む
  - `<groupId>`, `<artifactId>`, `<version>`（CLI引数から）
  - `<packaging>war</packaging>`（Webプロジェクトの場合）または `jar`
  - `<dependencies>` セクション（解決済み依存関係）
  - systemスコープの依存は `<systemPath>` を含む
  - Javaのコンパイラバージョン設定（`maven-compiler-plugin`、ソースのJavaバージョンはEclipseの `.classpath` の `con` エントリから推定、不明の場合は11）

### Todo List
1. `PomGenerator.java` を作成する
   - Java DOM API（`javax.xml.parsers.DocumentBuilder`）または文字列テンプレートでXMLを生成
   - プロジェクト種別に応じた `<packaging>` の設定
   - 依存関係のループでsystemスコープの有無を分岐して `<dependency>` を出力

### Relevant Context
- Maven pom.xml の標準構造を参照
- systemスコープの依存は `<scope>system</scope>` と `<systemPath>${project.basedir}/libs/xxx.jar</systemPath>` の形式が一般的

---

## Sub-Task 5: ソースファイルおよびWebコンテンツのコピー

**Status**: [x] done

### Intent
EclipseプロジェクトのソースファイルをMaven標準ディレクトリ構造にコピーする。Webプロジェクトの場合はWebコンテンツも `src/main/webapp` にコピーする。

### Expected Outcomes
- `ProjectCopier` クラスが存在し、`EclipseProject`・入力ディレクトリ・出力ディレクトリを受け取り、ファイルをコピーする
- Javaソース（`.classpath` の `kind="src"` で指定されたフォルダ以下のファイル）が `src/main/java/` 以下にコピーされる
- テストソースフォルダ（名前に "test" を含むフォルダ）は `src/test/java/` 以下にコピーされる
- Webプロジェクトの場合、Webコンテンツルート以下のファイルが `src/main/webapp/` にコピーされる
- リソースファイル（Javaソースフォルダにある `.java` 以外のファイル）は `src/main/resources/` にコピーされる

### Todo List
1. `ProjectCopier.java` を作成する
   - `java.nio.file.Files.walkFileTree` または `Files.walk` を使用してファイルをコピー
   - ソースフォルダ名に "test" が含まれる場合は `src/test/java/` へ振り分け
   - `.java` ファイルは `src/main/java/`（または `src/test/java/`）へ、それ以外は `src/main/resources/`（または `src/test/resources/`）へ
   - Webコンテンツ（WTPプロジェクトの場合）は `src/main/webapp/` へコピー

### Relevant Context
- Maven標準ディレクトリ: `src/main/java`, `src/main/resources`, `src/main/webapp`, `src/test/java`, `src/test/resources`
- `java.nio.file.Files.copy` / `Files.walk` で再帰的コピーが可能

---

## Sub-Task 6: 統合テストと動作確認

**Status**: [x] done

### Intent
各コンポーネントを `Main.java` に統合し、サンプルのEclipseプロジェクトに対して変換が正しく動作することを確認する。

### Expected Outcomes
- `Main.java` が Sub-Task 2〜5 で実装したクラスを正しい順序で呼び出す
- サンプルのEclipseプロジェクト（Javaプロジェクト・Webプロジェクト）に対して `mvn package` で実行可能JARが生成でき、変換が正常に完了する
- エラーケース（入力ディレクトリが存在しない、`.project` ファイルがないなど）で適切なエラーメッセージが表示される

### Todo List
1. `Main.java` の `call()` メソッドで以下の順序で処理を実装する
   1. 入力ディレクトリの存在確認
   2. `EclipseProjectParser` でプロジェクト情報をパース
   3. `DependencyResolver` でJAR依存関係を解決
   4. 出力ディレクトリを作成
   5. `PomGenerator` で `pom.xml` を生成
   6. `ProjectCopier` でソース・Webコンテンツをコピー
2. サンプルのEclipseプロジェクト（`src/test/resources/` 以下にサンプルを配置）を用意して動作確認

### Relevant Context
- Sub-Task 1〜5 で実装されたクラス群
- GraalVM Native Imageビルドは `mvn -Pnative package` で実行
