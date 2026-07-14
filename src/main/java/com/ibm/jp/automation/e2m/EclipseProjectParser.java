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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        Document projectDoc = builder.parse(projectFile.toFile());
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
        List<String> jarPaths = new ArrayList<>();
        String outputFolder = null;
        String javaVersion = DEFAULT_JAVA_VERSION;

        NodeList entries = cpDoc.getElementsByTagName("classpathentry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String kind = entry.getAttribute("kind");
            String path = entry.getAttribute("path");

            switch (kind) {
                case "src" -> sourceFolders.add(path);
                case "output" -> outputFolder = path;
                case "lib" -> jarPaths.add(path);
                case "con" -> {
                    String version = extractJavaVersion(path);
                    if (version != null) {
                        javaVersion = version;
                    }
                }
            }
        }

        // ── WTP: .settings/org.eclipse.wst.common.component ──────
        String webContentRoot = null;
        if (webProject) {
            webContentRoot = parseWebContentRoot(inputDir, builder);
        }

        return new EclipseProject(
                projectName,
                webProject,
                List.copyOf(sourceFolders),
                outputFolder,
                List.copyOf(jarPaths),
                webContentRoot,
                javaVersion
        );
    }

    /**
     * `con` エントリの path から Java バージョン番号を抽出する。
     * 例: "...JavaSE-17" → "17", "...java-11-openjdk" → "11"
     */
    private static String extractJavaVersion(String conPath) {
        // JavaSE-XX / java-XX 形式を探す
        int idx = conPath.lastIndexOf('/');
        String segment = (idx >= 0) ? conPath.substring(idx + 1) : conPath;

        // JavaSE-XX
        if (segment.contains("JavaSE-")) {
            String after = segment.substring(segment.indexOf("JavaSE-") + "JavaSE-".length());
            String version = after.split("[^0-9.]")[0];
            if (!version.isEmpty()) {
                return version;
            }
        }

        // java-XX (例: java-17-openjdk)
        if (segment.startsWith("java-")) {
            String after = segment.substring("java-".length());
            String version = after.split("[^0-9.]")[0];
            if (!version.isEmpty()) {
                return version;
            }
        }

        return null;
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
}
