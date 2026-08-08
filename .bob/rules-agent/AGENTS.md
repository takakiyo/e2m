# Project Coding Rules (Non-Obvious Only)

- Always use `AppLogger.get(MyClass.class)` — never call `LoggerFactory.getLogger()` directly. Logback is configured programmatically in `AppLogger.init()`; no `logback.xml` exists.
- All user-visible strings MUST use `Messages.get("key", args...)` — keys live in `messages_ja.properties` / `messages_en.properties`. No string literals in log/error calls.
- `JavaVersion` instances are cached singletons — `JavaVersion.of("1.8") == JavaVersion.Java8` is true. Always call `isUnknown()` before comparisons; `toString()` returns the original form (`"1.8"`, not `"8"`).
- `DependencyResolver.callMavenCentralApi()` is `protected` by design for subclass stubbing in tests. Keep it protected when refactoring.
- `MavenDependency.scope=null` means Maven compile scope (no `<scope>` element). Do not substitute `""` or `"compile"`.
- System-scope JARs split by `exported` flag: `exported=true` → `libs/compile/`, `exported=false` → `libs/provided/`. This affects both `systemPath` in pom.xml and the copy destination in `ProjectCopier`.
- Output dir is `<outputDir>/<artifactId>/`, not `<outputDir>/`. Check existence before creating.
- `native2ascii-maven-plugin` is only added when `convertToUtf8=true` AND `javaTargetVersion <= 8`.
- Tests that call Maven Central API are `@Disabled` — never remove this annotation; use the `StubDependencyResolver` pattern instead.
- Reflection is used in `DependencyResolverTest` to access `private resolveJar()` — this is intentional because the static `resolve()` always instantiates a new `DependencyResolver`.
