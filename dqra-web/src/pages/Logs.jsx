import React, { useState, useEffect, useMemo } from "react";
import {
  collection,
  query,
  where,
  getDocs,
  orderBy,
} from "firebase/firestore";
import { db } from "../firebase";
import { useAdminProfile } from "../hooks/useAdminProfile";
import { aesDecrypt } from "../crypto";
import { formatDateTime, getTimestampMs } from "../utils/dateUtils";
import { formatBase64Image } from "../utils/imageUtils";
import ImageModal from "../components/ImageModal";
import {
  FileText,
  Clock,
  MapPin,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  Filter,
  RefreshCw,
  Search,
  CheckCircle,
  AlertTriangle,
  UserCheck,
  ShieldAlert,
} from "lucide-react";
import { useAdminNames } from "../hooks/useAdminNames";

/**
 * Resolves the relevant admin UID for an emergency report based on status:
 * - confirmedBy if status is "confirmed"
 * - resolvedBy if status is "resolved" or "completed"
 * - resolvedBy or confirmedBy (whichever present) for "expired"
 * - fallback to resolvedBy or confirmedBy for other handled reports
 */
function getHandledByUid(item) {
  if (!item) return null;
  const status = (item.status || "").toLowerCase();
  if (status === "confirmed") {
    return item.confirmedBy || null;
  }
  if (status === "resolved" || status === "completed") {
    return item.resolvedBy || null;
  }
  if (status === "expired") {
    return item.resolvedBy || item.confirmedBy || null;
  }
  return item.resolvedBy || item.confirmedBy || null;
}

