# --convert-to-utf8 機能追加 計画

## 概要

Eclipseプロジェクトのファイルを Maven プロジェクトへコピーする際、文字エンコードを UTF-8 に変換する
`--convert-to-utf8` オプションを追加する。

変換元エンコードは `-e` / `--sourceEncoding` オプションで指定し、未指定の場合は対話的に入力を受け付ける。
JSP ファイルは `<%@ page pageEncoding="..." %>` ディレクティブを解析して、
ファイルごとに変換元エンコーディングを個別に決定する。

### 対象ファイル種別と変換仕様

| ファイル種別 | 変換元エンコード | UTF-8変換後の追加処理 |
|---|---|---|
| `.java` ファイル | `--sourceEncoding` で指定した値（対話入力） | なし（テキスト再エンコードのみ） |
| `.properties` ファイル | `--sourceEncoding` で指定した値（対話入力） | `\uXXXX` エスケープを Unicode 文字に展開したうえで UTF-8 で書き出す。出力先は javaTargetVersion による（後述） |
| `.jsp` ファイル | `<%@ page pageEncoding="..." %>` を優先。なければ `--sourceEncoding` | `pageEncoding` 属性の値を `UTF-8` に書き換える |

### 対象外（バイナリコピーのまま）

- `.jar` ファイル
- 画像・バイナリリソース（`.gif`, `.png`, `.jpg`, `.class` など）
- `WEB-INF/lib` 以下の JAR
- `.html` / `.htm` — `<meta charset>` 書き換えはアプリ動作に影響するためそのままコピー
- `.css` / `.js` — アプリ動作に影響するためそのままコピー
- `.xml` / `.tag` / `.tld` — 従来より UTF-8 が慣習であり変換不要のためそのままコピー

### `.properties` の変換詳細

`\uXXXX` エスケープ展開 + テキスト再エンコードで変換する。

変換の流れ：
1. 変換元エンコード（例: Shift_JIS）でファイルを行単位のテキストとして読み込む
2. `Properties.load(Reader)` で読み込み、`\uXXXX` エスケープを自動展開する
3. コメントや空行の保持が必要なため、実際には行単位でテキストを読み込み、
   `\uXXXX` シーケンスを正規表現で検出・展開して UTF-8 で書き出す

**出力先の分岐（`javaTargetVersion` による）：**

| javaTargetVersion | 出力先 | 理由 |
|---|---|---|
| 9 以上 | `src/main/resources/` （通常のリソースフォルダ）| Java 9+ は `.properties` を UTF-8 で直接読めるため |
| 8 以下 | `src/main/resources-utf8/` （専用フォルダ）| Java 8 以下は UTF-8 の `.properties` を直接読めないため、ビルド時に `native2ascii-maven-plugin` で `\uXXXX` 形式に変換してから `src/main/resources/` に配置する |

**Java 8 以下の場合の pom.xml への追加：**

