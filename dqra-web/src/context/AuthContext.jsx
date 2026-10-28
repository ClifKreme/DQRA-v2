import React, { createContext, useContext, useEffect, useState } from "react";
import {
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  onAuthStateChanged,
} from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { aesDecrypt } from "../crypto";
import { requestNotificationPermission } from "../utils/audio";

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [adminProfile, setAdminProfile] = useState(null);
  const [municipality, setMunicipality] = useState("");
  const [stationName, setStationName] = useState("");
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [authError, setAuthError] = useState("");

  /**
   * Validates if the authenticated UID is in admins_with_stations or main.
   * If not, signs out and returns null.
   */
  const checkAuthorization = async (uid, authEmail) => {
    try {
      // 1. Check admins_with_stations/{uid}
      const stationDocRef = doc(db, "admins_with_stations", uid);
      const stationDocSnap = await getDoc(stationDocRef);

      // 2. Check main/{uid}
      const mainDocRef = doc(db, "main", uid);
      const mainDocSnap = await getDoc(mainDocRef);

      const hasStation = stationDocSnap.exists();
      const isSuper = mainDocSnap.exists();

      if (!hasStation && !isSuper) {
        // Neither admin nor super admin -> Unauthorized
        await firebaseSignOut(auth);
        return {
          authorized: false,
          error: "Access denied. This account is not authorized.",
        };
      }

      let adminMunicipality = "";
      let adminStationName = "";
      let rawData = {};

      if (hasStation) {
        rawData = stationDocSnap.data() || {};
        adminMunicipality = rawData.municipality || "";
        const rawStation = rawData.stationName || "";
        adminStationName = await aesDecrypt(rawStation);
      }

      if (isSuper && !adminMunicipality) {
        adminMunicipality = "All Municipalities";
      }

      return {
        authorized: true,
        profile: {
          uid,
          email: authEmail,
          municipality: adminMunicipality,
          stationName: adminStationName,
          isSuperAdmin: isSuper,
          ...rawData,
        },
      };
    } catch (err) {
      console.error("Error verifying admin authorization:", err);
      await firebaseSignOut(auth);
      return {
        authorized: false,
        error: "Verification failed. Please check network or credentials.",
      };
    }
  };

  // Listen to Auth State
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      setLoading(true);
      if (currentUser) {
        const result = await checkAuthorization(currentUser.uid, currentUser.email);
        if (result.authorized) {
          setUser(currentUser);
          setAdminProfile(result.profile);
          setMunicipality(result.profile.municipality);
          setStationName(result.profile.stationName);
          setIsSuperAdmin(result.profile.isSuperAdmin);
          setAuthError("");
        } else {
          setUser(null);
          setAdminProfile(null);
          setMunicipality("");
          setStationName("");
          setIsSuperAdmin(false);
          setAuthError(result.error || "Access denied. This account is not authorized.");
        }
      } else {
        setUser(null);
        setAdminProfile(null);
        setMunicipality("");
        setStationName("");
        setIsSuperAdmin(false);
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  /**
   * Log in with Email & Password and verify Firestore roles.
   */
  const login = async (email, password) => {
    setLoading(true);
    setAuthError("");

    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const currentUser = userCredential.user;

      const result = await checkAuthorization(currentUser.uid, currentUser.email);

      if (!result.authorized) {
        const errMsg = result.error || "Access denied. This account is not authorized.";
        setAuthError(errMsg);
        setLoading(false);
        throw new Error(errMsg);
      }

      setUser(currentUser);
      setAdminProfile(result.profile);
      setMunicipality(result.profile.municipality);
      setStationName(result.profile.stationName);
      setIsSuperAdmin(result.profile.isSuperAdmin);
      setAuthError("");

      // Request browser push notification permission on successful login
      requestNotificationPermission().catch(() => {});

      setLoading(false);
      return result.profile;
    } catch (err) {
      setLoading(false);
      let message = err.message;
      if (err.code === "auth/invalid-credential" || err.code === "auth/user-not-found" || err.code === "auth/wrong-password") {
        message = "Invalid email or password.";
      } else if (err.code === "auth/too-many-requests") {
        message = "Too many failed login attempts. Please try again later.";
      }
      setAuthError(message);
      throw new Error(message);
    }
  };

  /**
   * Signs out current user.
   */
  const logout = async () => {
    try {
      await firebaseSignOut(auth);
      setUser(null);
      setAdminProfile(null);
      setMunicipality("");
      setStationName("");
      setIsSuperAdmin(false);
    } catch (error) {
      console.error("Sign out error:", error);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        adminProfile,
        municipality,
        setMunicipality,
        stationName,
        isSuperAdmin,
        loading,
        authError,
        setAuthError,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