export default function Logs() {
  const { municipality, isSuperAdmin } = useAdminProfile();

  const [activeTab, setActiveTab] = useState("emergencies"); // 'emergencies' | 'registrations'

  // Emergency Logs State
  const [emergencyLogs, setEmergencyLogs] = useState([]);
  const [loadingEmergencies, setLoadingEmergencies] = useState(false);
  const [emergencyStatusFilter, setEmergencyStatusFilter] = useState("all");
  const [emergencyStartDate, setEmergencyStartDate] = useState("");
  const [emergencyEndDate, setEmergencyEndDate] = useState("");
  const [emergencyPage, setEmergencyPage] = useState(1);

  // Registration Logs State
  const [registrationLogs, setRegistrationLogs] = useState([]);
  const [decryptedRegistrations, setDecryptedRegistrations] = useState([]);
  const [loadingRegistrations, setLoadingRegistrations] = useState(false);
  const [registrationStatusFilter, setRegistrationStatusFilter] = useState("all");
  const [registrationStartDate, setRegistrationStartDate] = useState("");
  const [registrationEndDate, setRegistrationEndDate] = useState("");
  const [registrationPage, setRegistrationPage] = useState(1);

  // Modal State
  const [modalImage, setModalImage] = useState(null);
  const [modalTitle, setModalTitle] = useState("");

  const PAGE_SIZE = 20;

  // Extract all relevant Admin UIDs across both tables for batch lookup and decryption
  const allAdminUids = useMemo(() => {
    const uids = new Set();
    emergencyLogs.forEach((item) => {
      const uid = getHandledByUid(item);
      if (uid && typeof uid === "string") {
        uids.add(uid);
      }
    });
    registrationLogs.forEach((item) => {
      if (item.reviewedBy && typeof item.reviewedBy === "string") {
        uids.add(item.reviewedBy);
      }
    });
    return Array.from(uids);
  }, [emergencyLogs, registrationLogs]);

  const { getAdminName } = useAdminNames(allAdminUids);

  // 1. Fetch Emergency Logs
  const fetchEmergencyLogs = async () => {
    setLoadingEmergencies(true);
    try {
      const collRef = collection(db, "emergency_locations");
      let q;

      if (!isSuperAdmin && municipality && municipality !== "All Municipalities") {
        q = query(collRef, where("municipality", "==", municipality));
      } else {
        q = query(collRef);
      }

      const snapshot = await getDocs(q);
      const list = [];
      snapshot.forEach((d) => {
        const data = d.data();
        list.push({
          id: d.id,
          ...data,
          latitude: data.latitude ?? null,
          longitude: data.longitude ?? null,
          timestamp: data.timestamp ?? null,
        });
      });

      // Sort by timestamp desc
      list.sort((a, b) => {
        const timeA = getTimestampMs(a.timestamp) || 0;
        const timeB = getTimestampMs(b.timestamp) || 0;
        return timeB - timeA;
      });

      setEmergencyLogs(list);
    } catch (err) {
      console.error("Error fetching emergency logs:", err);
    } finally {
      setLoadingEmergencies(false);
    }
  };

  // 2. Fetch Registration Logs
  const fetchRegistrationLogs = async () => {
    setLoadingRegistrations(true);
    try {
      const collRef = collection(db, "user_registration_requests");
      const snapshot = await getDocs(collRef);
      const list = [];
      snapshot.forEach((d) => {
        const data = d.data();
        list.push({ id: d.id, ...data });
      });

      // Sort by submittedAt desc
      list.sort((a, b) => {
        const timeA = getTimestampMs(a.submittedAt) || 0;
        const timeB = getTimestampMs(b.submittedAt) || 0;
        return timeB - timeA;
      });

      setRegistrationLogs(list);

      // Decrypt PII for all fetched items
      const decryptedList = await Promise.all(
        list.map(async (item) => {
          try {
            const [decName, decEmail, decPhone] = await Promise.all([
              aesDecrypt(item.fullName),
              aesDecrypt(item.email),
              aesDecrypt(item.phone),
            ]);
            return {
              ...item,
              decryptedFullName: decName || "Unknown",
              decryptedEmail: decEmail || "N/A",
              decryptedPhone: decPhone || "N/A",
            };
          } catch (e) {
            return {
              ...item,
              decryptedFullName: item.fullName || "Error",
              decryptedEmail: item.email || "N/A",
              decryptedPhone: item.phone || "N/A",
            };
          }
        })
      );

      setDecryptedRegistrations(decryptedList);
    } catch (err) {
      console.error("Error fetching registration logs:", err);
    } finally {
      setLoadingRegistrations(false);
    }
  };

  useEffect(() => {
    fetchEmergencyLogs();
    fetchRegistrationLogs();
  }, [municipality, isSuperAdmin]);

  // Filter Emergency Logs
  const filteredEmergencies = useMemo(() => {
    return emergencyLogs.filter((item) => {
      // Status filter
      if (
        emergencyStatusFilter !== "all" &&
        (item.status || "").toLowerCase() !== emergencyStatusFilter.toLowerCase()
      ) {
        return false;
      }

      // Date range filter
      const itemTime = getTimestampMs(item.timestamp);
      if (itemTime) {
        if (emergencyStartDate) {
          const startMs = new Date(emergencyStartDate).setHours(0, 0, 0, 0);
          if (itemTime < startMs) return false;
        }
        if (emergencyEndDate) {
          const endMs = new Date(emergencyEndDate).setHours(23, 59, 59, 999);
          if (itemTime > endMs) return false;
        }
      }

      return true;
    });
  }, [emergencyLogs, emergencyStatusFilter, emergencyStartDate, emergencyEndDate]);

  // Filter Registration Logs
  const filteredRegistrations = useMemo(() => {
    return decryptedRegistrations.filter((item) => {
      // Status filter
      if (
        registrationStatusFilter !== "all" &&
        (item.status || "").toLowerCase() !== registrationStatusFilter.toLowerCase()
      ) {
        return false;
      }

      // Date range filter
      const itemTime = getTimestampMs(item.submittedAt);
      if (itemTime) {
        if (registrationStartDate) {
          const startMs = new Date(registrationStartDate).setHours(0, 0, 0, 0);
          if (itemTime < startMs) return false;
        }
        if (registrationEndDate) {
          const endMs = new Date(registrationEndDate).setHours(23, 59, 59, 999);
          if (itemTime > endMs) return false;
        }
      }

      return true;
    });
  }, [
    decryptedRegistrations,
    registrationStatusFilter,
    registrationStartDate,
    registrationEndDate,
  ]);

  // Pagination slicing
  const emergencyTotalPages = Math.max(1, Math.ceil(filteredEmergencies.length / PAGE_SIZE));
  const currentEmergencies = filteredEmergencies.slice(
    (emergencyPage - 1) * PAGE_SIZE,
    emergencyPage * PAGE_SIZE
  );

  const registrationTotalPages = Math.max(1, Math.ceil(filteredRegistrations.length / PAGE_SIZE));
  const currentRegistrations = filteredRegistrations.slice(
    (registrationPage - 1) * PAGE_SIZE,
    registrationPage * PAGE_SIZE
  );

  // Status Badge Helper
  const renderStatusBadge = (status = "") => {
    const s = status.toLowerCase();
    let bg = "bg-gray-100 text-gray-700 border-gray-200";
    if (s === "pending") bg = "bg-red-50 text-red-700 border-red-200 font-bold";
    else if (s === "confirmed") bg = "bg-amber-50 text-amber-700 border-amber-200 font-bold";
    else if (s === "resolved" || s === "completed" || s === "approved")
      bg = "bg-green-50 text-green-700 border-green-200 font-bold";
    else if (s === "rejected") bg = "bg-red-50 text-gray-700 border-gray-200";
    else if (s === "expired" || s === "cancelled") bg = "bg-gray-100 text-gray-500 border-gray-200";

    return (
      <span className={`inline-flex px-2.5 py-1 text-xs rounded-full border ${bg}`}>
        {status.toUpperCase()}
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-gray-200">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-gray-200 text-gray-800">
              Audit & Archive
            </span>
            <span className="text-xs text-gray-500 font-medium">
              Historical Emergency & Registration Records
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-black text-gray-900 tracking-tight mt-1">
            System Activity Logs
          </h1>
        </div>

        {/* Tab switcher */}
        <div className="flex bg-gray-200/80 p-1 rounded-xl">
          <button
            type="button"
            onClick={() => {
              setActiveTab("emergencies");
              setEmergencyPage(1);
            }}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-bold transition ${
              activeTab === "emergencies"
                ? "bg-white text-gray-900 shadow-xs"
                : "text-gray-600 hover:text-gray-900"
            }`}
          >
            <ShieldAlert className="w-4 h-4 text-red-600" />
            <span>Emergency Logs</span>
            <span className="px-1.5 py-0.2 rounded-full bg-gray-100 text-[11px] text-gray-600">
              {filteredEmergencies.length}
            </span>
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab("registrations");
              setRegistrationPage(1);
            }}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-bold transition ${
              activeTab === "registrations"
                ? "bg-white text-gray-900 shadow-xs"
                : "text-gray-600 hover:text-gray-900"
            }`}
          >
            <UserCheck className="w-4 h-4 text-green-600" />
            <span>Registration Logs</span>
            <span className="px-1.5 py-0.2 rounded-full bg-gray-100 text-[11px] text-gray-600">
              {filteredRegistrations.length}
            </span>
          </button>
        </div>
      </div>

      {/* TAB 1: EMERGENCY LOGS */}
      {activeTab === "emergencies" && (
        <div className="space-y-4">
          {/* Filter Bar */}
          <div className="bg-white p-4 rounded-2xl border border-gray-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              {/* Status Dropdown */}
              <div>
                <label className="block text-[11px] font-bold text-gray-500 uppercase tracking-wider mb-1">
                  Status
                </label>
                <select
                  value={emergencyStatusFilter}
                  onChange={(e) => {
                    setEmergencyStatusFilter(e.target.value);
                    setEmergencyPage(1);
                  }}
                  className="bg-gray-50 border border-gray-200 text-gray-800 text-xs rounded-xl px-3 py-2 focus:ring-2 focus:ring-red-500 focus:outline-hidden font-medium"
                >
                  <option value="all">All Statuses</option>
                  <option value="pending">Pending</option>
                  <option value="confirmed">Confirmed</option>
                  <option value="resolved">Resolved</option>
                  <option value="rejected">Rejected</option>
                  <option value="cancelled">Cancelled</option>
                  <option value="expired">Expired</option>
                  <option value="completed">Completed</option>
                </select>
              </div>

              {/* Start Date */}
              <div>
                <label className="block text-[11px] font-bold text-gray-500 uppercase tracking-wider mb-1">
                  From Date
                </label>
                <input
                  type="date"
                  value={emergencyStartDate}
                  onChange={(e) => {
                    setEmergencyStartDate(e.target.value);
                    setEmergencyPage(1);
                  }}
                  className="bg-gray-50 border border-gray-200 text-gray-800 text-xs rounded-xl px-3 py-1.5 focus:ring-2 focus:ring-red-500 focus:outline-hidden font-medium"
                />
              </div>

              {/* End Date */}
              <div>
                <label className="block text-[11px] font-bold text-gray-500 uppercase tracking-wider mb-1">
                  To Date
                </label>
                <input
                  type="date"
                  value={emergencyEndDate}
                  onChange={(e) => {
                    setEmergencyEndDate(e.target.value);
                    setEmergencyPage(1);
                  }}
                  className="bg-gray-50 border border-gray-200 text-gray-800 text-xs rounded-xl px-3 py-1.5 focus:ring-2 focus:ring-red-500 focus:outline-hidden font-medium"
                />
              </div>
            </div>

            {/* Reset / Refresh buttons */}
            <div className="flex items-end gap-2 pt-4 sm:pt-0">
              <button
                type="button"
                onClick={() => {
                  setEmergencyStatusFilter("all");
                  setEmergencyStartDate("");
                  setEmergencyEndDate("");
                  setEmergencyPage(1);
                }}
                className="px-3 py-2 text-xs font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition"
              >
                Reset Filters
              </button>
              <button
                type="button"
                onClick={fetchEmergencyLogs}
                title="Refresh Logs"
                className="p-2 text-gray-600 hover:text-gray-900 bg-gray-100 hover:bg-gray-200 rounded-xl transition"
              >
                <RefreshCw className={`w-4 h-4 ${loadingEmergencies ? "animate-spin" : ""}`} />
              </button>
            </div>
          </div>

          {/* Table Container */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-gray-50/80 border-b border-gray-200 text-gray-500 uppercase tracking-wider font-bold">
                  <tr>
                    <th className="py-3.5 px-4">Date & Time</th>
                    <th className="py-3.5 px-4">Reporter</th>
                    <th className="py-3.5 px-4">Phone</th>
                    <th className="py-3.5 px-4">Municipality</th>
                    <th className="py-3.5 px-4">Status</th>
                    <th className="py-3.5 px-4">Handled By</th>
                    <th className="py-3.5 px-4">Location</th>
                    <th className="py-3.5 px-4 text-center">Photo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {loadingEmergencies ? (
                    <tr>
                      <td colSpan="8" className="py-8 text-center text-gray-500">
                        <div className="flex items-center justify-center gap-2">
                          <span className="w-4 h-4 border-2 border-red-600 border-t-transparent rounded-full animate-spin"></span>
                          <span>Loading emergency logs...</span>
                        </div>
                      </td>
                    </tr>
                  ) : currentEmergencies.length > 0 ? (
                    currentEmergencies.map((item) => {
                      const lat = typeof item.latitude === "string" ? parseFloat(item.latitude) : item.latitude;
                      const lng = typeof item.longitude === "string" ? parseFloat(item.longitude) : item.longitude;
                      const hasGps = lat != null && lng != null && !isNaN(lat) && !isNaN(lng) && !(lat === 0 && lng === 0);
                      const mapsUrl = `https://www.google.com/maps?q=${lat},${lng}`;
                      const hasPhoto = item.hasImage && item.imageBase64;
                      const handledUid = getHandledByUid(item);
                      const handledDisplayName = handledUid ? getAdminName(handledUid) : null;

                      return (
                        <tr key={item.id} className="hover:bg-gray-50/80 transition-colors">
                          <td className="py-3 px-4 whitespace-nowrap font-medium text-gray-900">
                            {formatDateTime(item.timestamp)}
                          </td>
                          <td className="py-3 px-4 font-semibold text-gray-800">
                            {item.reportedBy || "Anonymous Citizen"}
                          </td>
                          <td className="py-3 px-4">
                            {item.phoneNumber ? (
                              <a
                                href={`tel:${item.phoneNumber}`}
                                className="text-red-600 hover:underline font-medium"
                              >
                                {item.phoneNumber}
                              </a>
                            ) : (
                              <span className="text-gray-400">N/A</span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-gray-600 font-medium">
                            {item.municipality || "—"}
                          </td>
                          <td className="py-3 px-4">{renderStatusBadge(item.status)}</td>
                          <td className="py-3 px-4 text-xs">
                            {handledUid ? (
                              handledDisplayName === null ? (
                                <span className="text-gray-400 italic text-[11px] animate-pulse">
                                  Loading...
                                </span>
                              ) : (
                                <span className="font-semibold text-gray-800">
                                  {handledDisplayName}
                                </span>
                              )
                            ) : (
                              <span className="text-gray-400">—</span>
                            )}
                          </td>
                          <td className="py-3 px-4 whitespace-nowrap">
                            {hasGps ? (
                              <div className="flex items-center gap-2">
                                <span className="font-mono text-gray-700">📍 {Number(lat).toFixed(5)}, {Number(lng).toFixed(5)}</span>
                                <a
                                  href={mapsUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="inline-flex items-center gap-1 text-blue-600 underline hover:text-blue-800 font-medium"
                                >
                                  <span>View Map</span>
                                  <ExternalLink className="w-3 h-3" />
                                </a>
                              </div>
                            ) : (
                              <span className="text-gray-400">—</span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-center">
                            {hasPhoto ? (
                              <button
                                type="button"
                                onClick={() => {
                                  const imgUri = formatBase64Image(item.imageBase64);
                                  if (imgUri) {
                                    setModalImage(imgUri);
                                    setModalTitle(`Emergency: ${item.reportedBy} (${item.municipality})`);
                                  }
                                }}
                                className="px-2 py-1 bg-gray-900 hover:bg-black text-white rounded-md text-[11px] font-semibold transition"
                              >
                                View
                              </button>
                            ) : (
                              <span className="text-gray-400 text-[11px]">—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan="8" className="py-8 text-center text-gray-500">
                        No emergency logs match your filter criteria.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="px-4 py-3 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
              <div className="text-xs text-gray-600 font-medium">
                Showing{" "}
                <span className="font-bold">
                  {filteredEmergencies.length > 0 ? (emergencyPage - 1) * PAGE_SIZE + 1 : 0}
                </span>{" "}
                to{" "}
                <span className="font-bold">
                  {Math.min(emergencyPage * PAGE_SIZE, filteredEmergencies.length)}
                </span>{" "}
                of <span className="font-bold">{filteredEmergencies.length}</span> records
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={emergencyPage <= 1}
                  onClick={() => setEmergencyPage((p) => Math.max(1, p - 1))}
                  className="p-1.5 rounded-lg border border-gray-200 bg-white text-gray-700 hover:bg-gray-100 disabled:opacity-40 transition"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="text-xs font-semibold text-gray-700 px-2">
                  Page {emergencyPage} of {emergencyTotalPages}
                </span>
                <button
                  type="button"
                  disabled={emergencyPage >= emergencyTotalPages}
                  onClick={() => setEmergencyPage((p) => Math.min(emergencyTotalPages, p + 1))}
                  className="p-1.5 rounded-lg border border-gray-200 bg-white text-gray-700 hover:bg-gray-100 disabled:opacity-40 transition"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: REGISTRATION LOGS */}
      {activeTab === "registrations" && (
        <div className="space-y-4">
          {/* Filter Bar */}
          <div className="bg-white p-4 rounded-2xl border border-gray-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              {/* Status Dropdown */}
              <div>
                <label className="block text-[11px] font-bold text-gray-500 uppercase tracking-wider mb-1">
                  Status
                </label>
                <select
                  value={registrationStatusFilter}
                  onChange={(e) => {
                    setRegistrationStatusFilter(e.target.value);
                    setRegistrationPage(1);
                  }}
                  className="bg-gray-50 border border-gray-200 text-gray-800 text-xs rounded-xl px-3 py-2 focus:ring-2 focus:ring-red-500 focus:outline-hidden font-medium"
                >
                  <option value="all">All Registrations</option>
                  <option value="pending">Pending</option>
                  <option value="approved">Approved</option>
                  <option value="rejected">Rejected</option>
                </select>
              </div>

              {/* Start Date */}
              <div>
                <label className="block text-[11px] font-bold text-gray-500 uppercase tracking-wider mb-1">
                  From Date
                </label>
                <input
                  type="date"
                  value={registrationStartDate}
                  onChange={(e) => {
                    setRegistrationStartDate(e.target.value);
                    setRegistrationPage(1);
                  }}
                  className="bg-gray-50 border border-gray-200 text-gray-800 text-xs rounded-xl px-3 py-1.5 focus:ring-2 focus:ring-red-500 focus:outline-hidden font-medium"
                />
              </div>

              {/* End Date */}
              <div>
                <label className="block text-[11px] font-bold text-gray-500 uppercase tracking-wider mb-1">
                  To Date
                </label>
                <input
                  type="date"
                  value={registrationEndDate}
                  onChange={(e) => {
                    setRegistrationEndDate(e.target.value);
                    setRegistrationPage(1);
                  }}
                  className="bg-gray-50 border border-gray-200 text-gray-800 text-xs rounded-xl px-3 py-1.5 focus:ring-2 focus:ring-red-500 focus:outline-hidden font-medium"
                />
              </div>
            </div>

            {/* Reset / Refresh buttons */}
            <div className="flex items-end gap-2 pt-4 sm:pt-0">
              <button
                type="button"
                onClick={() => {
                  setRegistrationStatusFilter("all");
                  setRegistrationStartDate("");
                  setRegistrationEndDate("");
                  setRegistrationPage(1);
                }}
                className="px-3 py-2 text-xs font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition"
              >
                Reset Filters
              </button>
              <button
                type="button"
                onClick={fetchRegistrationLogs}
                title="Refresh Logs"
                className="p-2 text-gray-600 hover:text-gray-900 bg-gray-100 hover:bg-gray-200 rounded-xl transition"
              >
                <RefreshCw className={`w-4 h-4 ${loadingRegistrations ? "animate-spin" : ""}`} />
              </button>
            </div>
          </div>

          {/* Table Container */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-gray-50/80 border-b border-gray-200 text-gray-500 uppercase tracking-wider font-bold">
                  <tr>
                    <th className="py-3.5 px-4">Submitted</th>
                    <th className="py-3.5 px-4">Full Name (Decrypted)</th>
                    <th className="py-3.5 px-4">Email</th>
                    <th className="py-3.5 px-4">Phone</th>
                    <th className="py-3.5 px-4">Status</th>
                    <th className="py-3.5 px-4">Reviewed By</th>
                    <th className="py-3.5 px-4">Review Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {loadingRegistrations ? (
                    <tr>
                      <td colSpan="7" className="py-8 text-center text-gray-500">
                        <div className="flex items-center justify-center gap-2">
                          <span className="w-4 h-4 border-2 border-red-600 border-t-transparent rounded-full animate-spin"></span>
                          <span>Loading and decrypting registration logs...</span>
                        </div>
                      </td>
                    </tr>
                  ) : currentRegistrations.length > 0 ? (
                    currentRegistrations.map((item) => {
                      const reviewedByUid = item.reviewedBy;
                      const reviewerDisplayName = reviewedByUid
                        ? getAdminName(reviewedByUid)
                        : null;

                      return (
                        <tr key={item.id} className="hover:bg-gray-50/80 transition-colors">
                          <td className="py-3 px-4 whitespace-nowrap font-medium text-gray-900">
                            {formatDateTime(item.submittedAt)}
                          </td>
                          <td className="py-3 px-4 font-bold text-gray-900">
                            {item.decryptedFullName}
                          </td>
                          <td className="py-3 px-4 text-gray-600 select-all">
                            {item.decryptedEmail}
                          </td>
                          <td className="py-3 px-4 font-mono">
                            {item.decryptedPhone}
                          </td>
                          <td className="py-3 px-4">{renderStatusBadge(item.status)}</td>
                          <td className="py-3 px-4 text-xs">
                            {reviewedByUid ? (
                              reviewerDisplayName === null ? (
                                <span className="text-gray-400 italic text-[11px] animate-pulse">
                                  Loading...
                                </span>
                              ) : (
                                <span className="font-semibold text-gray-800">
                                  {reviewerDisplayName}
                                </span>
                              )
                            ) : (
                              <span className="text-gray-400">—</span>
                            )}
                          </td>
                          <td className="py-3 px-4 whitespace-nowrap text-gray-500">
                            {item.reviewedAt ? formatDateTime(item.reviewedAt) : "—"}
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan="7" className="py-8 text-center text-gray-500">
                        No registration logs match your filter criteria.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="px-4 py-3 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
              <div className="text-xs text-gray-600 font-medium">
                Showing{" "}
                <span className="font-bold">
                  {filteredRegistrations.length > 0 ? (registrationPage - 1) * PAGE_SIZE + 1 : 0}
                </span>{" "}
                to{" "}
                <span className="font-bold">
                  {Math.min(registrationPage * PAGE_SIZE, filteredRegistrations.length)}
                </span>{" "}
                of <span className="font-bold">{filteredRegistrations.length}</span> records
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={registrationPage <= 1}
                  onClick={() => setRegistrationPage((p) => Math.max(1, p - 1))}
                  className="p-1.5 rounded-lg border border-gray-200 bg-white text-gray-700 hover:bg-gray-100 disabled:opacity-40 transition"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="text-xs font-semibold text-gray-700 px-2">
                  Page {registrationPage} of {registrationTotalPages}
                </span>
                <button
                  type="button"
                  disabled={registrationPage >= registrationTotalPages}
                  onClick={() => setRegistrationPage((p) => Math.min(registrationTotalPages, p + 1))}
                  className="p-1.5 rounded-lg border border-gray-200 bg-white text-gray-700 hover:bg-gray-100 disabled:opacity-40 transition"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Image Modal for inspection */}
      <ImageModal
        isOpen={Boolean(modalImage)}
        imageUrl={modalImage}
        title={modalTitle}
        onClose={() => setModalImage(null)}
      />
    </div>
  );
}
