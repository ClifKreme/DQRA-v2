import { useContext } from "react";
import { AuthContext } from "../context/AuthContext";

/**
 * Custom hook wrapping authentication state.
 * Returns { user, loading, login, logout, authError }
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }

  return {
    user: context.user,
    loading: context.loading,
    login: context.login,
    logout: context.logout,
    authError: context.authError,
  };
}

export default useAuth;
