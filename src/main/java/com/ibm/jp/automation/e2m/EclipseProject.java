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

import java.util.List;

/**
 * Eclipseプロジェクトのメタデータを保持するレコード。
 */
public record EclipseProject(
        String projectName,
        boolean webProject,
        List<String> sourceFolders,
        String outputFolder,
        List<String> jarPaths,
        String webContentRoot,
        String javaVersion
) {}
