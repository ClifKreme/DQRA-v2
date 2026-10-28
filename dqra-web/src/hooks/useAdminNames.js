import { useState, useEffect, useMemo } from "react";
import { doc, getDoc } from "firebase/firestore";
import { db } from "../firebase";
import { aesDecrypt } from "../crypto";

// In-memory cache shared across the application session
const globalNameCache = new Map();
const inFlightPromises = new Map();

/**
 * Resolves a single admin UID into a decrypted display name.
 * 
 * 1. Checks admins_with_stations/{uid} -> decrypts stationName
 * 2. Checks users/{uid} -> decrypts fullName
 * 3. Checks main/{uid} -> decrypts name / stationName / fullName
 * 4. Fallback: UID truncated to 8 characters + '...'
 * 
 * @param {string} uid 
 * @returns {Promise<string>}
 */
export async function resolveAdminName(uid) {
  if (!uid || typeof uid !== "string" || !uid.trim()) {
    return "—";
  }

  const cleanUid = uid.trim();

  // Return cached result if available
  if (globalNameCache.has(cleanUid)) {
    return globalNameCache.get(cleanUid);
  }

  // Deduplicate ongoing network requests
  if (inFlightPromises.has(cleanUid)) {
    return inFlightPromises.get(cleanUid);
  }

  const promise = (async () => {
    try {
      // 1. Check admins_with_stations/{uid}
      const adminDocRef = doc(db, "admins_with_stations", cleanUid);
      const adminDocSnap = await getDoc(adminDocRef);

      if (adminDocSnap.exists()) {
        const data = adminDocSnap.data() || {};
        if (data.stationName) {
          const decrypted = await aesDecrypt(data.stationName);
          if (decrypted && decrypted.trim()) {
            const name = decrypted.trim();
            globalNameCache.set(cleanUid, name);
            return name;
          }
        }
      }

      // 2. Fallback: check users/{uid}
      const userDocRef = doc(db, "users", cleanUid);
      const userDocSnap = await getDoc(userDocRef);

      if (userDocSnap.exists()) {
        const data = userDocSnap.data() || {};
        if (data.fullName) {
          const decrypted = await aesDecrypt(data.fullName);
          if (decrypted && decrypted.trim()) {
            const name = decrypted.trim();
            globalNameCache.set(cleanUid, name);
            return name;
          }
        }
      }

      // 3. Fallback: check main/{uid} (e.g. Super Admin)
      const mainDocRef = doc(db, "main", cleanUid);
      const mainDocSnap = await getDoc(mainDocRef);

      if (mainDocSnap.exists()) {
        const data = mainDocSnap.data() || {};
        const candidateField = data.stationName || data.name || data.fullName;
        if (candidateField) {
          const decrypted = await aesDecrypt(candidateField);
          if (decrypted && decrypted.trim()) {
            const name = decrypted.trim();
            globalNameCache.set(cleanUid, name);
            return name;
          }
        }
      }

      // 4. Default fallback: UID truncated to 8 characters with ...
      const truncated = cleanUid.length > 8 ? `${cleanUid.substring(0, 8)}...` : cleanUid;
      globalNameCache.set(cleanUid, truncated);
      return truncated;
    } catch (err) {
      console.warn(`Error resolving admin name for UID ${cleanUid}:`, err);
      const truncated = cleanUid.length > 8 ? `${cleanUid.substring(0, 8)}...` : cleanUid;
      globalNameCache.set(cleanUid, truncated);
      return truncated;
    } finally {
      inFlightPromises.delete(cleanUid);
    }
  })();

  inFlightPromises.set(cleanUid, promise);
  return promise;
}

/**
 * Custom hook to resolve and cache display names for an array of admin UIDs.
 * 
 * @param {Array<string>} uids - Array of Firebase Auth UIDs
 * @returns {{ nameMap: Object, getAdminName: Function, loading: boolean }}
 */
export function useAdminNames(uids = []) {
  // Deduplicate and filter valid UIDs
  const uniqueUids = useMemo(() => {
    const set = new Set();
    if (Array.isArray(uids)) {
      for (const u of uids) {
        if (u && typeof u === "string" && u.trim()) {
          set.add(u.trim());
        }
      }
    }
    return Array.from(set);
  }, [JSON.stringify(uids)]);

  // Local state with immediate cache hits
  const [nameMap, setNameMap] = useState(() => {
    const initial = {};
    for (const uid of uniqueUids) {
      if (globalNameCache.has(uid)) {
        initial[uid] = globalNameCache.get(uid);
      }
    }
    return initial;
  });

  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let isMounted = true;

    // Check which UIDs are missing from the cache
    const missingUids = uniqueUids.filter((uid) => !globalNameCache.has(uid));

    if (missingUids.length === 0) {
      // All UIDs are cached; populate state immediately
      const cachedMap = {};
      uniqueUids.forEach((uid) => {
        cachedMap[uid] = globalNameCache.get(uid);
      });
      setNameMap(cachedMap);
      return;
    }

    setLoading(true);

    Promise.all(uniqueUids.map((uid) => resolveAdminName(uid)))
      .then((resolvedNames) => {
        if (isMounted) {
          const updatedMap = {};
          uniqueUids.forEach((uid, index) => {
            updatedMap[uid] = resolvedNames[index];
          });
          setNameMap(updatedMap);
          setLoading(false);
        }
      })
      .catch((err) => {
        console.error("Failed resolving admin names:", err);
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [uniqueUids]);

  /**
   * Helper function to get the resolved name or a loading indicator.
   * 
   * @param {string} uid 
   * @returns {string|null} The resolved name, or null if currently loading
   */
  const getAdminName = (uid) => {
    if (!uid || typeof uid !== "string" || !uid.trim()) {
      return "—";
    }
    const cleanUid = uid.trim();
    if (nameMap[cleanUid]) {
      return nameMap[cleanUid];
    }
    if (globalNameCache.has(cleanUid)) {
      return globalNameCache.get(cleanUid);
    }
    return null; // indicates still loading
  };

  return { nameMap, getAdminName, loading };
}

export default useAdminNames;
