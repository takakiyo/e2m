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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Command(name = "e2m", description = "Eclipse project to Maven project converter", version = "1.0", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {

    @Option(names = {"-g", "--groupId"}, required = false, description = "Maven groupId of the output project")
    private String groupId;

    @Option(names = {"-a", "--artifactId"}, required = false, description = "Maven artifactId of the output project")
    private String artifactId;

    @Option(names = {"-v", "--artifactVersion"}, required = false, description = "Maven version of the output project (default: 1.0-SNAPSHOT)")
    private String artifactVersion;
    private static final String DEFAULT_ARTIFACT_VERSION = "1.0-SNAPSHOT";

    @Parameters(index = "0", paramLabel = "<inputDir>", description = "Eclipse project directory to convert")
    private File inputDir;

    @Parameters(index = "1", paramLabel = "<outputDir>", description = "Output directory for the Maven project")
    private File outputDir;

    @Override
    public Integer call() {
        Path inputPath = inputDir.toPath();

        // 1. 入力ディレクトリの存在確認
        if (!inputDir.isDirectory()) {
            System.err.println("[ERROR] 入力ディレクトリが見つかりません: " + inputDir);
            return 1;
        }
        if (!inputPath.resolve(".project").toFile().exists()) {
            System.err.println("[ERROR] Eclipseプロジェクトではありません (.project ファイルが見つかりません): " + inputDir);
            return 1;
        }
        if (outputDir.exists() && !outputDir.isDirectory()) {
            System.err.println("[ERROR] 出力パスはディレクトリである必要があります: " + outputDir);
            return 1;
        }

        try {
            // 2. Eclipseプロジェクト情報をパース
            System.out.println("\n[1/5] Eclipseプロジェクトを解析中...");
            EclipseProject eclipseProject = EclipseProjectParser.parse(inputPath);
            System.out.println("  プロジェクト名: " + eclipseProject.projectName());
            System.out.println("  種別: " + (eclipseProject.webProject() ? "Webプロジェクト (WTP)" : "Javaプロジェクト"));
            System.out.println("  Javaソースバージョン: " + eclipseProject.javaSourceVersion());
            System.out.println("  Javaターゲットバージョン: " + eclipseProject.javaTargetVersion());
            if (eclipseProject.webProject()) {
                System.out.println("  Web仕様バージョン: " + eclipseProject.webVersion());
            }
            System.out.println("  ソースフォルダ: " + eclipseProject.sourceFolders());
            if (eclipseProject.webProject()) {
                System.out.println("  Webコンテンツフォルダ: " + eclipseProject.webContentRoot());
            }
            System.out.println("  JARファイル数: " + eclipseProject.jarPaths().size());

            System.out.println("\n[2/5] 移行先のMavenプロジェクトを決定中...");
            String defaultArtifactId = convertToArtifactId(eclipseProject.projectName());
            if (groupId == null || groupId.isBlank()) {
                // オプション未指定の場合は対話的に入力を受け付ける
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    groupId = promptIfAbsent(reader, "groupId", groupId, null);
                    artifactId = promptIfAbsent(reader, "artifactId", artifactId, defaultArtifactId);
                    artifactVersion = promptIfAbsent(reader, "artifactVersion", artifactVersion, DEFAULT_ARTIFACT_VERSION);
                }
            } else {
                // 少なくとも--groupIdが指定されていれば，あとは空ならデフォルトを使用する
                if (artifactId == null || artifactId.isBlank()) {
                    artifactId = defaultArtifactId;
                }
                if (artifactVersion == null || artifactId.isBlank()) {
                    artifactVersion = DEFAULT_ARTIFACT_VERSION;
                }
                System.out.println("groupId: " + groupId);
                System.out.println("artifactId: " + artifactId);
                System.out.println("version: " + artifactVersion);
            }

            // 出力先は outputDir/artifactId
            Path outputPath = outputDir.toPath().resolve(artifactId);
            if (outputPath.toFile().exists()) {
                System.err.println("[ERROR] 出力先ディレクトリがすでに存在しています: " + outputPath);
                return 1;
            }

            System.out.println("\n=== e2m: Eclipse → Maven 変換開始 ===");
            System.out.println("  入力: " + inputPath.toAbsolutePath());
            System.out.println("  出力: " + outputPath.toAbsolutePath());

            // 3.(続き) JAR依存関係を解決
            System.out.println("\n[3/5] JAR依存関係を解決中...");
            List<MavenDependency> dependencies = DependencyResolver.resolve(eclipseProject.jarPaths(), inputPath);
            long found = dependencies.stream().filter(d -> !"system".equals(d.scope())).count();
            long system = dependencies.stream().filter(d -> "system".equals(d.scope())).count();
            System.out.println("  Maven Central で見つかった依存: " + found + " 件");
            System.out.println("  system スコープの依存: " + system + " 件");

            // 4. pom.xml を生成
            System.out.println("\n[4/5] pom.xml を生成中...");
            PomGenerator.generate(eclipseProject, dependencies, groupId, artifactId, artifactVersion, outputPath);

            // 5. ソース・Webコンテンツをコピー
            System.out.println("\n[5/5] ソースファイルをコピー中...");
            ProjectCopier.copy(eclipseProject, dependencies, inputPath, outputPath);

            System.out.println("\n=== 変換完了 ===");
            System.out.println("出力先: " + outputPath.toAbsolutePath());
            return 0;

        } catch (Exception e) {
            System.err.println("[ERROR] 変換中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace(System.err);
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
                System.err.println("  値を入力してください。");
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

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
