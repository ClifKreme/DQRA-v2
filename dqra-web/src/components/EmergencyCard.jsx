import React, { useState } from "react";
import {
  MapPin,
  Phone,
  Clock,
  CheckCircle2,
  XCircle,
  Camera,
  User,
  ShieldAlert,
  ExternalLink,
} from "lucide-react";
import { formatDateTime, formatRelativeTime } from "../utils/dateUtils";
import { formatBase64Image } from "../utils/imageUtils";
import { useAdminNames } from "../hooks/useAdminNames";

/**
 * Emergency Card component for displaying and managing incident reports.
 */
export default function EmergencyCard({
  emergency,
  onConfirm,
  onReject,
  onResolve,
  onViewImage,
  isProcessing = false,
}) {
  const [imgError, setImgError] = useState(false);

  const {
    id,
    reportedBy = "Anonymous Citizen",
    phoneNumber,
    municipality,
    latitude,
    longitude,
    timestamp,
    status = "pending",
    hasImage,
    imageBase64,
    reportedByProfileImageBase64,
    confirmedBy,
    resolvedBy,
  } = emergency;

  const handlerUid = resolvedBy || confirmedBy;
  const { getAdminName } = useAdminNames(handlerUid ? [handlerUid] : []);

  // Format profile image
  const profileImgSrc = !imgError && reportedByProfileImageBase64
    ? formatBase64Image(reportedByProfileImageBase64)
    : null;

  // Status badge styling
  let badgeClasses = "bg-gray-100 text-gray-700 border-gray-200";
  let statusLabel = status.toUpperCase();

  switch (status.toLowerCase()) {
    case "pending":
      badgeClasses = "bg-red-50 text-red-700 border-red-200 ring-1 ring-red-300 animate-pulse";
      statusLabel = "PENDING REVIEW";
      break;
    case "confirmed":
      badgeClasses = "bg-amber-50 text-amber-700 border-amber-200 ring-1 ring-amber-300";
      statusLabel = "CONFIRMED / ACTIVE";
      break;
    case "resolved":
    case "completed":
      badgeClasses = "bg-green-50 text-green-700 border-green-200";
      statusLabel = "RESOLVED";
      break;
    case "rejected":
      badgeClasses = "bg-red-50 text-gray-600 border-gray-200";
      statusLabel = "REJECTED";
      break;
    case "cancelled":
    case "expired":
      badgeClasses = "bg-gray-100 text-gray-500 border-gray-200";
      statusLabel = status.toUpperCase();
      break;
    default:
      badgeClasses = "bg-gray-100 text-gray-700 border-gray-200";
  }

  const lat = typeof emergency.latitude === "string" ? parseFloat(emergency.latitude) : emergency.latitude;
  const lng = typeof emergency.longitude === "string" ? parseFloat(emergency.longitude) : emergency.longitude;
  const hasGps = lat != null && lng != null && !isNaN(lat) && !isNaN(lng) && !(lat === 0 && lng === 0);
  const mapsUrl = `https://www.google.com/maps?q=${lat},${lng}`;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-xs hover:shadow-md transition-all overflow-hidden flex flex-col justify-between">
      {/* Top Banner with Status & Relative Time */}
      <div className="px-5 pt-4 pb-3 border-b border-gray-100 flex items-center justify-between gap-3 bg-gray-50/60">
        <span
          className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold tracking-wide border ${badgeClasses}`}
        >
          {statusLabel}
        </span>
        <div className="flex items-center gap-1.5 text-xs text-gray-500 font-medium">
          <Clock className="w-3.5 h-3.5 text-gray-400" />
          <span>{formatRelativeTime(timestamp)}</span>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="p-5 flex-1 space-y-4">
        {/* Reporter Info Row */}
        <div className="flex items-start gap-3.5">
          {profileImgSrc ? (
            <img
              src={profileImgSrc}
              alt={reportedBy}
              onError={() => setImgError(true)}
              onClick={() => onViewImage && onViewImage(profileImgSrc, `${reportedBy} - Profile Photo`)}
              className="w-12 h-12 rounded-full object-cover border-2 border-gray-100 shrink-0 shadow-xs cursor-pointer hover:opacity-90"
            />
          ) : (
            <div className="w-12 h-12 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center shrink-0 text-gray-400">
              <User className="w-6 h-6" />
            </div>
          )}

          <div className="min-w-0 flex-1">
            <h4 className="font-bold text-gray-900 text-base leading-tight truncate">
              {reportedBy}
            </h4>
            {phoneNumber ? (
              <a
                href={`tel:${phoneNumber}`}
                className="mt-1 inline-flex items-center gap-1.5 text-sm font-semibold text-red-600 hover:text-red-700 hover:underline"
              >
                <Phone className="w-3.5 h-3.5" />
                <span>{phoneNumber}</span>
              </a>
            ) : (
              <span className="text-xs text-gray-400">No phone provided</span>
            )}
            <div className="mt-1 text-xs text-gray-500">
              Municipality: <span className="font-semibold text-gray-700">{municipality || "N/A"}</span>
            </div>
          </div>
        </div>

        {/* Date & Time details */}
        <div className="text-xs text-gray-500 bg-gray-50 rounded-xl p-2.5 flex items-center justify-between">
          <span className="font-medium">Reported:</span>
          <span className="font-semibold text-gray-800">{formatDateTime(timestamp)}</span>
        </div>

        {/* Location & GPS Info */}
        <div className="space-y-2">
          {hasGps ? (
            <div className="flex items-center gap-2 text-sm text-gray-600">
              <span>📍 {lat.toFixed(5)}, {lng.toFixed(5)}</span>
              <a
                href={mapsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-600 underline hover:text-blue-800 font-medium"
              >
                Open in Maps
              </a>
            </div>
          ) : (
            <div className="text-sm text-gray-400">📍 Location unavailable</div>
          )}
        </div>

        {/* Image Attachment Button */}
        {hasImage && imageBase64 && (
          <div className="pt-1">
            <button
              type="button"
              onClick={() => {
                const fullImg = formatBase64Image(imageBase64);
                if (onViewImage && fullImg) {
                  onViewImage(fullImg, `Emergency Scene - ${reportedBy} (${municipality})`);
                }
              }}
              className="w-full flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl bg-gray-900 hover:bg-black text-white text-xs font-semibold shadow-xs transition"
            >
              <Camera className="w-4 h-4 text-amber-400" />
              <span>📷 View Photo</span>
            </button>
          </div>
        )}
      </div>

      {/* Action Footer */}
      <div className="p-4 bg-gray-50/80 border-t border-gray-100">
        {status === "pending" && (
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              disabled={isProcessing}
              onClick={() => onConfirm(emergency)}
              className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-xl bg-green-600 hover:bg-green-700 active:bg-green-800 text-white font-bold text-xs shadow-xs transition disabled:opacity-50"
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>✓ Confirm</span>
            </button>
            <button
              type="button"
              disabled={isProcessing}
              onClick={() => onReject(emergency)}
              className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-xl bg-red-600 hover:bg-red-700 active:bg-red-800 text-white font-bold text-xs shadow-xs transition disabled:opacity-50"
            >
              <XCircle className="w-4 h-4" />
              <span>✕ Reject</span>
            </button>
          </div>
        )}

        {status === "confirmed" && (
          <button
            type="button"
            disabled={isProcessing}
            onClick={() => onResolve(emergency)}
            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-bold text-xs shadow-xs transition disabled:opacity-50"
          >
            <CheckCircle2 className="w-4 h-4" />
            <span>✓ Mark as Resolved</span>
          </button>
        )}

        {status !== "pending" && status !== "confirmed" && (
          <div className="text-center text-xs text-gray-500 font-medium py-1">
            Handled by:{" "}
            <span className="font-semibold text-gray-700">
              {handlerUid
                ? getAdminName(handlerUid) ||
                  (handlerUid.length > 8 ? `${handlerUid.substring(0, 8)}...` : handlerUid)
                : "Admin"}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
