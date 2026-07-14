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

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EclipseProjectParser} のユニットテスト。
 */
class EclipseProjectParserTest {

    /** テストリソースのルートパスを取得するヘルパー。 */
    private Path resourceDir(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource(name);
        assertNotNull(url, "テストリソースが見つかりません: " + name);
        return Paths.get(url.toURI());
    }

    // ─────────────────────────────────────────────────────────────
    // 通常の Java プロジェクト
    // ─────────────────────────────────────────────────────────────

    @Test
    void javaProject_projectName() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertEquals("SampleJavaProject", project.projectName());
    }

    @Test
    void javaProject_notWebProject() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertFalse(project.webProject(), "通常の Java プロジェクトは webProject=false であるべき");
    }

    @Test
    void javaProject_sourceFolders() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertEquals(2, project.sourceFolders().size());
        assertTrue(project.sourceFolders().contains("src"));
        assertTrue(project.sourceFolders().contains("test"));
    }

    @Test
    void javaProject_outputFolder() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertEquals("bin", project.outputFolder());
    }

    @Test
    void javaProject_jarPaths() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertEquals(2, project.jarPaths().size());
        assertTrue(project.jarPaths().contains("lib/commons-lang3-3.12.0.jar"));
        assertTrue(project.jarPaths().contains("lib/slf4j-api-2.0.0.jar"));
    }

    @Test
    void javaProject_javaVersion() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertEquals("17", project.javaVersion());
    }

    @Test
    void javaProject_webContentRootIsNull() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-java-project"));
        assertNull(project.webContentRoot(), "通常の Java プロジェクトは webContentRoot=null であるべき");
    }

    // ─────────────────────────────────────────────────────────────
    // WTP Web プロジェクト
    // ─────────────────────────────────────────────────────────────

    @Test
    void webProject_projectName() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-web-project"));
        assertEquals("SampleWebProject", project.projectName());
    }

    @Test
    void webProject_isWebProject() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-web-project"));
        assertTrue(project.webProject(), "WTP プロジェクトは webProject=true であるべき");
    }

    @Test
    void webProject_sourceFolders() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-web-project"));
        assertEquals(1, project.sourceFolders().size());
        assertTrue(project.sourceFolders().contains("src"));
    }

    @Test
    void webProject_jarPaths() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-web-project"));
        assertEquals(1, project.jarPaths().size());
        assertTrue(project.jarPaths().contains("WebContent/WEB-INF/lib/commons-io-2.11.0.jar"));
    }

    @Test
    void webProject_javaVersion() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-web-project"));
        assertEquals("11", project.javaVersion());
    }

    @Test
    void webProject_webContentRoot() throws Exception {
        EclipseProject project = EclipseProjectParser.parse(resourceDir("sample-web-project"));
        // 先頭の "/" が除去された相対パスになっているべき
        assertEquals("WebContent", project.webContentRoot());
    }
}
