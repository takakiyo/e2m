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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EclipseプロジェクトのソースファイルをMavenの標準ディレクトリ構造にコピーするクラス。
 *
 * <ul>
 *   <li>Javaソース (.java) → src/main/java/ または src/test/java/</li>
 *   <li>リソースファイル (非.java) → src/main/resources/ または src/test/resources/</li>
 *   <li>Webコンテンツ (WTPプロジェクトの場合) → src/main/webapp/</li>
 * </ul>
 *
 * <p>{@code convertToUtf8} が {@code true} の場合、以下の変換を行う：</p>
 * <ul>
 *   <li>{@code .java} ファイル: {@code sourceEncoding} で読み込み、UTF-8 で書き出す</li>
 *   <li>{@code .properties} ファイル: {@code sourceEncoding} で読み込み、Unicode エスケープを
 *       Unicode 文字に展開して UTF-8 で書き出す。{@code effectiveTargetVersion <= 8} の場合は
 *       {@code src/main/resources-utf8/} へ出力する</li>
 *   <li>{@code .jsp} ファイル: {@code pageEncoding} 属性を検出してそのエンコードで読み込み、
 *       属性値を {@code UTF-8} に書き換えて出力する</li>
 * </ul>
 */
public class ProjectCopier {

    private ProjectCopier() {}

    /** ISO-8859-1 でプリアンブルを読む際のバッファサイズ（4KB）。 */
    private static final int JSP_PREAMBLE_BYTES = 4096;

