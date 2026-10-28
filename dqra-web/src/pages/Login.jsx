import React, { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { ShieldAlert, Lock, Mail, AlertCircle, ArrowRight } from "lucide-react";
import { useAuth } from "../hooks/useAuth";

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const { login, user, loading: authLoading, authError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = location.state?.from?.pathname || "/dashboard";

  // Redirect if already logged in and verified
  useEffect(() => {
    if (user && !authLoading) {
      navigate(from, { replace: true });
    }
  }, [user, authLoading, navigate, from]);

  useEffect(() => {
    if (authError) {
      setErrorMessage(authError);
    }
  }, [authError]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage("");

    if (!email.trim() || !password.trim()) {
      setErrorMessage("Please enter both your email and password.");
      return;
    }

    setSubmitting(true);

    try {
      await login(email.trim(), password);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setErrorMessage(err.message || "Failed to sign in. Please verify your credentials.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-900 px-4 py-12 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* Background ambient lighting */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-red-600/15 rounded-full blur-3xl pointer-events-none" />

      <div className="max-w-md w-full relative z-10">
        {/* Logo and title */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-red-600 shadow-xl shadow-red-600/30 text-white mb-4 transform hover:scale-105 transition">
            <ShieldAlert className="w-9 h-9" />
          </div>
          <h2 className="text-3xl font-black text-white tracking-tight">
            DQRA Admin
          </h2>
          <p className="mt-2 text-sm text-gray-400">
            Disaster Quick Response Emergency Management Portal
          </p>
        </div>

        {/* Login Form Card */}
        <div className="bg-gray-800/90 backdrop-blur-md rounded-2xl p-8 shadow-2xl border border-gray-700/80">
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Error banner */}
            {errorMessage && (
              <div className="p-4 rounded-xl bg-red-950/80 border border-red-800 text-red-200 text-sm flex items-start gap-3 animate-shake">
                <AlertCircle className="w-5 h-5 shrink-0 text-red-400 mt-0.5" />
                <div className="leading-snug">{errorMessage}</div>
              </div>
            )}

            {/* Email input */}
            <div>
              <label
                htmlFor="email"
                className="block text-xs font-bold uppercase tracking-wider text-gray-300 mb-2"
              >
                Official Email Address
              </label>
              <div className="relative rounded-xl shadow-xs">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400">
                  <Mail className="w-5 h-5" />
                </div>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@station.gov"
                  className="block w-full pl-11 pr-4 py-3 bg-gray-900/90 border border-gray-700 rounded-xl text-white placeholder-gray-500 focus:outline-hidden focus:ring-2 focus:ring-red-500 focus:border-transparent text-sm transition"
                />
              </div>
            </div>

            {/* Password input */}
            <div>
              <label
                htmlFor="password"
                className="block text-xs font-bold uppercase tracking-wider text-gray-300 mb-2"
              >
                Password
              </label>
              <div className="relative rounded-xl shadow-xs">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400">
                  <Lock className="w-5 h-5" />
                </div>
                <input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="block w-full pl-11 pr-4 py-3 bg-gray-900/90 border border-gray-700 rounded-xl text-white placeholder-gray-500 focus:outline-hidden focus:ring-2 focus:ring-red-500 focus:border-transparent text-sm transition"
                />
              </div>
            </div>

            {/* Submit button */}
            <div>
              <button
                type="submit"
                disabled={submitting}
                className="w-full flex items-center justify-center gap-2 py-3.5 px-4 rounded-xl bg-red-600 hover:bg-red-700 active:bg-red-800 text-white font-bold text-sm shadow-lg shadow-red-600/30 transition focus:outline-hidden focus:ring-2 focus:ring-red-500 disabled:opacity-60 cursor-pointer"
              >
                {submitting ? (
                  <>
                    <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                    <span>Authenticating...</span>
                  </>
                ) : (
                  <>
                    <span>Sign In to Console</span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </div>
          </form>

          {/* Security note */}
          <div className="mt-6 pt-5 border-t border-gray-700/60 text-center">
            <p className="text-xs text-gray-400">
              Only authorized disaster responders and system administrators with verified station records may access this system.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
