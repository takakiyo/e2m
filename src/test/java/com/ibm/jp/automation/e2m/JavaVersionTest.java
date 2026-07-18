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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaVersionTest {

    // ── of() パース ──────────────────────────────────────────────────────────

    @Test
    void of_legacyForm_1_1() {
        JavaVersion v = JavaVersion.of("1.1");
        assertEquals(1, v.toInt());
        assertEquals("1.1", v.toString());
    }

    @Test
    void of_legacyForm_1_8() {
        JavaVersion v = JavaVersion.of("1.8");
        assertEquals(8, v.toInt());
        assertEquals("1.8", v.toString());
    }

    @Test
    void of_modernForm_5() {
        JavaVersion v = JavaVersion.of("5");
        assertEquals(5, v.toInt());
        assertEquals("1.5", v.toString());
    }

    @Test
    void of_modernForm_11() {
        JavaVersion v = JavaVersion.of("11");
        assertEquals(11, v.toInt());
        assertEquals("11", v.toString());
    }

    @Test
    void of_modernForm_17() {
        JavaVersion v = JavaVersion.of("17");
        assertEquals(17, v.toInt());
        assertEquals("17", v.toString());
    }

    @Test
    void of_modernForm_21() {
        JavaVersion v = JavaVersion.of("21");
        assertEquals(21, v.toInt());
        assertEquals("21", v.toString());
    }

    @Test
    void of_modernForm_25() {
        JavaVersion v = JavaVersion.of("25");
        assertEquals(25, v.toInt());
        assertEquals("25", v.toString());
    }

    @Test
    void of_trailingWhitespace_ignored() {
        assertEquals(17, JavaVersion.of("  17  ").toInt());
    }

    @Test
    void of_null_returnsUnknown() {
        assertTrue(JavaVersion.of(null).isUnknown());
        assertEquals("null", JavaVersion.of(null).toString());
    }

    @Test
    void of_blank_returnsUnknown() {
        assertTrue(JavaVersion.of("  ").isUnknown());
        assertEquals("", JavaVersion.of("  ").toString());
    }

    @Test
    void of_invalidString_returnsUnknown() {
        assertTrue(JavaVersion.of("abc").isUnknown());
        assertEquals("abc", JavaVersion.of("abc").toString());
    }

    @Test
    void of_negativeNumber_returnsUnknown() {
        assertTrue(JavaVersion.of("-1").isUnknown());
        assertEquals("-1", JavaVersion.of("-1").toString());
    }

    @Test
    void validVersion_isNotUnknown() {
        assertFalse(JavaVersion.of("17").isUnknown());
    }

    // ── compareTo ─────────────────────────────────────────────────────────────

    @Test
    void compareTo_lessThan() {
        assertTrue(JavaVersion.of("11").compareTo(JavaVersion.of("17")) < 0);
    }

    @Test
    void compareTo_greaterThan() {
        assertTrue(JavaVersion.of("21").compareTo(JavaVersion.of("17")) > 0);
    }

    @Test
    void compareTo_equal() {
        assertEquals(0, JavaVersion.of("17").compareTo(JavaVersion.of("17")));
    }

    @Test
    void compareTo_legacyAndModern_equal() {
        // "1.8" と "8" は同じバージョンとして扱われる
        assertEquals(0, JavaVersion.of("1.8").compareTo(JavaVersion.of("8")));
    }

    @Test
    void compareTo_legacyLessThanModern() {
        assertTrue(JavaVersion.of("1.8").compareTo(JavaVersion.of("11")) < 0);
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Test
    void equals_sameValue() {
        assertEquals(JavaVersion.of("17"), JavaVersion.of("17"));
    }

    @Test
    void equals_legacyAndModern() {
        assertEquals(JavaVersion.of("1.8"), JavaVersion.of("8"));
    }

    @Test
    void equals_differentValue() {
        assertNotEquals(JavaVersion.of("11"), JavaVersion.of("17"));
    }

    @Test
    void hashCode_equalValues_sameHash() {
        assertEquals(JavaVersion.of("1.8").hashCode(), JavaVersion.of("8").hashCode());
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_preservesOriginalForm() {
        assertEquals("1.8", JavaVersion.of("1.8").toString());
        assertEquals("1.8", JavaVersion.of("8").toString());
        assertEquals("17",  JavaVersion.of("17").toString());
        assertEquals("21",  JavaVersion.of("21").toString());
    }
}
