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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * ファイル操作に関するユーティリティクラス。
 */
public class FileUtils {

    private FileUtils() {}

    /**
     * ファイルのSHA1ハッシュを小文字16進数文字列として計算する。
     *
     * @param file ハッシュを計算するファイルのパス
     * @return SHA1ハッシュの小文字16進数文字列
     * @throws Exception ファイル読み取りまたはダイジェスト計算に失敗した場合
     */
    public static String computeSha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
