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
 * Eclipse {@code .classpath} ファイルの {@code classpathentry} 要素1件分の属性情報を保持するレコード。
 *
 * <p>classpathentry 要素に直接書かれる属性と、子要素 {@code <attributes>} で定義される属性の
 * 両方を管理する。</p>
 *
 * @param kind     {@code kind} 属性値（{@code "lib"}, {@code "con"}, {@code "src"}, {@code "output"} など）
 * @param path     {@code path} 属性値
 * @param exported {@code exported="true"} 属性が存在するか
 * @param test     {@code <attribute name="test" value="true"/>} 子要素が存在するか
 */
public record ClasspathEntry(
        String kind,
        String path,
        boolean exported,
        boolean test
) {}
