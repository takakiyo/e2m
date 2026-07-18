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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * デバッグ情報を ZIP アーカイブとして出力するクラス。
 *
 * <p>出力内容:</p>
 * <ul>
 *   <li>コマンドライン引数一覧（{@code commandline_args.txt}）</li>
 *   <li>実行環境のシステムプロパティ一覧（{@code system_properties.txt}）</li>
 *   <li>Eclipseプロジェクトの {@code .project}，{@code .classpath}，{@code .factorypath}，
 *       {@code .settings/} 以下の全ファイル</li>
 *   <li>Eclipseプロジェクト内の全ファイル一覧（名前・サイズ・日付。JARの場合はSHA1も）</li>
 *   <li>生成したMavenプロジェクトの {@code pom.xml}</li>
 *   <li>生成したMavenプロジェクトの全ファイル一覧（名前・サイズ・日付）</li>
 * </ul>
 */
public class DebugArchiver {

    private static final Logger log = AppLogger.get(DebugArchiver.class);

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FILETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private DebugArchiver() {}

    /**
     * デバッグ ZIP を生成して出力ディレクトリに保存する。
     *
     * @param inputDir  Eclipseプロジェクトのルートディレクトリ
     * @param outputDir outputDir（artifactId を含まないトップレベルのディレクトリ）
     * @param mavenDir  生成したMavenプロジェクトのルートディレクトリ
     * @param args      コマンドライン引数のコピー
     * @throws IOException ZIP生成またはファイル書き込みに失敗した場合
     */
    public static void archive(Path inputDir, Path outputDir, Path mavenDir, String[] args) throws IOException {

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path zipPath = outputDir.resolve("e2m_debug_" + timestamp + ".zip");
        Files.createDirectories(outputDir);

        log.info("");
        log.info("[DEBUG] デバッグ情報を出力中: {}", zipPath);

        // ログファイルをフラッシュしてからZIPに追加するため、先にZIPを作成してログを最後に追加する
        Path logFile = AppLogger.getLogFile();

        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {

            // ── 0. コマンドライン引数一覧 ─────────────────────────────────────
            addText(zos, "commandline_args.txt", buildArgsText(args));

            // ── 0b. システムプロパティ一覧 ────────────────────────────────────
            addText(zos, "system_properties.txt", buildSystemPropertiesText());

            // ── 1. Eclipseプロジェクトのメタファイル ──────────────────────────
            addEclipseMetaFiles(zos, inputDir);

            // ── 2. Eclipseプロジェクトのファイル一覧（JARはSHA1付き）──────────
            addText(zos, "eclipse_files.json", buildEclipseFileListing(inputDir));

            // ── 3. 生成したMavenプロジェクトのpom.xml ────────────────────────
            if (mavenDir != null) {
                Path pomFile = mavenDir.resolve("pom.xml");
                if (Files.isRegularFile(pomFile)) {
                    addFile(zos, pomFile, "pom.xml");
                }

                // ── 4. 生成したMavenプロジェクトのファイル一覧 ──────────────
                addText(zos, "maven_files.json", buildFileListing(mavenDir));
            }

            // ── 5. デバッグログファイル ────────────────────────────────────
            if (logFile != null && Files.isRegularFile(logFile)) {
                addFile(zos, logFile, "e2m_debug.log");
            }
        }

        log.info("[DEBUG] 出力完了: {}", zipPath.toAbsolutePath());

        // ZIPに取り込まれたログファイルを削除
        if (logFile != null && Files.isRegularFile(logFile)) {
            Files.delete(logFile);
        }
    }