    /** JSP の pageEncoding 属性を検出する正規表現。 */
    private static final Pattern JSP_PAGE_ENCODING_PATTERN =
            Pattern.compile("<%@\\s*page\\b[^%]*\\bpageEncoding\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.DOTALL);

    /** properties ファイルの Unicode エスケープシーケンスを検出する正規表現。 */
    private static final Pattern UNICODE_ESCAPE_PATTERN =
            Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    /**
     * EclipseプロジェクトのファイルをMaven標準ディレクトリ構造にコピーする。
     *
     * @param eclipseProject        パース済みEclipseプロジェクト情報
     * @param dependencies          解決済み依存関係リスト（system スコープの JAR を libs/ にコピーする）
     * @param inputDir              Eclipseプロジェクトのルートディレクトリ
     * @param outputDir             Mavenプロジェクトの出力ルートディレクトリ
     * @param convertToUtf8         true の場合はエンコーディング変換を行う
     * @param sourceEncoding        変換元エンコーディング（convertToUtf8=false の場合は無視）
     * @param effectiveTargetVersion 実効Javaターゲットバージョン（.properties 出力先の判定に使用）
     * @throws IOException ファイルコピーに失敗した場合
     */
    public static void copy(EclipseProject eclipseProject, List<MavenDependency> dependencies,
                            Path inputDir, Path outputDir,
                            boolean convertToUtf8, Charset sourceEncoding,
                            JavaVersion effectiveTargetVersion) throws IOException {

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
            // .properties の出力先: Java 9+ → src/main/resources/、Java 8以下 → src/main/resources-utf8/
            boolean useUtf8ResourceDir = convertToUtf8 && !effectiveTargetVersion.isUnknown()
                    && effectiveTargetVersion.compareTo(JavaVersion.Java8) <= 0;
            Path resourceDestBase = outputDir.resolve(
                    isTest ? "src/test/resources"
                           : (useUtf8ResourceDir ? "src/main/resources-utf8" : "src/main/resources"));
            Path resourceDestNormal = outputDir.resolve(isTest ? "src/test/resources" : "src/main/resources");

            copySourceFolder(srcPath, srcPath, javaDestBase, resourceDestBase, resourceDestNormal,
                    convertToUtf8, sourceEncoding);
        }

        // Webコンテンツのコピー（WTPプロジェクトの場合）
        if (eclipseProject.webProject() && eclipseProject.webContentRoot() != null) {
            Path webContentPath = resolveSourcePath(inputDir, eclipseProject.webContentRoot());
            if (Files.isDirectory(webContentPath)) {
                Path webappDest = outputDir.resolve("src/main/webapp");
                copyWebContents(webContentPath, webappDest, convertToUtf8, sourceEncoding);
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
     * @param baseDir            コピー元のルート（相対パス計算の基点）
     * @param currentDir         現在処理中のディレクトリ
     * @param javaDestBase       .javaファイルのコピー先ベースディレクトリ
     * @param resourceDest       リソースファイルのコピー先ベースディレクトリ（.properties はここへ）
     * @param resourceDestNormal 通常のリソースコピー先（.properties 以外の非.javaファイルはここへ）
     * @param convertToUtf8      true の場合はエンコーディング変換を行う
     * @param sourceEncoding     変換元エンコーディング
     * @throws IOException ファイルコピーに失敗した場合
     */
    private static void copySourceFolder(Path baseDir, Path currentDir, Path javaDestBase,
                                         Path resourceDest, Path resourceDestNormal,
                                         boolean convertToUtf8, Charset sourceEncoding)
            throws IOException {
        Files.walk(currentDir)
                .filter(Files::isRegularFile)
                .forEach(srcFile -> {
                    try {
                        Path relative = baseDir.relativize(srcFile);
                        String fileName = srcFile.getFileName().toString();
                        boolean isJava = fileName.endsWith(".java");
                        boolean isProperties = fileName.endsWith(".properties");

                        if (isJava) {
                            Path destFile = javaDestBase.resolve(relative);
                            Files.createDirectories(destFile.getParent());
                            if (convertToUtf8) {
                                copyWithEncodingConversion(srcFile, destFile, sourceEncoding);
                                System.out.println("  [UTF-8変換] " + relative);
                            } else {
                                Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } else if (isProperties && convertToUtf8) {
                            // .properties: Unicode エスケープ展開 + UTF-8 で resourceDest へ
                            Path destFile = resourceDest.resolve(relative);
                            Files.createDirectories(destFile.getParent());
                            copyPropertiesWithConversion(srcFile, destFile, sourceEncoding);
                            System.out.println("  [UTF-8変換] " + relative);
                        } else {
                            // その他のリソース（バイナリコピー）
                            Path destFile = resourceDestNormal.resolve(relative);
                            Files.createDirectories(destFile.getParent());
                            Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("ファイルコピーに失敗しました: " + srcFile, e);
                    }
                });
        System.out.println("  Copied source folder: " + currentDir + " → " + javaDestBase);
    }

    /**
     * Webコンテンツディレクトリを再帰的にコピーする。
     * WEB-INF/lib 直下の .jar ファイルはスキップする。
     * {@code convertToUtf8=true} の場合、.jsp ファイルはエンコーディング変換を行う。
     *
     * @param srcDir         コピー元ディレクトリ
     * @param destDir        コピー先ディレクトリ
     * @param convertToUtf8  true の場合はエンコーディング変換を行う
     * @param sourceEncoding 変換元エンコーディング（.jsp の pageEncoding が不明な場合に使用）
     * @throws IOException ファイルコピーに失敗した場合
     */
    private static void copyWebContents(Path srcDir, Path destDir,
                                        boolean convertToUtf8, Charset sourceEncoding)
            throws IOException {
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
                            String fileName = srcPath.getFileName().toString();
                            if (convertToUtf8 && fileName.endsWith(".jsp")) {
                                Charset jspEncoding = extractJspPageEncoding(srcPath, sourceEncoding);
                                if (jspEncoding != null) {
                                    copyJspWithConversion(srcPath, destPath, jspEncoding);
                                    System.out.println("  [UTF-8変換] " + relative);
                                } else {
                                    // pageEncoding が既に UTF-8 → バイナリコピー
                                    Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } else {
                                Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("ファイルコピーに失敗しました: " + srcPath, e);
                    }
                });
    }

    /**
     * テキストファイルを指定エンコードで読み込み、UTF-8 で書き出す。
     *
     * @param src  コピー元ファイル
     * @param dest コピー先ファイル
     * @param from 変換元エンコーディング
     * @throws IOException ファイル読み書きに失敗した場合
     */
    static void copyWithEncodingConversion(Path src, Path dest, Charset from) throws IOException {
        String content = Files.readString(src, from);
        Files.writeString(dest, content, StandardCharsets.UTF_8);
    }

    /**
     * .properties ファイルを指定エンコードで読み込み、Unicode エスケープを展開して UTF-8 で書き出す。
     *
     * @param src  コピー元ファイル
     * @param dest コピー先ファイル
     * @param from 変換元エンコーディング
     * @throws IOException ファイル読み書きに失敗した場合
     */
    static void copyPropertiesWithConversion(Path src, Path dest, Charset from) throws IOException {
        List<String> lines = Files.readAllLines(src, from);
        List<String> converted = lines.stream()
                .map(ProjectCopier::unescapeUnicode)
                .toList();
        Files.write(dest, converted, StandardCharsets.UTF_8);
    }

    /**
     * 文字列中の Unicode エスケープシーケンス（&#92;uXXXX 形式）を Unicode 文字に展開する。
     *
     * @param line 入力文字列
     * @return Unicode エスケープを展開した文字列
     */
    static String unescapeUnicode(String line) {
        Matcher m = UNICODE_ESCAPE_PATTERN.matcher(line);
        if (!m.find()) {
            return line;
        }
        m.reset();
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int codePoint = Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) codePoint)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * JSP ファイルの先頭 4KB を ISO-8859-1 で読み込み、{@code pageEncoding} 属性を検出する。
     *
     * <ul>
     *   <li>検出でき、かつ UTF-8 系でない場合: そのエンコーディングの {@link Charset} を返す</li>
     *   <li>検出できない場合: {@code fallback} を返す</li>
     *   <li>検出でき、かつ既に UTF-8 系の場合: {@code null} を返す（呼び出し元でバイナリコピーに切り替える）</li>
     * </ul>
     *
     * @param jspFile  JSP ファイルのパス
     * @param fallback pageEncoding が見つからない場合に返すエンコーディング
     * @return 変換元エンコーディング、または null（既に UTF-8 の場合）
     * @throws IOException ファイル読み込みに失敗した場合
     */
    static Charset extractJspPageEncoding(Path jspFile, Charset fallback) throws IOException {
        // 先頭 4KB を ISO-8859-1 で読み込む（バイト値をそのまま文字として扱うため）
        byte[] bytes = Files.readAllBytes(jspFile);
        int len = Math.min(bytes.length, JSP_PREAMBLE_BYTES);
        String preamble = new String(bytes, 0, len, StandardCharsets.ISO_8859_1);

        Matcher m = JSP_PAGE_ENCODING_PATTERN.matcher(preamble);
        if (!m.find()) {
            return fallback;
        }
        String encodingName = m.group(1).trim();
        // 既に UTF-8 系であれば null を返す
        if (encodingName.equalsIgnoreCase("UTF-8") || encodingName.equalsIgnoreCase("UTF8")) {
            return null;
        }
        try {
            return Charset.forName(encodingName);
        } catch (Exception e) {
            // 不明なエンコーディング名の場合は fallback を使用
            System.out.println("  [WARN] JSP の pageEncoding が不明です: " + encodingName + " → fallback を使用");
            return fallback;
        }
    }

    /**
     * JSP ファイルを指定エンコードで読み込み、{@code pageEncoding} 属性値を {@code UTF-8} に書き換えて出力する。
     *
     * @param src  コピー元 JSP ファイル
     * @param dest コピー先ファイル
     * @param from 変換元エンコーディング
     * @throws IOException ファイル読み書きに失敗した場合
     */
    static void copyJspWithConversion(Path src, Path dest, Charset from) throws IOException {
        String content = Files.readString(src, from);
        // pageEncoding 属性値を UTF-8 に書き換える（シングルクォートとダブルクォートの両方に対応）
        String converted = content.replaceAll(
                "(<%@\\s*page\\b[^%]*\\bpageEncoding\\s*=\\s*)([\"'])([^\"']+)\\2",
                "$1$2UTF-8$2");
        Files.writeString(dest, converted, StandardCharsets.UTF_8);
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
