# Point of Sale — Android Point of Sale

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7-blue.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/minSdk-26-orange.svg)](https://developer.android.com)

A modern, lightweight Android POS (Point of Sale) application built with Jetpack Compose. Supports
Bluetooth thermal receipt printing, NFC card scanning, a full store checkout flow, and customizable
settings — all in a polished, responsive UI.

---

## 🚀 Try the App (APK Download)

Want to test the app without building from source? Download the demo APK directly!

👉 **[Download Latest Demo APK](https://github.com/Emelio101/point-of-sale/releases/latest)**

> **Note:** This is a **debug build** for demo purposes. Since it's not from the Play Store, Android
> will ask you to allow installation from "Unknown Sources."

### Installation Steps

1. Download the APK from the link above
2. On your Android device, tap the downloaded file
3. If prompted, enable **"Install from unknown sources"** in your settings
4. Launch **Point of Sale** and explore!

### Build Info

```bash
# This APK was built using:
./gradlew assembleDebug
```

---

## ✨ Features

### 🛒 Checkout

- Product grid with tap-to-add functionality and live quantity badges
- Cart with inline **+/–** quantity controls and swipe-to-remove
- Receipt preview dialog before printing
- Supports multiple currencies with instant symbol switching

### 🖨️ Bluetooth Printing

- Auto-connects to the first paired POS/thermal printer
- Manual printer override from the Diagnostics screen
- Prints formatted ESC/POS receipts with:
    - Store logo (bitmap → ESC/POS raster conversion)
    - Dot-leader aligned item lines
    - Bold totals, timestamps, and partial paper cut
- Supports Standard test receipt, NFC scan receipt, and Checkout receipt

### 📡 NFC Scanner

- Reads NFC-A and NFC-B cards in Reader Mode
- Detects NDEF text records and ISO-DEP (EMV bank cards)
- Displays UID, technology stack, and payload info live
- One-tap refresh to clear and re-scan

### ⚙️ Settings

- Dark / Light theme toggle with persistence
- Currency selector (ZMW, USD, EUR, GBP, ZAR, NGN, KES)
- All preferences saved via `SharedPreferences`

### 🔧 Hardware Diagnostic

- Lists all paired Bluetooth devices with manual connect option
- Real-time NFC scan panel
- One-tap test print (standard or NFC receipt)
- Refresh button to reload hardware state

---

## 🛠️ Tech Stack

| Layer        | Technology                                    |
|--------------|-----------------------------------------------|
| Language     | Kotlin 2.0                                    |
| UI           | Jetpack Compose (Material 3)                  |
| Architecture | Single-Activity, Composable screens           |
| Printing     | ESC/POS over Bluetooth RFCOMM                 |
| NFC          | `NfcAdapter.ReaderCallback` (NFC-A / NFC-B)   |
| Theme        | Dynamic color + custom amber/charcoal palette |
| Persistence  | `SharedPreferences`                           |

---

## 📦 Key Dependencies

```kotlin
// Jetpack Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-core")

// Core Android
implementation("androidx.activity:activity-compose:1.9.2")
implementation("androidx.core:core-ktx:1.13.1")
```

---

## 🏗️ Project Structure

```
app/src/main/java/com/pos/
├── MainActivity.kt              # NFC reader, BT printing, app entry point
├── models/
│   └── AppModels.kt             # Product, CartItem, PrintJob, AppScreen enums
├── ui/
│   ├── screens/
│   │   ├── MainAppHost.kt       # Bottom nav scaffold
│   │   ├── CheckoutScreen.kt    # Product grid + cart + receipt preview
│   │   ├── DiagnosticScreen.kt  # NFC panel + BT device list + test prints
│   │   └── SettingsScreen.kt    # Theme, currency, developer info
│   └── theme/
│       ├── Color.kt             # Amber/charcoal brand palette
│       ├── Theme.kt             # Light + dark Material 3 color schemes
│       └── Type.kt              # Full M3 typography scale
└── utils/
    └── PrintUtils.kt            # Bitmap → ESC/POS raster encoder
```

---

## 🔧 Setup & Build

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 26+
- A Bluetooth thermal printer (e.g. any 58mm ESC/POS printer) — *optional for testing*
- An NFC-capable Android device — *optional for NFC testing*

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/Emelio101/point-of-sale.git
cd point-of-sale

# 2. Open in Android Studio and let Gradle sync

# 3. Build debug APK
./gradlew assembleDebug

# 4. Install on connected device
./gradlew installDebug
```

### Permissions Required

| Permission          | Purpose                                  |
|---------------------|------------------------------------------|
| `BLUETOOTH_CONNECT` | Connect to paired printers (Android 12+) |
| `BLUETOOTH_SCAN`    | Discover paired devices (Android 12+)    |
| `NFC`               | Read NFC tags and cards                  |
| `INTERNET`          | (none — fully offline app)               |

---

## 🖨️ Printer Compatibility

Any thermal printer supporting the **ESC/POS** command set over **Bluetooth SPP (Serial Port
Profile)** should work. Tested with common 58mm and 80mm POS printers. The app auto-connects to the
first paired device whose name contains "printer", "inner", or "pos". You can manually override this
on the **Hardware Diagnostic** screen.

---

## 🚧 Roadmap

### Phase 1 — Core

- [ ] Local data persistence (Room DB)
- [ ] Transaction history
- [ ] Product catalog management (add/edit/delete products)
- [ ] Custom store name and address in settings

### Phase 2 — Business Features

- [ ] Daily sales summary
- [ ] Receipt via QR code / email
- [ ] Barcode scanner support
- [ ] Discount and tax configuration

### Phase 3 — Cloud

- [ ] Cloud backup and sync
- [ ] Multi-device support
- [ ] Staff accounts and roles

---

## 🤝 Contributing

Contributions are welcome!

1. **Fork** the repository
2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Commit your changes**
   ```bash
   git commit -m "feat: add your feature"
   ```
4. **Push and open a Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

Please follow standard Kotlin coding conventions and ensure the project builds cleanly before
submitting.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Emmanuel C. Phiri**

- GitHub: [Emelio101](https://github.com/Emelio101)
- LinkedIn: [Emmanuel C. Phiri](https://www.linkedin.com/in/emmanuel-c-phiri-13420315b/)
- WakaTime: [Emelio101](https://wakatime.com/@Emelio101)

---

## 🙏 Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI toolkit
- [Material Design 3](https://m3.material.io/) — Design system
- [ESC/POS](https://reference.epson-biz.com/modules/ref_escpos/index.php) — Thermal printer command
  standard
