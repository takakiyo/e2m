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

    private EclipseProject webProject() {
        return new EclipseProject("MyWebApp", true, List.of("src"), "build/classes",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("1.8"), "3.0");
    }

    private Document parsePom() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(tempDir.resolve("pom.xml").toFile());
    }

    @Test
    void javaProject_noPackagingElement() throws Exception {
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "1.0", null, tempDir);
        Document doc = parsePom();
        NodeList packaging = doc.getElementsByTagName("packaging");
        assertEquals(0, packaging.getLength(), "<packaging> 要素はJavaプロジェクトでは省略される");
    }

    @Test
    void javaProject_coordinates() throws Exception {
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "2.0", null, tempDir);
        Document doc = parsePom();
        assertEquals("com.example", doc.getElementsByTagName("groupId").item(0).getTextContent());
        assertEquals("myapp", doc.getElementsByTagName("artifactId").item(0).getTextContent());
        assertEquals("2.0", doc.getElementsByTagName("version").item(0).getTextContent());
    }

    @Test
    void webProject_hasWarPackaging() throws Exception {
        PomGenerator.generate(webProject(), List.of(), "com.example", "webapp", "1.0", null, tempDir);
        Document doc = parsePom();
        NodeList packaging = doc.getElementsByTagName("packaging");
        assertEquals(1, packaging.getLength());
        assertEquals("war", packaging.item(0).getTextContent());
    }

    @Test
    void javaSourceVersion_reflectedInProperties() throws Exception {
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "1.0", null, tempDir);
        Document doc = parsePom();
        NodeList props = doc.getElementsByTagName("maven.compiler.source");
        assertEquals(1, props.getLength());
        assertEquals("17", props.item(0).getTextContent());
    }

    @Test
    void javaTargetVersion_reflectedInProperties() throws Exception {
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "1.0", null, tempDir);
        Document doc = parsePom();
        NodeList props = doc.getElementsByTagName("maven.compiler.target");
        assertEquals(1, props.getLength());
        assertEquals("17", props.item(0).getTextContent());
    }

    @Test
    void sourceAndTargetCanDiffer() throws Exception {
        PomGenerator.generate(webProject(), List.of(), "com.example", "webapp", "1.0", null, tempDir);
        Document doc = parsePom();
        assertEquals("11", doc.getElementsByTagName("maven.compiler.source").item(0).getTextContent());
        assertEquals("1.8", doc.getElementsByTagName("maven.compiler.target").item(0).getTextContent());
    }

    @Test
    void dependency_centralFound_noScope() throws Exception {
        MavenDependency dep = new MavenDependency("org.apache.commons", "commons-lang3", "3.12.0", null, null);
        PomGenerator.generate(javaProject(), List.of(dep), "com.example", "myapp", "1.0", null, tempDir);
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
        PomGenerator.generate(javaProject(), List.of(dep), "com.example", "myapp", "1.0", null, tempDir);
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
        PomGenerator.generate(webProject(), List.of(), "com.example", "webapp", "1.0", null, tempDir);
    }

    @Test
    void webProject_hasJavaEEDependencyWithProvidedScope() throws Exception {
        PomGenerator.generate(webProject(), List.of(), "com.example", "webapp", "1.0", null, tempDir);
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
        PomGenerator.generate(webProject(), List.of(), "com.example", "webapp", "1.0", null, tempDir);
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
        PomGenerator.generate(webProject(), List.of(), "com.example", "webapp", "1.0", null, tempDir);
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
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "1.0", null, tempDir);
        Document doc = parsePom();

        NodeList artifactIds = doc.getElementsByTagName("artifactId");
        for (int i = 0; i < artifactIds.getLength(); i++) {
            assertNotEquals("maven-war-plugin", artifactIds.item(i).getTextContent(),
                    "Java プロジェクトには maven-war-plugin は出力されない");
        }
    }

    @Test
    void javaTargetOverride_appliedToProperty() throws Exception {
        PomGenerator.generate(javaProject(), List.of(), "com.example", "myapp", "1.0", JavaVersion.of("21"), tempDir);
        Document doc = parsePom();
        // source は Eclipse プロジェクトの値 (17) のまま
        assertEquals("17", doc.getElementsByTagName("maven.compiler.source").item(0).getTextContent());
        // target はオーバーライドした値 (21)
        assertEquals("21", doc.getElementsByTagName("maven.compiler.target").item(0).getTextContent());
    }

}
