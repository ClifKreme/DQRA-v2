import React, { useState, useEffect, useRef } from "react";
import {
  collection,
  query,
  where,
  onSnapshot,
  doc,
  updateDoc,
  serverTimestamp,
} from "firebase/firestore";
import { db } from "../firebase";
import { useAuth } from "../hooks/useAuth";
import { useAdminProfile } from "../hooks/useAdminProfile";
import { useToast } from "../context/ToastContext";
import { playEmergencyBeep, sendBrowserNotification } from "../utils/audio";
import { isToday, isThisMonth, getTimestampMs } from "../utils/dateUtils";
import StatsCards from "../components/StatsCards";
import EmergencyCard from "../components/EmergencyCard";
import ImageModal from "../components/ImageModal";
import ConfirmDialog from "../components/ConfirmDialog";
import {
  ShieldAlert,
  Radio,
  Filter,
  RefreshCw,
  Bell,
  CheckCircle,
} from "lucide-react";

export default function Dashboard() {
  const { user } = useAuth();
  const { municipality, isSuperAdmin } = useAdminProfile();
  const { showToast } = useToast();

  const [emergencies, setEmergencies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("active"); // 'active' (pending + confirmed), 'all', 'pending', 'confirmed', 'resolved'

  // Image Modal State
  const [modalImage, setModalImage] = useState(null);
  const [modalTitle, setModalTitle] = useState("");

  // Confirmation Dialog State
  const [confirmAction, setConfirmAction] = useState(null); // { type: 'reject' | 'resolve', emergency }
  const [actionLoading, setActionLoading] = useState(false);

  // Sound/Notification tracking ref to prevent alerting on initial load
  const isInitialLoadRef = useRef(true);
  const knownEmergencyIdsRef = useRef(new Set());

  // Real-time onSnapshot listener
  useEffect(() => {
    setLoading(true);
    isInitialLoadRef.current = true;
    knownEmergencyIdsRef.current.clear();

    try {
      const collRef = collection(db, "emergency_locations");
      let q;

      // Filter by admin municipality if not super admin
      if (!isSuperAdmin && municipality && municipality !== "All Municipalities") {
        q = query(collRef, where("municipality", "==", municipality));
      } else {
        q = query(collRef);
      }

      const unsubscribe = onSnapshot(
        q,
        (snapshot) => {
          const list = [];
          let hasNewPending = false;
          let newestPendingDoc = null;

          snapshot.forEach((docSnap) => {
            const data = docSnap.data();
            const emergency = {
              id: docSnap.id,
              ...data,
              latitude: data.latitude ?? null,
              longitude: data.longitude ?? null,
              timestamp: data.timestamp ?? null,
            };
            list.push(emergency);

            // Check for new pending emergencies after initial load
            if (!isInitialLoadRef.current) {
              if (
                emergency.status === "pending" &&
                !knownEmergencyIdsRef.current.has(emergency.id)
              ) {
                hasNewPending = true;
                newestPendingDoc = emergency;
              }
            }

            knownEmergencyIdsRef.current.add(emergency.id);
          });

          // Trigger audio and push notification if new pending emergency arrives
          if (hasNewPending && newestPendingDoc) {
            playEmergencyBeep();
            sendBrowserNotification("🚨 New Emergency Alert!", {
              body: `New pending report in ${newestPendingDoc.municipality || "your area"} by ${
                newestPendingDoc.reportedBy || "Citizen"
              }`,
            });
            showToast(
              `New emergency reported in ${newestPendingDoc.municipality || "your area"}!`,
              "warning"
            );
          }

          isInitialLoadRef.current = false;
          setEmergencies(list);
          setLoading(false);
        },
        (error) => {
          console.error("Firestore onSnapshot error on emergency_locations:", error);
          showToast("Failed to connect to live emergency feed.", "error");
          setLoading(false);
        }
      );

      return () => unsubscribe();
    } catch (err) {
      console.error("Dashboard listener setup failed:", err);
      setLoading(false);
    }
  }, [municipality, isSuperAdmin, showToast]);

  // Compute Metrics for Stats Cards
  const pendingList = emergencies.filter((e) => e.status === "pending");
  const confirmedList = emergencies.filter((e) => e.status === "confirmed");
  const resolvedTodayList = emergencies.filter(
    (e) =>
      (e.status === "resolved" || e.status === "completed") &&
      isToday(e.resolvedAt || e.timestamp)
  );
  const totalThisMonthList = emergencies.filter((e) =>
    isThisMonth(e.timestamp)
  );

  // Filter and Sort:
  // Pending emergencies first (oldest first: ascending timestamp),
  // followed by Confirmed emergencies (oldest first or newest first).
  const displayedEmergencies = emergencies
    .filter((item) => {
      if (statusFilter === "active") {
        return item.status === "pending" || item.status === "confirmed";
      }
      if (statusFilter === "all") return true;
      return item.status === statusFilter;
    })
    .sort((a, b) => {
      const aStatus = (a.status || "").toLowerCase();
      const bStatus = (b.status || "").toLowerCase();

      // Pending always comes first
      if (aStatus === "pending" && bStatus !== "pending") return -1;
      if (bStatus === "pending" && aStatus !== "pending") return 1;

      // Confirmed comes second
      if (aStatus === "confirmed" && bStatus !== "confirmed") return -1;
      if (bStatus === "confirmed" && aStatus !== "confirmed") return 1;

      // Oldest first for pending emergencies so earliest reported gets handled first
      const timeA = getTimestampMs(a.timestamp) || 0;
      const timeB = getTimestampMs(b.timestamp) || 0;

      if (aStatus === "pending") {
        return timeA - timeB; // Oldest first
      }

      // For others, newer first
      return timeB - timeA;
    });

  // Action Handlers
  const handleConfirmEmergency = async (emergency) => {
    try {
      setActionLoading(true);
      const emergencyRef = doc(db, "emergency_locations", emergency.id);
      await updateDoc(emergencyRef, {
        status: "confirmed",
        confirmedBy: user?.uid || "Admin",
        confirmedAt: serverTimestamp(),
      });
      showToast(`Emergency reported by ${emergency.reportedBy || "citizen"} has been confirmed!`, "success");
    } catch (err) {
      console.error("Error confirming emergency:", err);
      showToast("Failed to confirm emergency. Please check permissions.", "error");
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenRejectDialog = (emergency) => {
    setConfirmAction({
      type: "reject",
      emergency,
      title: "Reject Emergency Report",
      message: `Are you sure you want to reject this emergency report from ${emergency.reportedBy || "the citizen"}? This marks the report as invalid.`,
      confirmColor: "red",
      confirmText: "Reject Report",
    });
  };

  const handleOpenResolveDialog = (emergency) => {
    setConfirmAction({
      type: "resolve",
      emergency,
      title: "Mark Emergency as Resolved",
      message: `Are you sure you want to mark the emergency reported by ${emergency.reportedBy || "the citizen"} as resolved? This confirms all disaster responders have concluded assistance.`,
      confirmColor: "blue",
      confirmText: "Mark as Resolved",
    });
  };

  const handleExecuteDialogAction = async () => {
    if (!confirmAction) return;
    const { type, emergency } = confirmAction;

    try {
      setActionLoading(true);
      const emergencyRef = doc(db, "emergency_locations", emergency.id);

      if (type === "reject") {
        await updateDoc(emergencyRef, {
          status: "rejected",
        });
        showToast("Emergency report rejected.", "info");
      } else if (type === "resolve") {
        await updateDoc(emergencyRef, {
          status: "resolved",
          resolvedAt: serverTimestamp(),
          resolvedBy: user?.uid || "Admin",
        });
        showToast("Emergency marked as successfully resolved.", "success");
      }

      setConfirmAction(null);
    } catch (err) {
      console.error(`Error executing ${type}:`, err);
      showToast(`Failed to ${type} emergency.`, "error");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-gray-200">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-700">
              <span className="w-1.5 h-1.5 rounded-full bg-red-600 mr-1.5 animate-ping"></span>
              Live Operations
            </span>
            <span className="text-xs text-gray-500 font-medium">
              Station Jurisdiction: <strong className="text-gray-900">{municipality || "All Municipalities"}</strong>
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-black text-gray-900 tracking-tight mt-1">
            Emergency Dispatch Center
          </h1>
        </div>

        {/* Action / Audio Beep Test & Notification Info */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => {
              playEmergencyBeep();
              showToast("Test alert beep played.", "info");
            }}
            title="Test Alert Tone"
            className="flex items-center gap-2 px-3 py-2 bg-white hover:bg-gray-50 border border-gray-200 rounded-xl text-xs font-semibold text-gray-700 shadow-xs transition"
          >
            <Bell className="w-4 h-4 text-red-600" />
            <span>Test Siren</span>
          </button>
        </div>
      </div>

      {/* 4 Stats Cards */}
      <StatsCards
        pendingCount={pendingList.length}
        confirmedCount={confirmedList.length}
        resolvedTodayCount={resolvedTodayList.length}
        totalThisMonthCount={totalThisMonthList.length}
        loading={loading}
      />

      {/* Filter Tabs Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-4 rounded-2xl border border-gray-200 shadow-xs">
        <div className="flex items-center gap-2 text-sm font-bold text-gray-800">
          <Radio className="w-4 h-4 text-red-600 animate-pulse" />
          <span>Active Emergencies Stream</span>
          <span className="ml-1 text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 font-semibold">
            {displayedEmergencies.length}
          </span>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-1.5 flex-wrap">
          <button
            type="button"
            onClick={() => setStatusFilter("active")}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition ${
              statusFilter === "active"
                ? "bg-red-600 text-white shadow-xs"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            Active & Pending ({pendingList.length + confirmedList.length})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter("pending")}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition ${
              statusFilter === "pending"
                ? "bg-red-600 text-white shadow-xs"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            Pending Only ({pendingList.length})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter("confirmed")}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition ${
              statusFilter === "confirmed"
                ? "bg-red-600 text-white shadow-xs"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            Confirmed Only ({confirmedList.length})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter("all")}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition ${
              statusFilter === "all"
                ? "bg-gray-900 text-white shadow-xs"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            All Logs
          </button>
        </div>
      </div>

      {/* Grid of Emergency Cards */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3, 4, 5, 6].map((n) => (
            <div
              key={n}
              className="bg-white rounded-2xl p-6 border border-gray-200 shadow-xs animate-pulse space-y-4"
            >
              <div className="h-6 bg-gray-200 rounded-md w-1/3"></div>
              <div className="flex gap-3">
                <div className="w-12 h-12 rounded-full bg-gray-200"></div>
                <div className="flex-1 space-y-2">
                  <div className="h-4 bg-gray-200 rounded-sm w-3/4"></div>
                  <div className="h-3 bg-gray-200 rounded-sm w-1/2"></div>
                </div>
              </div>
              <div className="h-20 bg-gray-100 rounded-xl"></div>
              <div className="h-10 bg-gray-200 rounded-xl"></div>
            </div>
          ))}
        </div>
      ) : displayedEmergencies.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {displayedEmergencies.map((emergency) => (
            <EmergencyCard
              key={emergency.id}
              emergency={emergency}
              onConfirm={handleConfirmEmergency}
              onReject={handleOpenRejectDialog}
              onResolve={handleOpenResolveDialog}
              onViewImage={(url, title) => {
                setModalImage(url);
                setModalTitle(title);
              }}
              isProcessing={actionLoading}
            />
          ))}
        </div>
      ) : (
        /* Empty State */
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center shadow-xs">
          <div className="w-16 h-16 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto mb-4 border border-emerald-200">
            <CheckCircle className="w-8 h-8" />
          </div>
          <h3 className="text-lg font-bold text-gray-900">
            No Active Emergencies in {municipality || "All Municipalities"}
          </h3>
          <p className="mt-1 text-sm text-gray-500 max-w-md mx-auto">
            All reports have been addressed or no incidents have been recorded. New incoming distress calls will alert this console automatically.
          </p>
        </div>
      )}

      {/* Image Modal */}
      <ImageModal
        isOpen={Boolean(modalImage)}
        imageUrl={modalImage}
        title={modalTitle}
        onClose={() => setModalImage(null)}
      />

      {/* Action Confirm Dialog */}
      {confirmAction && (
        <ConfirmDialog
          isOpen={Boolean(confirmAction)}
          title={confirmAction.title}
          message={confirmAction.message}
          confirmText={confirmAction.confirmText}
          confirmColor={confirmAction.confirmColor}
          isLoading={actionLoading}
          onConfirm={handleExecuteDialogAction}
          onCancel={() => setConfirmAction(null)}
        />
      )}
    </div>
  );
}
