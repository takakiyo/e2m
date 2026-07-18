# e2m — Eclipse to Maven Converter

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

`target/e2m-1.0.jar` が生成されます。

### ネイティブ実行ファイル（GraalVM Native Image）

GraalVM JDKが `JAVA_HOME` に設定されている環境で実行してください。

```bash
mvn -Pnative package
```

`target/e2m`（macOS/Linux）または `target/e2m.exe`（Windows）が生成されます。

## 使い方

```
e2m [-g <groupId>] [-a <artifactId>] [-v <version>] <inputDir> <outputDir>
```

オプションを省略した場合は、プロジェクト解析後に対話的に入力を求めます。
`artifactId` のデフォルトは Eclipse のプロジェクト名、`artifactVersion` のデフォルトは `1.0-SNAPSHOT` です。

出力先は `<outputDir>/<artifactId>/` ディレクトリに生成されます。

### オプション

| オプション | 必須 | 説明 |
|---|---|---|
| `-g`, `--groupId` | | 生成するpom.xmlのgroupId（省略時は対話入力） |
| `-a`, `--artifactId` | | 生成するpom.xmlのartifactId（省略時はプロジェクト名がデフォルト） |
| `-v`, `--artifactVersion` | | 生成するpom.xmlのversion（省略時は `1.0-SNAPSHOT`） |
| `-h`, `--help` | | ヘルプを表示 |
| `-V`, `--version` | | バージョンを表示 |

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
