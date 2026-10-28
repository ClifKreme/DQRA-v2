/**
 * DQRA AES-256-GCM Decryption and SHA-256 Hashing Utilities
 *
 * Implements Android-compatible decryption using the browser Web Crypto API.
 * Passphrase: DQRA_AES_FIELD_KEY_V1_2026
 * Key derivation: SHA-256(passphrase) -> 32-byte AES-GCM key.
 * Payload format: enc:v1:[Base64 of 12-byte IV + ciphertext + 16-byte GCM tag]
 */

const PASSPHRASE = "DQRA_AES_FIELD_KEY_V1_2026";
const PREFIX = "enc:v1:";

let cachedCryptoKey = null;

/**
 * Derives and caches the 256-bit AES-GCM CryptoKey from the SHA-256 of the passphrase.
 * @returns {Promise<CryptoKey>}
 */
export async function getCryptoKey() {
  if (cachedCryptoKey) {
    return cachedCryptoKey;
  }

  const encoder = new TextEncoder();
  const passphraseBytes = encoder.encode(PASSPHRASE);
  const keyHash = await crypto.subtle.digest("SHA-256", passphraseBytes);

  cachedCryptoKey = await crypto.subtle.importKey(
    "raw",
    keyHash,
    { name: "AES-GCM" },
    false,
    ["decrypt", "encrypt"]
  );

  return cachedCryptoKey;
}

/**
 * Decrypts an AES-256-GCM encrypted string.
 * If the value does NOT start with "enc:v1:", it returns the value as-is.
 * 
 * @param {string|any} value - The encrypted string or plain value
 * @returns {Promise<string>} - The decrypted plaintext string
 */
export async function aesDecrypt(value) {
  if (value === null || value === undefined) {
    return "";
  }

  if (typeof value !== "string") {
    return String(value);
  }

  if (!value.startsWith(PREFIX)) {
    return value;
  }

  try {
    const base64Data = value.slice(PREFIX.length);
    const binaryString = atob(base64Data);
    const totalLength = binaryString.length;

    // IV is 12 bytes, Tag is 16 bytes (128 bits) -> minimum 28 bytes
    if (totalLength < 28) {
      console.warn("Invalid encrypted payload length:", totalLength);
      return value;
    }

    const payloadBytes = new Uint8Array(totalLength);
    for (let i = 0; i < totalLength; i++) {
      payloadBytes[i] = binaryString.charCodeAt(i);
    }

    const iv = payloadBytes.subarray(0, 12);
    const ciphertextWithTag = payloadBytes.subarray(12);

    const key = await getCryptoKey();

    const decryptedBuffer = await crypto.subtle.decrypt(
      {
        name: "AES-GCM",
        iv: iv,
        tagLength: 128,
      },
      key,
      ciphertextWithTag
    );

    const decoder = new TextDecoder("utf-8");
    return decoder.decode(decryptedBuffer);
  } catch (error) {
    console.error("Failed to decrypt value:", error);
    return value; // Fallback to raw value if decryption fails
  }
}

/**
 * Helper to encrypt a plain text string in the exact same format
 * (useful for tests and verification).
 * 
 * @param {string} plainText 
 * @returns {Promise<string>} enc:v1:<base64>
 */
export async function aesEncrypt(plainText) {
  if (plainText === null || plainText === undefined) return "";
  const key = await getCryptoKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encoder = new TextEncoder();
  const encodedData = encoder.encode(plainText);

  const encryptedBuffer = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv: iv,
      tagLength: 128,
    },
    key,
    encodedData
  );

  const combined = new Uint8Array(iv.length + encryptedBuffer.byteLength);
  combined.set(iv, 0);
  combined.set(new Uint8Array(encryptedBuffer), iv.length);

  let binary = "";
  for (let i = 0; i < combined.length; i++) {
    binary += String.fromCharCode(combined[i]);
  }

  return PREFIX + btoa(binary);
}

/**
 * Computes the SHA-256 hash of a string and returns it as a lowercase hex string.
 * Used for emailHash and search lookups.
 * 
 * @param {string} value 
 * @returns {Promise<string>} Hex-encoded SHA-256 string
 */
export async function sha256Hex(value) {
  if (typeof value !== "string") {
    return "";
  }

  const encoder = new TextEncoder();
  const data = encoder.encode(value.trim().toLowerCase());
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}
