package com.appname.dqra;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Unit tests for the pure logic methods inside AlertService:
 *  - coordinate regex parsing
 *  - extractField()
 *  - firstNonEmpty()
 *
 * These methods contain no Android dependencies so they run as plain JUnit tests.
 * The logic is duplicated here as package-private helpers that mirror AlertService exactly.
 */
public class AlertServiceLogicTest {

    // ─── Coordinate regex (mirrored from AlertService) ───────────────────────

    private static final String LAT_LON_PATTERN = "(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)";

    private String[] parseCoordinates(String message) {
        if (message == null) return null;
        Pattern pattern = Pattern.compile(LAT_LON_PATTERN);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return null;
    }

    // ─── extractField() (mirrored from AlertService) ─────────────────────────

    private String extractField(String message, String label) {
        if (message == null) return "";
        Pattern pattern = Pattern.compile(
                "(?im)^\\s*" + Pattern.quote(label) + "\\s*:\\s*(.+?)\\s*$");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    // ─── firstNonEmpty() (mirrored from AlertService) ─────────────────────────

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    // =========================================================================
    // Coordinate Regex Tests
    // =========================================================================

    @Test
    public void parseCoordinates_standardFormat_returnsLatLon() {
        String[] result = parseCoordinates("Emergency at 10.1234, 123.4567");
        assertNotNull(result);
        assertEquals("10.1234", result[0]);
        assertEquals("123.4567", result[1]);
    }

    @Test
    public void parseCoordinates_noSpaceAfterComma_returnsLatLon() {
        String[] result = parseCoordinates("Location: 14.5995,120.9842");
        assertNotNull(result);
        assertEquals("14.5995", result[0]);
        assertEquals("120.9842", result[1]);
    }

    @Test
    public void parseCoordinates_negativeLatitude_returnsNegativeLat() {
        String[] result = parseCoordinates("Coords: -33.8688, 151.2093");
        assertNotNull(result);
        assertEquals("-33.8688", result[0]);
        assertEquals("151.2093", result[1]);
    }

    @Test
    public void parseCoordinates_negativeBothValues_returnsNegatives() {
        String[] result = parseCoordinates("Position -10.1234, -123.4567");
        assertNotNull(result);
        assertEquals("-10.1234", result[0]);
        assertEquals("-123.4567", result[1]);
    }

    @Test
    public void parseCoordinates_coordsEmbeddedInLongMessage_stillFindsCoords() {
        String message = "Name: Juan Dela Cruz\nPhone: 09171234567\nCoords: 10.3157, 123.8854\nNote: near barangay hall";
        String[] result = parseCoordinates(message);
        assertNotNull(result);
        assertEquals("10.3157", result[0]);
        assertEquals("123.8854", result[1]);
    }

    @Test
    public void parseCoordinates_noCoordinatesInMessage_returnsNull() {
        assertNull(parseCoordinates("Help needed at the market!"));
    }

    @Test
    public void parseCoordinates_emptyMessage_returnsNull() {
        assertNull(parseCoordinates(""));
    }

    @Test
    public void parseCoordinates_nullMessage_returnsNull() {
        assertNull(parseCoordinates(null));
    }

    @Test
    public void parseCoordinates_integerLookingNumbers_notMatched() {
        // The pattern requires decimal points — plain integers should not match
        assertNull(parseCoordinates("10, 123"));
    }

    // =========================================================================
    // extractField() Tests
    // =========================================================================

    @Test
    public void extractField_namePresent_returnsName() {
        String message = "Name: Juan Dela Cruz\nPhone: 09171234567";
        assertEquals("Juan Dela Cruz", extractField(message, "Name"));
    }

    @Test
    public void extractField_phonePresent_returnsPhone() {
        String message = "Name: Maria Santos\nPhone: 09181234567";
        assertEquals("09181234567", extractField(message, "Phone"));
    }

    @Test
    public void extractField_labelWithExtraWhitespace_stillMatches() {
        String message = "Name :  Juan Dela Cruz  \nPhone: 09171234567";
        assertEquals("Juan Dela Cruz", extractField(message, "Name"));
    }

    @Test
    public void extractField_labelNotPresent_returnsEmpty() {
        String message = "Some random message without labels";
        assertEquals("", extractField(message, "Name"));
    }

    @Test
    public void extractField_nullMessage_returnsEmpty() {
        assertEquals("", extractField(null, "Name"));
    }

    @Test
    public void extractField_emptyMessage_returnsEmpty() {
        assertEquals("", extractField("", "Name"));
    }

    @Test
    public void extractField_caseInsensitive_matchesUpperCase() {
        // The regex uses (?im) so it's case-insensitive
        String message = "NAME: Pedro Reyes";
        assertEquals("Pedro Reyes", extractField(message, "Name"));
    }

    // =========================================================================
    // firstNonEmpty() Tests
    // =========================================================================

    @Test
    public void firstNonEmpty_firstValueValid_returnsFirst() {
        assertEquals("Alice", firstNonEmpty("Alice", "Bob", "Charlie"));
    }

    @Test
    public void firstNonEmpty_firstNullSecondValid_returnsSecond() {
        assertEquals("Bob", firstNonEmpty(null, "Bob", "Charlie"));
    }

    @Test
    public void firstNonEmpty_firstEmptySecondValid_returnsSecond() {
        assertEquals("Bob", firstNonEmpty("", "Bob", "Charlie"));
    }

    @Test
    public void firstNonEmpty_firstBlankSecondValid_returnsSecond() {
        assertEquals("Bob", firstNonEmpty("   ", "Bob"));
    }

    @Test
    public void firstNonEmpty_allNullOrEmpty_returnsEmptyString() {
        assertEquals("", firstNonEmpty(null, "", "   "));
    }

    @Test
    public void firstNonEmpty_singleValidValue_returnsTrimmed() {
        assertEquals("Juan", firstNonEmpty("  Juan  "));
    }

    @Test
    public void firstNonEmpty_noArguments_returnsEmptyString() {
        assertEquals("", firstNonEmpty());
    }

    @Test
    public void firstNonEmpty_realWorldScenario_picksReportedByOverSender() {
        // Simulates: reportedBy from message wins over senderName fallback
        String reportedBy = "Juan Dela Cruz";
        String senderName = "Messenger User";
        assertEquals("Juan Dela Cruz", firstNonEmpty(reportedBy, senderName));
    }

    @Test
    public void firstNonEmpty_reportedByMissing_fallsBackToSender() {
        String reportedBy = null;
        String extractedName = "";
        String senderName = "Messenger User";
        assertEquals("Messenger User", firstNonEmpty(reportedBy, extractedName, senderName));
    }
}
