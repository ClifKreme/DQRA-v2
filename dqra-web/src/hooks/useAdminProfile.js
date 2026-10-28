import { useContext } from "react";
import { AuthContext } from "../context/AuthContext";

/**
 * Custom hook returning the authenticated admin's profile and station details.
 * Returns { municipality, stationName, isSuperAdmin, adminProfile, loading, setMunicipality }
 */
export function useAdminProfile() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAdminProfile must be used within an AuthProvider");
  }

  return {
    municipality: context.municipality,
    setMunicipality: context.setMunicipality,
    stationName: context.stationName,
    isSuperAdmin: context.isSuperAdmin,
    adminProfile: context.adminProfile,
    loading: context.loading,
  };
}

export default useAdminProfile;
