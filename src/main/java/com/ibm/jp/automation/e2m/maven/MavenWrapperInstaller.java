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

package com.ibm.jp.automation.e2m.maven;

import com.ibm.jp.automation.e2m.i18n.Messages;
import com.ibm.jp.automation.e2m.util.AppLogger;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Maven Wrapper ファイルを出力先Mavenプロジェクトに追加するクラス。
 *
 * <p>クラスパス上の {@code maven-wrapper/} リソースディレクトリ配下にある
 * ファイル一式をそのまま出力先ルートディレクトリへコピーします。</p>
 *
 * <ul>
 *   <li>{@code mvnw} / {@code mvnw.cmd} — ラッパースクリプト</li>
 *   <li>{@code .mvn/wrapper/maven-wrapper.jar} — ラッパー本体</li>
 *   <li>{@code .mvn/wrapper/maven-wrapper.properties} — バージョン設定</li>
 * </ul>
 */
public class MavenWrapperInstaller {

    private static final Logger log = AppLogger.get(MavenWrapperInstaller.class);

    /** クラスパス上のリソースディレクトリ名。 */
    private static final String RESOURCE_PREFIX = "maven-wrapper/";

    private MavenWrapperInstaller() {}

    /**
     * Maven Wrapper ファイルを {@code outputDir} 直下にコピーする。
     *
     * @param outputDir Mavenプロジェクトのルートディレクトリ
     * @throws IOException コピーに失敗した場合
     */
    public static void install(Path outputDir) throws IOException {
        // 実行中 JAR（または classes ディレクトリ）を取得してリソースを列挙する
        URL selfUrl = MavenWrapperInstaller.class.getProtectionDomain()
                .getCodeSource().getLocation();
        String selfPath = selfUrl.getPath();

        if (selfPath.endsWith(".jar")) {
            installFromJar(selfPath, outputDir);
        } else {
            installFromClassesDir(outputDir);
        }

        // mvnw に実行権限を付与（POSIX 対応OS のみ）
        Path mvnw = outputDir.resolve("mvnw");
        if (Files.exists(mvnw)) {
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(mvnw, perms);
            } catch (UnsupportedOperationException ignored) {
                // Windows など POSIX 非対応環境では無視する
            }
        }

        log.info(Messages.get("mavenWrapper.installed", outputDir.toAbsolutePath()));
    }

    // ── Fat JAR から展開する場合 ──────────────────────────────────────────────

    private static void installFromJar(String jarPath, Path outputDir) throws IOException {
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(RESOURCE_PREFIX) || entry.isDirectory()) {
                    continue;
                }
                // maven-wrapper/mvnw → mvnw  /  maven-wrapper/.mvn/... → .mvn/...
                String relative = name.substring(RESOURCE_PREFIX.length());
                if (relative.isEmpty()) {
                    continue;
                }
                Path dest = outputDir.resolve(relative);
                Files.createDirectories(dest.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                log.debug("  Installed: {}", dest);
            }
        }
    }

    // ── IDE / テスト時（classes ディレクトリ）から展開する場合 ─────────────────

    private static void installFromClassesDir(Path outputDir) throws IOException {
        URL resourceRoot = MavenWrapperInstaller.class.getClassLoader()
                .getResource(RESOURCE_PREFIX);
        if (resourceRoot == null) {
            throw new IOException(Messages.get("mavenWrapper.resourceNotFound"));
        }
        Path srcRoot = Path.of(resourceRoot.getPath());
        if (!Files.isDirectory(srcRoot)) {
            throw new IOException(Messages.get("mavenWrapper.resourceNotFound"));
        }
        Files.walk(srcRoot)
             .filter(Files::isRegularFile)
             .forEach(src -> {
                 try {
                     Path relative = srcRoot.relativize(src);
                     Path dest = outputDir.resolve(relative);
                     Files.createDirectories(dest.getParent());
                     Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                     log.debug("  Installed: {}", dest);
                 } catch (IOException e) {
                     throw new RuntimeException(e);
                 }
             });
    }
}
