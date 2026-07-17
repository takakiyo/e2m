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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * EclipseプロジェクトのソースファイルをMavenの標準ディレクトリ構造にコピーするクラス。
 *
 * <ul>
 *   <li>Javaソース (.java) → src/main/java/ または src/test/java/</li>
 *   <li>リソースファイル (非.java) → src/main/resources/ または src/test/resources/</li>
 *   <li>Webコンテンツ (WTPプロジェクトの場合) → src/main/webapp/</li>
 * </ul>
 */
public class ProjectCopier {

    private ProjectCopier() {}

    /**
     * EclipseプロジェクトのファイルをMaven標準ディレクトリ構造にコピーする。
     *
     * @param eclipseProject パース済みEclipseプロジェクト情報
     * @param dependencies   解決済み依存関係リスト（system スコープの JAR を libs/ にコピーする）
     * @param inputDir       Eclipseプロジェクトのルートディレクトリ
     * @param outputDir      Mavenプロジェクトの出力ルートディレクトリ
     * @throws IOException ファイルコピーに失敗した場合
     */
    public static void copy(EclipseProject eclipseProject, List<MavenDependency> dependencies,
                            Path inputDir, Path outputDir) throws IOException {

        // Javaソースフォルダのコピー
        List<String> sourceFolders = eclipseProject.sourceFolders();
        for (String sourceFolder : sourceFolders) {
            Path srcPath = resolveSourcePath(inputDir, sourceFolder);
            if (!Files.isDirectory(srcPath)) {
                System.out.println("  [WARN] ソースフォルダが見つかりません: " + srcPath);
                continue;
            }
            boolean isTest = isTestFolder(sourceFolder);
            Path javaDestBase = outputDir.resolve(isTest ? "src/test/java" : "src/main/java");
            Path resourceDestBase = outputDir.resolve(isTest ? "src/test/resources" : "src/main/resources");

            copySourceFolder(srcPath, srcPath, javaDestBase, resourceDestBase);
        }

        // Webコンテンツのコピー（WTPプロジェクトの場合）
        if (eclipseProject.webProject() && eclipseProject.webContentRoot() != null) {
            Path webContentPath = resolveSourcePath(inputDir, eclipseProject.webContentRoot());
            if (Files.isDirectory(webContentPath)) {
                Path webappDest = outputDir.resolve("src/main/webapp");
                // WEB-INF/lib 内のJARは<dependency>として処理済みのためコピー不要（JAR以外はコピーする）
    //            Path webInfLib = webContentPath.resolve("WEB-INF/lib");
                copyWebContents(webContentPath, webappDest);
                System.out.println("  Copied web content: " + webContentPath + " → " + webappDest);
            } else {
                System.out.println("  [WARN] Webコンテンツルートが見つかりません: " + webContentPath);
            }
        }

        // system スコープの JAR を libs/ にコピー
        for (MavenDependency dep : dependencies) {
            if ("system".equals(dep.scope()) && dep.systemPath() != null) {
                Path src = Path.of(dep.systemPath());
                if (Files.isRegularFile(src)) {
                    Path libsDir = outputDir.resolve("libs");
                    Files.createDirectories(libsDir);
                    Path dest = libsDir.resolve(src.getFileName());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("  Copied JAR to libs/: " + dest.getFileName());
                }
            }
        }
    }

    /**
     * ソースフォルダ内のファイルを.javaとリソースに振り分けてコピーする。
     *
     * @param baseDir      コピー元のルート（相対パス計算の基点）
     * @param currentDir   現在処理中のディレクトリ
     * @param javaDestBase .javaファイルのコピー先ベースディレクトリ
     * @param resourceDest リソースファイルのコピー先ベースディレクトリ
     * @throws IOException ファイルコピーに失敗した場合
     */
    private static void copySourceFolder(Path baseDir, Path currentDir, Path javaDestBase, Path resourceDest)
            throws IOException {
        Files.walk(currentDir)
                .filter(Files::isRegularFile)
                .forEach(srcFile -> {
                    try {
                        Path relative = baseDir.relativize(srcFile);
                        boolean isJava = srcFile.getFileName().toString().endsWith(".java");
                        Path destBase = isJava ? javaDestBase : resourceDest;
                        Path destFile = destBase.resolve(relative);
                        Files.createDirectories(destFile.getParent());
                        Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException("ファイルコピーに失敗しました: " + srcFile, e);
                    }
                });
        System.out.println("  Copied source folder: " + currentDir + " → " + javaDestBase);
    }

    /**
     * ディレクトリを再帰的にコピーする。
     * {@code excludeJarDir} が指定された場合、そのディレクトリ直下の {@code .jar} ファイルのみスキップする。
     *
     * @param srcDir       コピー元ディレクトリ
     * @param destDir      コピー先ディレクトリ
     * @param excludeJarDir このディレクトリ直下の .jar ファイルを除外する（null の場合は除外なし）
     * @throws IOException ファイルコピーに失敗した場合
     */
    private static void copyWebContents(Path srcDir, Path destDir) throws IOException {
        Path excludeJarDir = srcDir.resolve("WEB-INF/lib");
        Files.walk(srcDir)
                .filter(srcPath -> !srcPath.startsWith(excludeJarDir)
                        || !srcPath.getFileName().toString().endsWith(".jar"))
                .forEach(srcPath -> {
                    try {
                        Path relative = srcDir.relativize(srcPath);
                        Path destPath = destDir.resolve(relative);
                        if (Files.isDirectory(srcPath)) {
                            Files.createDirectories(destPath);
                        } else {
                            Files.createDirectories(destPath.getParent());
                            Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("ファイルコピーに失敗しました: " + srcPath, e);
                    }
                });
    }

    /**
     * ソースフォルダのパス文字列をinputDir基点の絶対Pathに解決する。
     * 先頭の '/' は除去して相対パスとして扱う。
     */
    private static Path resolveSourcePath(Path inputDir, String folderPath) {
        String normalized = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
        if (normalized.isEmpty()) {
            return inputDir;
        }
        return inputDir.resolve(normalized);
    }

    /**
     * ソースフォルダ名に "test" が含まれるかどうかを判定する。
     * パスの各セグメントを小文字で比較する。
     */
    static boolean isTestFolder(String folderPath) {
        String lower = folderPath.toLowerCase();
        for (String segment : lower.split("[/\\\\]")) {
            if (segment.equals("test") || segment.startsWith("test-") || segment.endsWith("-test")
                    || segment.contains("test")) {
                return true;
            }
        }
        return false;
    }
}
