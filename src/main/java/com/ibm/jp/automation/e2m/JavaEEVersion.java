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

/**
 * Java EE / Jakarta EE 仕様のバージョン情報を保持するクラス。
 * Servlet 仕様のバージョン（{@code jst.web} ファセットの version）から
 * pom.xml に出力すべき {@code <dependency>} 情報を提供する。
 *
 * <p>使用例:
 * <pre>
 *   JavaEEVersion v = JavaEEVersion.of("3.0");
 *   v.getGroupId();    // "javax"
 *   v.getArtifactId(); // "javaee-api"
 *   v.getVersion();    // "6.0"
 * </pre>
 */
public class JavaEEVersion {

    private final String groupId;
    private final String artifactId;
    private final String version;

    private JavaEEVersion(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * Servlet 仕様のバージョン文字列から {@link JavaEEVersion} インスタンスを返す。
     *
     * <table>
     *   <tr><th>Servlet</th><th>仕様</th><th>groupId</th><th>artifactId</th><th>version</th></tr>
     *   <tr><td>3.0</td><td>Java EE 6</td><td>javax</td><td>javaee-api</td><td>6.0</td></tr>
     *   <tr><td>3.1</td><td>Java EE 7</td><td>javax</td><td>javaee-api</td><td>7.0</td></tr>
     *   <tr><td>4.0</td><td>Java EE 8</td><td>javax</td><td>javaee-api</td><td>8.0</td></tr>
     *   <tr><td>5.0</td><td>Jakarta EE 9</td><td>jakarta.platform</td><td>jakarta.jakartaee-api</td><td>9.1.0</td></tr>
     *   <tr><td>6.0</td><td>Jakarta EE 10</td><td>jakarta.platform</td><td>jakarta.jakartaee-api</td><td>10.0.0</td></tr>
     *   <tr><td>6.1</td><td>Jakarta EE 11</td><td>jakarta.platform</td><td>jakarta.jakartaee-api</td><td>11.0.0</td></tr>
     * </table>
     *
     * @param webVersion Servlet 仕様のバージョン（例: {@code "3.0"}）
     * @return 対応する {@link JavaEEVersion} インスタンス
     * @throws IllegalArgumentException 未知の webVersion が指定された場合
     */
    public static JavaEEVersion of(String webVersion) {
        return switch (webVersion) {
            case "3.0" -> new JavaEEVersion("javax", "javaee-api", "6.0");
            case "3.1" -> new JavaEEVersion("javax", "javaee-api", "7.0");
            case "4.0" -> new JavaEEVersion("javax", "javaee-api", "8.0");
            case "5.0" -> new JavaEEVersion("jakarta.platform", "jakarta.jakartaee-api", "9.1.0");
            case "6.0" -> new JavaEEVersion("jakarta.platform", "jakarta.jakartaee-api", "10.0.0");
            case "6.1" -> new JavaEEVersion("jakarta.platform", "jakarta.jakartaee-api", "11.0.0");
            default    -> throw new IllegalArgumentException("Unknown Servlet version: " + webVersion);
        };
    }

    /** {@code <groupId>} に出力するグループID。 */
    public String getGroupId() {
        return groupId;
    }

    /** {@code <artifactId>} に出力するアーティファクトID。 */
    public String getArtifactId() {
        return artifactId;
    }

    /** {@code <version>} に出力するバージョン。 */
    public String getVersion() {
        return version;
    }
}
