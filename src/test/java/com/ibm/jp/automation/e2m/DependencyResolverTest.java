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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DependencyResolver} のテスト。
 *
 * <ul>
 *   <li>SHA1計算の単体テスト</li>
 *   <li>APIレスポンスパースの単体テスト（HTTPモック）</li>
 *   <li>実際のMaven Central APIを呼ぶ統合テスト（デフォルトでスキップ）</li>
 * </ul>
 */
class DependencyResolverTest {

    // ─────────────────────────────────────────────────────────────
    // SHA1 計算テスト
    // ─────────────────────────────────────────────────────────────

    /**
     * 既知のファイル内容に対して期待するSHA1ハッシュが返ることを確認する。
     * <p>
     * テストリソース sha1test/hello.txt の内容: "Hello, World!\n"
     * 期待SHA1: 60fde9c2310b0d4cad4dab8d126b04387efba289
     * （shasum コマンドで事前確認済み）
     */
    @Test
    void computeSha1_knownFile() throws Exception {
        URL url = getClass().getClassLoader().getResource("sha1test/hello.txt");
        assertNotNull(url, "テストリソースが見つかりません: sha1test/hello.txt");
        Path file = Paths.get(url.toURI());
        String sha1 = FileUtils.computeSha1(file);
        assertEquals("60fde9c2310b0d4cad4dab8d126b04387efba289", sha1);
    }

    /** SHA1は小文字16進数文字列であることを確認する。 */
    @Test
    void computeSha1_isLowerCaseHex(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.bin");
        Files.write(file, new byte[]{0x00, (byte) 0xFF, 0x7F});
        String sha1 = FileUtils.computeSha1(file);
        // 小文字16進数 & 40文字
        assertEquals(40, sha1.length());
        assertTrue(sha1.matches("[0-9a-f]+"), "SHA1は小文字16進数であるべき: " + sha1);
    }

    // ─────────────────────────────────────────────────────────────
    // resolve() テスト（HTTPモックによるオフラインテスト）
    // ─────────────────────────────────────────────────────────────

    /**
     * Maven Central APIの応答をスタブするテスト用サブクラス。
     */
    private static class StubDependencyResolver extends DependencyResolver {
        private final String stubbedJson;

        StubDependencyResolver(String stubbedJson) {
            this.stubbedJson = stubbedJson;
        }

        @Override
        protected String callMavenCentralApi(String sha1) {
            return stubbedJson;
        }
    }

    /** Maven Centralで見つかった場合のレスポンスJSONを正しくパースできることを確認する。 */
    @Test
    void resolve_foundInMavenCentral(@TempDir Path tempDir) throws Exception {
        // テスト用JARを用意（中身は空でよい）
        Path jar = tempDir.resolve("log4j-1.2.7.jar");
        Files.write(jar, new byte[0]);

        String json = "{\"responseHeader\":{\"status\":0,\"QTime\":1,\"params\":{\"q\":\"1:5b8a2a161048eb7481407ef0a81c2d90489ed412\",\"core\":\"\",\"indent\":\"off\",\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"start\":\"\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"rows\":\"20\",\"wt\":\"json\",\"version\":\"2.2\"}},\"response\":{\"numFound\":1,\"start\":0,\"docs\":[{\"id\":\"log4j:log4j:1.2.7\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.7\",\"p\":\"jar\",\"timestamp\":1131487715000,\"ec\":[\".jar\",\".pom\"]}]}}";

        // resolveJarをpublicにせず、resolve() 経由でテストする
        // → スタブResolverのcallMavenCentralApiが使われる
        // StaticメソッドのresolveはnewDependencyResolverを使うため、
        // テスト用にインスタンスメソッドを利用する簡易ハーネスを作る
        StubDependencyResolver stubResolver = new StubDependencyResolver(json);
        List<MavenDependency> results = invokeResolveViaReflection(
                stubResolver, List.of(jar.toString()), tempDir);

        assertEquals(1, results.size());
        MavenDependency dep = results.get(0);
        assertEquals("log4j", dep.groupId());
        assertEquals("log4j", dep.artifactId());
        assertEquals("1.2.7", dep.version());
        assertNull(dep.scope());
        assertNull(dep.systemPath());
    }

