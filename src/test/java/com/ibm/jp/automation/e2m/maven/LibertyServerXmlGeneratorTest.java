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

package com.ibm.jp.automation.e2m.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ibm.jp.automation.e2m.eclipse.EclipseProject;
import com.ibm.jp.automation.e2m.spec.JavaVersion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibertyServerXmlGeneratorTest {

    @TempDir
    Path tempDir;

    /** webContextRoot あり（通常パターン） */
    private EclipseProject webProject(String servletVersion) {
        return new EclipseProject("MyWebApp", true, List.of("src"), "build/classes",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), servletVersion, "myapp");
    }

    /** webContextRoot なし、webContentRoot あり */
    private EclipseProject webProjectNoContextRoot(String servletVersion) {
        return new EclipseProject("MyWebApp", true, List.of("src"), "build/classes",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), servletVersion, null);
    }

    private String readServerXml() throws Exception {
        Path serverXmlPath = tempDir.resolve("src/main/liberty/config/server.xml");
        return Files.readString(serverXmlPath, StandardCharsets.UTF_8);
    }

    // =========================================================
    // resolveLibertyFeature() のユニットテスト
    // =========================================================

    @Test
    void feature_servlet30_mapsToJavaEE7() {
        assertEquals("javaee-7.0", LibertyServerXmlGenerator.resolveLibertyFeature("3.0"));
    }

    @Test
    void feature_servlet31_mapsToJavaEE7() {
        assertEquals("javaee-7.0", LibertyServerXmlGenerator.resolveLibertyFeature("3.1"));
    }

    @Test
    void feature_servlet40_mapsToJavaEE8() {
        assertEquals("javaee-8.0", LibertyServerXmlGenerator.resolveLibertyFeature("4.0"));
    }

    @Test
    void feature_servlet50_mapsToJakartaEE91() {
        assertEquals("jakartaee-9.1", LibertyServerXmlGenerator.resolveLibertyFeature("5.0"));
    }

    @Test
    void feature_servlet60_mapsToJakartaEE10() {
        assertEquals("jakartaee-10.0", LibertyServerXmlGenerator.resolveLibertyFeature("6.0"));
    }

    @Test
    void feature_servlet61_mapsToJakartaEE11() {
        assertEquals("jakartaee-11.0", LibertyServerXmlGenerator.resolveLibertyFeature("6.1"));
    }

    @Test
    void feature_null_mapsToJavaEE7() {
        assertEquals("javaee-7.0", LibertyServerXmlGenerator.resolveLibertyFeature(null));
    }

    @Test
    void feature_unknown_mapsToJavaEE7() {
        assertEquals("javaee-7.0", LibertyServerXmlGenerator.resolveLibertyFeature("2.5"));
    }

    // =========================================================
    // generate() のファイル出力テスト
    // =========================================================

    @Test
    void generate_createsServerXmlFile() throws Exception {
        LibertyServerXmlGenerator.generate(webProject("4.0"), "my-app", tempDir);
        Path serverXmlPath = tempDir.resolve("src/main/liberty/config/server.xml");
        assertTrue(Files.exists(serverXmlPath), "server.xml が生成される");
    }

    @Test
    void generate_featureIsWrittenToServerXml() throws Exception {
        LibertyServerXmlGenerator.generate(webProject("4.0"), "my-app", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("<feature>javaee-8.0</feature>"),
                "Servlet 4.0 → javaee-8.0 が server.xml に出力される");
    }

    @Test
    void generate_webApplicationLocationUsesArtifactId() throws Exception {
        LibertyServerXmlGenerator.generate(webProject("6.0"), "my-app", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("location=\"my-app.war\""),
                "webApplication の location が artifactId.war になる");
    }

    @Test
    void generate_webApplicationContextRootUsesWebContextRoot() throws Exception {
        // webContextRoot="myapp" → contextRoot="/myapp"（webContentRoot より優先）
        LibertyServerXmlGenerator.generate(webProject("6.0"), "my-app", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("contextRoot=\"/myapp\""),
                "webApplication の contextRoot が webContextRoot になる");
    }

    @Test
    void generate_webApplicationContextRootFallsBackToArtifactIdWhenNull() throws Exception {
        // webContextRoot=null → contextRoot="/artifactId"
        LibertyServerXmlGenerator.generate(webProjectNoContextRoot("4.0"), "my-app", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("contextRoot=\"/my-app\""),
                "webContextRoot が null の場合は contextRoot が /artifactId になる");
    }

    @Test
    void generate_httpEndpointIsPresent() throws Exception {
        LibertyServerXmlGenerator.generate(webProject("4.0"), "my-app", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("<httpEndpoint"),
                "httpEndpoint 要素が出力される");
        assertTrue(content.contains("httpPort=\"9080\""),
                "httpPort=9080 が出力される");
    }

    @Test
    void generate_jakartaEE91Feature() throws Exception {
        LibertyServerXmlGenerator.generate(webProject("5.0"), "my-app", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("<feature>jakartaee-9.1</feature>"),
                "Servlet 5.0 → jakartaee-9.1 が server.xml に出力される");
    }

    @Test
    void generate_noPlaceholderRemains() throws Exception {
        LibertyServerXmlGenerator.generate(webProject("4.0"), "sample-web", tempDir);
        String content = readServerXml();
        assertTrue(content.contains("location=\"sample-web.war\""), "location に artifactId が反映される");
        assertTrue(content.contains("contextRoot=\"/myapp\""), "contextRoot に webContextRoot が反映される");
        assertFalse(content.contains("ARTIFACT_ID"), "プレースホルダーが残らない");
        assertFalse(content.contains("LIBERTY_FEATURE"), "プレースホルダーが残らない");
        assertFalse(content.contains("CONTEXT_ROOT"), "プレースホルダーが残らない");
    }
}
