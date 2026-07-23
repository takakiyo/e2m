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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Eclipseプロジェクトのメタデータファイルを解析して {@link EclipseProject} を返すパーサー。
 */
public class EclipseProjectParser {

    private static final String WTP_NATURE = "org.eclipse.wst.common.project.facet.core.nature";
    private static final String DEFAULT_JAVA_VERSION = "11";

    /**
     * 指定した入力ディレクトリを解析して {@link EclipseProject} を返す。
     *
     * @param inputDir Eclipseプロジェクトのルートディレクトリ
     * @return 解析結果
     * @throws Exception 解析中に発生した例外
     */
    public static EclipseProject parse(Path inputDir) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // ── .project ──────────────────────────────────────────────
        Path projectFile = inputDir.resolve(".project");
        // 行頭の空白・改行をスキップするため InputStream 経由でパース
        Document projectDoc = builder.parse(
                new java.io.ByteArrayInputStream(
                        Files.readString(projectFile).stripLeading().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        projectDoc.getDocumentElement().normalize();

        String projectName = projectDoc.getElementsByTagName("name")
                .item(0).getTextContent().trim();

        boolean webProject = false;
        NodeList natures = projectDoc.getElementsByTagName("nature");
        for (int i = 0; i < natures.getLength(); i++) {
            if (WTP_NATURE.equals(natures.item(i).getTextContent().trim())) {
                webProject = true;
                break;
            }
        }

        // ── .classpath ────────────────────────────────────────────
        Path classpathFile = inputDir.resolve(".classpath");
        Document cpDoc = builder.parse(classpathFile.toFile());
        cpDoc.getDocumentElement().normalize();

        List<String> sourceFolders = new ArrayList<>();
        List<ClasspathEntry> libEntries = new ArrayList<>();
        String outputFolder = null;

        NodeList entries = cpDoc.getElementsByTagName("classpathentry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String kind = entry.getAttribute("kind");
            String path = entry.getAttribute("path");

            switch (kind) {
                case "src" -> {
                    if (!isOptional(entry)) {
                        sourceFolders.add(path);
                    }
                }
                case "output" -> outputFolder = path;
                case "lib" -> {
                    boolean exported = "true".equals(entry.getAttribute("exported"));
                    boolean test = hasAttribute(entry, "test", "true");
                    libEntries.add(new ClasspathEntry(kind, path, exported, test));
                }
                case "con" -> {
                    if ("org.eclipse.jst.j2ee.internal.web.container".equals(path)) {
                        libEntries.add(new ClasspathEntry(kind, path, false, false));
                    }
                }
            }
        }

        // ── .settings/org.eclipse.jdt.core.prefs ─────────────────
        // source/target を prefs から取得し、見つからない場合は .classpath の con エントリの値を使う
        String[] versions = parseJdtCorePrefs(inputDir);
        JavaVersion javaSourceVersion = JavaVersion.of(versions[0]);
        JavaVersion javaTargetVersion = JavaVersion.of(versions[1]);

        // ── WTP: .settings/org.eclipse.wst.common.component ──────
        String webContentRoot = null;
        String webVersion = null;
        if (webProject) {
            webContentRoot = parseWebContentRoot(inputDir, builder);
            webVersion = parseWebVersion(inputDir, builder);
        }

        // ── ClasspathEntry リスト → JarFile リスト ─────────────────
        List<JarFile> jarFiles = buildJarFiles(libEntries, webContentRoot, inputDir);

        return new EclipseProject(
                projectName,
                webProject,
                List.copyOf(sourceFolders),
                outputFolder,
                List.copyOf(jarFiles),
                webContentRoot,
                javaSourceVersion,
                javaTargetVersion,
                webVersion
        );
    }

    /**
     * `.settings/org.eclipse.jdt.core.prefs` を解析して [sourceVersion, targetVersion] を返す。
     * ファイルが存在しない、またはキーが見つからない場合は fallback 値を使用する。
     */
    private static String[] parseJdtCorePrefs(Path inputDir) {
        Path prefsFile = inputDir.resolve(".settings")
                .resolve("org.eclipse.jdt.core.prefs");

        if (Files.exists(prefsFile)) {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(prefsFile)) {
                props.load(reader);
                String source = normalizeJavaVersion(
                        props.getProperty("org.eclipse.jdt.core.compiler.source"));
                String target = normalizeJavaVersion(
                        props.getProperty("org.eclipse.jdt.core.compiler.codegen.targetPlatform"));
                // 両方取得できた場合はそのまま使う。片方のみの場合はもう一方も同じ値にする
                if (source != null && target != null) {
                    return new String[]{source, target};
                } else if (source != null) {
                    return new String[]{source, source};
                } else if (target != null) {
                    return new String[]{target, target};
                }
            } catch (IOException e) {
                // 読み込み失敗時は fallback を使用
            }
        }

        return new String[]{DEFAULT_JAVA_VERSION, DEFAULT_JAVA_VERSION};
    }

