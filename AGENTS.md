# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Project Overview

`e2m` is a Java 17 CLI tool (Maven) that converts Eclipse projects to Maven projects.
Entry point: [`Main.java`](src/main/java/com/ibm/jp/automation/e2m/Main.java) — picocli-based `Callable<Integer>`.

## Build / Test Commands

```bash
# Full build (fat JAR via maven-shade-plugin → target/e2m.jar)
mvn package

# GraalVM native image (requires GraalVM JDK)
mvn package -Pnative

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=PomGeneratorTest

# Run a single test method
mvn test -Dtest=PomGeneratorTest#javaProject_noPackagingElement
```

## Architecture: Conversion Steps

`Main.call()` orchestrates these steps in order:
1. `EclipseProjectParser.parse()` — reads `.project`, `.classpath`, `.settings/` files
2. Interactive prompts (or CLI args) to determine Maven coordinates
3. `DependencyResolver.resolve()` — SHA1-lookup against Maven Central REST API
4. `PomGenerator.generate()` — builds `pom.xml` via DOM API
5. `ProjectCopier.copy()` — copies source/web content (optional UTF-8 conversion)
6. `LibertyServerXmlGenerator.generate()` — web projects only, unless `--noLiberty`
7. `MavenWrapperInstaller.install()` — only if `--addMavenWrapper`

## Critical Patterns

### Logging — use `AppLogger`, not direct SLF4J
Always use `AppLogger.get(MyClass.class)` instead of `LoggerFactory.getLogger(...)`.
`AppLogger.init()` must be called before `CommandLine.execute()` — logback is configured programmatically, no `logback.xml` exists.

### Internationalization — always use `Messages.get()`
All user-visible strings are in [`messages_ja.properties`](src/main/resources/messages_ja.properties) / [`messages_en.properties`](src/main/resources/messages_en.properties).
Never use string literals for log/error output; always call `Messages.get("key", args...)`.

### `JavaVersion` — singleton cache, identity-comparable
`JavaVersion.of("1.8")` and `JavaVersion.Java8` are the same instance.
`if (v == JavaVersion.Java8)` is valid. Use `isUnknown()` before any version comparison.
`toString()` returns the original string (`"1.8"` not `"8"`), so use it for pom.xml output.

### `DependencyResolver` — HTTP mock pattern for tests
`callMavenCentralApi()` is `protected` specifically so tests can subclass and stub it.
Network integration tests are annotated `@Disabled` — do not remove that annotation.

### `MavenDependency` — `scope=null` means compile scope
`scope=null` → no `<scope>` element in pom.xml (Maven compile default).
`exported=true` → system JAR goes to `libs/compile/`; `exported=false` → `libs/provided/`.

### JAR scope resolution from Eclipse `.classpath` attributes
- `test="true"` attribute → Maven `test` scope
- `exported="true"` attribute → Maven compile scope (`scope=null`)
- Neither → Maven `provided` scope

### Output directory structure
The output Maven project is created at `<outputDir>/<artifactId>/`, not directly at `<outputDir>`.
If `<outputDir>/<artifactId>/` already exists, the tool exits with error code 1.

## Data Models

- [`EclipseProject`](src/main/java/com/ibm/jp/automation/e2m/eclipse/EclipseProject.java) — Java record
- [`MavenDependency`](src/main/java/com/ibm/jp/automation/e2m/maven/MavenDependency.java) — Java record
- [`JarFile`](src/main/java/com/ibm/jp/automation/e2m/eclipse/JarFile.java) — Java record

## Test Resources

Test fixtures for Eclipse project parsing are in [`src/test/resources/`](src/test/resources/):
- `sample-java-project/` — standard Java project
- `sample-web-project/` — WTP web project (Servlet 3.0)
- `sha1test/hello.txt` — known SHA1: `60fde9c2310b0d4cad4dab8d126b04387efba289`
