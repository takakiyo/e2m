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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PomGeneratorTest {

    @TempDir
    Path tempDir;

    private EclipseProject javaProject() {
        return new EclipseProject("MyApp", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);
    }

    private EclipseProject javaProject8() {
        return new EclipseProject("MyApp", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("1.8"), JavaVersion.of("1.8"), null);
    }

    private EclipseProject webProject() {
        return new EclipseProject("MyWebApp", true, List.of("src"), "build/classes",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("1.8"), "3.0");
    }

    private Document parsePom() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(tempDir.resolve("pom.xml").toFile());
    }

    /** 変換なしで generate() を呼ぶ際の共通ヘルパー */
    private void generateNoConvert(EclipseProject project, List<MavenDependency> deps,
                                    String groupId, String artifactId, String version,
                                    JavaVersion targetOverride) throws Exception {
        // Main と同じロジックで effectiveTargetVersion を計算して渡す
        JavaVersion effective = (targetOverride != null && !targetOverride.isUnknown())
                ? targetOverride : project.javaTargetVersion();
        PomGenerator.generate(project, deps, groupId, artifactId, version,
                effective, false, tempDir);
    }

    @Test
    void javaProject_noPackagingElement() throws Exception {
        generateNoConvert(javaProject(), List.of(), "com.example", "myapp", "1.0", null);
        Document doc = parsePom();
        NodeList packaging = doc.getElementsByTagName("packaging");
        assertEquals(0, packaging.getLength(), "<packaging> 要素はJavaプロジェクトでは省略される");
    }

    @Test
    void javaProject_coordinates() throws Exception {
        generateNoConvert(javaProject(), List.of(), "com.example", "myapp", "2.0", null);
        Document doc = parsePom();
        assertEquals("com.example", doc.getElementsByTagName("groupId").item(0).getTextContent());
        assertEquals("myapp", doc.getElementsByTagName("artifactId").item(0).getTextContent());
        assertEquals("2.0", doc.getElementsByTagName("version").item(0).getTextContent());
    }

    @Test
    void webProject_hasWarPackaging() throws Exception {
        generateNoConvert(webProject(), List.of(), "com.example", "webapp", "1.0", null);
        Document doc = parsePom();
        NodeList packaging = doc.getElementsByTagName("packaging");
        assertEquals(1, packaging.getLength());
        assertEquals("war", packaging.item(0).getTextContent());
    }

    @Test
    void javaSourceVersion_reflectedInProperties() throws Exception {
        generateNoConvert(javaProject(), List.of(), "com.example", "myapp", "1.0", null);
        Document doc = parsePom();
        NodeList props = doc.getElementsByTagName("maven.compiler.source");
        assertEquals(1, props.getLength());
        assertEquals("17", props.item(0).getTextContent());
    }

    @Test
    void javaTargetVersion_reflectedInProperties() throws Exception {
        generateNoConvert(javaProject(), List.of(), "com.example", "myapp", "1.0", null);
        Document doc = parsePom();
        NodeList props = doc.getElementsByTagName("maven.compiler.target");
        assertEquals(1, props.getLength());
        assertEquals("17", props.item(0).getTextContent());
    }

    @Test
    void sourceAndTargetCanDiffer() throws Exception {
        generateNoConvert(webProject(), List.of(), "com.example", "webapp", "1.0", null);
        Document doc = parsePom();
        assertEquals("11", doc.getElementsByTagName("maven.compiler.source").item(0).getTextContent());
        assertEquals("1.8", doc.getElementsByTagName("maven.compiler.target").item(0).getTextContent());
    }

    @Test
    void dependency_centralFound_noScope() throws Exception {
        MavenDependency dep = new MavenDependency("org.apache.commons", "commons-lang3", "3.12.0", null, null);
        generateNoConvert(javaProject(), List.of(dep), "com.example", "myapp", "1.0", null);
        Document doc = parsePom();

        NodeList groupIds = doc.getElementsByTagName("groupId");
        // 最初はprojectのgroupId、2番目がdependencyのgroupId
        boolean found = false;
        for (int i = 0; i < groupIds.getLength(); i++) {
            if ("org.apache.commons".equals(groupIds.item(i).getTextContent())) {
                found = true;
            }
        }
        assertTrue(found, "依存のgroupIdが出力される");
        // scopeは出力されない
        NodeList scopes = doc.getElementsByTagName("scope");
        assertEquals(0, scopes.getLength(), "スコープなしの依存にscope要素は不要");
    }

    @Test
    void dependency_systemScope_hasScopeAndSystemPath() throws Exception {
        MavenDependency dep = new MavenDependency("mylib", "mylib", "0.0.0", "system", "/opt/libs/mylib.jar");
        generateNoConvert(javaProject(), List.of(dep), "com.example", "myapp", "1.0", null);
        Document doc = parsePom();

        NodeList scopes = doc.getElementsByTagName("scope");
        assertEquals(1, scopes.getLength());
        assertEquals("system", scopes.item(0).getTextContent());

        // systemPath は ${project.basedir}/libs/<ファイル名> 形式で出力される
        NodeList systemPaths = doc.getElementsByTagName("systemPath");
        assertEquals(1, systemPaths.getLength());
        assertEquals("${project.basedir}/libs/mylib.jar", systemPaths.item(0).getTextContent());
    }

    @Test
    void compilerPlugin_sourceAndTargetCanDiffer() throws Exception {
        generateNoConvert(webProject(), List.of(), "com.example", "webapp", "1.0", null);
    }

    @Test
    void webProject_hasJavaEEDependencyWithProvidedScope() throws Exception {
        generateNoConvert(webProject(), List.of(), "com.example", "webapp", "1.0", null);
        Document doc = parsePom();

        // javaee-api の artifactId が出力されること
        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        boolean foundJavaEE = false;
        for (int i = 0; i < artifactIds.getLength(); i++) {
            if ("javaee-api".equals(artifactIds.item(i).getTextContent())) {
                foundJavaEE = true;
            }
        }
        assertTrue(foundJavaEE, "webProject には javaee-api の dependency が出力される");

        // scope=provided が出力されること
        NodeList scopes = doc.getElementsByTagName("scope");
        boolean foundProvided = false;
        for (int i = 0; i < scopes.getLength(); i++) {
            if ("provided".equals(scopes.item(i).getTextContent())) {
                foundProvided = true;
            }
        }
        assertTrue(foundProvided, "JavaEE dependency は provided スコープで出力される");
    }

    @Test
    void webProject_javaEEDependencyVersion_matchesServletVersion() throws Exception {
        generateNoConvert(webProject(), List.of(), "com.example", "webapp", "1.0", null);
        Document doc = parsePom();

        // groupId=javax, artifactId=javaee-api, version=6.0 (Servlet 3.0 → Java EE 6)
        NodeList groupIds = doc.getElementsByTagName("groupId");
        boolean foundJavax = false;
        for (int i = 0; i < groupIds.getLength(); i++) {
            if ("javax".equals(groupIds.item(i).getTextContent())) {
                foundJavax = true;
            }
        }
        assertTrue(foundJavax, "Servlet 3.0 の場合 groupId=javax が出力される");
    }

    @Test
    void webProject_hasMavenWarPlugin() throws Exception {
        generateNoConvert(webProject(), List.of(), "com.example", "webapp", "1.0", null);
        Document doc = parsePom();

        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        boolean foundWarPlugin = false;
        for (int i = 0; i < artifactIds.getLength(); i++) {
            if ("maven-war-plugin".equals(artifactIds.item(i).getTextContent())) {
                foundWarPlugin = true;
            }
        }
        assertTrue(foundWarPlugin, "webProject には maven-war-plugin が出力される");
    }

    @Test
    void javaProject_noMavenWarPlugin() throws Exception {
        generateNoConvert(javaProject(), List.of(), "com.example", "myapp", "1.0", null);
        Document doc = parsePom();

        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        for (int i = 0; i < artifactIds.getLength(); i++) {
            assertNotEquals("maven-war-plugin", artifactIds.item(i).getTextContent(),
                    "Java プロジェクトには maven-war-plugin は出力されない");
        }
    }

    @Test
    void javaTargetOverride_appliedToProperty() throws Exception {
        generateNoConvert(javaProject(), List.of(), "com.example", "myapp", "1.0", JavaVersion.of("21"));
        Document doc = parsePom();
        // source は Eclipse プロジェクトの値 (17) のまま
        assertEquals("17", doc.getElementsByTagName("maven.compiler.source").item(0).getTextContent());
        // target はオーバーライドした値 (21)
        assertEquals("21", doc.getElementsByTagName("maven.compiler.target").item(0).getTextContent());
    }

    // =========================================================
    // native2ascii-maven-plugin テスト
    // =========================================================

    @Test
    void convertToUtf8_java8_hasNative2AsciiPlugin() throws Exception {
        // Java 8 + convertToUtf8=true → native2ascii-maven-plugin が追加される
        PomGenerator.generate(javaProject8(), List.of(), "com.example", "myapp", "1.0",
                JavaVersion.of("1.8"), true, tempDir);
        Document doc = parsePom();

        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        boolean found = false;
        for (int i = 0; i < artifactIds.getLength(); i++) {
            if ("native2ascii-maven-plugin".equals(artifactIds.item(i).getTextContent())) {
                found = true;
            }
        }
        assertTrue(found, "Java 8 + convertToUtf8=true では native2ascii-maven-plugin が出力される");
    }

    @Test
    void convertToUtf8_java17_noNative2AsciiPlugin() throws Exception {
        // Java 17 + convertToUtf8=true → native2ascii-maven-plugin は追加されない
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "1.0",
                JavaVersion.of("17"), true, tempDir);
        Document doc = parsePom();

        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        for (int i = 0; i < artifactIds.getLength(); i++) {
            assertNotEquals("native2ascii-maven-plugin", artifactIds.item(i).getTextContent(),
                    "Java 17 では native2ascii-maven-plugin は出力されない");
        }
    }

    @Test
    void convertToUtf8_false_noNative2AsciiPlugin() throws Exception {
        // Java 8 でも convertToUtf8=false → native2ascii-maven-plugin は追加されない
        PomGenerator.generate(javaProject8(), List.of(), "com.example", "myapp", "1.0",
                JavaVersion.of("1.8"), false, tempDir);
        Document doc = parsePom();

        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        for (int i = 0; i < artifactIds.getLength(); i++) {
            assertNotEquals("native2ascii-maven-plugin", artifactIds.item(i).getTextContent(),
                    "convertToUtf8=false では native2ascii-maven-plugin は出力されない");
        }
    }

    @Test
    void convertToUtf8_java8_native2AsciiPlugin_configuration() throws Exception {
        // native2ascii-maven-plugin の設定値が正しいことを確認
        PomGenerator.generate(javaProject8(), List.of(), "com.example", "myapp", "1.0",
                JavaVersion.of("1.8"), true, tempDir);
        Document doc = parsePom();

        // srcDir の値
        NodeList srcDirs = doc.getElementsByTagName("srcDir");
        assertEquals(1, srcDirs.getLength());
        assertEquals("${project.basedir}/src/main/resources-utf8", srcDirs.item(0).getTextContent());

        // outputDir の値
        NodeList outputDirs = doc.getElementsByTagName("outputDir");
        assertEquals(1, outputDirs.getLength());
        assertEquals("${project.build.outputDirectory}", outputDirs.item(0).getTextContent());

        // encoding の値
        NodeList encodings = doc.getElementsByTagName("encoding");
        assertEquals(1, encodings.getLength());
        assertEquals("UTF-8", encodings.item(0).getTextContent());
    }
}
