# FlipperDroid

Toolkit Android bergaya Flipper Zero untuk HP yang punya **IR blaster** dan **NFC**
(dibuat & dites untuk **POCO X6 5G**).

Bukan port firmware. Bukan emulator. Ini aplikasi native Kotlin yang memakai
hardware HP untuk melakukan sebagian fungsi Flipper — dan jujur soal yang tidak bisa.

---

## Fitur v0.5

| Menu | Status | Keterangan |
|---|---|---|
| **Infrared** | ✅ jalan | Kirim 38 kHz lewat IR blaster. Impor file `.ir` (format Flipper/IRDB) berisi NEC, NECext, Samsung32, SIRC/15/20, dan RAW (remote AC). Tombol bisa juga ditambah manual. |
| **NFC** | ✅ jalan | Reader mode: baca UID, daftar teknologi, isi NDEF. Dump halaman MIFARE Ultralight / NTAG (bisa disalin). Tulis teks/link ke tag NDEF atau format tag kosong. |
| **NFC Emulator (HCE)** | ✅ jalan | HP jadi kartu NFC Forum Type 4 Tag dengan **banyak record** (teks + URL + vCard). APDU `D2760000850101`, bisa dibaca HP lain / Flipper / reader USB. |
| **Bad-BT Keyboard** | ✅ jalan | HP jadi keyboard HID Bluetooth. Pasangkan ke PC, lalu "ketik" payload otomatis. |
| **Widget TV Power** | ✅ jalan | Widget home screen: satu tap mengirim tombol IR bernama "power" (default TV Power NEC). |
| **Sub-GHz** | 🔌 butuh board | CC1101 + ESP32 lewat USB serial OTG: capture raw & replay 315/433/868/915 MHz. Frame didecode di HP (Princeton / CAME / Keeloq). Firmware contoh ada di `firmware/cc1101_bridge`. |
| **RFID 125 kHz** | ⛔ hardware | Butuh reader 125 kHz (Proxmark3/RDM6300). |
| **iButton** | ⛔ hardware | Butuh reader 1-Wire. |
| **Kapabilitas HP** | ✅ | Deteksi runtime: IR, NFC, BT, USB host, model, SDK. |
| **Tentang** | ✅ | Lisensi & batasan. |

Fitur yang **tidak** diklaim: clone UID kartu akses, buka gerbang, tembus
sub-GHz, emulate iButton. Itu butuh hardware fisik, bukan software.

---

## Build APK tanpa PC (GitHub Actions)

1. Buat repo baru di GitHub (private juga boleh), misal `flipperdroid`.
2. Upload seluruh isi folder ini ke repo tersebut (lewat web GitHub: **Add file → Upload files**, drag semua).
3. Buka tab **Actions** → workflow **Build FlipperDroid APK** → **Run workflow**.
4. Tunggu ±3-5 menit sampai centang hijau.
5. Buka run yang selesai → scroll ke bawah → **Artifacts** → download `FlipperDroid-debug.zip`.
6. Extract → dapat `app-debug.apk` → buka di HP → izinkan **Install unknown apps** → install.

APK debug ditandatangani dengan debug key, jadi bisa langsung dipasang.
Kalau nanti mau update tanpa uninstall, pakai keystore yang sama (lihat bawah).

---

## Build lokal (opsional)

Butuh JDK 17 + Android SDK (platform 34, build-tools 34.0.0).

```bash
gradle :app:assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```

Di Termux murni ini tidak jalan karena `aapt2` dari Android build-tools
hanya tersedia untuk x86_64. Alternatif di HP: aplikasi **AndroidIDE**.

---

## Struktur

```
app/src/main/java/dev/flipperdroid/
├── MainActivity.kt          # navigasi antar layar
├── caps/Caps.kt             # deteksi kemampuan hardware
├── ir/IrEncoder.kt          # encoder NEC/NECext/Samsung32/SIRC + RAW
├── ir/IrFileParser.kt       # parser file .ir (Flipper / IRDB)
├── ir/IrStore.kt            # simpan tombol IR (SharedPreferences/JSON)
├── nfc/NfcReader.kt         # baca/tulis tag NDEF
├── nfc/UltralightDump.kt    # dump halaman MIFARE Ultralight / NTAG
├── hce/NdefEmulator.kt      # Type 4 Tag: CC + NDEF file
├── hce/NdefHostApduService.kt
├── subghz/Cc1101Link.kt     # USB serial ke firmware CC1101
├── subghz/SubGhzDecoder.kt  # decoder Princeton / CAME / Keeloq (di HP)
├── hid/HidKeyboard.kt       # BluetoothHidDevice + map karakter → HID keycode
├── widget/TvPowerWidgetProvider.kt  # widget home screen TV Power
└── ui/                      # Compose: theme, home, IR, NFC, HCE, Sub-GHz, HID
```

## Sub-GHz: rangkaian minimum

CC1101 tidak punya USB, jadi butuh microcontroller sebagai jembatan serial:

| CC1101 | ESP32 |
|---|---|
| VCC | 3V3 (jangan 5V) |
| GND | GND |
| SCK | GPIO18 |
| MISO | GPIO19 |
| MOSI | GPIO23 |
| CSN | GPIO5 |
| GDO0 | GPIO4 |

1. Install library **SmartRC-CC1101-Driver-Lib** di Arduino IDE.
2. Flash `firmware/cc1101_bridge/cc1101_bridge.ino` ke ESP32.
3. Colok ESP32 ke HP pakai kabel OTG → buka menu **Sub-GHz** → **SCAN USB** → izinkan.
4. Pilih frekuensi → **MULAI RX** → pencet remote → frame muncul di daftar → tap untuk detail, decode, dan replay.

Protokol serial (115200 8N1): `PING`, `FREQ <hz>`, `RX`, `IDLE`, `TX <durasi,...>`.
Durasi dalam mikrodetik, positif = carrier ON (konvensi Flipper Raw_Data).

Decoder Princeton (`te 390/1170`), CAME (`te 320/640`), dan Keeloq (`te 400/800 +
preamble`) berjalan di aplikasi, bukan di firmware. Jadi kalau decoder diperbaiki,
cukup update APK — tidak perlu flash ulang. Hasil decode bersifat heuristik:
semua frame tetap disimpan sebagai raw dan bisa di-replay apa adanya.

Firmware ini contoh dan belum diuji di semua modul. Kalau replay tidak memicu
perangkat, coba balik tanda durasi (polaritas GDO0 bisa berbeda antar board).

## Roadmap

- [x] Import file `.ir` dari database IR (format Flipper/IRDB) + protokol Sony SIRC, Samsung, NECext, RAW
- [x] Sub-GHz RX/TX raw via CC1101 + USB serial (OTG)
- [x] NFC: dump MIFARE Ultralight + NDEF multi-record di HCE
- [ ] Import/export tombol IR ke file JSON
- [x] Decoder protokol Sub-GHz (Princeton, CAME, Keeloq) di aplikasi
- [x] Widget cepat: "TV Power" di layar utama
- [ ] Tema: opsi font pixel & animasi dolphin

## Lisensi & catatan legal

- Kode proyek ini: **GPL-3.0**.
- Protokol NEC diimplementasikan ulang dari spesifikasi publik — bukan salinan kode Flipper.
- Nama/merek **Flipper Zero** milik Flipper Devices Inc. Proyek ini **tidak berafiliasi**.
- Gunakan hanya pada perangkat dan jaringan milikmu atau yang kamu punya izinnya.
  Mengirim kode IR ke perangkat orang lain, atau menyuntikkan input ke komputer
  tanpa izin, bisa melanggar hukum.
