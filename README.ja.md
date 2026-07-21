# e2m — Eclipse to Maven Converter

[English](README.md) | **[日本語](README.ja.md)**

EclipseのJavaプロジェクトをMavenの標準ディレクトリ構造に変換するコマンドラインツールです。

## 機能

- **Eclipse Javaプロジェクト**（`.project` / `.classpath`）の変換に対応
- **Eclipse WTP Webプロジェクト**（`.settings/org.eclipse.wst.common.component`）の変換に対応
- `.classpath` に記載されたJARファイルのSHA-1ハッシュを計算し、**Maven Central**を自動検索して `<dependency>` を生成
  - 見つからないJARはsystemスコープの依存として `pom.xml` に出力
- Javaソースを `src/main/java`（テストは `src/test/java`）にコピー
- リソースファイルを `src/main/resources`（テストは `src/test/resources`）にコピー
- WebコンテンツをMaven標準の `src/main/webapp` にコピー
- `<packaging>war</packaging>` をWTPプロジェクトに自動付与
- **文字エンコード変換**（`--convertToUtf8`）：Shift_JIS などのレガシーエンコードで書かれたソースを UTF-8 に変換してコピー

## 動作要件

- Java 17以上
- Maven 3.8以上（ビルド時のみ）
- GraalVM 21以上（ネイティブイメージビルド時のみ）
- インターネット接続（Maven Central検索時）

## ビルド

### Fat JAR

```bash
mvn package
```

`target/e2m.jar` が生成されます。

### ネイティブ実行ファイル（GraalVM Native Image）

GraalVM JDKが `JAVA_HOME` に設定されている環境で実行してください。

```bash
mvn -Pnative package
```

`target/e2m`（macOS/Linux）または `target/e2m.exe`（Windows）が生成されます。

## 使い方

```
e2m [-g <groupId>] [-a <artifactId>] [-v <version>] [--javaTargetVersion <version>]
    [--convertToUtf8 [-e <encoding>]]
    <inputDir> <outputDir>
```

オプションを省略した場合は、プロジェクト解析後に対話的に入力を求めます。
`artifactId` のデフォルトは Eclipse のプロジェクト名、`artifactVersion` のデフォルトは `1.0-SNAPSHOT` です。

出力先は `<outputDir>/<artifactId>/` ディレクトリに生成されます。

### オプション

| オプション | 説明 |
|---|---|
| `-g`, `--groupId` | 生成するpom.xmlのgroupId（省略時は対話入力） |
| `-a`, `--artifactId` | 生成するpom.xmlのartifactId（省略時はプロジェクト名がデフォルト） |
| `-v`, `--artifactVersion` | 生成するpom.xmlのversion（省略時は `1.0-SNAPSHOT`） |
| `--javaTargetVersion` | 生成するpom.xmlの `maven.compiler.target` を上書きする（省略時はEclipseプロジェクトの設定値を使用。ソースバージョンより低い値は指定不可） |
| `--convertToUtf8` | ソースファイルをUTF-8に変換してコピーする（詳細は後述） |
| `-e`, `--sourceEncoding` | 変換元エンコーディング（例: `Shift_JIS`）。`--convertToUtf8` 指定時のみ有効。省略時は対話入力 |
| `--debug` | デバッグ情報をZIPファイルとして出力する（詳細は後述） |
| `-h`, `--help` | ヘルプを表示 |
| `-V`, `--version` | バージョンを表示 |

### 引数

| 引数 | 説明 |
|---|---|
| `<inputDir>` | 変換元のEclipseプロジェクトディレクトリ |
| `<outputDir>` | 変換先のMavenプロジェクトを生成するベースディレクトリ |

### 実行例

オプションをすべて指定する場合（Fat JAR）:

```bash
java -jar target/e2m-1.0.jar \
  -g com.example \
  -a myapp \
  -v 1.0 \
  /path/to/eclipse-project \
  /path/to/output
# → /path/to/output/myapp/ に生成される
```

オプションをすべて指定する場合（ネイティブ実行ファイル）:

```bash
./e2m \
  --groupId com.example \
  --artifactId myapp \
  --artifactVersion 1.0 \
  /path/to/eclipse-project \
  /path/to/output
```

オプションを省略して対話入力する場合:

```bash
./e2m /path/to/eclipse-project /path/to/output

groupId: com.example
artifactId [MyEclipseProject]: myapp
artifactVersion [1.0-SNAPSHOT]:
# → /path/to/output/myapp/ に生成される
```

## 文字エンコード変換（--convertToUtf8）

Eclipseプロジェクトがレガシーエンコード（Shift_JIS など）で書かれている場合、`--convertToUtf8` オプションを指定することで、コピーと同時に UTF-8 へ変換できます。

### 基本的な使い方

```bash
./e2m --convertToUtf8 -e Shift_JIS \
  /path/to/eclipse-project \
  /path/to/output
```

`-e` / `--sourceEncoding` を省略した場合は、プロジェクト解析後に対話入力を求めます。

```bash
./e2m --convertToUtf8 /path/to/eclipse-project /path/to/output

groupId: com.example
artifactId [MyApp]:
artifactVersion [1.0-SNAPSHOT]:
sourceEncoding: Shift_JIS
```

### ファイル種別ごとの変換仕様

#### `.java` ファイル

`--sourceEncoding` で指定されたエンコードで読み込み、UTF-8 で書き出します。

#### `.properties` ファイル

`--sourceEncoding` で読み込み、`\uXXXX` 形式の Unicode エスケープシーケンスを Unicode 文字に展開したうえで UTF-8 で書き出します。

