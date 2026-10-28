/**
 * Emergency sound alert using Web Audio API and browser Notification integration.
 */

let audioCtx = null;

function getAudioContext() {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === "suspended") {
    audioCtx.resume().catch((err) => console.warn("AudioContext resume failed:", err));
  }
  return audioCtx;
}

/**
 * Plays an urgent dual-beep alert tone via Web Audio API.
 */
export function playEmergencyBeep() {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;

    const now = ctx.currentTime;

    // Beep 1
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();

    osc1.type = "sine";
    osc1.frequency.setValueAtTime(880, now); // A5
    osc1.frequency.exponentialRampToValueAtTime(1174.66, now + 0.15); // D6

    gain1.gain.setValueAtTime(0.3, now);
    gain1.gain.exponentialRampToValueAtTime(0.01, now + 0.18);

    osc1.connect(gain1);
    gain1.connect(ctx.destination);

    osc1.start(now);
    osc1.stop(now + 0.2);

    // Beep 2 (short pause then higher tone)
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();

    osc2.type = "sine";
    osc2.frequency.setValueAtTime(1174.66, now + 0.25);
    osc2.frequency.exponentialRampToValueAtTime(1480, now + 0.42);

    gain2.gain.setValueAtTime(0.35, now + 0.25);
    gain2.gain.exponentialRampToValueAtTime(0.01, now + 0.48);

    osc2.connect(gain2);
    gain2.connect(ctx.destination);

    osc2.start(now + 0.25);
    osc2.stop(now + 0.5);
  } catch (error) {
    console.warn("Failed to play emergency beep:", error);
  }
}

/**
 * Requests browser notification permission.
 * @returns {Promise<string>} "granted" | "denied" | "default" | "unsupported"
 */
export async function requestNotificationPermission() {
  if (!("Notification" in window)) {
    return "unsupported";
  }

  if (Notification.permission === "granted") {
    return "granted";
  }

  try {
    const permission = await Notification.requestPermission();
    return permission;
  } catch (error) {
    console.error("Error requesting notification permission:", error);
    return Notification.permission;
  }
}

/**
 * Sends a browser push notification if permission is granted.
 * 
 * @param {string} title 
 * @param {NotificationOptions} options 
 */
export function sendBrowserNotification(title, options = {}) {
  if (!("Notification" in window)) return;

  if (Notification.permission === "granted") {
    try {
      new Notification(title, {
        icon: "/favicon.ico",
        badge: "/favicon.ico",
        vibrate: [200, 100, 200],
        ...options,
      });
    } catch (error) {
      console.warn("Notification error:", error);
    }
  }
}
