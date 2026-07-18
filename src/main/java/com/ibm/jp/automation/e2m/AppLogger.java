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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * アプリケーション全体のロギング設定を管理するユーティリティクラス。
 *
 * <p>デフォルト（通常モード）では INFO 以上のメッセージを System.out へ出力する。
 * {@link #enableDebug(Path)} を呼び出すと DEBUG 以上のメッセージを指定ファイルへも書き出す。
 * ファイルのパスは {@link #getLogFile()} で取得できる。</p>
 */
public final class AppLogger {

    /** コンソールアペンダーのパターン（メッセージのみ）。 */
    private static final String CONSOLE_PATTERN = "%msg%n";

    /** ファイルアペンダーのパターン（タイムスタンプ+レベル+メッセージ）。 */
    private static final String FILE_PATTERN = "%date{yyyy-MM-dd HH:mm:ss.SSS} %-5level %msg%n";

    private static Path logFile = null;

    private AppLogger() {}

    /**
     * 指定クラス用の SLF4J ロガーを返す。
     *
     * @param clazz ロガーを取得するクラス
     * @return {@link org.slf4j.Logger}
     */
    public static org.slf4j.Logger get(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * アプリケーション起動時にコンソールアペンダーを初期化する。
     * INFO 以上のログを System.out へ出力する。
     * picocli がインスタンス化した後、{@code call()} の前に呼び出す。
     */
    public static void init() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.reset();

        // コンソールアペンダー（INFO 以上 → System.out）
        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setContext(ctx);
        console.setTarget("System.out");
        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setContext(ctx);
        consoleEncoder.setPattern(CONSOLE_PATTERN);
        consoleEncoder.start();
        console.setEncoder(consoleEncoder);
        console.start();

        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        root.addAppender(console);
    }

    /**
     * デバッグモードを有効化する。
     * DEBUG 以上のメッセージを指定ファイルへ出力するアペンダーを追加する。
     * コンソール出力は引き続き INFO 以上のみ。
     *
     * @param file デバッグログの出力先ファイルパス
     */
    public static void enableDebug(Path file) {
        logFile = file;

        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        // ファイルアペンダー（DEBUG 以上）
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(ctx);
        fileAppender.setFile(file.toAbsolutePath().toString());
        fileAppender.setAppend(false);

        PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
        fileEncoder.setContext(ctx);
        fileEncoder.setPattern(FILE_PATTERN);
        fileEncoder.start();
        fileAppender.setEncoder(fileEncoder);
        fileAppender.start();

        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
        root.addAppender(fileAppender);
    }

    /**
     * デバッグログファイルのパスを返す。デバッグモードが有効でない場合は {@code null}。
     *
     * @return ログファイルパス、または {@code null}
     */
    public static Path getLogFile() {
        return logFile;
    }
}