    /**
     * コマンドライン引数の一覧をテキスト形式で構築する。
     * 各引数はインデックス付きで1行ずつ出力される。
     *
     * @param args コマンドライン引数のコピー（null または空配列も許容）
     * @return テキスト表現
     */
    private static String buildArgsText(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("# e2m command-line arguments\n");
        if (args == null || args.length == 0) {
            sb.append("(no arguments)\n");
        } else {
            for (int i = 0; i < args.length; i++) {
                sb.append("args[").append(i).append("] = ").append(args[i]).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 実行環境の {@link System#getProperties()} を取得し、キーのアルファベット順に
     * テキスト形式で構築する。
     *
     * @return テキスト表現
     */
    private static String buildSystemPropertiesText() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Java System Properties\n");
        Properties props = System.getProperties();
        new TreeMap<>(props).forEach((k, v) ->
                sb.append(k).append(" = ").append(v).append("\n"));
        return sb.toString();
    }

    // ── プライベートヘルパー ──────────────────────────────────────────────────

    /**
     * Eclipseプロジェクトのメタファイル（.project, .classpath, .factorypath,
     * .settings/ 以下全て）を ZIP に追加する。
     */
    private static void addEclipseMetaFiles(ZipOutputStream zos, Path inputDir) throws IOException {
        for (String name : new String[]{".project", ".classpath", ".factorypath"}) {
            Path file = inputDir.resolve(name);
            if (Files.isRegularFile(file)) {
                addFile(zos, file, "eclipse/" + name);
            }
        }
        Path settingsDir = inputDir.resolve(".settings");
        if (Files.isDirectory(settingsDir)) {
            Files.walk(settingsDir)
                    .filter(Files::isRegularFile)
                    .sorted()
                    .forEach(p -> {
                        try {
                            String relative = inputDir.relativize(p).toString().replace('\\', '/');
                            addFile(zos, p, "eclipse/" + relative);
                        } catch (IOException e) {
                            throw new RuntimeException("ZIPへの追加に失敗しました: " + p, e);
                        }
                    });
        }
    }

    /**
     * Eclipseプロジェクトのファイル一覧を JSON 形式で構築する。
     * JARファイルの場合は sha1 フィールドも出力する。
     */
    private static String buildEclipseFileListing(Path root) throws IOException {
        List<String> entries = new ArrayList<>();

        if (Files.exists(root)) {
            for (Path p : (Iterable<Path>) Files.walk(root).sorted()::iterator) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                boolean isDir = attrs.isDirectory();
                long size = isDir ? 0L : attrs.size();
                Instant instant = attrs.lastModifiedTime().toInstant();
                String modified = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                        .format(FILETIME_FMT);
                String relative = root.relativize(p).toString().replace('\\', '/');
                if (relative.isEmpty()) {
                    relative = ".";
                }

                StringBuilder entry = new StringBuilder();
                entry.append("    {");
                entry.append("\"type\":\"").append(isDir ? "directory" : "file").append("\"");
                entry.append(",\"path\":\"").append(jsonEscape(relative)).append("\"");
                entry.append(",\"size\":").append(size);
                entry.append(",\"lastModified\":\"").append(modified).append("\"");

                if (!isDir && p.getFileName().toString().endsWith(".jar")) {
                    String sha1;
                    try {
                        sha1 = FileUtils.computeSha1(p);
                    } catch (Exception e) {
                        sha1 = null;
                    }
                    if (sha1 != null) {
                        entry.append(",\"sha1\":\"").append(sha1).append("\"");
                    }
                }

                entry.append("}");
                entries.add(entry.toString());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"root\":\"").append(jsonEscape(root.toAbsolutePath().toString())).append("\",\n");
        sb.append("  \"entries\":[\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append(entries.get(i));
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 指定ディレクトリ以下の全ファイル・ディレクトリ一覧を JSON 形式で返す。
     */
    private static String buildFileListing(Path root) throws IOException {
        List<String> entries = new ArrayList<>();

        if (Files.exists(root)) {
            for (Path p : (Iterable<Path>) Files.walk(root).sorted()::iterator) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                boolean isDir = attrs.isDirectory();
                long size = isDir ? 0L : attrs.size();
                Instant instant = attrs.lastModifiedTime().toInstant();
                String modified = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                        .format(FILETIME_FMT);
                String relative = root.relativize(p).toString().replace('\\', '/');
                if (relative.isEmpty()) {
                    relative = ".";
                }

                entries.add("    {" +
                        "\"type\":\"" + (isDir ? "directory" : "file") + "\"" +
                        ",\"path\":\"" + jsonEscape(relative) + "\"" +
                        ",\"size\":" + size +
                        ",\"lastModified\":\"" + modified + "\"" +
                        "}");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"root\":\"").append(jsonEscape(root.toAbsolutePath().toString())).append("\",\n");
        sb.append("  \"entries\":[\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append(entries.get(i));
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * JSON 文字列値として安全にエスケープする。
     * バックスラッシュ・ダブルクォート・制御文字を処理する。
     */
    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** ファイルを ZIP エントリとして追加する。 */
    private static void addFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    /** 文字列テキストを ZIP エントリとして追加する。 */
    private static void addText(ZipOutputStream zos, String entryName, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(bytes);
        zos.closeEntry();
    }
}
