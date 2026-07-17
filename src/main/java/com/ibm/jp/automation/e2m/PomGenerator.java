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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * EclipseProject情報と依存関係リストからMavenのpom.xmlを生成するクラス。
 */
public class PomGenerator {

    private static final String MAVEN_COMPILER_PLUGIN_VERSION = "3.15.0";
    private static final String MAVEN_WAR_PLUGIN_VERSION = "3.5.1";

    private PomGenerator() {}

    /**
     * pom.xmlを生成して出力ディレクトリに保存する。
     *
     * @param eclipseProject パース済みEclipseプロジェクト情報
     * @param dependencies   解決済み依存関係リスト
     * @param groupId        生成するpom.xmlのgroupId
     * @param artifactId     生成するpom.xmlのartifactId
     * @param version        生成するpom.xmlのversion
     * @param outputDir      出力ディレクトリ
     * @throws Exception XML生成またはファイル書き込みに失敗した場合
     */
    public static void generate(
            EclipseProject eclipseProject,
            List<MavenDependency> dependencies,
            String groupId,
            String artifactId,
            String version,
            Path outputDir) throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        // <project>
        Element project = doc.createElement("project");
        project.setAttribute("xmlns", "http://maven.apache.org/POM/4.0.0");
        project.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        project.setAttribute("xsi:schemaLocation",
                "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd");
        doc.appendChild(project);

        addTextElement(doc, project, "modelVersion", "4.0.0");
        addTextElement(doc, project, "groupId", groupId);
        addTextElement(doc, project, "artifactId", artifactId);
        addTextElement(doc, project, "version", version);

        if (eclipseProject.webProject()) {
            addTextElement(doc, project, "packaging", "war");
        }

        // <properties>
        String javaSourceVersion = eclipseProject.javaSourceVersion();
        String javaTargetVersion = eclipseProject.javaTargetVersion();
        Element properties = doc.createElement("properties");
        addTextElement(doc, properties, "project.build.sourceEncoding", "UTF-8");
        addTextElement(doc, properties, "maven.compiler.source", javaSourceVersion);
        addTextElement(doc, properties, "maven.compiler.target", javaTargetVersion);
        project.appendChild(properties);

        // <dependencies>
        boolean hasDependencies = !dependencies.isEmpty() || eclipseProject.webProject();
        if (hasDependencies) {
            Element deps = doc.createElement("dependencies");
            for (MavenDependency dep : dependencies) {
                Element dependency = doc.createElement("dependency");
                addTextElement(doc, dependency, "groupId", dep.groupId());
                addTextElement(doc, dependency, "artifactId", dep.artifactId());
                addTextElement(doc, dependency, "version", dep.version());
                if (dep.scope() != null && !dep.scope().isEmpty()) {
                    addTextElement(doc, dependency, "scope", dep.scope());
                }
                if (dep.systemPath() != null) {
                    // system スコープの JAR は libs/ にコピーされるため ${project.basedir}/libs/<ファイル名> で参照
                    String fileName = Path.of(dep.systemPath()).getFileName().toString();
                    addTextElement(doc, dependency, "systemPath",
                            "${project.basedir}/libs/" + fileName);
                }
                deps.appendChild(dependency);
            }
            // webProject の場合は Java EE / Jakarta EE API を provided で追加
            if (eclipseProject.webProject() && eclipseProject.webVersion() != null) {
                String webVersion = eclipseProject.webVersion();
                boolean tooOld = webVersion.startsWith("2");
                String resolvedVersion = tooOld ? "3.0" : webVersion;
                JavaEEVersion javaEE = JavaEEVersion.of(resolvedVersion);
                Element dependency = doc.createElement("dependency");
                if (tooOld) {
                    deps.appendChild(doc.createComment(
                            " WARNING: Servlet " + webVersion + " は古すぎるため対応する javaee-api dependency が存在しません。"
                            + " Java EE 6 (javaee-api:6.0) に置き換えています。マイグレーションが必要です。 "));
                }
                addTextElement(doc, dependency, "groupId", javaEE.getGroupId());
                addTextElement(doc, dependency, "artifactId", javaEE.getArtifactId());
                addTextElement(doc, dependency, "version", javaEE.getVersion());
                addTextElement(doc, dependency, "scope", "provided");
                deps.appendChild(dependency);
            }
            project.appendChild(deps);
        }

        // <build><plugins><plugin> maven-compiler-plugin
        Element build = doc.createElement("build");
        Element plugins = doc.createElement("plugins");
        Element plugin = doc.createElement("plugin");
        addTextElement(doc, plugin, "groupId", "org.apache.maven.plugins");
        addTextElement(doc, plugin, "artifactId", "maven-compiler-plugin");
        addTextElement(doc, plugin, "version", MAVEN_COMPILER_PLUGIN_VERSION);
        plugins.appendChild(plugin);
        // webProject の場合は maven-war-plugin を追加
        if (eclipseProject.webProject()) {
            Element warPlugin = doc.createElement("plugin");
            addTextElement(doc, warPlugin, "groupId", "org.apache.maven.plugins");
            addTextElement(doc, warPlugin, "artifactId", "maven-war-plugin");
            addTextElement(doc, warPlugin, "version", MAVEN_WAR_PLUGIN_VERSION);
            Element configuration = doc.createElement("configuration");
            addTextElement(doc, configuration, "failOnMissingWebXml", "false");
            warPlugin.appendChild(configuration);
            plugins.appendChild(warPlugin);
        }
        build.appendChild(plugins);
        project.appendChild(build);

        // XMLをファイルに書き出す
        Files.createDirectories(outputDir);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        Path pomPath = outputDir.resolve("pom.xml");
        transformer.transform(new DOMSource(doc), new StreamResult(pomPath.toFile()));

        System.out.println("Generated: " + pomPath);
    }

    private static void addTextElement(Document doc, Element parent, String tagName, String text) {
        Element el = doc.createElement(tagName);
        el.appendChild(doc.createTextNode(text));
        parent.appendChild(el);
    }
}
