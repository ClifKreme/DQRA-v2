import React from "react";
import { NavLink, useNavigate } from "react-router-dom";
import {
  ShieldAlert,
  LayoutDashboard,
  UserCheck,
  FileText,
  LogOut,
  MapPin,
  Building2,
  Menu,
  X,
} from "lucide-react";
import { useAuth } from "../hooks/useAuth";
import { useAdminProfile } from "../hooks/useAdminProfile";

/**
 * Sidebar navigation component with active state highlighting,
 * live badge counters, station metadata, and logout.
 */
export default function Sidebar({
  pendingEmergenciesCount = 0,
  pendingRegistrationsCount = 0,
  isMobileOpen = false,
  onMobileClose,
}) {
  const { logout, user } = useAuth();
  const { municipality, stationName, isSuperAdmin } = useAdminProfile();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logout();
      navigate("/login");
    } catch (err) {
      console.error("Logout failed:", err);
    }
  };

  const navItems = [
    {
      name: "Dashboard",
      path: "/dashboard",
      icon: LayoutDashboard,
      badge: pendingEmergenciesCount > 0 ? pendingEmergenciesCount : null,
      badgeColor: "bg-red-500 text-white",
    },
    {
      name: "Registrations",
      path: "/registrations",
      icon: UserCheck,
      badge: pendingRegistrationsCount > 0 ? pendingRegistrationsCount : null,
      badgeColor: "bg-red-600 text-white",
    },
    {
      name: "Logs & History",
      path: "/logs",
      icon: FileText,
      badge: null,
    },
  ];

  return (
    <>
      {/* Mobile Backdrop */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 bg-black/60 z-40 lg:hidden backdrop-blur-xs transition-opacity"
          onClick={onMobileClose}
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed top-0 bottom-0 left-0 z-40 w-72 bg-gray-900 text-gray-200 flex flex-col justify-between border-r border-gray-800 transition-transform duration-300 ease-in-out lg:translate-x-0 ${
          isMobileOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        {/* Top Header & Admin Profile */}
        <div>
          {/* Logo Section */}
          <div className="px-6 py-5 border-b border-gray-800 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-red-600 flex items-center justify-center shadow-lg shadow-red-600/30 text-white">
                <ShieldAlert className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-xl font-black text-white tracking-wider flex items-center gap-1">
                  DQRA <span className="text-red-500 text-xs font-bold px-1.5 py-0.5 rounded-md bg-red-950 border border-red-800">ADMIN</span>
                </h1>
                <p className="text-[11px] text-gray-400 font-medium">Disaster Quick Response</p>
              </div>
            </div>

            {/* Mobile close button */}
            <button
              type="button"
              onClick={onMobileClose}
              className="lg:hidden text-gray-400 hover:text-white p-1"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Admin Info Box */}
          <div className="p-4 mx-4 my-4 rounded-xl bg-gray-800/80 border border-gray-700/60 shadow-inner">
            <div className="flex items-center gap-2 mb-1.5">
              <div className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></div>
              <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">
                {isSuperAdmin ? "Super Admin" : "Station Admin"}
              </span>
            </div>

            <div className="text-xs text-gray-300 font-medium truncate mb-2">
              {user?.email || "admin@dqra.gov"}
            </div>

            <div className="space-y-1 text-xs text-gray-400">
              <div className="flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-red-400 shrink-0" />
                <span className="font-semibold text-gray-200 truncate">
                  {municipality || "All Municipalities"}
                </span>
              </div>
              {stationName && (
                <div className="flex items-center gap-1.5">
                  <Building2 className="w-3.5 h-3.5 text-blue-400 shrink-0" />
                  <span className="truncate text-gray-300">{stationName}</span>
                </div>
              )}
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="px-3 space-y-1.5">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  onClick={() => onMobileClose && onMobileClose()}
                  className={({ isActive }) =>
                    `flex items-center justify-between px-3.5 py-3 rounded-xl font-medium text-sm transition-all group ${
                      isActive
                        ? "bg-red-600 text-white font-semibold shadow-md shadow-red-600/20"
                        : "text-gray-300 hover:bg-gray-800 hover:text-white"
                    }`
                  }
                >
                  <div className="flex items-center gap-3">
                    <Icon className="w-5 h-5 transition-transform group-hover:scale-110" />
                    <span>{item.name}</span>
                  </div>
                  {item.badge !== null && (
                    <span
                      className={`text-xs font-bold px-2 py-0.5 rounded-full min-w-5 text-center ${item.badgeColor}`}
                    >
                      {item.badge}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </nav>
        </div>

        {/* Logout and Footer */}
        <div className="p-4 border-t border-gray-800">
          <button
            type="button"
            onClick={handleLogout}
            className="w-full flex items-center justify-center gap-2.5 px-4 py-2.5 rounded-xl bg-gray-800/80 hover:bg-red-600/20 text-gray-300 hover:text-red-400 border border-gray-700/60 hover:border-red-500/40 text-sm font-semibold transition group"
          >
            <LogOut className="w-4 h-4 transition-transform group-hover:-translate-x-0.5" />
            <span>Sign Out</span>
          </button>
          <div className="mt-3 text-center text-[10px] text-gray-500">
            DQRA Web Console &bull; v1.0.0
          </div>
        </div>
      </aside>
    </>
  );
}
