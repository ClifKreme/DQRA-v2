import React, { createContext, useContext, useState, useCallback } from "react";
import { CheckCircle2, AlertTriangle, AlertCircle, Info, X } from "lucide-react";

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback((message, type = "info", duration = 4000) => {
    const id = Date.now() + Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, message, type }]);

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  }, [removeToast]);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {/* Toast Render Container */}
      <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 max-w-md w-full pointer-events-none px-4">
        {toasts.map((toast) => {
          let bg = "bg-gray-900 text-white";
          let Icon = Info;
          let iconColor = "text-blue-400";

          if (toast.type === "success") {
            bg = "bg-green-800 text-white";
            Icon = CheckCircle2;
            iconColor = "text-green-300";
          } else if (toast.type === "error") {
            bg = "bg-red-800 text-white";
            Icon = AlertCircle;
            iconColor = "text-red-300";
          } else if (toast.type === "warning") {
            bg = "bg-amber-800 text-white";
            Icon = AlertTriangle;
            iconColor = "text-amber-300";
          }

          return (
            <div
              key={toast.id}
              className={`pointer-events-auto flex items-start gap-3 p-4 rounded-xl shadow-xl border border-white/10 ${bg} transition-all duration-300 transform translate-y-0`}
            >
              <Icon className={`w-5 h-5 mt-0.5 shrink-0 ${iconColor}`} />
              <div className="flex-1 text-sm font-medium leading-5">
                {toast.message}
              </div>
              <button
                type="button"
                onClick={() => removeToast(toast.id)}
                className="text-white/70 hover:text-white p-0.5 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context;
}
