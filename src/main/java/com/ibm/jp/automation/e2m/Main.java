/*
 * Copyright 2026 Takakiyo Tanaka (IBM Japan)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.jp.automation.e2m;

import com.ibm.jp.automation.e2m.eclipse.EclipseProject;
import com.ibm.jp.automation.e2m.eclipse.EclipseProjectParser;
import com.ibm.jp.automation.e2m.i18n.Messages;
import com.ibm.jp.automation.e2m.maven.DependencyResolver;
import com.ibm.jp.automation.e2m.maven.LibertyServerXmlGenerator;
import com.ibm.jp.automation.e2m.maven.MavenDependency;
import com.ibm.jp.automation.e2m.maven.PomGenerator;
import com.ibm.jp.automation.e2m.maven.ProjectCopier;
import com.ibm.jp.automation.e2m.spec.JavaVersion;
import com.ibm.jp.automation.e2m.util.AppLogger;
import com.ibm.jp.automation.e2m.util.DebugArchiver;

import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Command(name = "e2m", description = "Eclipse project to Maven project converter", version = "1.0.1", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {

    private static final Logger log = AppLogger.get(Main.class);
    private static String e2mVersion;

    /** コマンドライン引数のコピー（デバッグ ZIP 用）。 */
    private String[] rawArgs;

    @Option(names = {"-g", "--groupId"}, required = false, description = "Maven groupId of the output project")
    private String groupId;

    @Option(names = {"-a", "--artifactId"}, required = false, description = "Maven artifactId of the output project")
    private String artifactId;

    @Option(names = {"-v", "--artifactVersion"}, required = false, description = "Maven version of the output project (default: 1.0-SNAPSHOT)")
    private String artifactVersion;
    private static final String DEFAULT_ARTIFACT_VERSION = "1.0-SNAPSHOT";

    @Option(names = {"--javaTargetVersion"}, required = false, converter = JavaVersionConverter.class,
            description = "Override maven.compiler.target in the generated pom.xml (default: taken from the Eclipse project settings)")
    private JavaVersion javaTargetVersion;

    @Option(names = {"--convertToUtf8"}, description = "Convert source files to UTF-8 encoding during copy")
    private boolean convertToUtf8;

    @Option(names = {"-e", "--sourceEncoding"}, required = false, description = "Source encoding of the input files (e.g. Shift_JIS). Required when --convertToUtf8 is specified.")
    private String sourceEncoding;

    @Option(names = {"--debug"}, description = "Output debug information as e2m_debug_<datetime>.zip in the output directory")
    private boolean debug;

    @Option(names = {"-n", "--noLiberty"}, description = "Do not add Liberty support (liberty-maven-plugin and server.xml) to the output project")
    private boolean noLiberty;

    @Parameters(index = "0", paramLabel = "<inputDir>", description = "Eclipse project directory to convert")
    private File inputDir;

    @Parameters(index = "1", paramLabel = "<outputDir>", description = "Output directory for the Maven project")
    private File outputDir;

    public static void main(String[] args) {
        AppLogger.init();
        Main main = new Main();
        main.rawArgs = args.clone();
        CommandLine command = new CommandLine(main);
        e2mVersion = command.getCommandSpec().version()[0];
        System.exit(command.execute(args));
    }

    @Override
    public Integer call() {
        // --debug が指定された場合はファイルログを有効化（outputDir 配下に一時ファイルを作成）
        if (debug) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path logFilePath = outputDir.toPath().resolve("e2m_debug_" + timestamp + ".log");
            AppLogger.enableDebug(logFilePath);
            log.debug("デバッグモード有効: ログファイル={}", logFilePath);
            log.debug("e2mバージョン={}", e2mVersion);
        }

        Path eclipsePath = inputDir.toPath();
        Path mavenPath = null; 

        // 1. 引数の確認
        if (!inputDir.isDirectory()) {
            log.error(Messages.get("main.inputDirNotFound", inputDir));
            return 1;
        }
        if (!eclipsePath.resolve(".project").toFile().exists()) {
            log.error(Messages.get("main.notEclipseProject", inputDir));
            return 1;
        }
        if (outputDir.exists() && !outputDir.isDirectory()) {
            log.error(Messages.get("main.outputPathMustBeDirectory", outputDir));
            return 1;
        }
        if (javaTargetVersion != null && javaTargetVersion.isUnknown()) {
            log.error(Messages.get("main.invalidJavaTargetVersion", javaTargetVersion));
            return 1;
        }

        try {
            // 2. Eclipseプロジェクト情報をパース
            log.info("");
            log.info(Messages.get("main.step1"));
            EclipseProject eclipseProject = EclipseProjectParser.parse(eclipsePath);
            log.info(Messages.get("main.projectName", eclipseProject.projectName()));
            log.info(Messages.get("main.projectType",
                    eclipseProject.webProject() ? Messages.get("main.projectType.web") : Messages.get("main.projectType.java")));
            log.info(Messages.get("main.javaSourceVersion", eclipseProject.javaSourceVersion()));
            log.info(Messages.get("main.javaTargetVersion", eclipseProject.javaTargetVersion()));
            if (eclipseProject.webProject()) {
                log.info(Messages.get("main.webVersion", eclipseProject.webVersion()));
            }
            log.info(Messages.get("main.sourceFolders", eclipseProject.sourceFolders()));
            if (eclipseProject.webProject()) {
                log.info(Messages.get("main.webContentFolder", eclipseProject.webContentRoot()));
            }
            log.info(Messages.get("main.jarCount", eclipseProject.jarFiles().size()));

            if (eclipseProject.javaSourceVersion().isUnknown()) {
                log.error(Messages.get("main.unrecognizedJavaSourceVersion", eclipseProject.javaSourceVersion()));
                return 1;
            }

            log.info("");
            log.info(Messages.get("main.step2"));
            String defaultArtifactId = convertToArtifactId(eclipseProject.projectName());
            if (groupId == null || groupId.isBlank()) {
                // オプション未指定の場合は対話的に入力を受け付ける
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    log.debug("対話的入力モード開始");
                    groupId = promptIfAbsent(reader, "groupId", groupId, null);
                    artifactId = promptIfAbsent(reader, "artifactId", artifactId, defaultArtifactId);
                    artifactVersion = promptIfAbsent(reader, "artifactVersion", artifactVersion, DEFAULT_ARTIFACT_VERSION);
                    log.debug("groupId: {}", groupId);
                    log.debug("artifactId: {}", artifactId);
                    log.debug("version: {}", artifactVersion);
                    // --convertToUtf8 指定時は sourceEncoding も対話入力
                    if (convertToUtf8) {
                        sourceEncoding = promptIfAbsent(reader, "sourceEncoding", sourceEncoding, null);
                    }
                    log.debug("sourceEncoding: {}", sourceEncoding);
                }
            } else {
                // 少なくとも--groupIdが指定されていれば，あとは空ならデフォルトを使用する
                if (artifactId == null || artifactId.isBlank()) {
                    artifactId = defaultArtifactId;
                }
                if (artifactVersion == null || artifactId.isBlank()) {
                    artifactVersion = DEFAULT_ARTIFACT_VERSION;
                }
                log.info(Messages.get("main.groupId", groupId));
                log.info(Messages.get("main.artifactId", artifactId));
                log.info(Messages.get("main.version", artifactVersion));
                // --convertToUtf8 指定時は sourceEncoding も対話入力（--groupId 指定時で未指定の場合）
                if (convertToUtf8 && (sourceEncoding == null || sourceEncoding.isBlank())) {
                    log.debug("対話的入力モード開始");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                        sourceEncoding = promptIfAbsent(reader, "sourceEncoding", sourceEncoding, null);
                    }
                    log.debug("sourceEncoding: {}", sourceEncoding);
                }
            }

            // --convertToUtf8 指定時の sourceEncoding バリデーション
            Charset srcCharset = null;
            if (convertToUtf8) {
                try {
                    srcCharset = Charset.forName(sourceEncoding);
                } catch (Exception e) {
                    log.error(Messages.get("main.invalidSourceEncoding", sourceEncoding));
                    return 1;
                }
            }

            // 出力先は outputDir/artifactId
            mavenPath = outputDir.toPath().resolve(artifactId);
            if (mavenPath.toFile().exists()) {
                log.error(Messages.get("main.outputDirAlreadyExists", mavenPath));
                return 1;
            }

            log.info("");
            log.info(Messages.get("main.conversionStart"));
            log.info(Messages.get("main.input", eclipsePath.toAbsolutePath()));
            log.info(Messages.get("main.output", mavenPath.toAbsolutePath()));

            // 3.(続き) JAR依存関係を解決
            log.info("");
            log.info(Messages.get("main.step3"));
            List<MavenDependency> dependencies = DependencyResolver.resolve(eclipseProject.jarFiles(), eclipsePath);
            long found = dependencies.stream().filter(d -> !"system".equals(d.scope())).count();
            long system = dependencies.stream().filter(d -> "system".equals(d.scope())).count();
            log.info(Messages.get("main.foundDependencies", found));
            log.info(Messages.get("main.systemDependencies", system));

            // 4. pom.xml を生成
            log.info("");
            log.info(Messages.get("main.step4"));
            // --javaTargetVersion が指定された場合はソースバージョンと比較してバリデーション
            JavaVersion sourceVer = eclipseProject.javaSourceVersion();
            if (javaTargetVersion != null) {
                if (!sourceVer.isUnknown() && javaTargetVersion.compareTo(sourceVer) < 0) {
                    log.error(Messages.get("main.javaTargetLowerThanSource",
                            javaTargetVersion, eclipseProject.javaSourceVersion()));
                    return 1;
                }
            }
            // 実効 javaTargetVersion を確定（--javaTargetVersion 指定 > Eclipseプロジェクト設定）
            JavaVersion effectiveTargetVersion = (javaTargetVersion != null && !javaTargetVersion.isUnknown())
                    ? javaTargetVersion
                    : eclipseProject.javaTargetVersion();
            PomGenerator.generate(eclipseProject, dependencies, groupId, artifactId, artifactVersion,
                    effectiveTargetVersion, convertToUtf8, noLiberty, mavenPath);

            // 4.(続き) Liberty 対応: server.xml を生成（webProject かつ --noLiberty 未指定の場合のみ）
            if (!noLiberty && eclipseProject.webProject()) {
                LibertyServerXmlGenerator.generate(eclipseProject, artifactId, mavenPath);
            }

            // 5. ソース・Webコンテンツをコピー
            log.info("");
            log.info(Messages.get("main.step5"));
            if (convertToUtf8) {
                log.info(Messages.get("main.encodingConversionMode", sourceEncoding));
            }
            ProjectCopier.copy(eclipseProject, dependencies, eclipsePath, mavenPath,
                    convertToUtf8, srcCharset, effectiveTargetVersion);

            log.info("");
            log.info(Messages.get("main.conversionComplete"));
            log.info(Messages.get("main.outputDir", mavenPath.toAbsolutePath()));

            return 0;

        } catch (Exception e) {
            log.error(Messages.get("main.conversionError", e.getMessage()));
            log.debug("スタックトレース:", e);
            return 2;
        } finally {
            // --debug オプション: デバッグ ZIP を生成
            if (debug) try {
                DebugArchiver.archive(eclipsePath, outputDir.toPath(), mavenPath, rawArgs);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 引数が null または空の場合にプロンプトを表示して入力を受け付ける。
     * デフォルト値がある場合は "[default]" 形式で表示し、空入力でデフォルト値を採用する。
     *
     * @param reader       入力ストリーム
     * @param name         項目名（表示用）
     * @param value        コマンドライン引数の値（null または空の場合にプロンプト表示）
     * @param defaultValue デフォルト値（null の場合はデフォルトなし）
     * @return 確定した値
     */
    private static String promptIfAbsent(BufferedReader reader, String name,
            String value, String defaultValue) throws Exception {
        if (value != null && !value.isBlank()) {
            return value;
        }
        while (true) {
            if (defaultValue != null) {
                System.out.print(Messages.get("main.prompt.withDefault", name, defaultValue));
            } else {
                System.out.print(Messages.get("main.prompt.withoutDefault", name));
            }
            System.out.flush();
            String line = reader.readLine();
            if (line == null) {
                // EOF（パイプ等）の場合はデフォルト値を使用
                return defaultValue != null ? defaultValue : "";
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                // 対話的入力モードでのメッセージは，ロガーを使わず，直接System.outに出力
                System.out.print(Messages.get("main.prompt.enterValue"));
                continue;
            }
            return trimmed;
        }
    }

    // artifactIdとして許可する文字（半角英数字、ハイフン、アンダースコア）以外にマッチする正規表現
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9\\-_]");

    // キャメルケースの境界（大文字の前）にマッチする正規表現
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    /**
     * プロジェクト名からMavenのartifactIdとして利用可能な文字列を生成します。
     *
     * @param projectName プロジェクト名
     * @return artifactId用文字列
     */
    public static String convertToArtifactId(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return "default-artifact";
        }

        // 1. パッケージ名が含まれている場合（完全限定名）、最後のクラス名部分だけを抽出
        String simpleName = projectName.substring(projectName.lastIndexOf('.') + 1);

        // 2. キャメルケース（OrderManager -> Order-Manager）をハイフン区切りに変換
        String kebabCase = CAMEL_CASE_BOUNDARY.matcher(simpleName).replaceAll("-");

        // 3. すべて小文字に変換
        String lowerCase = kebabCase.toLowerCase();

        // 4. artifactIdとして使えない文字（日本語や特殊記号など）を完全に除去
        String sanitized = INVALID_CHARS.matcher(lowerCase).replaceAll("");

        // 5. 連続したハイフンやアンダースコアを綺麗に整理
        sanitized = sanitized.replaceAll("[-_]{2,}", "-");

        // 6. 先頭や末尾にあるハイフン・アンダースコアを除去
        sanitized = sanitized.replaceAll("^[-_]+|[-_]+$", "");

        // すべての文字が削られて空になった場合のフォールバック
        return sanitized.isBlank() ? "converted-artifact" : sanitized;
    }

    /**
     * picocli 用の {@link JavaVersion} 型変換クラス。
     * 変換に失敗した場合は {@link JavaVersion#UNKNOWN_VERSION} を返す。
     */
    static class JavaVersionConverter implements ITypeConverter<JavaVersion> {
        @Override
        public JavaVersion convert(String value) {
            return JavaVersion.of(value);
        }
    }
}
