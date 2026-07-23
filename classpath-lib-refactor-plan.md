# classpath lib エントリのスコープ付きJarFile型への変換 プラン

## 概要

`EclipseProjectParser.parse()` の `.classpath` パース処理を修正する。

現状 `kind="lib"` エントリを直接 `jarPaths` (List<String>) に追加しているが、
2段階の変換を行う設計に変更する：

1. **第1段階**: `.classpath` をパースして `List<ClasspathEntry>` を構築（classpathentry の属性を管理）
2. **第2段階**: `.settings` 検査完了後、`List<ClasspathEntry>` から `List<JarFile>` を構築

`ClasspathEntry` は classpathentry 要素の属性を管理するオブジェクト。
`JarFile` は実際のJARファイルのパスとスコープを管理するオブジェクト。

## 現状の整理

### jarPaths の問題

| 追加元 | 値の種類 | DependencyResolver の扱い |
|---|---|---|
| `kind="lib"` エントリ | JARファイルパス (e.g. `lib/A.jar`) | `isDirectory()=false` → **スキップされていた（バグ）** |
| WEB-INF/lib 処理 | ディレクトリパス (e.g. `WebContent/WEB-INF/lib`) | isDirectory()=true → .jar を走査して解決 |

今回の修正でこの問題も解消する。

## 2つの新規クラス

### `ClasspathEntry` レコード

`.classpath` の `classpathentry` 要素1件分の情報を保持する。

| フィールド | 型 | 説明 |
|---|---|---|
| `kind` | `String` | classpathentry の kind 属性値 (`"lib"`, `"con"`, ...) |
| `path` | `String` | classpathentry の path 属性値 |
| `exported` | `boolean` | `exported="true"` 属性があるか |
| `test` | `boolean` | `<attribute name="test" value="true"/>` 子要素があるか |

### `JarFile` レコード

実際のJARファイル1件分のパスとスコープを保持する。

| フィールド | 型 | 説明 |
|---|---|---|
| `path` | `String` | JARファイルへの相対パス文字列（inputDir基点） |
| `scope` | `JarScope` | Mavenスコープを表す enum |

### `JarScope` enum

`JarFile` のスコープを表す enum。`toMavenScope()` メソッドで Maven の scope 文字列（`"compile"`, `"test"`, `"provided"`）に変換できるようにする。null（通常のcompile扱い）との区別のため、`COMPILE` は Maven 的には scope 要素なしに相当するが、enum としては明示的に保持する。

| 値 | Maven scope 文字列 |
|---|---|
| `COMPILE` | `null`（scope要素を出力しない） |
| `TEST` | `"test"` |
| `PROVIDED` | `"provided"` |

## スコープ決定ロジック（ClasspathEntry → JarFile）

`ClasspathEntry` の `path` が `.jar` で終わる場合：
- `test == true` → scope = `JarScope.TEST`
- `exported == true` → scope = `JarScope.COMPILE`
- それ以外 → scope = `JarScope.PROVIDED`

`ClasspathEntry` の `path` が `org.eclipse.jst.j2ee.internal.web.container` の場合：
- `webContentRoot` 確定後、`{webContentRoot}/WEB-INF/lib` ディレクトリの各 `.jar` ファイルを
  scope = `JarScope.COMPILE` で個別に `JarFile` として追加（ディレクトリが存在する場合のみ）

それ以外は何もしない（無視）。

## 変更対象ファイル

| ファイル | 変更種別 | 変更内容 |
|---|---|---|
| `ClasspathEntry.java` | **新規作成** | classpathentry属性を保持するレコード |
| `JarFile.java` | **新規作成** | JARパスとスコープを保持するレコード |
| `EclipseProjectParser.java` | 修正 | 2段階変換ロジックに変更 |
| `EclipseProject.java` | 修正 | `jarPaths: List<String>` → `jarFiles: List<JarFile>` |
| `DependencyResolver.java` | 修正 | `List<JarFile>` を受け取り個別JARを直接処理 |
| `Main.java` | 修正 | `jarPaths()` → `jarFiles()` 参照変更 |
| `EclipseProjectParserTest.java` | 修正 | `jarPaths()` → `jarFiles()` ベースに更新 |

---

## サブタスク

