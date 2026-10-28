/**
 * Date and Timestamp utilities for DQRA.
 * Correctly handles Firestore Timestamp objects (.toMillis()), plain milliseconds numbers,
 * Date instances, and ISO strings.
 */

/**
 * Safe Date converter handling plain numbers (milliseconds), Firestore Timestamp objects (.toMillis(), .toDate()),
 * Date instances, and string representations.
 * 
 * @param {any} ts 
 * @returns {Date|null}
 */
export function toDate(ts) {
  if (!ts) return null;
  if (typeof ts === "number") return new Date(ts);
  if (ts.toMillis && typeof ts.toMillis === "function") return new Date(ts.toMillis());
  if (ts.toDate && typeof ts.toDate === "function") return ts.toDate();
  const d = new Date(ts);
  return isNaN(d.getTime()) ? null : d;
}

/**
 * Extracts milliseconds timestamp from various timestamp formats safely using toDate().
 * @param {any} ts 
 * @returns {number|null} Milliseconds timestamp or null if invalid
 */
export function getTimestampMs(ts) {
  const d = toDate(ts);
  return d ? d.getTime() : null;
}

/**
 * Formats a timestamp into a human-readable date and time string.
 * @param {any} ts 
 * @returns {string} e.g. "Sep 21, 2026, 4:30 PM"
 */
export function formatDateTime(ts) {
  const d = toDate(ts);
  if (!d) return "N/A";

  return d.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

/**
 * Formats a timestamp into just the date string.
 * @param {any} ts 
 * @returns {string} e.g. "Sep 21, 2026"
 */
export function formatDate(ts) {
  const d = toDate(ts);
  if (!d) return "N/A";

  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/**
 * Formats a timestamp into just the time string.
 * @param {any} ts 
 * @returns {string} e.g. "4:30:12 PM"
 */
export function formatTime(ts) {
  const d = toDate(ts);
  if (!d) return "N/A";

  return d.toLocaleTimeString("en-US", {
    hour: "numeric",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });
}

/**
 * Formats relative time (e.g., "just now", "5m ago", "2h ago").
 * @param {any} ts 
 * @returns {string}
 */
export function formatRelativeTime(ts) {
  const d = toDate(ts);
  if (!d) return "N/A";

  const ms = d.getTime();
  const now = Date.now();
  const diffSec = Math.floor((now - ms) / 1000);

  if (diffSec < 10) return "just now";
  if (diffSec < 60) return `${diffSec}s ago`;

  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;

  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}h ago`;

  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}d ago`;

  return formatDate(d);
}

/**
 * Checks if the timestamp occurred today (local time).
 * @param {any} ts 
 * @returns {boolean}
 */
export function isToday(ts) {
  const date = toDate(ts);
  if (!date) return false;

  const today = new Date();
  return (
    date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()
  );
}

/**
 * Checks if the timestamp occurred in the current month (local time).
 * @param {any} ts 
 * @returns {boolean}
 */
export function isThisMonth(ts) {
  const date = toDate(ts);
  if (!date) return false;

  const today = new Date();
  return (
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()
  );
}
