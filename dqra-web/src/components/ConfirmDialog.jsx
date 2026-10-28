import React, { useEffect } from "react";
import { AlertCircle, AlertTriangle, CheckCircle, HelpCircle } from "lucide-react";

/**
 * Centered modal for confirmation actions.
 * Buttons: Cancel and Confirm (red for reject, green for approve, blue for resolve).
 */
export default function ConfirmDialog({
  isOpen,
  title = "Confirm Action",
  message = "Are you sure you want to proceed?",
  confirmText = "Confirm",
  cancelText = "Cancel",
  confirmColor = "green", // 'green' | 'red' | 'blue'
  isLoading = false,
  onConfirm,
  onCancel,
}) {
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === "Escape" && !isLoading) {
        onCancel();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    document.body.style.overflow = "hidden";

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "unset";
    };
  }, [isOpen, onCancel, isLoading]);

  if (!isOpen) return null;

  let confirmBtnClasses = "bg-green-600 hover:bg-green-700 text-white focus:ring-green-500";
  let IconComponent = CheckCircle;
  let iconBg = "bg-green-100 text-green-600";

  if (confirmColor === "red") {
    confirmBtnClasses = "bg-red-600 hover:bg-red-700 text-white focus:ring-red-500";
    IconComponent = AlertTriangle;
    iconBg = "bg-red-100 text-red-600";
  } else if (confirmColor === "blue") {
    confirmBtnClasses = "bg-blue-600 hover:bg-blue-700 text-white focus:ring-blue-500";
    IconComponent = AlertCircle;
    iconBg = "bg-blue-100 text-blue-600";
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-fadeIn"
      onClick={() => {
        if (!isLoading) onCancel();
      }}
    >
      <div
        className="bg-white rounded-2xl shadow-2xl border border-gray-100 max-w-md w-full p-6 transform transition-all scale-100"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start gap-4">
          <div className={`p-3 rounded-full shrink-0 ${iconBg}`}>
            <IconComponent className="w-6 h-6" />
          </div>
          <div className="flex-1">
            <h3 className="text-lg font-bold text-gray-900 leading-6">
              {title}
            </h3>
            <p className="mt-2 text-sm text-gray-600 leading-5">
              {message}
            </p>
          </div>
        </div>

        <div className="mt-6 flex items-center justify-end gap-3">
          <button
            type="button"
            disabled={isLoading}
            onClick={onCancel}
            className="px-4 py-2 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-xl transition focus:outline-hidden disabled:opacity-50"
          >
            {cancelText}
          </button>
          <button
            type="button"
            disabled={isLoading}
            onClick={onConfirm}
            className={`px-5 py-2 text-sm font-semibold rounded-xl shadow-xs transition focus:outline-hidden disabled:opacity-60 flex items-center gap-2 ${confirmBtnClasses}`}
          >
            {isLoading && (
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            )}
            <span>{confirmText}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
