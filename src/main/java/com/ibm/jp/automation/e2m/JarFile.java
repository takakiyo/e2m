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
 * 実際のJARファイル1件分のパスと、Eclipseの classpathentry 属性を保持するレコード。
 *
 * <p>スコープの決定は {@link DependencyResolver} 内で行う。</p>
 *
 * @param path     JARファイルへのパス文字列（Eclipseプロジェクトルートからの相対パス、または絶対パス）
 * @param test     {@code <attribute name="test" value="true"/>} 子要素が存在するか
 * @param exported {@code exported="true"} 属性が存在するか
 */
public record JarFile(
        String path,
        boolean test,
        boolean exported
) {}
