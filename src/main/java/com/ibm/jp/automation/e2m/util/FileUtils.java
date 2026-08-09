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

package com.ibm.jp.automation.e2m.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * ファイル操作に関するユーティリティクラス。
 */
public class FileUtils {

    private FileUtils() {}

    /**
     * ファイルのSHA1ハッシュを小文字16進数文字列として計算する。
     *
     * @param file ハッシュを計算するファイルのパス
     * @return SHA1ハッシュの小文字16進数文字列
     * @throws Exception ファイル読み取りまたはダイジェスト計算に失敗した場合
     */
    public static String computeSha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /** ディレクトリの深さ1段あたりのインデント幅。 */
    private static final double INDENT_EM = 1.2;

    private static final DateTimeFormatter FILETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy'年'M'月'd'日' H:mm");

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="ja">
            <head>
            <meta charset="UTF-8">
            <title>%1$s</title>
            <style>
            * { box-sizing: border-box; }
            body {
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Hiragino Sans", sans-serif;
              font-size: 13px;
              color: #1d1d1f;
              margin: 0;
              padding: 1em;
            }
            h1 {
              font-size: 14px;
              font-weight: 600;
              margin: 0 0 0.8em;
              word-break: break-all;
            }
            .filelist {
              display: grid;
              grid-template-columns: minmax(16em, 1fr) 11em 8em;
              border: 1px solid #d8d8d8;
              border-radius: 6px;
              overflow: hidden;
            }
            .header {
              position: relative;
              background: #f0f0f0;
              border-bottom: 1px solid #d8d8d8;
              padding: 0.4em 0.8em;
              font-weight: 600;
              font-size: 12px;
              color: #444;
            }
            .header.date, .header.size { text-align: right; }
            .header.name, .header.date { border-right: 1px solid #c8c8c8; }
            .sort-arrow { font-size: 0.7em; color: #888; margin-left: 0.3em; }
            .resizer {
              position: absolute;
              top: 0;
              right: -3px;
              width: 6px;
              height: 100%%;
              cursor: col-resize;
              z-index: 1;
            }
            .resizer:hover, .resizer.active { background: rgba(0, 0, 0, 0.15); }
            .row { display: contents; }
            .row.even .cell { background: #f5f5f7; }
            .row.odd .cell { background: #ffffff; }
            .cell {
              padding: 0.3em 0.8em;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
              border-bottom: 1px solid #eee;
            }
            .cell.name, .cell.date { border-right: 1px solid #ececec; }
            .cell.date, .cell.size { text-align: right; color: #555; }
            .cell.name { display: flex; align-items: center; gap: 0.35em; }
            .chevron { width: 0.9em; flex: none; text-align: center; color: #888; font-size: 0.8em; }
            .row.dir > .cell.name { cursor: pointer; }
            .icon { flex: none; font-size: 1.1em; }
            .label { overflow: hidden; text-overflow: ellipsis; }
            .link { color: #888; font-size: 0.85em; }
            </style>
            </head>
            <body>
            <h1>%1$s</h1>
            <div class="filelist">
            <div class="header name">名前 <span class="sort-arrow">▲</span><div class="resizer" data-col="0"></div></div>
            <div class="header date">変更日<div class="resizer" data-col="1"></div></div>
            <div class="header size">サイズ</div>
            %2$s\
            </div>
            <script>
            (function() {
              var container = document.querySelector('.filelist');
              var headerCells = [
                document.querySelector('.header.name'),
                document.querySelector('.header.date'),
                document.querySelector('.header.size')
              ];

              function currentWidths() {
                return headerCells.map(function(cell) { return cell.getBoundingClientRect().width; });
              }

              document.querySelectorAll('.resizer').forEach(function(resizer) {
                resizer.addEventListener('mousedown', function(e) {
                  e.preventDefault();
                  var col = parseInt(resizer.dataset.col, 10);
                  var widths = currentWidths();
                  var startX = e.clientX;
                  var startWidth = widths[col];
                  resizer.classList.add('active');
                  document.body.style.userSelect = 'none';

                  function onMouseMove(ev) {
                    widths[col] = Math.max(48, startWidth + (ev.clientX - startX));
                    container.style.gridTemplateColumns = widths.map(function(w) { return w + 'px'; }).join(' ');
                  }
                  function onMouseUp() {
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);
                    resizer.classList.remove('active');
                    document.body.style.userSelect = '';
                  }
                  document.addEventListener('mousemove', onMouseMove);
                  document.addEventListener('mouseup', onMouseUp);
                });
              });
            })();
            function refreshVisibility() {
              var rows = document.querySelectorAll('.row');
              var hideDepth = null;
              rows.forEach(function(row) {
                var depth = parseInt(row.dataset.depth, 10);
                if (hideDepth !== null) {
                  if (depth > hideDepth) {
                    row.style.display = 'none';
                    return;
                  }
                  hideDepth = null;
                }
                row.style.display = '';
                if (row.classList.contains('dir') && row.classList.contains('collapsed')) {
                  hideDepth = depth;
                }
              });
            }
            function toggleDir(row) {
              row.classList.toggle('collapsed');
              var collapsed = row.classList.contains('collapsed');
              row.querySelector('.chevron').textContent = collapsed ? '▸' : '▾';
              row.querySelector('.icon').textContent = collapsed ? '📁' : '📂';
              refreshVisibility();
            }
            refreshVisibility();
            </script>
            </body>
            </html>
            """;

    private static final String DIRECTORY_ROW_TEMPLATE = """
            <div class="row dir collapsed %1$s" data-depth="%2$s">
            <div class="cell name" style="padding-left: %3$s" onclick="toggleDir(this.closest('.row'))"><span class="chevron">▸</span><span class="icon">📁</span><span class="label">%4$s</span></div>
            <div class="cell date">%5$s</div>
            <div class="cell size">--</div>
            </div>
            """;

    private static final String FILE_ROW_TEMPLATE = """
            <div class="row %1$s" data-depth="%2$s">
            <div class="cell name" style="padding-left: %3$s"><span class="chevron"></span><span class="icon">📄</span><span class="label">%4$s</span>%5$s</div>
            <div class="cell date">%6$s</div>
            <div class="cell size">%7$s</div>
            </div>
            """;


    /**
     * 指定ディレクトリ以下のファイル一覧を表示するHTMLファイルを生成する。
     * macOS Finderのリスト表示を模した，列（名前・変更日・サイズ）が揃うツリー表示になり，
     * ディレクトリ行をクリックすると開閉できる。
     *
     * @param dir      一覧表示するディレクトリ
     * @param htmlFile 生成するHTMLファイルのパス
     * @throws IOException ディレクトリの読み取りまたはHTMLファイルの書き込みに失敗した場合
     */
    public static void createFileListHtml(Path dir, Path htmlFile) throws IOException {
        String html = buildFileListHtml(dir);

        Files.createDirectories(htmlFile.toAbsolutePath().getParent());
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
    }

    /**
     * 指定ディレクトリ以下のファイル一覧を表示するHTMLを文字列として構築する。
     * 内容は {@link #createFileListHtml(Path, Path)} と同じで，ファイルに書き出さず
     * 文字列として利用したい場合（例えばZIPへの直接格納）に使う。
     *
     * @param dir 一覧表示するディレクトリ
     * @return HTML全体の文字列
     * @throws IOException ディレクトリの読み取りに失敗した場合
     */
    public static String buildFileListHtml(Path dir) throws IOException {
        String title = escapeHtml(dir.getFileName().toString());
        StringBuilder rows = new StringBuilder();
        appendRows(dir, 0, new int[1], rows);
        return HTML_TEMPLATE.formatted(title, rows.toString());
    }

    /**
     * ディレクトリ直下のエントリを行として {@code out} に追記する。
     * サブディレクトリは深さ優先でその場に再帰的に展開される（フラットな行の並びにすることで，
     * 階層をまたいで変更日・サイズの列を揃えられる）。
     *
     * @param dir      一覧表示するディレクトリ
     * @param depth    現在のディレクトリの深さ（トップレベルは0）
     * @param rowIndex 縞模様のための行番号カウンタ（{@code int[1]} で受け渡す）
     * @param out      出力先
     */
    private static void appendRows(Path dir, int depth, int[] rowIndex, StringBuilder out) throws IOException {
        List<Path> entries;
        try (var stream = Files.list(dir)) {
            entries = stream
                    .sorted(Comparator.comparing((Path p) -> !isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            // 権限がない等でディレクトリを読み取れない場合は，その配下を含めずに無視する
            return;
        }

        for (Path entry : entries) {
            appendRow(entry, depth, rowIndex, out);
        }
    }

    /**
     * 1つのファイル・ディレクトリを1行として {@code out} に追記する。
     * ディレクトリの場合は，その直下のエントリも続けて再帰的に追記する。
     */
    private static void appendRow(Path entry, int depth, int[] rowIndex, StringBuilder out) throws IOException {
        String stripe = (rowIndex[0]++ % 2 == 0) ? "even" : "odd";
        String indent = String.format(Locale.ROOT, "%.2fem", depth * INDENT_EM);
        String name = escapeHtml(entry.getFileName().toString());
        String modified = formatLastModified(entry);

        if (isDirectory(entry)) {
            out.append(DIRECTORY_ROW_TEMPLATE.formatted(stripe, depth, indent, name, modified));
            appendRows(entry, depth + 1, rowIndex, out);
            return;
        }

        String size = formatSize(entry);
        String linkHtml = "";
        if (Files.isSymbolicLink(entry)) {
            Path target = Files.readSymbolicLink(entry);
            linkHtml = " <span class=\"link\">→ " + escapeHtml(target.toString()) + "</span>";
        }
        out.append(FILE_ROW_TEMPLATE.formatted(stripe, depth, indent, name, linkHtml, modified, size));
    }

    /** シンボリックリンクを辿らずにディレクトリかどうかを判定する。 */
    private static boolean isDirectory(Path p) {
        return !Files.isSymbolicLink(p) && Files.isDirectory(p);
    }

    private static String formatLastModified(Path p) throws IOException {
        Instant instant = Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(FILETIME_FMT);
    }

    /** ファイルサイズを「バイト/KB/MB/GB」の単位で読みやすく整形する。 */
    private static String formatSize(Path p) {
        long bytes;
        try {
            bytes = Files.size(p);
        } catch (IOException e) {
            return "-";
        }

        if (bytes < 1024) {
            return bytes + " バイト";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return Math.round(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return Math.round(mb) + " MB";
        }
        double gb = mb / 1024.0;
        return Math.round(gb) + " GB";
    }

    /** HTML特殊文字をエスケープする。 */
    private static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * {@link #createFileListHtml(Path, Path)} の動作確認用エントリポイント。
     *
     * @param args {@code args[0]} に一覧表示するディレクトリ、{@code args[1]} に出力先HTMLファイルを指定する。
     *             省略時はカレントディレクトリと {@code filelist.html} を使用する。
     */
    public static void main(String[] args) throws IOException {
        Path dir = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path htmlFile = args.length > 1 ? Paths.get(args[1]) : Paths.get("filelist.html");

        createFileListHtml(dir, htmlFile);
        System.out.println("生成しました: " + htmlFile.toAbsolutePath());
    }
}
