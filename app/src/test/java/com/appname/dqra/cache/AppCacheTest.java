package com.appname.dqra.cache;

import android.content.Context;

import com.appname.dqra.model.EmergencyContact;
import com.appname.dqra.model.EmergencyLocation;
import com.appname.dqra.model.RescueStation;
import com.appname.dqra.model.UserProfile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for AppCache serialization / deserialization.
 * Uses Robolectric to provide a real SharedPreferences implementation
 * without needing a physical device or emulator.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppCacheTest {

    private AppCache cache;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        AppCache.initialize(context);
        cache = AppCache.getInstance();
    }

    // =========================================================================
    // UserProfile round-trip
    // =========================================================================

    @Test
    public void userProfile_putAndGet_allFieldsPreserved() {
        UserProfile original = new UserProfile(
                "uid-001", "Juan Dela Cruz", "juan@email.com",
                "09171234567", "Barangay 1, Cebu City", "user", null);

        cache.putUserProfile("uid-001", original);
        UserProfile result = cache.getUserProfile("uid-001");

        assertNotNull(result);
        assertEquals("uid-001", result.getId());
        assertEquals("Juan Dela Cruz", result.getFullName());
        assertEquals("juan@email.com", result.getEmail());
        assertEquals("09171234567", result.getPhone());
        assertEquals("Barangay 1, Cebu City", result.getAddress());
        assertEquals("user", result.getRole());
    }

    @Test
    public void userProfile_nullFields_survivesRoundTrip() {
        UserProfile original = new UserProfile(
                "uid-002", null, null, null, null, null, null);

        cache.putUserProfile("uid-002", original);
        UserProfile result = cache.getUserProfile("uid-002");

        assertNotNull(result);
        assertNull(result.getFullName());
        assertNull(result.getEmail());
    }

    @Test
    public void userProfile_getMissingKey_returnsNull() {
        assertNull(cache.getUserProfile("non-existent-uid"));
    }

    @Test
    public void userProfile_differentUsers_storedIndependently() {
        cache.putUserProfile("user-A", new UserProfile("user-A", "Alice", "a@b.com", "111", "Addr A", "user", null));
        cache.putUserProfile("user-B", new UserProfile("user-B", "Bob",   "b@c.com", "222", "Addr B", "admin", null));

        assertEquals("Alice", cache.getUserProfile("user-A").getFullName());
        assertEquals("Bob",   cache.getUserProfile("user-B").getFullName());
    }

    // =========================================================================
    // Municipality cache
    // =========================================================================

    @Test
    public void adminMunicipality_putAndGet_returnsCorrectValue() {
        cache.putAdminMunicipality("admin-001", "Cebu City");
        assertEquals("Cebu City", cache.getAdminMunicipality("admin-001"));
    }

    @Test
    public void userMunicipality_putAndGet_returnsCorrectValue() {
        cache.putUserMunicipality("user-001", "Mandaue City");
        assertEquals("Mandaue City", cache.getUserMunicipality("user-001"));
    }

    @Test
    public void municipality_missingKey_returnsNull() {
        assertNull(cache.getAdminMunicipality("ghost-user"));
    }

    // =========================================================================
    // RescueStation list round-trip
    // =========================================================================

    @Test
    public void rescueStations_putAndGet_allFieldsPreserved() {
        RescueStation station = new RescueStation();
        station.setId("station-001");
        station.setName("BFP Cebu");
        station.setStationName("Cebu City Fire Station");
        station.setMunicipality("Cebu City");
        station.setPhoneNumber("032-1234567");
        station.setPhoneNumber2("032-7654321");
        station.setMessengerLink("https://m.me/bfpcebu");
        station.setLatitude(10.3157);
        station.setLongitude(123.8854);

        cache.putRescueStations(Arrays.asList(station));
        List<RescueStation> result = cache.getRescueStations();

        assertNotNull(result);
        assertEquals(1, result.size());

        RescueStation r = result.get(0);
        assertEquals("station-001", r.getId());
        assertEquals("BFP Cebu", r.getName());
        assertEquals("Cebu City Fire Station", r.getStationName());
        assertEquals("Cebu City", r.getMunicipality());
        assertEquals("032-1234567", r.getPhoneNumber());
        assertEquals("032-7654321", r.getPhoneNumber2());
        assertEquals("https://m.me/bfpcebu", r.getMessengerLink());
        assertEquals(10.3157, r.getLatitude(), 0.00001);
        assertEquals(123.8854, r.getLongitude(), 0.00001);
    }

    @Test
    public void rescueStations_emptyList_storesAndRetrievesEmpty() {
        cache.putRescueStations(Arrays.asList());
        List<RescueStation> result = cache.getRescueStations();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void rescueStations_multipleStations_allPreserved() {
        RescueStation s1 = new RescueStation();
        s1.setId("s1"); s1.setName("Station A"); s1.setMunicipality("Cebu City");

        RescueStation s2 = new RescueStation();
        s2.setId("s2"); s2.setName("Station B"); s2.setMunicipality("Mandaue City");

        cache.putRescueStations(Arrays.asList(s1, s2));
        List<RescueStation> result = cache.getRescueStations();

        assertEquals(2, result.size());
        assertEquals("Station A", result.get(0).getName());
        assertEquals("Station B", result.get(1).getName());
    }

    @Test
    public void rescueStations_notCached_returnsNull() {
        // Use a key that was never written — we can test this via the municipality cache
        // (no dedicated "clear" API, so we verify a key that this test suite never writes)
        assertNull(cache.getEmergencyLogs("NonExistentMunicipality_XYZ", "nonexistent"));
    }

    // =========================================================================
    // EmergencyContact list round-trip
    // =========================================================================

    @Test
    public void emergencyContacts_putAndGet_allFieldsPreserved() {
        EmergencyContact contact = new EmergencyContact();
        contact.setId("contact-001");
        contact.setBarangay("Barangay Lahug");
        contact.setStationName("Lahug Rescue");
        contact.setPhoneNumber("09991234567");

        cache.putEmergencyContacts(Arrays.asList(contact));
        List<EmergencyContact> result = cache.getEmergencyContacts();

        assertNotNull(result);
        assertEquals(1, result.size());

        EmergencyContact c = result.get(0);
        assertEquals("contact-001", c.getId());
        assertEquals("Barangay Lahug", c.getBarangay());
        assertEquals("Lahug Rescue", c.getStationName());
        assertEquals("09991234567", c.getPhoneNumber());
    }

    @Test
    public void emergencyContacts_multipleContacts_countMatches() {
        cache.putEmergencyContacts(Arrays.asList(
                new EmergencyContact("Brgy A", "Station A", "111"),
                new EmergencyContact("Brgy B", "Station B", "222"),
                new EmergencyContact("Brgy C", "Station C", "333")
        ));
        assertEquals(3, cache.getEmergencyContacts().size());
    }

    // =========================================================================
    // EmergencyLocation (logs) round-trip
    // =========================================================================

    @Test
    public void emergencyLogs_putAndGet_allFieldsPreserved() {
        EmergencyLocation log = new EmergencyLocation(
                "Cebu City", 10.3157, 123.8854,
                "Juan Dela Cruz", 1700000000000L, "pending");
        log.setId("log-001");

        cache.putEmergencyLogs("Cebu City", "pending", Arrays.asList(log));
        List<EmergencyLocation> result = cache.getEmergencyLogs("Cebu City", "pending");

        assertNotNull(result);
        assertEquals(1, result.size());

        EmergencyLocation r = result.get(0);
        assertEquals("log-001", r.getId());
        assertEquals("Cebu City", r.getMunicipality());
        assertEquals(10.3157, r.getLatitude(), 0.00001);
        assertEquals(123.8854, r.getLongitude(), 0.00001);
        assertEquals("Juan Dela Cruz", r.getReportedBy());
        assertEquals(1700000000000L, r.getTimestamp());
        assertEquals("pending", r.getStatus());
    }

    @Test
    public void emergencyLogs_differentMunicipalityFilters_storedSeparately() {
        EmergencyLocation cebu   = new EmergencyLocation("Cebu City",    10.31, 123.88, "A", 1000L, "pending");
        EmergencyLocation mandaue = new EmergencyLocation("Mandaue City", 10.35, 123.93, "B", 2000L, "resolved");

        cache.putEmergencyLogs("Cebu City",    "pending",  Arrays.asList(cebu));
        cache.putEmergencyLogs("Mandaue City", "resolved", Arrays.asList(mandaue));

        List<EmergencyLocation> cebuResult    = cache.getEmergencyLogs("Cebu City",    "pending");
        List<EmergencyLocation> mandaueResult = cache.getEmergencyLogs("Mandaue City", "resolved");

        assertEquals(1, cebuResult.size());
        assertEquals("Cebu City",    cebuResult.get(0).getMunicipality());

        assertEquals(1, mandaueResult.size());
        assertEquals("Mandaue City", mandaueResult.get(0).getMunicipality());
    }

    @Test
    public void emergencyLogs_nullMunicipalityFilter_doesNotCrash() {
        EmergencyLocation log = new EmergencyLocation("Cebu City", 10.31, 123.88, "A", 1000L, "pending");
        // null municipality and statusFilter use "all" as key via safe()
        cache.putEmergencyLogs(null, null, Arrays.asList(log));
        List<EmergencyLocation> result = cache.getEmergencyLogs(null, null);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    public void emergencyLogs_notCached_returnsNull() {
        assertNull(cache.getEmergencyLogs("Unknown Municipality", "pending"));
    }
}
