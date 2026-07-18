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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectCopierTest {

    @TempDir
    Path inputDir;

    @TempDir
    Path outputDir;

    /** テスト用のソースファイルを作成するヘルパー */
    private Path createFile(Path base, String relative, String content) throws Exception {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    /** 変換なしで copy() を呼ぶ際の共通ヘルパー */
    private void copyNoConvert(EclipseProject project, List<MavenDependency> deps) throws Exception {
        ProjectCopier.copy(project, deps, inputDir, outputDir, false, null, JavaVersion.of("17"));
    }

    @Test
    void isTestFolder_detectsTestPaths() {
        assertTrue(ProjectCopier.isTestFolder("src/test/java"));
        assertTrue(ProjectCopier.isTestFolder("test"));
        assertTrue(ProjectCopier.isTestFolder("src-test"));
        assertTrue(ProjectCopier.isTestFolder("mytest"));
        assertFalse(ProjectCopier.isTestFolder("src"));
        assertFalse(ProjectCopier.isTestFolder("src/main/java"));
    }

    @Test
    void copy_javaSourceGoesToSrcMainJava() throws Exception {
        // src/com/example/Foo.java を作成
        createFile(inputDir, "src/com/example/Foo.java", "public class Foo {}");

        EclipseProject project = new EclipseProject("Foo", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);

        copyNoConvert(project, List.of());

        Path expected = outputDir.resolve("src/main/java/com/example/Foo.java");
        assertTrue(Files.exists(expected), "Javaファイルが src/main/java に配置される");
    }

    @Test
    void copy_resourceFileGoesToSrcMainResources() throws Exception {
        createFile(inputDir, "src/config.properties", "key=value");

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("11"), JavaVersion.of("11"), null);

        copyNoConvert(project, List.of());

        Path expected = outputDir.resolve("src/main/resources/config.properties");
        assertTrue(Files.exists(expected), "リソースファイルが src/main/resources に配置される");
    }

    @Test
    void copy_testSourceGoesToSrcTestJava() throws Exception {
        createFile(inputDir, "test/com/example/FooTest.java", "public class FooTest {}");

        EclipseProject project = new EclipseProject("Foo", false, List.of("test"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);

        copyNoConvert(project, List.of());

        Path expected = outputDir.resolve("src/test/java/com/example/FooTest.java");
        assertTrue(Files.exists(expected), "テストJavaファイルが src/test/java に配置される");
    }

    @Test
    void copy_webContentGoesToSrcMainWebapp() throws Exception {
        createFile(inputDir, "WebContent/index.html", "<html/>");
        createFile(inputDir, "WebContent/WEB-INF/web.xml", "<web-app/>");

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "build/classes",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");

        copyNoConvert(project, List.of());

        assertTrue(Files.exists(outputDir.resolve("src/main/webapp/index.html")),
                "index.html が src/main/webapp に配置される");
        assertTrue(Files.exists(outputDir.resolve("src/main/webapp/WEB-INF/web.xml")),
                "web.xml が src/main/webapp/WEB-INF に配置される");
    }

    @Test
    void copy_missingSourceFolder_doesNotThrow() throws Exception {
        EclipseProject project = new EclipseProject("App", false, List.of("nonexistent"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);
        // 存在しないフォルダは警告を出してスキップ（例外は投げない）
        assertDoesNotThrow(() -> copyNoConvert(project, List.of()));
    }

    @Test
    void copy_webContentRootWithLeadingSlash() throws Exception {
        createFile(inputDir, "WebContent/index.jsp", "<%@ page %>");

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "bin",
                List.of(), "/WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");  // 先頭スラッシュあり

        copyNoConvert(project, List.of());

        assertTrue(Files.exists(outputDir.resolve("src/main/webapp/index.jsp")),
                "先頭スラッシュが除去されてWebコンテンツがコピーされる");
    }

    @Test
    void copy_systemScopeJar_copiedToLibs() throws Exception {
        // 実在するJARファイルをinputDir内に作成
        Path jarFile = createFile(inputDir, "lib/mylib.jar", "dummy");

        MavenDependency systemDep = new MavenDependency(
                "mylib", "mylib", "0.0.0", "system", jarFile.toAbsolutePath().toString());

        EclipseProject project = new EclipseProject("App", false, List.of(), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);

        copyNoConvert(project, List.of(systemDep));

        assertTrue(Files.exists(outputDir.resolve("libs/mylib.jar")),
                "system スコープの JAR が libs/ にコピーされる");
    }

    @Test
    void copy_systemScopeJar_missingFile_doesNotThrow() throws Exception {
        MavenDependency systemDep = new MavenDependency(
                "mylib", "mylib", "0.0.0", "system", "/nonexistent/path/mylib.jar");

        EclipseProject project = new EclipseProject("App", false, List.of(), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);

        // ファイルが存在しない場合はスキップ（例外は投げない）
        assertDoesNotThrow(() -> copyNoConvert(project, List.of(systemDep)));
        assertFalse(Files.exists(outputDir.resolve("libs")),
                "ファイルが存在しない場合は libs/ ディレクトリも作成されない");
    }

    // =========================================================
    // --convert-to-utf8 変換テスト
    // =========================================================

    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

    @Test
    void convertToUtf8_javaFile_reencoded() throws Exception {
        // Shift_JIS で「こんにちは」を含む .java ファイルを作成
        String content = "// \u3053\u3093\u306b\u3061\u306f\npublic class Hello {}";
        Path javaFile = inputDir.resolve("src/Hello.java");
        Files.createDirectories(javaFile.getParent());
        Files.write(javaFile, content.getBytes(SHIFT_JIS));

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("17"));

        Path dest = outputDir.resolve("src/main/java/Hello.java");
        assertTrue(Files.exists(dest));
        String result = Files.readString(dest, StandardCharsets.UTF_8);
        assertTrue(result.contains("\u3053\u3093\u306b\u3061\u306f"), "Shift_JIS の日本語が UTF-8 に変換される");
    }

    @Test
    void convertToUtf8_propertiesFile_java17_goesToResources() throws Exception {
        // Shift_JIS で「名前=テスト」を含む .properties ファイルを作成
        String content = "\u540d\u524d=\u30c6\u30b9\u30c8";
        Path propFile = inputDir.resolve("src/app.properties");
        Files.createDirectories(propFile.getParent());
        Files.write(propFile, content.getBytes(SHIFT_JIS));

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("17"));

        // Java 17 → src/main/resources/ へ
        Path dest = outputDir.resolve("src/main/resources/app.properties");
        assertTrue(Files.exists(dest), "Java 17 では src/main/resources に配置される");
        String result = Files.readString(dest, StandardCharsets.UTF_8);
        assertTrue(result.contains("\u540d\u524d"), "Shift_JIS の日本語が UTF-8 に変換される");
    }

    @Test
    void convertToUtf8_propertiesFile_java8_goesToResourcesUtf8() throws Exception {
        // Shift_JIS で「名前=テスト」を含む .properties ファイルを作成
        String content = "\u540d\u524d=\u30c6\u30b9\u30c8";
        Path propFile = inputDir.resolve("src/app.properties");
        Files.createDirectories(propFile.getParent());
        Files.write(propFile, content.getBytes(SHIFT_JIS));

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("1.8"), JavaVersion.of("1.8"), null);
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("1.8"));

        // Java 8 → src/main/resources-utf8/ へ
        Path dest = outputDir.resolve("src/main/resources-utf8/app.properties");
        assertTrue(Files.exists(dest), "Java 8 では src/main/resources-utf8 に配置される");
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/app.properties")),
                "Java 8 では src/main/resources には配置されない");
        String result = Files.readString(dest, StandardCharsets.UTF_8);
        assertTrue(result.contains("\u540d\u524d"), "Shift_JIS の日本語が UTF-8 に変換される");
    }

    @Test
    void convertToUtf8_propertiesFile_unicodeEscapeExpanded() throws Exception {
        // \u540d\u524d=value という行（Shift_JIS でそのままのテキスト）
        String content = "\\u540d\\u524d=value";
        Path propFile = inputDir.resolve("src/msg.properties");
        Files.createDirectories(propFile.getParent());
        Files.write(propFile, content.getBytes(SHIFT_JIS));

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("17"));

        Path dest = outputDir.resolve("src/main/resources/msg.properties");
        assertTrue(Files.exists(dest));
        String result = Files.readString(dest, StandardCharsets.UTF_8);
        // \u540d\u524d が「名前」に展開されていること
        assertTrue(result.contains("\u540d\u524d=value"), "Unicode エスケープが展開される");
    }

    @Test
    void convertToUtf8_jspFile_withPageEncoding_converted() throws Exception {
        // Shift_JIS で書かれた JSP（pageEncoding="Shift_JIS" 付き）
        String jspContent = "<%@ page language=\"java\" pageEncoding=\"Shift_JIS\" %>\n"
                + "<html><body>\u3053\u3093\u306b\u3061\u306f</body></html>";
        Path jspFile = inputDir.resolve("WebContent/hello.jsp");
        Files.createDirectories(jspFile.getParent());
        Files.write(jspFile, jspContent.getBytes(SHIFT_JIS));

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "bin",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("11"));

        Path dest = outputDir.resolve("src/main/webapp/hello.jsp");
        assertTrue(Files.exists(dest));
        String result = Files.readString(dest, StandardCharsets.UTF_8);
        assertTrue(result.contains("pageEncoding=\"UTF-8\""), "pageEncoding が UTF-8 に書き換えられる");
        assertTrue(result.contains("\u3053\u3093\u306b\u3061\u306f"), "日本語コンテンツが正しく変換される");
    }

    @Test
    void convertToUtf8_jspFile_withoutPageEncoding_usesSourceEncoding() throws Exception {
        // pageEncoding なし、Shift_JIS で書かれた JSP
        String jspContent = "<html><body>\u3053\u3093\u306b\u3061\u306f</body></html>";
        Path jspFile = inputDir.resolve("WebContent/hello.jsp");
        Files.createDirectories(jspFile.getParent());
        Files.write(jspFile, jspContent.getBytes(SHIFT_JIS));

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "bin",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("11"));

        Path dest = outputDir.resolve("src/main/webapp/hello.jsp");
        assertTrue(Files.exists(dest));
        String result = Files.readString(dest, StandardCharsets.UTF_8);
        assertTrue(result.contains("\u3053\u3093\u306b\u3061\u306f"), "sourceEncoding で読み込まれ UTF-8 に変換される");
    }

    @Test
    void convertToUtf8_jspFile_alreadyUtf8_binaryCopy() throws Exception {
        // pageEncoding が既に UTF-8 の JSP
        String jspContent = "<%@ page pageEncoding=\"UTF-8\" %>\n<html/>";
        Path jspFile = inputDir.resolve("WebContent/hello.jsp");
        Files.createDirectories(jspFile.getParent());
        Files.write(jspFile, jspContent.getBytes(StandardCharsets.UTF_8));

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "bin",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, true, SHIFT_JIS, JavaVersion.of("11"));

        Path dest = outputDir.resolve("src/main/webapp/hello.jsp");
        assertTrue(Files.exists(dest));
        // バイナリコピーされているのでバイト列が同一
        assertArrayEquals(Files.readAllBytes(jspFile), Files.readAllBytes(dest),
                "既に UTF-8 の JSP はバイナリコピーされる");
    }

    @Test
    void convertToUtf8_false_binaryCopy() throws Exception {
        // convertToUtf8=false のときはバイナリコピー
        byte[] sjisBytes = "\u3053\u3093\u306b\u3061\u306f".getBytes(SHIFT_JIS);
        Path javaFile = inputDir.resolve("src/Hello.java");
        Files.createDirectories(javaFile.getParent());
        Files.write(javaFile, sjisBytes);

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);
        ProjectCopier.copy(project, List.of(), inputDir, outputDir, false, null, JavaVersion.of("17"));

        Path dest = outputDir.resolve("src/main/java/Hello.java");
        assertTrue(Files.exists(dest));
        assertArrayEquals(sjisBytes, Files.readAllBytes(dest), "convertToUtf8=false ではバイナリコピーされる");
    }

    // =========================================================
    // extractJspPageEncoding 単体テスト
    // =========================================================

    @Test
    void extractJspPageEncoding_detectsShiftJIS() throws Exception {
        String jsp = "<%@ page language=\"java\" pageEncoding=\"Shift_JIS\" contentType=\"text/html\" %>";
        Path f = inputDir.resolve("test.jsp");
        Files.writeString(f, jsp, StandardCharsets.ISO_8859_1);

        Charset result = ProjectCopier.extractJspPageEncoding(f, StandardCharsets.UTF_8);
        assertNotNull(result);
        assertEquals("Shift_JIS", result.name());
    }

    @Test
    void extractJspPageEncoding_alreadyUtf8_returnsNull() throws Exception {
        String jsp = "<%@ page pageEncoding=\"UTF-8\" %>";
        Path f = inputDir.resolve("test.jsp");
        Files.writeString(f, jsp, StandardCharsets.ISO_8859_1);

        Charset result = ProjectCopier.extractJspPageEncoding(f, StandardCharsets.UTF_8);
        assertNull(result, "pageEncoding が UTF-8 の場合は null を返す");
    }

    @Test
    void extractJspPageEncoding_noPageEncoding_returnsFallback() throws Exception {
        String jsp = "<html><body>Hello</body></html>";
        Path f = inputDir.resolve("test.jsp");
        Files.writeString(f, jsp, StandardCharsets.ISO_8859_1);

        Charset result = ProjectCopier.extractJspPageEncoding(f, SHIFT_JIS);
        assertEquals(SHIFT_JIS, result, "pageEncoding がない場合は fallback を返す");
    }

    @Test
    void unescapeUnicode_expandsEscapes() {
        assertEquals("\u540d\u524d", ProjectCopier.unescapeUnicode("\\u540d\\u524d"));
        assertEquals("abc\u540d\u524dxyz", ProjectCopier.unescapeUnicode("abc\\u540d\\u524dxyz"));
        assertEquals("no escape", ProjectCopier.unescapeUnicode("no escape"));
    }
}
