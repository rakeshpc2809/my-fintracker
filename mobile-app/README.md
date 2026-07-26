# Portfolio OS Mobile Companion (Android APK Build Guide)

This SvelteKit 5 + Capacitor app compiles natively into an Android `.apk` file for your mobile device.

---

## 🛠️ How to Generate the `.apk` on your CachyOS Machine

### 1. Install Android SDK / Android Studio & Node.js
If you haven't installed Android Studio on CachyOS / Arch Linux:
```bash
sudo pacman -S android-studio android-sdk
```

### 2. Build & Generate Android Debug APK
Inside the `/home/rakeshpc/Projects/my-fintracker/mobile-app` directory:
```bash
cd mobile-app
npm install
npx cap add android
npm run apk:debug
```

The compiled Android `.apk` file will be generated at:
`mobile-app/android/app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 How to Install the APK on your Pixel 7a

### Method A: Direct USB Installation via ADB
Connect your Pixel 7a via USB with USB Debugging enabled:
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### Method B: Wireless / Local File Transfer
1. Send `app-debug.apk` to your phone via Tailscale, LocalSend, KDE Connect, or Telegram/Google Drive.
2. Tap `app-debug.apk` on your Pixel 7a and select **Install**.

---

## ⚡ How Mobile Autonomous Offline Mode Works
1. **P2P Tailscale Sync**: When opening the app on your home Wi-Fi or Tailscale mesh network, it connects to `http://100.x.y.z:8080/api/v1/portfolio/snapshot` on your CachyOS machine and caches your holdings locally.
2. **Offline AMFI Live NAV Fetch**: Even when your laptop is turned OFF, your phone app fetches `https://www.amfiindia.com/spages/NAVAll.txt` directly to calculate your live net worth autonomously!