### サブタスク 1: `ClasspathEntry` レコードの新規作成

**Intent**
`.classpath` の `classpathentry` 要素1件分の属性情報を保持するレコードを作成する。
classpathentry に直接書かれる属性と、子要素 `<attributes>` で定義される属性の両方を管理する。

**Expected Outcomes**
- `ClasspathEntry.java` が新規作成される
- `kind`, `path`, `exported`, `test` の4フィールドを持つ record として定義される

**Todo List**
1. `src/main/java/com/ibm/jp/automation/e2m/ClasspathEntry.java` を新規作成
2. フィールド: `String kind`, `String path`, `boolean exported`, `boolean test`
3. Java record として定義する
4. 既存ファイルに合わせてライセンスヘッダーを付与する

**Relevant Context**
- 既存の `MavenDependency.java` のレコード定義スタイルを参照

**Status** [ ] pending

---

### サブタスク 2: `JarScope` enum と `JarFile` レコードの新規作成

**Intent**
JARファイルのMavenスコープを型安全に表す `JarScope` enum と、
実際のJARファイル1件分のパスとスコープを保持する `JarFile` レコードを作成する。

**Expected Outcomes**
- `JarScope.java` が新規作成される（`COMPILE`, `TEST`, `PROVIDED` の3値）
- `JarFile.java` が新規作成される
- `JarFile` は `String path` と `JarScope scope` の2フィールドを持つ record として定義される

**Todo List**
1. `src/main/java/com/ibm/jp/automation/e2m/JarScope.java` を新規作成
   - enum 値: `COMPILE`, `TEST`, `PROVIDED`
   - `toMavenScope()` メソッドを追加: `COMPILE` → `null`、`TEST` → `"test"`、`PROVIDED` → `"provided"`
2. `src/main/java/com/ibm/jp/automation/e2m/JarFile.java` を新規作成
3. フィールド: `String path`, `JarScope scope`
4. Java record として定義する
5. 既存ファイルに合わせてライセンスヘッダーを付与する（両ファイルとも）

**Relevant Context**
- 既存の `MavenDependency.java` のレコード定義スタイルを参照

**Status** [ ] pending

---

### サブタスク 3: `EclipseProjectParser.parse()` の修正

**Intent**
`.classpath` パース時に `kind="lib"` および web container `con` エントリを
`ClasspathEntry` として収集し、`.settings` 検査後に `List<JarFile>` を確定する。

**Expected Outcomes**
- `.classpath` パース中に `List<ClasspathEntry> libEntries` が構築される
- `.settings` 検査完了後に `buildJarFiles()` を呼んで `List<JarFile>` を生成する
- `EclipseProject` の生成に `jarFiles` を渡す（`jarPaths` を廃止）
- 現在の `jarPaths.add(webContentRoot + "/WEB-INF/lib")` という処理は削除する

**Todo List**
1. `parse()` の冒頭で `List<ClasspathEntry> libEntries = new ArrayList<>()` を追加
2. `.classpath` パースループ内:
   - `case "lib"`: `exported` 属性と `test` 子要素属性を読み取って `ClasspathEntry` を生成し `libEntries` に追加
   - `case "con"`: path が `org.eclipse.jst.j2ee.internal.web.container` の場合も `ClasspathEntry` として `libEntries` に追加
3. `.settings` 検査後（`webContentRoot` 確定後）に `buildJarFiles(libEntries, webContentRoot, inputDir)` を呼ぶ
4. プライベート静的メソッド `buildJarFiles()` を実装:
   - 各 `ClasspathEntry` を順に処理
   - `path.endsWith(".jar")` の場合: `test` → `JarScope.TEST`, `exported` → `JarScope.COMPILE`, それ以外 → `JarScope.PROVIDED`
   - path が `org.eclipse.jst.j2ee.internal.web.container` かつ `webContentRoot` が非null の場合:
     `{inputDir}/{webContentRoot}/WEB-INF/lib` を走査し各 `.jar` を `JarScope.COMPILE` で `JarFile` に追加
   - それ以外は無視
5. `EclipseProject` コンストラクタの `jarPaths` 引数を `jarFiles` に変更

