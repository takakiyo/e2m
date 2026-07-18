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

        ProjectCopier.copy(project, List.of(), inputDir, outputDir);

        Path expected = outputDir.resolve("src/main/java/com/example/Foo.java");
        assertTrue(Files.exists(expected), "Javaファイルが src/main/java に配置される");
    }

    @Test
    void copy_resourceFileGoesToSrcMainResources() throws Exception {
        createFile(inputDir, "src/config.properties", "key=value");

        EclipseProject project = new EclipseProject("App", false, List.of("src"), "bin",
                List.of(), null, JavaVersion.of("11"), JavaVersion.of("11"), null);

        ProjectCopier.copy(project, List.of(), inputDir, outputDir);

        Path expected = outputDir.resolve("src/main/resources/config.properties");
        assertTrue(Files.exists(expected), "リソースファイルが src/main/resources に配置される");
    }

    @Test
    void copy_testSourceGoesToSrcTestJava() throws Exception {
        createFile(inputDir, "test/com/example/FooTest.java", "public class FooTest {}");

        EclipseProject project = new EclipseProject("Foo", false, List.of("test"), "bin",
                List.of(), null, JavaVersion.of("17"), JavaVersion.of("17"), null);

        ProjectCopier.copy(project, List.of(), inputDir, outputDir);

        Path expected = outputDir.resolve("src/test/java/com/example/FooTest.java");
        assertTrue(Files.exists(expected), "テストJavaファイルが src/test/java に配置される");
    }

    @Test
    void copy_webContentGoesToSrcMainWebapp() throws Exception {
        createFile(inputDir, "WebContent/index.html", "<html/>");
        createFile(inputDir, "WebContent/WEB-INF/web.xml", "<web-app/>");

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "build/classes",
                List.of(), "WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");

        ProjectCopier.copy(project, List.of(), inputDir, outputDir);

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
        assertDoesNotThrow(() -> ProjectCopier.copy(project, List.of(), inputDir, outputDir));
    }

    @Test
    void copy_webContentRootWithLeadingSlash() throws Exception {
        createFile(inputDir, "WebContent/index.jsp", "<%@ page %>");

        EclipseProject project = new EclipseProject("WebApp", true, List.of("src"), "bin",
                List.of(), "/WebContent", JavaVersion.of("11"), JavaVersion.of("11"), "3.0");  // 先頭スラッシュあり

        ProjectCopier.copy(project, List.of(), inputDir, outputDir);

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

        ProjectCopier.copy(project, List.of(systemDep), inputDir, outputDir);

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
        assertDoesNotThrow(() -> ProjectCopier.copy(project, List.of(systemDep), inputDir, outputDir));
        assertFalse(Files.exists(outputDir.resolve("libs")),
                "ファイルが存在しない場合は libs/ ディレクトリも作成されない");
    }
}
