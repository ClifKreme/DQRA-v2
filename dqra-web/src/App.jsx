import React from "react";
import {
  Routes,
  Route,
  Navigate,
  useLocation,
} from "react-router-dom";
import { useAuth } from "./hooks/useAuth";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Registrations from "./pages/Registrations";
import Logs from "./pages/Logs";
import { ShieldAlert } from "lucide-react";

/**
 * Route Guard for authenticated and authorized admin users.
 */
function ProtectedRoute({ children }) {
  const { user, loading, authError } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex flex-col items-center justify-center text-white">
        <div className="w-16 h-16 rounded-2xl bg-red-600 flex items-center justify-center mb-4 shadow-xl shadow-red-600/30 animate-pulse">
          <ShieldAlert className="w-9 h-9 text-white" />
        </div>
        <div className="flex items-center gap-2 text-sm font-semibold tracking-wide text-gray-300">
          <span className="w-4 h-4 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></span>
          <span>Verifying Station Authorization...</span>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}

export default function App() {
  return (
    <Routes>
      {/* Public Login Route */}
      <Route path="/login" element={<Login />} />

      {/* Protected Routes inside Layout */}
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="registrations" element={<Registrations />} />
        <Route path="logs" element={<Logs />} />
      </Route>

      {/* Catch-all fallback */}
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
