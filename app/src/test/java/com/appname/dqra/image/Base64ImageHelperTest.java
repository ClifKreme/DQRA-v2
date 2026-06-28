package com.appname.dqra.image;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Base64ImageHelper utility methods.
 * Only getBase64SizeKB() is tested here because it is pure Java with
 * no Android dependencies. The bitmap-related methods require Android
 * graphics APIs and are better covered by instrumented tests.
 */
public class Base64ImageHelperTest {

    // =========================================================================
    // getBase64SizeKB()
    // =========================================================================

    @Test
    public void getBase64SizeKB_nullInput_returnsZero() {
        assertEquals(0.0, Base64ImageHelper.getBase64SizeKB(null), 0.0001);
    }

    @Test
    public void getBase64SizeKB_emptyString_returnsZero() {
        assertEquals(0.0, Base64ImageHelper.getBase64SizeKB(""), 0.0001);
    }

    @Test
    public void getBase64SizeKB_knownLength_returnsExpectedSize() {
        // 1024 chars → (1024 * 0.75) / 1024 = 0.75 KB
        String input = "A".repeat(1024);
        assertEquals(0.75, Base64ImageHelper.getBase64SizeKB(input), 0.0001);
    }

    @Test
    public void getBase64SizeKB_2048chars_returns1point5KB() {
        // 2048 chars → (2048 * 0.75) / 1024 = 1.5 KB
        String input = "A".repeat(2048);
        assertEquals(1.5, Base64ImageHelper.getBase64SizeKB(input), 0.0001);
    }

    @Test
    public void getBase64SizeKB_singleChar_returnsSmallPositiveValue() {
        double result = Base64ImageHelper.getBase64SizeKB("A");
        assertTrue("Single char should return a small positive KB value", result > 0);
        assertTrue("Single char should be much less than 1 KB", result < 0.01);
    }

    @Test
    public void getBase64SizeKB_largeInput_scalesLinearly() {
        // Doubling the input should double the result
        String small = "A".repeat(512);
        String large = "A".repeat(1024);
        double smallKB = Base64ImageHelper.getBase64SizeKB(small);
        double largeKB = Base64ImageHelper.getBase64SizeKB(large);
        assertEquals(largeKB, smallKB * 2, 0.0001);
    }

    // =========================================================================
    // loadBase64IntoImageView() — null/empty guard logic
    // =========================================================================

    @Test
    public void loadBase64IntoImageView_nullBase64_returnsFalse() {
        // Passing null imageView would crash, but the null base64 check
        // fires first and returns false — verifying the guard logic
        assertFalse(Base64ImageHelper.loadBase64IntoImageView(null, null));
    }

    @Test
    public void loadBase64IntoImageView_emptyBase64_returnsFalse() {
        assertFalse(Base64ImageHelper.loadBase64IntoImageView(null, ""));
    }
}