**Relevant Context**
- `EclipseProjectParser.java` 71-130行: 現在の `.classpath` パースと `jarPaths` 構築
- `isOptional()` メソッド（216-230行）を参考に attribute 読み取りロジックを実装
- `webProject` の判定は `.project` パース時に行われ、`.classpath` の `con` エントリは web container を表す

**Status** [ ] pending

---

### サブタスク 4: `EclipseProject.java` の変更

**Intent**
`EclipseProject` レコードの `jarPaths: List<String>` フィールドを `jarFiles: List<JarFile>` に変更する。

**Expected Outcomes**
- `EclipseProject.jarFiles()` アクセサが使えるようになる
- `EclipseProject.jarPaths()` は廃止される

**Todo List**
1. `List<String> jarPaths` を `List<JarFile> jarFiles` に変更する

**Relevant Context**
- `EclipseProject.java` 29行目の `List<String> jarPaths`

**Status** [ ] pending

---

### サブタスク 5: `DependencyResolver.java` と `Main.java` の変更

**Intent**
`DependencyResolver.resolve()` を `List<JarFile>` 対応に変更する。
現在はディレクトリ走査（`Files.list(dir)` で `.jar` を列挙）だったが、
`JarFile` が個別 JAR ファイルパスを直接持つため、ファイルへの直接参照として処理する。

スコープの引き継ぎ:
- `jarFile.scope().toMavenScope()` の戻り値を `MavenDependency.scope` に使う
- Maven Central で見つかった場合（現在 scope=null で返している箇所）も `JarFile.scope()` を使う
  ただし `JarScope.COMPILE` のときは Maven Central で見つかったら scope=null（通常のcompile）で返す

**Expected Outcomes**
- `DependencyResolver.resolve(List<JarFile> jarFiles, Path inputDir)` が個別 JAR を処理できる
- スコープが正しく `MavenDependency` に反映される
- `Main.java` の `eclipseProject.jarPaths()` が `eclipseProject.jarFiles()` に変わる

**Todo List**
1. `resolve()` メソッドのシグネチャを `List<String> jarPaths` から `List<JarFile> jarFiles` に変更
2. ループ内でディレクトリ走査をやめ、`jarFile.path()` を直接 JAR ファイルパスとして渡す
3. `resolveJar(String jarPath, Path inputDir)` のシグネチャに `String scope` を追加
4. `resolveJar()` 内で `MavenDependency` 生成時にスコープを適用:
   - Maven Central で見つかった場合: `JarScope.COMPILE` なら null、それ以外は `jarFile.scope().toMavenScope()` を使う
   - 見つからない / エラー時: scope=`"system"` のまま（変更なし）
5. `Main.java` の `eclipseProject.jarPaths()` を `eclipseProject.jarFiles()` に変更
6. `Main.java` の `eclipseProject.jarPaths().size()` も `eclipseProject.jarFiles().size()` に変更

**Relevant Context**
- `DependencyResolver.java` 89-108行: 現在のディレクトリ走査ロジック
- `Main.java` 204行: `DependencyResolver.resolve(eclipseProject.jarPaths(), inputPath)`
- `Main.java` 131行: `eclipseProject.jarPaths().size()`

**Status** [ ] pending

---

### サブタスク 6: テストの更新

**Intent**
既存テストの `jarPaths()` 参照を `jarFiles()` ベースに更新し、
スコープ判定の動作確認テストを追加する。

**Expected Outcomes**
- 既存テストがコンパイル・実行できる
- `JarFile` のスコープ判定が正しく動作することを検証するテストが追加される
- `mvn test` で全テストがパス

**Todo List**
1. `javaProject_jarPaths()` を `javaProject_jarFiles()` にリネーム・修正（`JarFile` の path と scope を検証）
2. `webProject_noJarPaths()` を `webProject_jarFiles_empty()` にリネーム・修正
3. sample-java-project の `.classpath` に `exported="true"` を持つエントリと
   `<attribute name="test" value="true"/>` を持つエントリを追加してスコープのテストデータを整備
4. 追加したエントリのスコープが正しく判定されることを確認するテストケースを追加
5. `mvn test` を実行して全テストがパスすることを確認

**Relevant Context**
- `src/test/resources/sample-java-project/.classpath`
- `EclipseProjectParserTest.java`

**Status** [ ] pending