出力先は **Javaターゲットバージョン** によって変わります。

| javaTargetVersion | 出力先 | 理由 |
|---|---|---|
| 9 以上 | `src/main/resources/` | Java 9 以降は `.properties` を UTF-8 で直接読み込めるため |
| 8 以下 | `src/main/resources-utf8/` | Java 8 以下は UTF-8 の `.properties` を直接読み込めないため（後述） |

**Java 8 以下の場合：** `src/main/resources-utf8/` に配置した UTF-8 の `.properties` を、ビルド時に `native2ascii-maven-plugin` が `\uXXXX` 形式に変換して `src/main/resources/` 相当の出力ディレクトリに配置します。このため `pom.xml` に以下のプラグイン設定が自動追加されます。

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>native2ascii-maven-plugin</artifactId>
  <version>2.1.1</version>
  <executions>
    <execution>
      <id>native2ascii-utf8-resources</id>
      <phase>process-resources</phase>
      <goals><goal>resources</goal></goals>
      <configuration>
        <encoding>UTF-8</encoding>
        <srcDir>${project.basedir}/src/main/resources-utf8</srcDir>
        <outputDir>${project.build.outputDirectory}</outputDir>
      </configuration>
    </execution>
  </executions>
</plugin>
```

#### `.jsp` ファイル

ファイル先頭の最大 4KB を ISO-8859-1 で読み込み、`<%@ page pageEncoding="..." %>` ディレクティブを検出します。

| 状況 | 動作 |
|---|---|
| `pageEncoding` が検出でき、かつ UTF-8 以外 | 検出されたエンコードでファイルを読み込み、`pageEncoding` 属性値を `UTF-8` に書き換えて出力 |
| `pageEncoding` が検出できない | `--sourceEncoding` の値でファイルを読み込み、UTF-8 で出力 |
| `pageEncoding` が既に `UTF-8` | 変換せずバイナリコピー |

#### その他のファイル

以下のファイルはアプリケーションの動作に直接影響するため、変換せずにバイナリコピーします。

- `.html` / `.htm` — `<meta charset>` の書き換えはアプリ動作に影響するため
- `.css` / `.js` — アプリ動作に影響するため
- `.xml` / `.tag` / `.tld` — 従来より UTF-8 の使用が慣習であり変換不要のため
- `.jar` / 画像 / バイナリファイル

### 変換後のディレクトリ構造（Java 8 以下の場合）

```
output/<artifactId>
├── pom.xml              ← native2ascii-maven-plugin が追加済み
└── src/
    └── main/
        ├── java/
        ├── resources-utf8/  ← UTF-8 に変換した .properties（ビルド時に変換される）
        └── webapp/
```

## 変換がうまくいかない場合

変換結果が期待どおりにならない場合は、`--debug` オプションを付けて再実行してください。
変換完了後に `<outputDir>/e2m_debug_<日付時刻>.zip` が生成されます。

```bash
./e2m --debug \
  -g com.example \
  -a myapp \
  /path/to/eclipse-project \
  /path/to/output
# → /path/to/output/e2m_debug_20250101_120000.zip が生成される
```

ZIPファイルには以下のデバッグ情報が含まれます。

| ファイル | 内容 |
|---|---|
| `eclipse/.project` | Eclipseプロジェクト定義ファイル |
| `eclipse/.classpath` | Eclipseクラスパス定義ファイル |
| `eclipse/.factorypath` | Eclipseファクトリパス定義ファイル |
| `eclipse/.settings/…` | `.settings/` ディレクトリ以下の全ファイル |
| `eclipse_files.json` | Eclipseプロジェクト内の全ディレクトリ・ファイルの一覧（名前・サイズ・日付。JARファイルにはSHA1ハッシュも含む） |
| `pom.xml` | 生成したMavenプロジェクトの `pom.xml` |
| `maven_files.json` | 生成したMavenプロジェクトの全ディレクトリ・ファイルの一覧（名前・サイズ・日付） |

問題が解決しない場合は、生成された ZIP ファイルを添付して [Issues](../../issues) にご報告ください。

## 変換結果のディレクトリ構造

### Javaプロジェクト

```
output/<artifactId>
├── pom.xml
└── src/
    ├── main/
    │   ├── java/        ← Eclipseのsrcフォルダ以下の.javaファイル
    │   └── resources/   ← Eclipseのsrcフォルダ以下の非.javaファイル
    └── test/
        ├── java/        ← 名前に"test"を含むsrcフォルダ以下の.javaファイル
        └── resources/   ← 名前に"test"を含むsrcフォルダ以下の非.javaファイル
```

### WTP Webプロジェクト

```
output/<artifactId>
├── pom.xml              ← <packaging>war</packaging> を含む
└── src/
    └── main/
        ├── java/        ← Javaソース
        ├── resources/   ← リソースファイル
        └── webapp/      ← Webコンテンツ (JSP, HTML, WEB-INF/web.xml など)
```

## Webコンテンツルートの解決

Webコンテンツのルートディレクトリは `WebContent` 等に固定せず、`.settings/org.eclipse.wst.common.component` の `tag="defaultRootSource"` エントリから動的に解決します。

```xml
<!-- .settings/org.eclipse.wst.common.component の例 -->
<wb-module deploy-name="MyApp">
  <wb-resource deploy-path="/" source-path="/WebContent" tag="defaultRootSource"/>
</wb-module>
```

## テスト

```bash
mvn test
```

## ライセンス

Apache License, Version 2.0 — [LICENSE](LICENSE) を参照してください。

## 作者

Takakiyo Tanaka (IBM Japan)
