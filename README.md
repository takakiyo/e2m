# e2m — Eclipse to Maven Converter

**[English](README.md)** | [日本語](README.ja.md)

A command-line tool that converts Eclipse Java projects to the standard Maven directory structure.

## Features

- Supports **Eclipse Java projects** (`.project` / `.classpath`)
- Supports **Eclipse WTP Web projects** (`.settings/org.eclipse.wst.common.component`)
- Computes SHA-1 hashes of JAR files listed in `.classpath`, searches **Maven Central** automatically, and generates `<dependency>` entries
  - JARs not found in Maven Central are added as system-scoped dependencies in `pom.xml`
- Copies Java sources to `src/main/java` (tests to `src/test/java`)
- Copies resource files to `src/main/resources` (tests to `src/test/resources`)
- Copies Web content to the Maven standard `src/main/webapp`
- Automatically adds `<packaging>war</packaging>` for WTP projects
- **Character encoding conversion** (`--convertToUtf8`): converts sources written in a legacy encoding such as Shift_JIS to UTF-8 during copy

## Requirements

- Java 17 or later
- Maven 3.8 or later (build only)
- GraalVM 21 or later (native image build only)
- Internet access (for Maven Central lookup)

## Build

### Fat JAR

```bash
mvn package
```

Produces `target/e2m.jar`.

### Native Executable (GraalVM Native Image)

Run in an environment where a GraalVM JDK is set as `JAVA_HOME`.

```bash
mvn -Pnative package
```

Produces `target/e2m` (macOS/Linux) or `target/e2m.exe` (Windows).

## Usage

```
e2m [-g <groupId>] [-a <artifactId>] [-v <version>] [--javaTargetVersion <version>]
    [--convertToUtf8 [-e <encoding>]]
    <inputDir> <outputDir>
```

If options are omitted, the tool prompts for input interactively after analysing the project.  
The default for `artifactId` is the Eclipse project name; the default for `artifactVersion` is `1.0-SNAPSHOT`.

Output is placed in `<outputDir>/<artifactId>/`.

### Options

| Option | Description |
|---|---|
| `-g`, `--groupId` | `groupId` in the generated `pom.xml` (prompted if omitted) |
| `-a`, `--artifactId` | `artifactId` in the generated `pom.xml` (defaults to the project name) |
| `-v`, `--artifactVersion` | `version` in the generated `pom.xml` (defaults to `1.0-SNAPSHOT`) |
| `--javaTargetVersion` | Overrides `maven.compiler.target` in the generated `pom.xml` (defaults to the Eclipse project setting; cannot be lower than the source version) |
| `--convertToUtf8` | Converts source files to UTF-8 during copy (see below) |
| `-e`, `--sourceEncoding` | Source encoding (e.g. `Shift_JIS`). Only valid with `--convertToUtf8`; prompted if omitted |
| `--debug` | Outputs debug information as a ZIP file (see below) |
| `-h`, `--help` | Show help |
| `-V`, `--version` | Show version |

### Arguments

| Argument | Description |
|---|---|
| `<inputDir>` | Source Eclipse project directory |
| `<outputDir>` | Base directory where the Maven project will be generated |

### Examples

All options specified (Fat JAR):

```bash
java -jar target/e2m-1.0.jar \
  -g com.example \
  -a myapp \
  -v 1.0 \
  /path/to/eclipse-project \
  /path/to/output
# → generated in /path/to/output/myapp/
```

All options specified (native executable):

```bash
./e2m \
  --groupId com.example \
  --artifactId myapp \
  --artifactVersion 1.0 \
  /path/to/eclipse-project \
  /path/to/output
```

Interactive input (options omitted):

```bash
./e2m /path/to/eclipse-project /path/to/output

groupId: com.example
artifactId [MyEclipseProject]: myapp
artifactVersion [1.0-SNAPSHOT]:
# → generated in /path/to/output/myapp/
```

## Character Encoding Conversion (--convertToUtf8)

If an Eclipse project is written in a legacy encoding such as Shift_JIS, the `--convertToUtf8` option converts source files to UTF-8 as they are copied.

### Basic Usage

```bash
./e2m --convertToUtf8 -e Shift_JIS \
  /path/to/eclipse-project \
  /path/to/output
```

If `-e` / `--sourceEncoding` is omitted, the tool prompts for it after analysing the project.

```bash
./e2m --convertToUtf8 /path/to/eclipse-project /path/to/output

groupId: com.example
artifactId [MyApp]:
artifactVersion [1.0-SNAPSHOT]:
sourceEncoding: Shift_JIS
```

