import React, { useState, useEffect } from "react";
import {
  collection,
  query,
  where,
  onSnapshot,
  doc,
  writeBatch,
  updateDoc,
} from "firebase/firestore";
import { db } from "../firebase";
import { useAuth } from "../hooks/useAuth";
import { useToast } from "../context/ToastContext";
import RegistrationCard from "../components/RegistrationCard";
import ImageModal from "../components/ImageModal";
import ConfirmDialog from "../components/ConfirmDialog";
import { getTimestampMs } from "../utils/dateUtils";
import { UserCheck, ShieldCheck, CheckCircle, Search, Filter } from "lucide-react";

export default function Registrations() {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");

  // Modal State
  const [modalImage, setModalImage] = useState(null);
  const [modalTitle, setModalTitle] = useState("");

  // Confirm Dialog State
  const [dialogState, setDialogState] = useState(null); // { type: 'approve' | 'reject', request, applicantName }
  const [processing, setProcessing] = useState(false);

  // Real-time onSnapshot for pending registrations
  useEffect(() => {
    setLoading(true);
    try {
      const collRef = collection(db, "user_registration_requests");
      const q = query(collRef, where("status", "==", "pending"));

      const unsubscribe = onSnapshot(
        q,
        (snapshot) => {
          const list = [];
          snapshot.forEach((docSnap) => {
            const data = docSnap.data();
            list.push({ id: docSnap.id, ...data });
          });

          // Sort by submittedAt descending (newest first)
          list.sort((a, b) => {
            const timeA = getTimestampMs(a.submittedAt) || 0;
            const timeB = getTimestampMs(b.submittedAt) || 0;
            return timeB - timeA;
          });

          setRequests(list);
          setLoading(false);
        },
        (error) => {
          console.error("Error fetching user registration requests:", error);
          showToast("Failed to fetch pending registrations.", "error");
          setLoading(false);
        }
      );

      return () => unsubscribe();
    } catch (err) {
      console.error("Registrations snapshot setup failed:", err);
      setLoading(false);
    }
  }, [showToast]);

  // Approve action trigger -> show ConfirmDialog
  const handleApproveClick = (request, applicantName) => {
    setDialogState({
      type: "approve",
      request,
      applicantName,
      title: "Approve User Registration",
      message: `Are you sure you want to approve the registration for ${applicantName || "this citizen"}? This creates an official User account in the DQRA system.`,
      confirmText: "Approve Registration",
      confirmColor: "green",
    });
  };

  // Reject action trigger -> show ConfirmDialog
  const handleRejectClick = (request, applicantName) => {
    setDialogState({
      type: "reject",
      request,
      applicantName,
      title: "Reject User Registration",
      message: `Are you sure you want to reject the registration for ${applicantName || "this citizen"}? Their registration request will be marked as rejected.`,
      confirmText: "Reject Registration",
      confirmColor: "red",
    });
  };

  // Execute batch write on confirm
  const handleExecuteDialog = async () => {
    if (!dialogState || !user) return;
    const { type, request, applicantName } = dialogState;
    const targetUid = request.userId || request.id;
    const currentAdminUid = user.uid;

    setProcessing(true);

    try {
      if (type === "approve") {
        const batch = writeBatch(db);

        // 1. Create or set users/{targetUid}
        // IMPORTANT: NEVER decrypt when writing to Firestore! Copy encrypted strings as-is.
        const userDocRef = doc(db, "users", targetUid);
        batch.set(userDocRef, {
          role: "User",
          fullName: request.fullName || "",
          email: request.email || "",
          emailHash: request.emailHash || "",
          phone: request.phone || "",
          address: request.address || "",
          profileCompleted: true,
          profileImageBase64: request.selfieImageBase64 || "",
          createdAt: Date.now(),
          approvedAt: Date.now(),
          approvedBy: currentAdminUid,
        });

        // 2. Update user_registration_requests/{reqId}
        const reqDocRef = doc(db, "user_registration_requests", request.id);
        batch.update(reqDocRef, {
          status: "approved",
          reviewedAt: Date.now(),
          reviewedBy: currentAdminUid,
        });

        await batch.commit();
        showToast(
          `Registration for ${applicantName || "citizen"} approved successfully!`,
          "success"
        );
      } else if (type === "reject") {
        // Update user_registration_requests/{reqId}
        const reqDocRef = doc(db, "user_registration_requests", request.id);
        await updateDoc(reqDocRef, {
          status: "rejected",
          reviewedAt: Date.now(),
          reviewedBy: currentAdminUid,
        });

        showToast(
          `Registration for ${applicantName || "citizen"} was rejected.`,
          "info"
        );
      }

      setDialogState(null);
    } catch (error) {
      console.error(`Error during registration ${type}:`, error);
      showToast(
        `Failed to ${type} registration. Please check network connection.`,
        "error"
      );
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-gray-200">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-700">
              Identity Verification
            </span>
            <span className="text-xs text-gray-500 font-medium">
              National ID & Biometric Verification
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-black text-gray-900 tracking-tight mt-1">
            Pending Citizen Registrations
          </h1>
        </div>

        <div className="flex items-center gap-2">
          <div className="px-3 py-1.5 bg-red-50 text-red-700 rounded-xl font-bold text-xs border border-red-200 flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-red-600 animate-ping"></span>
            <span>{requests.length} Pending Review</span>
          </div>
        </div>
      </div>

      {/* Grid of Pending Registrations */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map((n) => (
            <div
              key={n}
              className="bg-white rounded-2xl p-6 border border-gray-200 shadow-xs animate-pulse space-y-4"
            >
              <div className="flex items-center gap-4">
                <div className="w-16 h-16 rounded-full bg-gray-200"></div>
                <div className="flex-1 space-y-2">
                  <div className="h-5 bg-gray-200 rounded-sm w-3/4"></div>
                  <div className="h-3 bg-gray-200 rounded-sm w-1/2"></div>
                </div>
              </div>
              <div className="h-24 bg-gray-100 rounded-xl"></div>
              <div className="h-28 bg-gray-100 rounded-xl"></div>
            </div>
          ))}
        </div>
      ) : requests.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {requests.map((request) => (
            <RegistrationCard
              key={request.id}
              request={request}
              onApprove={handleApproveClick}
              onReject={handleRejectClick}
              onViewImage={(url, title) => {
                setModalImage(url);
                setModalTitle(title);
              }}
              isProcessing={processing}
            />
          ))}
        </div>
      ) : (
        /* Empty State */
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center shadow-xs">
          <div className="w-16 h-16 rounded-full bg-green-50 text-green-600 flex items-center justify-center mx-auto mb-4 border border-green-200">
            <ShieldCheck className="w-8 h-8" />
          </div>
          <h3 className="text-lg font-bold text-gray-900">
            All Registrations Reviewed
          </h3>
          <p className="mt-1 text-sm text-gray-500 max-w-md mx-auto">
            There are currently no pending registration requests awaiting KYC verification. Incoming registrations from the mobile app will show up here automatically.
          </p>
        </div>
      )}

      {/* Fullscreen Image Preview Modal */}
      <ImageModal
        isOpen={Boolean(modalImage)}
        imageUrl={modalImage}
        title={modalTitle}
        onClose={() => setModalImage(null)}
      />

      {/* Confirmation Dialog */}
      {dialogState && (
        <ConfirmDialog
          isOpen={Boolean(dialogState)}
          title={dialogState.title}
          message={dialogState.message}
          confirmText={dialogState.confirmText}
          confirmColor={dialogState.confirmColor}
          isLoading={processing}
          onConfirm={handleExecuteDialog}
          onCancel={() => setDialogState(null)}
        />
      )}
    </div>
  );
}
