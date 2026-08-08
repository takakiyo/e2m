# Project Architecture Rules (Non-Obvious Only)

- `Main.call()` is stateful: it modifies `groupId`, `artifactId`, `artifactVersion`, `convertToUtf8`, `sourceEncoding`, `noLiberty`, `addMavenWrapper` fields via interactive prompts when `--groupId` is not provided. Do not refactor these into local variables without preserving prompt logic.
- `DependencyResolver` makes live HTTPS calls to `search.maven.org` with SSL certificate validation intentionally disabled (for corporate SSL inspection proxies). This is a design decision, not a bug.
- `PomGenerator.generate()` receives `effectiveTargetVersion` (already resolved from CLI override or Eclipse setting) — it does NOT perform the override logic itself. The caller (`Main`) is responsible for resolution.
- `EclipseProject` is an immutable record; all parsing is done in `EclipseProjectParser` (static class). Adding new Eclipse metadata requires changing both the record and the parser.
- The fat JAR (`maven-shade-plugin`) is the primary distribution artifact — `target/e2m.jar`. The native binary (`-Pnative`) is a secondary build target requiring GraalVM.
- `JavaVersion` uses an integer-based cache (`HashMap<Integer, JavaVersion>`) — Java 8 is stored as `8`, not `1.8`. The `toString()` diverges from the internal value for versions ≤ 8.
- `LibertyServerXmlGenerator` only runs for web projects (`webProject=true`) and only when `--noLiberty` is NOT set — both conditions must be checked together.