### Conversion Rules by File Type

#### `.java` files

Read using the encoding specified by `--sourceEncoding` and written as UTF-8.

#### `.properties` files

Read using `--sourceEncoding`, Unicode escape sequences in `\uXXXX` format are expanded to Unicode characters, then written as UTF-8.

The output directory depends on the **Java target version**:

| javaTargetVersion | Output directory | Reason |
|---|---|---|
| 9 or later | `src/main/resources/` | Java 9+ can load `.properties` files directly as UTF-8 |
| 8 or earlier | `src/main/resources-utf8/` | Java 8 and below cannot load UTF-8 `.properties` directly (see below) |

**For Java 8 and below:** UTF-8 `.properties` files placed in `src/main/resources-utf8/` are converted back to `\uXXXX` format by `native2ascii-maven-plugin` during the build and placed in the equivalent of `src/main/resources/`. The following plugin configuration is automatically added to `pom.xml`:

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

#### `.jsp` files

The first 4 KB of each file is read as ISO-8859-1 to detect the `<%@ page pageEncoding="..." %>` directive.

| Situation | Behaviour |
|---|---|
| `pageEncoding` detected and not UTF-8 | File is read using the detected encoding; `pageEncoding` attribute value is rewritten to `UTF-8` on output |
| `pageEncoding` not detected | File is read using `--sourceEncoding` and written as UTF-8 |
| `pageEncoding` is already `UTF-8` | Copied as binary without conversion |

#### Other files

The following files are copied as binary without conversion because modifying them could affect application behaviour:

- `.html` / `.htm` — rewriting `<meta charset>` would affect app behaviour
- `.css` / `.js` — would affect app behaviour
- `.xml` / `.tag` / `.tld` — UTF-8 is already the conventional encoding; no conversion needed
- `.jar` / images / other binary files

### Output Directory Structure After Conversion (Java 8 and below)

```
output/<artifactId>
├── pom.xml              ← native2ascii-maven-plugin already configured
└── src/
    └── main/
        ├── java/
        ├── resources-utf8/  ← UTF-8 converted .properties (converted during build)
        └── webapp/
```

## Troubleshooting

If the conversion result is not as expected, re-run with the `--debug` option.  
After conversion completes, `<outputDir>/e2m_debug_<datetime>.zip` is generated.

```bash
./e2m --debug \
  -g com.example \
  -a myapp \
  /path/to/eclipse-project \
  /path/to/output
# → /path/to/output/e2m_debug_20250101_120000.zip is generated
```

The ZIP file contains the following debug information:

| File | Contents |
|---|---|
| `eclipse/.project` | Eclipse project definition file |
| `eclipse/.classpath` | Eclipse classpath definition file |
| `eclipse/.factorypath` | Eclipse factory path definition file |
| `eclipse/.settings/…` | All files under the `.settings/` directory |
| `eclipse_files.json` | List of all directories and files in the Eclipse project (name, size, date; JAR files include SHA-1 hash) |
| `pom.xml` | The `pom.xml` of the generated Maven project |
| `maven_files.json` | List of all directories and files in the generated Maven project (name, size, date) |

If the issue persists, please open an [Issue](../../issues) and attach the generated ZIP file.

## Output Directory Structure

### Java Project

```
output/<artifactId>
├── pom.xml
└── src/
    ├── main/
    │   ├── java/        ← .java files from Eclipse src folders
    │   └── resources/   ← non-.java files from Eclipse src folders
    └── test/
        ├── java/        ← .java files from src folders whose name contains "test"
        └── resources/   ← non-.java files from src folders whose name contains "test"
```

### WTP Web Project

```
output/<artifactId>
├── pom.xml              ← includes <packaging>war</packaging>
└── src/
    └── main/
        ├── java/        ← Java sources
        ├── resources/   ← resource files
        └── webapp/      ← Web content (JSP, HTML, WEB-INF/web.xml, etc.)
```

## Web Content Root Resolution

The Web content root directory is not hard-coded to `WebContent` or similar. It is resolved dynamically from the `tag="defaultRootSource"` entry in `.settings/org.eclipse.wst.common.component`.

```xml
<!-- Example: .settings/org.eclipse.wst.common.component -->
<wb-module deploy-name="MyApp">
  <wb-resource deploy-path="/" source-path="/WebContent" tag="defaultRootSource"/>
</wb-module>
```

## Testing

```bash
mvn test
```

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE).

## Author

Takakiyo Tanaka (IBM Japan)
