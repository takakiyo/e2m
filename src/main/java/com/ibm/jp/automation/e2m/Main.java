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

import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Command(name = "e2m", description = "Eclipse project to Maven project converter", version = "1.0", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {

    private static final Logger log = AppLogger.get(Main.class);

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

    @Parameters(index = "0", paramLabel = "<inputDir>", description = "Eclipse project directory to convert")
    private File inputDir;

    @Parameters(index = "1", paramLabel = "<outputDir>", description = "Output directory for the Maven project")
    private File outputDir;

    public static void main(String[] args) {
        AppLogger.init();
        Main main = new Main();
        main.rawArgs = args.clone();
        CommandLine command = new CommandLine(main);
        String[] versionInfo = command.getCommandSpec().version();
        log.debug("e2mが開始しました。バージョン={}", versionInfo[0]);
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
        }

        Path inputPath = inputDir.toPath();

        // 1. 引数の確認
        if (!inputDir.isDirectory()) {
            log.error("入力ディレクトリが見つかりません: {}", inputDir);
            return 1;
        }
        if (!inputPath.resolve(".project").toFile().exists()) {
            log.error("Eclipseプロジェクトではありません (.project ファイルが見つかりません): {}", inputDir);
            return 1;
        }
        if (outputDir.exists() && !outputDir.isDirectory()) {
            log.error("出力パスはディレクトリである必要があります: {}", outputDir);
            return 1;
        }
        if (javaTargetVersion != null && javaTargetVersion.isUnknown()) {
            log.error("--javaTargetVersionが不正です: {}", javaTargetVersion);
            return 1;
        }

        try {
            // 2. Eclipseプロジェクト情報をパース
            log.info("");
            log.info("[1/5] Eclipseプロジェクトを解析中...");
            EclipseProject eclipseProject = EclipseProjectParser.parse(inputPath);
            log.info("  プロジェクト名: {}", eclipseProject.projectName());
            log.info("  種別: {}", eclipseProject.webProject() ? "Webプロジェクト (WTP)" : "Javaプロジェクト");
            log.info("  Javaソースバージョン: {}", eclipseProject.javaSourceVersion());
            log.info("  Javaターゲットバージョン: {}", eclipseProject.javaTargetVersion());
            if (eclipseProject.webProject()) {
                log.info("  Web仕様バージョン: {}", eclipseProject.webVersion());
            }
            log.info("  ソースフォルダ: {}", eclipseProject.sourceFolders());
            if (eclipseProject.webProject()) {
                log.info("  Webコンテンツフォルダ: {}", eclipseProject.webContentRoot());
            }
            log.info("  JARファイル数: {}", eclipseProject.jarPaths().size());

            if (eclipseProject.javaSourceVersion().isUnknown()) {
                log.error("Javaソースバージョンを認識できませんでした: {}", eclipseProject.javaSourceVersion());
                return 1;
            }

            log.info("");
            log.info("[2/5] 移行先のMavenプロジェクトを決定中...");
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
                log.info("groupId: {}", groupId);
                log.info("artifactId: {}", artifactId);
                log.info("version: {}", artifactVersion);
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
                    log.error("--sourceEncoding が無効な文字セット名です: {}", sourceEncoding);
                    return 1;
                }
            }

            // 出力先は outputDir/artifactId
            Path outputPath = outputDir.toPath().resolve(artifactId);
            if (outputPath.toFile().exists()) {
                log.error("出力先ディレクトリがすでに存在しています: {}", outputPath);
                return 1;
            }

            log.info("");
            log.info("=== e2m: Eclipse → Maven 変換開始 ===");
            log.info("  入力: {}", inputPath.toAbsolutePath());
            log.info("  出力: {}", outputPath.toAbsolutePath());

            // 3.(続き) JAR依存関係を解決
            log.info("");
            log.info("[3/5] JAR依存関係を解決中...");
            List<MavenDependency> dependencies = DependencyResolver.resolve(eclipseProject.jarPaths(), inputPath);
            long found = dependencies.stream().filter(d -> !"system".equals(d.scope())).count();
            long system = dependencies.stream().filter(d -> "system".equals(d.scope())).count();
            log.info("  Maven Central で見つかった依存: {} 件", found);
            log.info("  system スコープの依存: {} 件", system);

            // 4. pom.xml を生成
            log.info("");
            log.info("[4/5] pom.xml を生成中...");
            // --javaTargetVersion が指定された場合はソースバージョンと比較してバリデーション
            JavaVersion sourceVer = eclipseProject.javaSourceVersion();
            if (javaTargetVersion != null) {
                if (!sourceVer.isUnknown() && javaTargetVersion.compareTo(sourceVer) < 0) {
                    log.error("--javaTargetVersion ({}) はJavaソースバージョン ({}) より低いバージョンを指定することはできません。",
                            javaTargetVersion, eclipseProject.javaSourceVersion());
                    return 1;
                }
            }
            // 実効 javaTargetVersion を確定（--javaTargetVersion 指定 > Eclipseプロジェクト設定）
            JavaVersion effectiveTargetVersion = (javaTargetVersion != null && !javaTargetVersion.isUnknown())
                    ? javaTargetVersion
                    : eclipseProject.javaTargetVersion();
            PomGenerator.generate(eclipseProject, dependencies, groupId, artifactId, artifactVersion,
                    effectiveTargetVersion, convertToUtf8, outputPath);

            // 5. ソース・Webコンテンツをコピー
            log.info("");
            log.info("[5/5] ソースファイルをコピー中...");
            if (convertToUtf8) {
                log.info("  エンコーディング変換モード: {} → UTF-8", sourceEncoding);
            }
            ProjectCopier.copy(eclipseProject, dependencies, inputPath, outputPath,
                    convertToUtf8, srcCharset, effectiveTargetVersion);

            log.info("");
            log.info("=== 変換完了 ===");
            log.info("出力先: {}", outputPath.toAbsolutePath());

            // --debug オプション: デバッグ ZIP を生成
            if (debug) {
                DebugArchiver.archive(inputPath, outputDir.toPath(), outputPath, rawArgs);
            }

            return 0;

        } catch (Exception e) {
            log.error("変換中にエラーが発生しました: {}", e.getMessage());
            log.debug("スタックトレース:", e);
            return 2;
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
                System.out.print(name + " [" + defaultValue + "]: ");
            } else {
                System.out.print(name + ": ");
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
                System.out.print("  値を入力してください。");
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
