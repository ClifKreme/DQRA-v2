import React, { useEffect } from "react";
import { X, Download } from "lucide-react";

/**
 * Fullscreen dark overlay image viewer.
 * Closes on backdrop click or Escape key, with a download button.
 */
export default function ImageModal({ isOpen, imageUrl, title = "Image Preview", onClose }) {
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    // Prevent scrolling behind modal
    document.body.style.overflow = "hidden";

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "unset";
    };
  }, [isOpen, onClose]);

  if (!isOpen || !imageUrl) return null;

  const handleDownload = (e) => {
    e.stopPropagation();
    try {
      const link = document.createElement("a");
      link.href = imageUrl;
      // Clean filename
      const cleanTitle = title.replace(/[^a-z0-9]/gi, "_").toLowerCase();
      link.download = `${cleanTitle || "dqra_image"}.jpg`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      console.error("Error downloading image:", err);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-sm p-4 transition-opacity animate-fadeIn"
      onClick={onClose}
    >
      {/* Top action bar */}
      <div className="absolute top-4 right-4 flex items-center gap-3 z-10">
        <button
          type="button"
          onClick={handleDownload}
          title="Download Image"
          className="flex items-center gap-2 px-3 py-2 bg-gray-800/90 hover:bg-gray-700 text-white rounded-lg text-sm font-medium transition shadow-lg border border-white/10"
        >
          <Download className="w-4 h-4" />
          <span>Download</span>
        </button>

        <button
          type="button"
          onClick={onClose}
          title="Close (Esc)"
          className="p-2 bg-gray-800/90 hover:bg-gray-700 text-white rounded-lg transition shadow-lg border border-white/10"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Image container */}
      <div
        className="relative max-w-[92vw] max-h-[88vh] flex flex-col items-center"
        onClick={(e) => e.stopPropagation()}
      >
        <img
          src={imageUrl}
          alt={title}
          className="max-w-full max-h-[84vh] object-contain rounded-lg shadow-2xl border border-white/10 bg-black/40"
        />
        {title && (
          <p className="mt-3 text-sm font-medium text-gray-300 text-center tracking-wide">
            {title}
          </p>
        )}
      </div>
    </div>
  );
}
