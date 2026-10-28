import React, { useState, useEffect } from "react";
import {
  User,
  Mail,
  Phone,
  MapPin,
  Clock,
  CheckCircle,
  XCircle,
  ShieldCheck,
  IdCard,
  Maximize2,
} from "lucide-react";
import { aesDecrypt } from "../crypto";
import { formatDateTime, formatRelativeTime } from "../utils/dateUtils";
import {
  formatBase64Image,
  getNationalIdFront,
  getNationalIdBack,
  getSelfieImage,
} from "../utils/imageUtils";

/**
 * Registration Card component.
 * Decrypts encrypted PII (Full Name, Email, Phone, Address) using Web Crypto AES-256-GCM.
 * Provides fullscreen modal inspection for Selfie, ID Front, and ID Back.
 */
export default function RegistrationCard({
  request,
  onApprove,
  onReject,
  onViewImage,
  isProcessing = false,
}) {
  const [decryptedData, setDecryptedData] = useState({
    fullName: "Decrypting...",
    email: "Decrypting...",
    phone: "Decrypting...",
    address: "Decrypting...",
    isDecrypted: false,
  });

  const {
    id,
    userId,
    submittedAt,
    status = "pending",
  } = request;

  // Asynchronously decrypt PII fields using Web Crypto AES-256-GCM
  useEffect(() => {
    let isMounted = true;

    async function decryptFields() {
      try {
        const [name, email, phone, addr] = await Promise.all([
          aesDecrypt(request.fullName),
          aesDecrypt(request.email),
          aesDecrypt(request.phone),
          aesDecrypt(request.address),
        ]);

        if (isMounted) {
          setDecryptedData({
            fullName: name || "Unknown",
            email: email || "N/A",
            phone: phone || "N/A",
            address: addr || "N/A",
            isDecrypted: true,
          });
        }
      } catch (err) {
        console.error("Error decrypting registration card fields:", err);
        if (isMounted) {
          setDecryptedData({
            fullName: request.fullName || "Decryption Failed",
            email: request.email || "N/A",
            phone: request.phone || "N/A",
            address: request.address || "N/A",
            isDecrypted: true,
          });
        }
      }
    }

    decryptFields();

    return () => {
      isMounted = false;
    };
  }, [request.fullName, request.email, request.phone, request.address]);

  // Image references with proper fallback
  const selfieSrc = getSelfieImage(request);
  const idFrontSrc = getNationalIdFront(request);
  const idBackSrc = getNationalIdBack(request);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-xs hover:shadow-md transition-all overflow-hidden flex flex-col justify-between">
      {/* Header with Submitted Time */}
      <div className="px-5 py-3.5 bg-gray-50/80 border-b border-gray-100 flex items-center justify-between text-xs text-gray-500">
        <div className="flex items-center gap-1.5 font-medium">
          <Clock className="w-3.5 h-3.5 text-gray-400" />
          <span>Submitted {formatRelativeTime(submittedAt)}</span>
        </div>
        <span className="font-mono text-gray-400 text-[11px]">
          ID: {(userId || id || "").substring(0, 12)}...
        </span>
      </div>

      {/* Body */}
      <div className="p-5 flex-1 space-y-5">
        {/* User Identity Header */}
        <div className="flex items-start gap-4">
          {selfieSrc ? (
            <div className="relative group shrink-0">
              <img
                src={selfieSrc}
                alt="Selfie"
                onClick={() =>
                  onViewImage &&
                  onViewImage(selfieSrc, `${decryptedData.fullName} - Selfie`)
                }
                className="w-16 h-16 rounded-full object-cover border-2 border-red-100 shadow-xs cursor-pointer hover:brightness-95 transition"
              />
              <button
                type="button"
                onClick={() =>
                  onViewImage &&
                  onViewImage(selfieSrc, `${decryptedData.fullName} - Selfie`)
                }
                className="absolute inset-0 bg-black/40 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition text-white"
                title="View Fullscreen"
              >
                <Maximize2 className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <div className="w-16 h-16 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center shrink-0 text-gray-400">
              <User className="w-8 h-8" />
            </div>
          )}

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-bold text-gray-900 leading-tight truncate">
                {decryptedData.fullName}
              </h3>
            </div>
            <p className="text-xs text-gray-500 mt-0.5">
              {formatDateTime(submittedAt)}
            </p>
          </div>
        </div>

        {/* Decrypted Contact Info Table */}
        <div className="bg-gray-50 rounded-xl p-3.5 space-y-2 text-xs border border-gray-100">
          <div className="flex items-center gap-2 text-gray-700">
            <Mail className="w-4 h-4 text-gray-400 shrink-0" />
            <span className="font-semibold text-gray-500 w-16">Email:</span>
            <span className="font-medium text-gray-900 truncate select-all">
              {decryptedData.email}
            </span>
          </div>
          <div className="flex items-center gap-2 text-gray-700">
            <Phone className="w-4 h-4 text-gray-400 shrink-0" />
            <span className="font-semibold text-gray-500 w-16">Phone:</span>
            {decryptedData.phone !== "N/A" ? (
              <a
                href={`tel:${decryptedData.phone}`}
                className="font-medium text-red-600 hover:text-red-700 hover:underline select-all"
              >
                {decryptedData.phone}
              </a>
            ) : (
              <span className="font-medium text-gray-400">N/A</span>
            )}
          </div>
          <div className="flex items-start gap-2 text-gray-700">
            <MapPin className="w-4 h-4 text-gray-400 shrink-0 mt-0.5" />
            <span className="font-semibold text-gray-500 w-16 shrink-0">Address:</span>
            <span className="font-medium text-gray-900 leading-snug">
              {decryptedData.address}
            </span>
          </div>
        </div>

        {/* National ID Verification Documents */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5 text-xs font-bold text-gray-700 uppercase tracking-wider">
              <IdCard className="w-4 h-4 text-red-600" />
              <span>National ID Documents</span>
            </div>
            <span className="text-[11px] text-gray-400 font-medium">
              Click to enlarge
            </span>
          </div>

          <div className="grid grid-cols-2 gap-3">
            {/* ID Front */}
            <div className="space-y-1">
              <div
                onClick={() =>
                  idFrontSrc &&
                  onViewImage &&
                  onViewImage(
                    idFrontSrc,
                    `${decryptedData.fullName} - National ID Front`
                  )
                }
                className={`relative aspect-4/3 rounded-xl border border-gray-200 overflow-hidden bg-gray-100 flex items-center justify-center group ${
                  idFrontSrc ? "cursor-pointer hover:border-red-400" : ""
                }`}
              >
                {idFrontSrc ? (
                  <>
                    <img
                      src={idFrontSrc}
                      alt="National ID Front"
                      className="w-full h-full object-cover transition group-hover:scale-105"
                    />
                    <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition flex items-center justify-center text-white">
                      <Maximize2 className="w-5 h-5" />
                    </div>
                  </>
                ) : (
                  <span className="text-xs text-gray-400 font-medium">No Front ID</span>
                )}
              </div>
              <span className="block text-center text-[11px] font-semibold text-gray-500">
                Front Side
              </span>
            </div>

            {/* ID Back */}
            <div className="space-y-1">
              <div
                onClick={() =>
                  idBackSrc &&
                  onViewImage &&
                  onViewImage(
                    idBackSrc,
                    `${decryptedData.fullName} - National ID Back`
                  )
                }
                className={`relative aspect-4/3 rounded-xl border border-gray-200 overflow-hidden bg-gray-100 flex items-center justify-center group ${
                  idBackSrc ? "cursor-pointer hover:border-red-400" : ""
                }`}
              >
                {idBackSrc ? (
                  <>
                    <img
                      src={idBackSrc}
                      alt="National ID Back"
                      className="w-full h-full object-cover transition group-hover:scale-105"
                    />
                    <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition flex items-center justify-center text-white">
                      <Maximize2 className="w-5 h-5" />
                    </div>
                  </>
                ) : (
                  <span className="text-xs text-gray-400 font-medium">No Back ID</span>
                )}
              </div>
              <span className="block text-center text-[11px] font-semibold text-gray-500">
                Back Side
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="p-4 bg-gray-50/80 border-t border-gray-100">
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            disabled={isProcessing}
            onClick={() => onApprove(request, decryptedData.fullName)}
            className="flex items-center justify-center gap-1.5 py-2.5 px-4 rounded-xl bg-green-600 hover:bg-green-700 active:bg-green-800 text-white font-bold text-xs shadow-xs transition disabled:opacity-50"
          >
            <CheckCircle className="w-4 h-4" />
            <span>✓ Approve</span>
          </button>
          <button
            type="button"
            disabled={isProcessing}
            onClick={() => onReject(request, decryptedData.fullName)}
            className="flex items-center justify-center gap-1.5 py-2.5 px-4 rounded-xl bg-red-600 hover:bg-red-700 active:bg-red-800 text-white font-bold text-xs shadow-xs transition disabled:opacity-50"
          >
            <XCircle className="w-4 h-4" />
            <span>✕ Reject</span>
          </button>
        </div>
      </div>
    </div>
  );
}
