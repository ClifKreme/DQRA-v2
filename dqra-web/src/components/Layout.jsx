import React, { useState, useEffect } from "react";
import { Outlet } from "react-router-dom";
import { collection, query, where, onSnapshot } from "firebase/firestore";
import { db } from "../firebase";
import { useAdminProfile } from "../hooks/useAdminProfile";
import Sidebar from "./Sidebar";
import { Menu, ShieldAlert } from "lucide-react";

/**
 * Main application layout wrapping protected routes.
 * Maintains global counts for pending emergencies and registration requests for the sidebar badges.
 */
export default function Layout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [pendingEmergenciesCount, setPendingEmergenciesCount] = useState(0);
  const [pendingRegistrationsCount, setPendingRegistrationsCount] = useState(0);
  const { municipality, isSuperAdmin } = useAdminProfile();

  // Listen to pending emergencies count
  useEffect(() => {
    try {
      const collRef = collection(db, "emergency_locations");
      let q;

      // Filter by municipality for station admins
      if (!isSuperAdmin && municipality && municipality !== "All Municipalities") {
        q = query(
          collRef,
          where("municipality", "==", municipality),
          where("status", "==", "pending")
        );
      } else {
        q = query(collRef, where("status", "==", "pending"));
      }

      const unsubscribe = onSnapshot(
        q,
        (snapshot) => {
          setPendingEmergenciesCount(snapshot.size);
        },
        (error) => {
          console.warn("Error listening to pending emergencies badge count:", error);
        }
      );

      return () => unsubscribe();
    } catch (err) {
      console.error("Layout emergency badge listener error:", err);
    }
  }, [municipality, isSuperAdmin]);

  // Listen to pending user registrations count
  useEffect(() => {
    try {
      const collRef = collection(db, "user_registration_requests");
      const q = query(collRef, where("status", "==", "pending"));

      const unsubscribe = onSnapshot(
        q,
        (snapshot) => {
          setPendingRegistrationsCount(snapshot.size);
        },
        (error) => {
          console.warn("Error listening to pending registrations badge count:", error);
        }
      );

      return () => unsubscribe();
    } catch (err) {
      console.error("Layout registration badge listener error:", err);
    }
  }, []);

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col lg:flex-row">
      {/* Sidebar navigation */}
      <Sidebar
        pendingEmergenciesCount={pendingEmergenciesCount}
        pendingRegistrationsCount={pendingRegistrationsCount}
        isMobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
      />

      {/* Main Content Area */}
      <div className="flex-1 lg:pl-72 flex flex-col min-h-screen">
        {/* Mobile top bar */}
        <header className="lg:hidden bg-gray-900 border-b border-gray-800 px-4 py-3 flex items-center justify-between text-white sticky top-0 z-30 shadow-md">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="p-2 text-gray-300 hover:text-white rounded-lg hover:bg-gray-800"
          >
            <Menu className="w-6 h-6" />
          </button>

          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-red-600 flex items-center justify-center">
              <ShieldAlert className="w-5 h-5 text-white" />
            </div>
            <span className="font-black tracking-wide text-base">DQRA ADMIN</span>
          </div>

          <div className="flex items-center gap-1.5">
            {pendingEmergenciesCount > 0 && (
              <span className="bg-red-600 text-white text-xs font-black px-2 py-0.5 rounded-full animate-pulse">
                {pendingEmergenciesCount} alert{pendingEmergenciesCount > 1 ? "s" : ""}
              </span>
            )}
          </div>
        </header>

        {/* Page View */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
