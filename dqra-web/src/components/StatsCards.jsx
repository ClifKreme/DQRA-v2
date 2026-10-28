import React from "react";
import { AlertOctagon, Activity, CheckCircle2, CalendarDays } from "lucide-react";

/**
 * 4 responsive metric cards for the Dashboard.
 * Pending, Confirmed/Active, Resolved Today, Total This Month.
 */
export default function StatsCards({
  pendingCount = 0,
  confirmedCount = 0,
  resolvedTodayCount = 0,
  totalThisMonthCount = 0,
  loading = false,
}) {
  const cards = [
    {
      label: "Pending Emergencies",
      value: pendingCount,
      icon: AlertOctagon,
      textColor: "text-red-600",
      bgColor: "bg-red-50",
      borderColor: "border-red-200",
      description: "Needs immediate attention",
    },
    {
      label: "Active / Confirmed",
      value: confirmedCount,
      icon: Activity,
      textColor: "text-amber-600",
      bgColor: "bg-amber-50",
      borderColor: "border-amber-200",
      description: "Responders dispatched",
    },
    {
      label: "Resolved Today",
      value: resolvedTodayCount,
      icon: CheckCircle2,
      textColor: "text-green-600",
      bgColor: "bg-green-50",
      borderColor: "border-green-200",
      description: "Cleared within today",
    },
    {
      label: "Total This Month",
      value: totalThisMonthCount,
      icon: CalendarDays,
      textColor: "text-blue-600",
      bgColor: "bg-blue-50",
      borderColor: "border-blue-200",
      description: "Cumulative emergencies",
    },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
      {cards.map((card, idx) => {
        const Icon = card.icon;
        return (
          <div
            key={idx}
            className={`bg-white rounded-2xl p-5 border ${card.borderColor} shadow-xs hover:shadow-md transition-shadow flex items-start justify-between`}
          >
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                {card.label}
              </p>
              <div className="mt-2 flex items-baseline gap-2">
                {loading ? (
                  <div className="h-9 w-16 bg-gray-200 rounded-md animate-pulse"></div>
                ) : (
                  <span className={`text-3xl font-black tracking-tight ${card.textColor}`}>
                    {card.value}
                  </span>
                )}
              </div>
              <p className="mt-1 text-xs text-gray-400 font-medium">
                {card.description}
              </p>
            </div>

            <div className={`p-3 rounded-xl ${card.bgColor} ${card.textColor}`}>
              <Icon className="w-6 h-6" />
            </div>
          </div>
        );
      })}
    </div>
  );
}