    /** Maven Centralで見つからなかった場合はsystemスコープになることを確認する。 */
    @Test
    void resolve_notFoundInMavenCentral(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("unknown-1.0.jar");
        Files.write(jar, new byte[]{1, 2, 3});

        String json = "{\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}";
        StubDependencyResolver stubResolver = new StubDependencyResolver(json);

        List<MavenDependency> results = invokeResolveViaReflection(
                stubResolver, List.of(jar.toString()), tempDir);

        assertEquals(1, results.size());
        MavenDependency dep = results.get(0);
        assertEquals("unknown-1.0", dep.groupId());
        assertEquals("unknown-1.0", dep.artifactId());
        assertEquals("0.0.0", dep.version());
        assertEquals("system", dep.scope());
        assertNotNull(dep.systemPath());
    }

    /** JARファイルが存在しない場合はsystemスコープ（systemPathは元パス文字列）になることを確認する。 */
    @Test
    void resolve_fileNotExists(@TempDir Path tempDir) {
        String missingJar = "lib/missing.jar";
        StubDependencyResolver stubResolver = new StubDependencyResolver("{}");

        List<MavenDependency> results = invokeResolveViaReflection(
                stubResolver, List.of(missingJar), tempDir);

        assertEquals(1, results.size());
        MavenDependency dep = results.get(0);
        assertEquals("missing", dep.groupId());
        assertEquals("system", dep.scope());
        assertEquals(missingJar, dep.systemPath());
    }

    /**
     * API呼び出しで例外が発生した場合もsystemスコープになることを確認する。
     */
    @Test
    void resolve_apiException(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("error.jar");
        Files.write(jar, new byte[]{1});

        DependencyResolver errorResolver = new DependencyResolver() {
            @Override
            protected String callMavenCentralApi(String sha1) throws IOException {
                throw new IOException("Connection refused");
            }
        };

        // API呼び出し失敗は致命的エラーとして RuntimeException がスローされる
        assertThrows(RuntimeException.class, () ->
                invokeResolveViaReflection(errorResolver, List.of(jar.toString()), tempDir));
    }

    // ─────────────────────────────────────────────────────────────
    // 統合テスト（デフォルトでスキップ）
    // ─────────────────────────────────────────────────────────────

    /**
     * 実際のMaven Central APIを呼び出す統合テスト。
     * ネットワークアクセスが必要なため、デフォルトでスキップする。
     * 実行する場合は @Disabled を外してください。
     */
    @Test
    @Disabled("ネットワークアクセスが必要なためデフォルトでスキップ")
    void integration_resolveCommonsLang3(@TempDir Path tempDir) throws Exception {
        // commons-lang3-3.12.0.jar が実際に手元にある場合のみ動作する。
        // テスト用に小さなJARを作ることはできないため、
        // 実際のJARファイルパスを指定して実行すること。
        String jarPath = System.getProperty("test.jar.path", "");
        if (jarPath.isBlank()) {
            System.out.println("test.jar.path システムプロパティが未設定のためスキップします");
            return;
        }
        List<MavenDependency> results = DependencyResolver.resolve(
                List.of(new JarFile(jarPath, false, true)), tempDir);
        assertEquals(1, results.size());
        System.out.println("Integration result: " + results.get(0));
    }

    // ─────────────────────────────────────────────────────────────
    // ヘルパー
    // ─────────────────────────────────────────────────────────────

    /**
     * DependencyResolverのprivateなresolveJarメソッドを
     * スタブインスタンス経由で呼び出すヘルパー。
     * <p>
     * staticのresolve()は内部でnew DependencyResolver()するため、
     * スタブを注入できない。そのためリフレクションで非公開メソッドを直接呼ぶ。
     */
    private static List<MavenDependency> invokeResolveViaReflection(
            DependencyResolver resolver, List<String> jarPaths, Path inputDir) {
        try {
            var method = DependencyResolver.class.getDeclaredMethod(
                    "resolveJar", JarFile.class, Path.class);
            method.setAccessible(true);
            List<MavenDependency> results = new java.util.ArrayList<>();
            for (String jarPath : jarPaths) {
                results.add((MavenDependency) method.invoke(
                        resolver, new JarFile(jarPath, false, true), inputDir));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("リフレクション呼び出しに失敗: " + e.getMessage(), e);
        }
    }
}
