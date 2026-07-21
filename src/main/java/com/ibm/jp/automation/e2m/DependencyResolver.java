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

import org.slf4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eclipseの.classpathに記載されたJARファイルのSHA1ハッシュを計算し、
 * Maven Central REST APIで依存関係情報を検索するクラス。
 */
public class DependencyResolver {

    private static final Logger log = AppLogger.get(DependencyResolver.class);

    private static final String MAVEN_SEARCH_URL =
            "https://search.maven.org/solrsearch/select?q=1:%s&rows=1&wt=json";


    private final HttpClient httpClient;

    public DependencyResolver() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(createTrustAllSslContext())
                .build();
    }

    /**
     * SSLインスペクション環境向けに、証明書検証を行わないSSLContextを生成する。
     */
    private static SSLContext createTrustAllSslContext() {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("SSL証明書検証バイパス用SSLContextの生成に失敗しました", e);
        }
    }

    /**
     * JARが置かれたディレクトリパスのリストを受け取り、Maven依存関係リストに解決して返す。
     * 各ディレクトリに含まれる全ての {@code .jar} ファイルについて解決を行う。
     *
     * @param jarPaths Eclipseの.classpathから取得したディレクトリパス文字列のリスト
     * @param inputDir Eclipseプロジェクトのルートディレクトリ（相対パス解決の基準）
     * @return 解決済みの {@link MavenDependency} リスト
     */
    public static List<MavenDependency> resolve(List<String> jarPaths, Path inputDir) {
        DependencyResolver resolver = new DependencyResolver();
        List<MavenDependency> results = new ArrayList<>();
        for (String jarPath : jarPaths) {
            Path dir = Path.of(jarPath).isAbsolute()
                    ? Path.of(jarPath)
                    : inputDir.resolve(jarPath);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                      .sorted()
                      .forEach(p -> results.add(resolver.resolveJar(p.toString(), inputDir)));
            } catch (IOException e) {
                log.warn("  [WARN] ディレクトリの読み取りに失敗しました: {}", dir);
            }
        }
        return results;
    }

    /**
     * 1つのJARパスを解決する。
     */
    private MavenDependency resolveJar(String jarPath, Path inputDir) {
        Path path = Path.of(jarPath).isAbsolute()
                ? Path.of(jarPath)
                : inputDir.resolve(jarPath);

        String jarFileName = path.getFileName().toString();
        // 拡張子なしのベース名
        String baseName = jarFileName.endsWith(".jar")
                ? jarFileName.substring(0, jarFileName.length() - 4)
                : jarFileName;

        log.info("  Resolving: {} ...", jarFileName);

        if (!Files.exists(path)) {
            log.warn("  [WARN] JARファイルが見つかりません: {} → systemスコープとして扱います", path);
            log.info("  → not found (system scope): {}", jarFileName);
            return new MavenDependency(baseName, baseName, "0.0.0", "system", jarPath);
        }

        String sha1;
        try {
            sha1 = FileUtils.computeSha1(path);
        } catch (Exception e) {
            log.error("  [ERROR] SHA1計算に失敗しました: {} → {}", path, e.getMessage());
            log.info("  → SHA1 error (system scope): {}", jarFileName);
            return new MavenDependency(baseName, baseName, "0.0.0", "system", path.toAbsolutePath().toString());
        }

        try {
            String json = callMavenCentralApi(sha1);
            return parseResponse(json, baseName, path.toAbsolutePath().toString());
        } catch (Exception e) {
            log.error("  [ERROR] Maven Central API呼び出しに失敗しました: {} → {}", jarFileName, e.getMessage());
            log.info("  → API error (system scope): {}", jarFileName);
            return new MavenDependency(baseName, baseName, "0.0.0", "system", path.toAbsolutePath().toString());
        }
    }

    /**
     * Maven Central Search APIを呼び出してJSONレスポンスを返す。
     * テスト用にprotectedとして定義し、サブクラスでオーバーライド可能にする。
     */
    protected String callMavenCentralApi(String sha1) throws IOException, InterruptedException {
        String url = String.format(MAVEN_SEARCH_URL, sha1);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    // private static final Pattern GROUP_ID_PATTERN    = Pattern.compile("\"g\"\\s*:\\s*\"([^\"]+)\"");
    // private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("\"a\"\\s*:\\s*\"([^\"]+)\"");
    // private static final Pattern VERSION_PATTERN     = Pattern.compile("\"latestVersion\"\\s*:\\s*\"([^\"]+)\"");
    // private static final Pattern NUM_FOUND_PATTERN   = Pattern.compile("\"numFound\"\\s*:\\s*(\\d+)");
    private static final Pattern GROUP_ID_PATTERN    = Pattern.compile("\"g\":\"([^\"]+)\"");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("\"a\":\"([^\"]+)\"");
    private static final Pattern VERSION_PATTERN     = Pattern.compile("\"v\":\"([^\"]+)\"");
    private static final Pattern NUM_FOUND_PATTERN   = Pattern.compile("\"numFound\"\\s*:\\s*(\\d+)");

    /**
     * Maven Central APIのJSONレスポンスをパースしてMavenDependencyを返す。
     */
    private static MavenDependency parseResponse(String json, String baseName, String absolutePath) {
        // numFound:0 なら見つからなかった
        Matcher numFoundMatcher = NUM_FOUND_PATTERN.matcher(json);
        if (numFoundMatcher.find()) {
            int numFound = Integer.parseInt(numFoundMatcher.group(1));
            if (numFound == 0) {
                log.info("  → not found in Maven Central (system scope): {}", baseName);
                return new MavenDependency(baseName, baseName, "0.0.0", "system", absolutePath);
            }
        }

        Matcher gMatcher = GROUP_ID_PATTERN.matcher(json);
        Matcher aMatcher = ARTIFACT_ID_PATTERN.matcher(json);
        Matcher vMatcher = VERSION_PATTERN.matcher(json);

        if (gMatcher.find() && aMatcher.find() && vMatcher.find()) {
            String groupId    = gMatcher.group(1);
            String artifactId = aMatcher.group(1);
            String version    = vMatcher.group(1);
            log.info("  → found: {}:{}:{}", groupId, artifactId, version);
            return new MavenDependency(groupId, artifactId, version, null, null);
        }

        // パースに失敗した場合もsystemスコープ
        log.warn("  [WARN] Maven Central APIレスポンスのパースに失敗しました: {}", baseName);
        log.info("  → parse error (system scope): {}", baseName);
        return new MavenDependency(baseName, baseName, "0.0.0", "system", absolutePath);
    }
}
