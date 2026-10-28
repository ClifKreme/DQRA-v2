# DQRA Web Admin Panel

**DQRA (Disaster Quick Response Application)** Web Admin Panel for emergency dispatchers and municipal responders.

Connects to the DQRA Firebase project (`dqra-c5683`) to monitor live incoming emergency reports, review citizen KYC registrations with Web Crypto AES-256-GCM decryption, and audit activity logs.

---

## 🚀 Quick Start

From the `dqra-web` directory:

```bash
# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

---

## 🛠 Tech Stack

- **Framework**: React 19 + Vite 8
- **Styling**: Tailwind CSS v4 (Red primary `#dc2626`, Dark sidebar `gray-900`, Clean card UI)
- **Backend / DB**: Firebase Web SDK v9 modular (Auth & Firestore)
- **Cryptography**: Web Crypto API (AES-256-GCM no-padding, SHA-256 key derivation)
- **Audio & Notifications**: Web Audio API synthesized siren & Browser Push Notifications
- **Icons**: Lucide React

---

## 📁 Key Features & Architecture

1. **Authentication & Role Authorization (`/login`)**:
   - Authenticates using Firebase Auth `signInWithEmailAndPassword`.
   - Checks `admins_with_stations/{uid}` for station admins (loads assigned `municipality` and decrypted station name).
   - Checks `main/{uid}` for Super Admins.
   - Automatically signs out unauthorized accounts with *"Access denied. This account is not authorized."*

2. **Emergency Dispatch Center (`/dashboard`)**:
   - 4-card metric overview: Pending, Active/Confirmed, Resolved Today, and Total This Month.
   - Real-time `onSnapshot` feed filtered by admin jurisdiction.
   - Pending emergencies sorted oldest-first for rapid response.
   - Google Maps direct link (`https://www.google.com/maps?q={lat},{lng}`).
   - Direct status updates:
     - `Confirm`: Sets `status: "confirmed"`, `confirmedBy: uid`, `confirmedAt: serverTimestamp()`.
     - `Reject`: Sets `status: "rejected"`.
     - `Mark as Resolved`: Sets `status: "resolved"`, `resolvedAt: serverTimestamp()`, `resolvedBy: uid`.
   - Synthesized dual-tone siren via Web Audio API and browser desktop push notifications on new pending distress calls.

3. **Citizen Registrations & KYC (`/registrations`)**:
   - Real-time pending registration queue.
   - Client-side decryption of encrypted PII (Full Name, Email, Phone, Address) using Web Crypto AES-256-GCM (`DQRA_AES_FIELD_KEY_V1_2026`).
   - Clickable full-screen inspection of Selfie, National ID Front, and National ID Back.
   - Batch write approval: Creates `users/{uid}` with `role: "User"` (preserving raw encrypted PII strings as-is) and marks `user_registration_requests/{uid}` as `approved`.
   - Rejection with confirmation modal.

4. **Activity Logs & Audit Archive (`/logs`)**:
   - **Emergency Logs**: Filter by status, date range, and pagination (20 per page).
   - **Registration Logs**: Decrypted PII records, reviewed-by admin UID, and review dates.

5. **Image & Modal Handling**:
   - Automatically handles Base64 images with or without `data:image/jpeg;base64,` prefix.
   - Fullscreen dark overlay modal with image zoom/fit, Escape key dismissal, and download button.
