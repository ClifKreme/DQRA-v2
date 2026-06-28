package com.appname.dqra.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for UserProfile business logic.
 * isComplete() is the only logic method and has real safety implications —
 * an incomplete profile should not be allowed to trigger emergencies.
 */
public class UserProfileTest {

    // =========================================================================
    // isComplete()
    // =========================================================================

    @Test
    public void isComplete_allFieldsPresent_returnsTrue() {
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", "juan@email.com",
                "09171234567", "Barangay 1, Cebu City", "user", null);
        assertTrue(profile.isComplete());
    }

    @Test
    public void isComplete_missingFullName_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", null, "juan@email.com",
                "09171234567", "Barangay 1", "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_emptyFullName_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", "  ", "juan@email.com",
                "09171234567", "Barangay 1", "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_missingPhone_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", "juan@email.com",
                null, "Barangay 1", "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_emptyPhone_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", "juan@email.com",
                "", "Barangay 1", "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_missingAddress_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", "juan@email.com",
                "09171234567", null, "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_emptyAddress_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", "juan@email.com",
                "09171234567", "", "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_whitespaceOnlyAddress_returnsFalse() {
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", "juan@email.com",
                "09171234567", "   ", "user", null);
        assertFalse(profile.isComplete());
    }

    @Test
    public void isComplete_allFieldsMissing_returnsFalse() {
        UserProfile profile = new UserProfile(
                null, null, null, null, null, null, null);
        assertFalse(profile.isComplete());
    }

    // Email and role are not required by isComplete() — verify this is intentional

    @Test
    public void isComplete_emailAndRoleNull_stillComplete_ifOtherFieldsPresent() {
        // isComplete() only checks fullName, phone, address
        UserProfile profile = new UserProfile(
                "uid-1", "Juan Dela Cruz", null,
                "09171234567", "Barangay 1", null, null);
        assertTrue("isComplete should not require email or role", profile.isComplete());
    }

    // =========================================================================
    // Getters
    // =========================================================================

    @Test
    public void getters_returnCorrectValues() {
        UserProfile profile = new UserProfile(
                "uid-99", "Maria Santos", "maria@email.com",
                "09181234567", "Cebu City", "admin", "base64img",
                1000L, 2000L);

        assertEquals("uid-99", profile.getId());
        assertEquals("Maria Santos", profile.getFullName());
        assertEquals("maria@email.com", profile.getEmail());
        assertEquals("09181234567", profile.getPhone());
        assertEquals("Cebu City", profile.getAddress());
        assertEquals("admin", profile.getRole());
        assertEquals("base64img", profile.getProfileImageBase64());
        assertEquals(1000L, profile.getProfileLastUpdatedAt());
        assertEquals(2000L, profile.getPhotoLastUpdatedAt());
    }
}