`native2ascii-maven-plugin`（`org.codehaus.mojo:native2ascii-maven-plugin:2.1.1`）を `<build><plugins>` に追加する。
設定例：
```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>native2ascii-maven-plugin</artifactId>
  <version>2.1.1</version>
  <executions>
    <execution>
      <id>native2ascii-utf8-resources</id>
      <phase>process-resources</phase>
      <goals>
        <goal>resources</goal>
      </goals>
      <configuration>
        <encoding>UTF-8</encoding>
        <srcDir>${project.basedir}/src/main/resources-utf8</srcDir>
        <outputDir>${project.build.outputDirectory}</outputDir>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### `.jsp` の変換詳細

JSP ファイルの `pageEncoding` 属性の処理：
1. ファイルをISO-8859-1で，最初の4KBまで読み込む。
2. `<%@ page ... pageEncoding="Shift_JIS" ... %>` のような指定を正規表現で検出
3. 検出できた場合: そのエンコーディングでファイルを読み込なおし、`pageEncoding` 属性値を `UTF-8` に書き換えて出力
4. 検出できなかった場合: `--sourceEncoding` の値でファイルを読み込み、そのまま UTF-8 で出力
5. `pageEncoding` が既に `UTF-8` または `utf-8` の場合: 変換せずバイナリコピー

---

## サブタスク一覧

### サブタスク 1: `Main.java` に `--convert-to-utf8` と `--sourceEncoding/-e` オプションを追加

**Intent**  
CLI に 2 つのオプションを追加し、`--convert-to-utf8` が指定されたとき、
`--sourceEncoding` が未指定であれば対話的に入力を求める。

**Expected Outcomes**
- `--convert-to-utf8` フラグが `Main` クラスに存在する
- `--sourceEncoding` / `-e` オプションが `Main` クラスに存在する
- `--convert-to-utf8` 指定時かつ `--sourceEncoding` 未指定の場合、既存の `promptIfAbsent()` を使って対話入力する
- `--convert-to-utf8` 未指定時は `--sourceEncoding` の入力を求めない
- `Charset` のバリデーション（無効な文字セット名はエラーメッセージを出して終了コード 1 を返す）
- `ProjectCopier.copy()` と `PomGenerator.generate()` の呼び出し時に、変換オプション情報を渡す（シグネチャ変更）
- `[5/5]` のログ出力に変換モードの表示を追加

**Todo List**
1. `Main.java` に `@Option(names = {"--convert-to-utf8"})` フィールド `convertToUtf8` を追加
2. `Main.java` に `@Option(names = {"-e", "--sourceEncoding"})` フィールド `sourceEncoding` を追加（String型）
3. `call()` 内の Maven 情報入力ブロック（または直後）に、`convertToUtf8` かつ `sourceEncoding` 未指定の場合に `promptIfAbsent()` で `sourceEncoding` を入力するロジックを追加
4. `sourceEncoding` の値が有効な `Charset` かバリデーション（`Charset.forName()`）し、不正なら終了コード 1
5. `PomGenerator.generate()` の呼び出しに `convertToUtf8`（boolean）と実効 `javaTargetVersion` を渡す（両メソッドのシグネチャ変更）
6. `ProjectCopier.copy()` の呼び出しに `convertToUtf8`（boolean）と `Charset sourceEncoding` と実効 `javaTargetVersion` を渡す（シグネチャ変更）

**Relevant Context**
- [`Main.java:49-54`](src/main/java/com/ibm/jp/automation/e2m/Main.java:49) — 既存オプション定義パターン
- [`Main.java:113-133`](src/main/java/com/ibm/jp/automation/e2m/Main.java:113) — groupId 対話入力ブロック（`promptIfAbsent` の使用例）
- [`Main.java:155-171`](src/main/java/com/ibm/jp/automation/e2m/Main.java:155) — `PomGenerator.generate()` および `ProjectCopier.copy()` 呼び出し箇所
- `call()` 内で実効 `javaTargetVersion` が確定するのは 157-165 行（`javaTargetOverride` と Eclipse 設定の合成後）

**Status**: [x] done

---

### サブタスク 2: `PomGenerator.java` に `native2ascii-maven-plugin` 追加ロジックを実装

**Intent**  
`--convert-to-utf8` が有効かつ `javaTargetVersion` が 8 以下の場合、
pom.xml の `<build><plugins>` に `native2ascii-maven-plugin` を追加する。

**Expected Outcomes**
- `generate()` のシグネチャに `boolean convertToUtf8` と `JavaVersion effectiveTargetVersion` が追加される
- `convertToUtf8 = true` かつ `effectiveTargetVersion <= 8` のとき、pom.xml に `native2ascii-maven-plugin` が追加される
- `convertToUtf8 = false` または `effectiveTargetVersion >= 9` のときは従来通りのpom.xml
- native2ascii プラグインの設定は仕様通り（`srcDir: src/main/resources-utf8`、`outputDir: ${project.build.outputDirectory}`、`encoding: UTF-8`、`phase: generate-resources`）

**Todo List**
1. `PomGenerator.generate()` のシグネチャに `boolean convertToUtf8` と `JavaVersion effectiveTargetVersion` を追加
2. `build/plugins` ブロックの構築箇所（現在 139-158 行）の後に、条件分岐で `native2ascii-maven-plugin` の `<plugin>` 要素を追加するロジックを実装
3. プラグインの定数（`NATIVE2ASCII_PLUGIN_VERSION = "2.1.1"` など）をクラス定数として定義

**Relevant Context**
- [`PomGenerator.java:139-159`](src/main/java/com/ibm/jp/automation/e2m/PomGenerator.java:139) — `<build><plugins>` ブロックの構築箇所
- [`PomGenerator.java:56-63`](src/main/java/com/ibm/jp/automation/e2m/PomGenerator.java:56) — `generate()` メソッドシグネチャ
- [`JavaVersion.java`](src/main/java/com/ibm/jp/automation/e2m/JavaVersion.java) — `compareTo()` / `toInt()` で 8 以下の判定が可能

**Status**: [x] done

---

### サブタスク 3: `ProjectCopier.java` にエンコーディング変換コピーロジックを追加

**Intent**  
`--convert-to-utf8` が有効なとき、ファイル種別（`.java`, `.properties`, `.jsp`）に応じたエンコーディング変換を行うコピーメソッドを実装する。

**Expected Outcomes**
- `copy()` のシグネチャが `boolean convertToUtf8, Charset sourceEncoding, JavaVersion effectiveTargetVersion` を受け取る形に変更される
- `convertToUtf8 = false` のときは従来通り `Files.copy()`（バイナリ）
- `.java` ファイル: `sourceEncoding` で読み込み、UTF-8 で書き出す
- `.properties` ファイル:
  - `sourceEncoding` で行単位に読み込み、`\uXXXX` シーケンスを Unicode 文字に展開し、UTF-8 で書き出す
  - `effectiveTargetVersion <= 8` の場合は `src/main/resources-utf8/` へ、`>= 9` の場合は `src/main/resources/` へ出力
- `.jsp` ファイル: `pageEncoding` 属性を正規表現で検出 → 検出できた場合はそのエンコードで読み込み `pageEncoding` 属性値を `UTF-8` に書き換えて出力、検出できない場合は `sourceEncoding` で読み込み UTF-8 で書き出す
- `.jar` や画像など変換対象外のファイルはバイナリコピーのまま
- JSPの `pageEncoding` が既にUTF-8系の場合はバイナリコピー（変換スキップ）
- 変換したファイルは `[UTF-8変換]` プレフィックスでログ出力

**Todo List**
1. `copy()` のシグネチャに `boolean convertToUtf8, Charset sourceEncoding, JavaVersion effectiveTargetVersion` を追加
2. `copySourceFolder()` のシグネチャに同様のパラメータを追加し、`.java` の変換処理と `.properties` の変換処理・出力先分岐を実装
3. `copyWebContents()` のシグネチャに `boolean convertToUtf8, Charset sourceEncoding` を追加し、`.jsp` の変換処理を実装
4. `copyWithEncodingConversion(Path src, Path dest, Charset from)` ヘルパー：テキストファイルを `from` エンコードで読み込み UTF-8 で書き出す
5. `unescapeUnicode(String line)` ヘルパー：`\uXXXX` シーケンスを Unicode 文字に展開する（`.properties` 用）
6. `extractJspPageEncoding(Path jspFile, Charset fallback)` ヘルパー：`<%@ page ... pageEncoding="..." ... %>` を正規表現でパースして `Charset` を返す（見つからない場合は `fallback` を返す）。既に UTF-8 の場合は `null` を返して呼び出し元でバイナリコピーに切り替える
7. `copyJspWithConversion(Path src, Path dest, Charset from)` ヘルパー：`pageEncoding` 属性の値を `UTF-8` に書き換えて UTF-8 で書き出す
8. 変換時に変換したファイルを1件ずつログ出力（`"  [UTF-8変換] ファイル名"` 形式）

**Relevant Context**
- [`ProjectCopier.java:103-120`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:103) — `copySourceFolder()` 実装
- [`ProjectCopier.java:131-150`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:131) — `copyWebContents()` 実装
- [`ProjectCopier.java:47-92`](src/main/java/com/ibm/jp/automation/e2m/ProjectCopier.java:47) — `copy()` メインメソッド
- JSP の `pageEncoding` 正規表現: `<%@\s*page\b[^%]*\bpageEncoding\s*=\s*["']([^"']+)["']`
- `\uXXXX` の正規表現: `\\u([0-9a-fA-F]{4})`

**Status**: [x] done

---

### サブタスク 4: テストの追加

**Intent**  
新機能のユニットテストを追加し、各ファイル種別の変換が正しく動作することを確認する。

**Expected Outcomes**
- `.java` ファイルの Shift_JIS → UTF-8 変換テスト
- `.properties` ファイルの Shift_JIS → UTF-8 変換テスト（`\uXXXX` 展開も含む）
- `.properties` ファイルの出力先が `javaTargetVersion` によって変わるテスト（Java 8: `resources-utf8/`, Java 17: `resources/`）
- `.jsp` ファイルの `pageEncoding` 検出 + 変換テスト（pageEncoding あり / なし の両ケース）
- `.jsp` ファイルの `pageEncoding` が既に UTF-8 の場合はバイナリコピーのままのテスト
- `convertToUtf8 = false` のときはバイナリコピーのままのテスト
- `PomGeneratorTest`: `native2ascii-maven-plugin` の有無テスト（Java 8 以下で `convertToUtf8=true` / Java 17 で `convertToUtf8=true` / `convertToUtf8=false`）
- 既存テストがすべて通ること

**Todo List**
1. `ProjectCopierTest.java` に変換系テストケースを追加（Shift_JIS バイト列をインラインで生成して入力ファイルを作成）
2. `PomGeneratorTest.java` に `native2ascii-maven-plugin` の有無を検証するテストケースを追加
3. `extractJspPageEncoding()` の単体テストを `ProjectCopierTest` に追加（package-private で OK）

**Relevant Context**
- [`src/test/java/com/ibm/jp/automation/e2m/ProjectCopierTest.java`](src/test/java/com/ibm/jp/automation/e2m/ProjectCopierTest.java)
- [`src/test/java/com/ibm/jp/automation/e2m/PomGeneratorTest.java`](src/test/java/com/ibm/jp/automation/e2m/PomGeneratorTest.java)

**Status**: [x] done
