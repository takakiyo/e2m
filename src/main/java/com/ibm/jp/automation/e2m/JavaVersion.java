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

import java.util.HashMap;

/**
 * Javaのバージョン番号を表す不変クラス。
 *
 * <p>以下の形式の文字列からインスタンスを生成できる。</p>
 * <ul>
 *   <li>{@code "1.1"} 〜 {@code "1.8"} — Java 1〜8 を表す旧形式</li>
 *   <li>{@code "5"} 〜 それ以降の整数 — Java 5 以降の新形式</li>
 * </ul>
 *
 * <p>認識できない文字列が渡された場合は {@link #UNKNOWN_VERSION} を返す。
 * {@code UNKNOWN_VERSION} は内部値 {@code -1} を持ち、全ての有効なバージョンより古い扱いになる。</p>
 *
 * <p>{@link Comparable} を実装しており、バージョンの大小比較が可能。</p>
 */
public final class JavaVersion implements Comparable<JavaVersion> {

    /**
     * 認識できないバージョン文字列を表す特殊インスタンス。
     * 内部値は {@code -1} であり、全ての有効な {@link JavaVersion} より小さい。
     */
    private static final int UNKNOWN_VERSION = -1;

    /**
     * 有効なJavaVersionについては，シングルインスタンスを返す。
     * なので，{@code if (version == JavaVersion.Java8)}などの比較が可能。
     */
    public static final JavaVersion Java8 = new JavaVersion(8, "1.8");
    public static final JavaVersion Java9 = new JavaVersion(9, "9");
    private static HashMap<Integer,JavaVersion> cache;
    static {
        cache = new HashMap<>();
        cache.put(8, Java8);
        cache.put(9, Java9);
    }
    private static JavaVersion getCache(int version) {
        JavaVersion ret = cache.get(version);
        if (ret != null) {
            return ret;
        } else {
            if (version <= 8) {
                ret = new JavaVersion(version, "1." + Integer.toString(version));
            } else {
                ret = new JavaVersion(version, Integer.toString(version));
            }
            cache.put(version, ret);
            return ret;
        }
    }

    /** 内部的に保持する整数バージョン番号（例: 1.8 → 8、17 → 17。UNKNOWN は -1）。 */
    private final int value;

    /** 元の文字列表現（toString() で返す）。 */
    private final String original;

    private JavaVersion(int value, String original) {
        this.value = value;
        this.original = original;
    }

    /**
     * バージョン文字列から {@link JavaVersion} インスタンスを生成する。
     *
     * <p>受け付ける形式:</p>
     * <ul>
     *   <li>{@code "1.0"} 〜 {@code "1.8"} — Java 1〜8（旧形式）</li>
     *   <li>{@code "5"} 以上の整数文字列 — Java 5 以降（新形式）</li>
     * </ul>
     *
     * <p>認識できない文字列（null・空文字・不正な形式・負数）が渡された場合は、
     * その文字列を {@code original} として保持する {@link #isUnknown()} が {@code true} を返す
     * インスタンスを生成して返す。null の場合は {@code original} に {@code "null"} を使用する。</p>
     *
     * @param version バージョン文字列
     * @return 対応する {@link JavaVersion} インスタンス。認識できない場合は {@code isUnknown() == true} のインスタンス
     */
    public static JavaVersion of(String version) {
        if (version == null || version.isBlank()) {
            return new JavaVersion(UNKNOWN_VERSION, version == null ? "null" : version.trim());
        }
        String v = version.trim();

        // "1.x" 形式（例: "1.8"）
        if (v.startsWith("1.")) {
            try {
                int minor = Integer.parseInt(v.substring(2));
                if (minor < 0 || minor > 8) {
                    return new JavaVersion(UNKNOWN_VERSION, v);
                }
                return getCache(minor);
            } catch (NumberFormatException e) {
                return new JavaVersion(UNKNOWN_VERSION, v);
            }
        }

        // 整数形式（例: "5", "11", "17"）
        try {
            int n = Integer.parseInt(v);
            if (n < 5) {
                return new JavaVersion(UNKNOWN_VERSION, v);
            }
            return getCache(n);
        } catch (NumberFormatException e) {
            return new JavaVersion(UNKNOWN_VERSION, v);
        }
    }

    /**
     * このインスタンスが {@link #UNKNOWN_VERSION} かどうかを返す。
     */
    public boolean isUnknown() {
        return this.value == UNKNOWN_VERSION;
    }

    /**
     * 内部的な整数バージョン番号を返す（例: "1.8" → 8、"17" → 17）。
     */
    public int toInt() {
        return value;
    }

    @Override
    public int compareTo(JavaVersion other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JavaVersion other)) return false;
        return this.value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    /** 元の文字列表現を返す（例: {@code "1.8"}、{@code "17"}）。 */
    @Override
    public String toString() {
        return original;
    }
}
