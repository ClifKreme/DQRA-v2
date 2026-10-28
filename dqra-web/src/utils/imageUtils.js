/**
 * Image formatting and handling utilities for Base64 assets
 */

/**
 * Normalizes a Base64 image string to a valid data URI for <img> src.
 * Prepends 'data:image/jpeg;base64,' if it does not already start with 'data:'.
 * 
 * @param {string} base64Str 
 * @returns {string|null} Full data URI or null if input is empty
 */
export function formatBase64Image(base64Str) {
  if (!base64Str || typeof base64Str !== "string") {
    return null;
  }

  const trimmed = base64Str.trim();
  if (!trimmed) {
    return null;
  }

  if (trimmed.startsWith("data:")) {
    return trimmed;
  }

  // Default to image/jpeg for raw Base64 data from the Android app
  return `data:image/jpeg;base64,${trimmed}`;
}

/**
 * Helper to get the National ID front image with backward compatibility fallback.
 * 
 * @param {Object} doc - registration document object
 * @returns {string|null}
 */
export function getNationalIdFront(doc) {
  if (!doc) return null;
  return formatBase64Image(doc.nationalIdFrontBase64 || doc.nationalIdImageBase64);
}

/**
 * Helper to get the National ID back image.
 * 
 * @param {Object} doc - registration document object
 * @returns {string|null}
 */
export function getNationalIdBack(doc) {
  if (!doc) return null;
  return formatBase64Image(doc.nationalIdBackBase64);
}

/**
 * Helper to get selfie / avatar image.
 * 
 * @param {Object} doc - user or registration document object
 * @returns {string|null}
 */
export function getSelfieImage(doc) {
  if (!doc) return null;
  return formatBase64Image(doc.selfieImageBase64 || doc.profileImageBase64);
}
