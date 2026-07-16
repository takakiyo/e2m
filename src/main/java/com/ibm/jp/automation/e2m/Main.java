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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "e2m",
    description = "Eclipse project to Maven project converter",
    version = "1.0",
    mixinStandardHelpOptions = true
)
public class Main implements Callable<Integer> {

    @Option(names = "--groupId", required = true, description = "Maven groupId of the output project")
    private String groupId;

    @Option(names = "--artifactId", required = true, description = "Maven artifactId of the output project")
    private String artifactId;

    @Option(names = "--artifactVersion", required = false, defaultValue = "1.0-SNAPSHOT",
        description = "Maven version of the output project")
    private String artifactVersion;

    @Parameters(index = "0", paramLabel = "<inputDir>", description = "Eclipse project directory to convert")
    private File inputDir;

    @Parameters(index = "1", paramLabel = "<outputDir>", description = "Output directory for the Maven project")
    private File outputDir;

    @Override
    public Integer call() {
        Path inputPath = inputDir.toPath();
        Path outputPath = outputDir.toPath();

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

        System.out.println("=== e2m: Eclipse → Maven 変換開始 ===");
        System.out.println("  入力: " + inputPath.toAbsolutePath());
        System.out.println("  出力: " + outputPath.toAbsolutePath());

        try {
            // 2. Eclipseプロジェクト情報をパース
            System.out.println("\n[1/4] Eclipseプロジェクトを解析中...");
            EclipseProject eclipseProject = EclipseProjectParser.parse(inputPath);
            System.out.println("  プロジェクト名: " + eclipseProject.projectName());
            System.out.println("  種別: " + (eclipseProject.webProject() ? "Webプロジェクト (WTP)" : "Javaプロジェクト"));
            System.out.println("  Javaバージョン: " + eclipseProject.javaVersion());
            System.out.println("  ソースフォルダ: " + eclipseProject.sourceFolders());
            System.out.println("  JARファイル数: " + eclipseProject.jarPaths().size());

            // 3. JAR依存関係を解決
            System.out.println("\n[2/4] JAR依存関係を解決中...");
            List<MavenDependency> dependencies = DependencyResolver.resolve(eclipseProject.jarPaths(), inputPath);
            long found = dependencies.stream().filter(d -> !"system".equals(d.scope())).count();
            long system = dependencies.stream().filter(d -> "system".equals(d.scope())).count();
            System.out.println("  Maven Central で見つかった依存: " + found + " 件");
            System.out.println("  system スコープの依存: " + system + " 件");

            // 4. pom.xml を生成
            System.out.println("\n[3/4] pom.xml を生成中...");
            PomGenerator.generate(eclipseProject, dependencies, groupId, artifactId, artifactVersion, outputPath);

            // 5. ソース・Webコンテンツをコピー
            System.out.println("\n[4/4] ソースファイルをコピー中...");
            ProjectCopier.copy(eclipseProject, inputPath, outputPath);

            System.out.println("\n=== 変換完了 ===");
            System.out.println("出力先: " + outputPath.toAbsolutePath());
            return 0;

        } catch (Exception e) {
            System.err.println("[ERROR] 変換中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace(System.err);
            return 2;
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
