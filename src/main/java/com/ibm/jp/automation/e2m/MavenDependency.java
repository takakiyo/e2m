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
 * Maven依存関係情報を保持するレコード。
 *
 * @param groupId    Maven groupId
 * @param artifactId Maven artifactId
 * @param version    バージョン文字列
 * @param scope      スコープ（nullまたは空の場合はscope要素を出力しない。systemスコープの場合は "system"）
 * @param systemPath systemスコープの場合のJARファイルの絶対パス。それ以外はnull
 */
public record MavenDependency(
        String groupId,
        String artifactId,
        String version,
        String scope,
        String systemPath
) {}
