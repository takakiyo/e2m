# Task Log: Update Debug File Extensions in README

- **Date:** 2026-09-04
- **Task:** Update `.json` debug file list descriptions to `.html` in `README.md` and `README.en.md` to match current code implementation.

---

## Conversation History

### User Query
> @README.md で，--debugで生成されるZIPファイルに含まれるデバッグ情報一覧で，.jsonになっているものを，現状に合わせて.htmlに変更。

### Investigation
Bob performed searches to locate occurrences of `_files.json` and `--debug` in the documentation.
- `README.md` at line 235 & 237 contained `eclipse_files.json` and `maven_files.json`.
- `README.en.md` at line 235 & 237 also contained `eclipse_files.json` and `maven_files.json`.
- Code base search via `grep` showed that `com.ibm.jp.automation.e2m.util.DebugArchiver` actually generates `eclipse_files.html` and `maven_files.html`.

### Implementation & Verification
Bob made surgical updates to both documents:
- **README.md** and **README.en.md** were modified to replace `eclipse_files.json` and `maven_files.json` with `eclipse_files.html` and `maven_files.html`.
- Run `mvn test` to verify everything works correctly. All 116 tests successfully passed.

---

## Git Commit Request
The user requested to invoke the `git-commit` skill. Bob prepared the commit message:

### Commit Message
- **Subject:** デバッグ情報ZIPに含まれるファイル一覧の拡張子を.htmlに更新
- **Body:**
  --debug オプション実行時に生成されるZIP内のファイル一覧は、
  現在の実装（DebugArchiver.java）において eclipse_files.html および
  maven_files.html として出力される仕様になっている。

  これらがドキュメント（README.md / README.en.md）上で
  eclipse_files.json および maven_files.json のまま
  記載されていたため、現状の実装に合わせて .html に変更する。