    /**
     * "1.8" → "1.8"、"17" → "17" のようにそのまま返す。
     * null や空文字の場合は null を返す。
     */
    private static String normalizeJavaVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * `.settings/org.eclipse.wst.common.component` を解析して Webコンテンツルートを返す。
     * ファイルが存在しない場合は null を返す。
     * 先頭の "/" は除去した相対パスとして返す。
     */
    private static String parseWebContentRoot(Path inputDir, DocumentBuilder builder) {
        Path componentFile = inputDir.resolve(".settings")
                .resolve("org.eclipse.wst.common.component");

        if (!Files.exists(componentFile)) {
            return null;
        }

        try {
            Document doc = builder.parse(componentFile.toFile());
            doc.getDocumentElement().normalize();

            NodeList resources = doc.getElementsByTagName("wb-resource");
            for (int i = 0; i < resources.getLength(); i++) {
                Element res = (Element) resources.item(i);
                if ("defaultRootSource".equals(res.getAttribute("tag"))) {
                    String sourcePath = res.getAttribute("source-path");
                    // 先頭の "/" を除去して相対パスに正規化
                    if (sourcePath.startsWith("/")) {
                        sourcePath = sourcePath.substring(1);
                    }
                    return sourcePath;
                }
            }
        } catch (Exception e) {
            // ファイルが壊れている場合は null を返す
        }

        return null;
    }

    /**
     * {@code ClasspathEntry} のリストから {@link JarFile} のリストを構築する。
     *
     * <ul>
     *   <li>{@code path} が {@code .jar} で終わるエントリ: {@code test} 属性・{@code exported}
     *       属性をそのまま {@link JarFile} に引き継ぐ。スコープの決定は
     *       {@link DependencyResolver} 内で行う。</li>
     *   <li>{@code path} が {@code org.eclipse.jst.j2ee.internal.web.container} のエントリ:
     *       {@code webContentRoot} が非 null の場合、対応する {@code WEB-INF/lib} ディレクトリを
     *       走査して各 {@code .jar} ファイルを {@code exported=true} として追加する。</li>
     *   <li>それ以外は無視する。</li>
     * </ul>
     *
     * @param libEntries     {@code .classpath} パースで収集した {@link ClasspathEntry} のリスト
     * @param webContentRoot Webコンテンツルートの相対パス（null の場合は web container エントリを無視）
     * @param inputDir       Eclipseプロジェクトのルートディレクトリ
     * @return 構築した {@link JarFile} のリスト
     */
    private static List<JarFile> buildJarFiles(List<ClasspathEntry> libEntries,
                                               String webContentRoot, Path inputDir) {
        List<JarFile> jarFiles = new ArrayList<>();
        for (ClasspathEntry e : libEntries) {
            if (e.path().endsWith(".jar")) {
                jarFiles.add(new JarFile(e.path(), e.test(), e.exported()));
            } else if ("org.eclipse.jst.j2ee.internal.web.container".equals(e.path())
                    && webContentRoot != null) {
                Path webInfLib = inputDir.resolve(webContentRoot).resolve("WEB-INF/lib");
                if (Files.isDirectory(webInfLib)) {
                    try (var stream = Files.list(webInfLib)) {
                        // WEB-INF/lib 配下のJARはコンテナが提供するものではなくWARに同梱されるため
                        // exported=true として扱う
                        stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                              .sorted()
                              .forEach(p -> jarFiles.add(
                                      new JarFile(inputDir.relativize(p).toString(), false, true)));
                    } catch (IOException ex) {
                        // 読み取り失敗時は無視
                    }
                }
            }
            // それ以外は無視
        }
        return jarFiles;
    }

    /**
     * {@code classpathentry} 要素の {@code <attributes>} 子要素内に、
     * 指定した名前と値を持つ {@code <attribute>} 要素が存在するかどうかを返す。
     *
     * @param entry 検査する {@code classpathentry} 要素
     * @param name  属性名
     * @param value 属性値
     * @return 存在する場合は {@code true}
     */
    private static boolean hasAttribute(Element entry, String name, String value) {
        NodeList attributesList = entry.getElementsByTagName("attributes");
        if (attributesList.getLength() == 0) {
            return false;
        }
        NodeList attributes = ((Element) attributesList.item(0)).getElementsByTagName("attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            if (name.equals(attr.getAttribute("name"))
                    && value.equals(attr.getAttribute("value"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code classpathentry} 要素が optional かどうかを返す。
     * {@code <attributes>} 子要素内に {@code <attribute name="optional" value="true"/>} が
     * 存在する場合に {@code true} を返す。
     */
    private static boolean isOptional(Element entry) {
        return hasAttribute(entry, "optional", "true");
    }

    /**
     * `.settings/org.eclipse.wst.common.project.facet.core.xml` を解析して
     * {@code jst.web} ファセットの version を返す。
     * ファイルが存在しない、またはファセットが見つからない場合は null を返す。
     */
    private static String parseWebVersion(Path inputDir, DocumentBuilder builder) {
        Path facetFile = inputDir.resolve(".settings")
                .resolve("org.eclipse.wst.common.project.facet.core.xml");

        if (!Files.exists(facetFile)) {
            return null;
        }

        try {
            Document doc = builder.parse(facetFile.toFile());
            doc.getDocumentElement().normalize();

            NodeList installed = doc.getElementsByTagName("installed");
            for (int i = 0; i < installed.getLength(); i++) {
                Element el = (Element) installed.item(i);
                if ("jst.web".equals(el.getAttribute("facet"))) {
                    return el.getAttribute("version");
                }
            }
        } catch (Exception e) {
            // ファイルが壊れている場合は null を返す
        }

        return null;
    }
}
