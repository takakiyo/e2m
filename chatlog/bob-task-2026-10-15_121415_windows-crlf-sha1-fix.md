# Bob タスクログ — Windows環境でのCRLF変換によるSHA1テスト失敗を防ぐ

**日時:** 2026-10-15 12:14:15  
**ブランチ:** master

---

## 会話ログ

### ユーザー

Windows環境にプロジェクトをCloneしてビルドしたところ，以下のメッセージでビルドが失敗しました。原因として考えられるものを挙げてください。

```
12:02:21.422 [main] INFO com.ibm.jp.automation.e2m.maven.DependencyResolver --  found: log4j:log4j:1.2.7
[ERROR] Tests run: 7, Failures: 1, Errors: 0, Skipped: 1, Time elapsed: 0.660 s <<< FAILURE! -- in com.ibm.jp.automation.e2m.maven.DependencyResolverTest
[ERROR] com.ibm.jp.automation.e2m.maven.DependencyResolverTest.computeSha1_knownFile -- Time elapsed: 0.016 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <60fde9c2310b0d4cad4dab8d126b04387efba289> but was: <9fbc3fafddca353898269a2f4069e4653083bcdb>
        at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
        ...
        at com.ibm.jp.automation.e2m.maven.DependencyResolverTest.computeSha1_knownFile(DependencyResolverTest.java:63)
```

### Bob

**原因：Git の改行コード自動変換（CRLF変換）**

Windows環境ではGitのデフォルト設定 `core.autocrlf=true` が有効になっており、**`checkout`時にLF（`\n`）をCRLF（`\r\n`）へ自動変換**します。

その結果、`src/test/resources/sha1test/hello.txt` の内容が変わります：

| 環境 | ファイル内容 | SHA1 |
|---|---|---|
| macOS/Linux（正常） | `Hello, World!\n` | `60fde9c2310b0d4cad4dab8d126b04387efba289` |
| Windows（改行変換後） | `Hello, World!\r\n` | `9fbc3fafddca353898269a2f4069e4653083bcdb` ← テストで出たハッシュ |

`printf 'Hello, World!\r\n' | shasum` の結果がまさにエラーメッセージの `9fbc3fafddca353898269a2f4069e4653083bcdb` と一致しており、これが原因であることが確認できました。

**対処方法**

プロジェクトルートに `.gitattributes` を追加し、このテストリソースファイルをバイナリ扱い（改行変換なし）に指定します。

---

### ユーザー

テストリソースを含むすべてのバイナリ・ハッシュ検証用ファイルを一括で保護する書き方の法を実施してください。

### Bob

`.gitattributes` を作成しました。

```
# バイナリとして扱い、改行変換を無効化
src/test/resources/**  -text
```

`-text` 属性により、`src/test/resources/` 配下のすべてのファイルはGitの改行変換（CRLF↔LF）の対象外になります。

既にWindows環境にclone済みのリポジトリに対しては、`.gitattributes` をコミット・プッシュした後、以下のコマンドでチェックアウト済みファイルを再適用する必要があります：

```bash
git rm --cached -r src/test/resources/
git checkout -- src/test/resources/
```

---

### ユーザー

コミットを実行してください。（git-commit スキル）

### Bob

`.gitattributes` を新規追加し、以下のコミットメッセージでコミットを実行しました。

**件名:** Windows環境でのCRLF変換によるSHA1テスト失敗を防ぐ

**本文:** Windows環境ではGitのcore.autocrlf=trueによりLFがCRLFに変換され、SHA1ハッシュ検証テスト（computeSha1_knownFile）が失敗する問題があった。.gitattributesを追加してテストリソース配下のファイルを改行変換対象外にすることで、全OS環境で一貫したバイト列が保持されるようにする。
