package com.appname.dqra.viewmodel;

public class AdminEmergencyStats {

    private final int pendingCount;
    private final int activeCount;
    private final int overallActiveCount;
    private final int resolvedCount;

    public AdminEmergencyStats(int pendingCount, int activeCount, int overallActiveCount, int resolvedCount) {
        this.pendingCount = pendingCount;
        this.activeCount = activeCount;
        this.overallActiveCount = overallActiveCount;
        this.resolvedCount = resolvedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getOverallActiveCount() {
        return overallActiveCount;
    }

    public int getResolvedCount() {
        return resolvedCount;
    }
}
