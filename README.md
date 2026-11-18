# FlipperDroid

Toolkit Android bergaya Flipper Zero untuk HP yang punya **IR blaster** dan **NFC**
(dibuat & dites untuk **POCO X6 5G**).

Bukan port firmware. Bukan emulator. Ini aplikasi native Kotlin yang memakai
hardware HP untuk melakukan sebagian fungsi Flipper — dan jujur soal yang tidak bisa.

---

## Fitur v0.1

| Menu | Status | Keterangan |
|---|---|---|
| **Infrared** | ✅ jalan | Kirim 38 kHz lewat IR blaster. Impor file `.ir` (format Flipper/IRDB) berisi NEC, NECext, Samsung32, SIRC/15/20, dan RAW (remote AC). Tombol bisa juga ditambah manual. |
| **NFC** | ✅ jalan | Reader mode: baca UID, daftar teknologi, isi NDEF. Tulis teks/link ke tag NDEF atau format tag kosong. |
| **NFC Emulator (HCE)** | ✅ jalan | HP jadi kartu NFC Forum Type 4 Tag. Emulasi NDEF text/URL lewat APDU (`D2760000850101`), bisa dibaca HP lain / Flipper / reader USB. |
| **Bad-BT Keyboard** | ✅ jalan | HP jadi keyboard HID Bluetooth. Pasangkan ke PC, lalu "ketik" payload otomatis. |
| **Sub-GHz** | ⛔ hardware | Butuh CC1101. Halaman menampilkan status OTG + penjelasan. |
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
├── ir/Nec.kt                # encoder protokol NEC (implementasi sendiri)
├── ir/IrStore.kt            # simpan tombol IR (SharedPreferences/JSON)
├── nfc/NfcReader.kt         # baca/tulis tag NDEF
├── hid/HidKeyboard.kt       # BluetoothHidDevice + map karakter → HID keycode
└── ui/                      # Compose: theme, home, IR, NFC, HID, caps, about
```

## Roadmap v0.2

- [x] Import file `.ir` dari database IR (format Flipper/IRDB) + protokol Sony SIRC, Samsung, NECext, RAW
- [ ] Import/export tombol IR ke file JSON
- [ ] Sub-GHz RX via CC1101 + USB serial (OTG)
- [ ] NFC: dump MIFARE Ultralight, emulate NDEF dengan beberapa record sekaligus
- [ ] Widget cepat: "TV Power" di layar utama
- [ ] Tema: opsi font pixel & animasi dolphin

## Lisensi & catatan legal

- Kode proyek ini: **GPL-3.0**.
- Protokol NEC diimplementasikan ulang dari spesifikasi publik — bukan salinan kode Flipper.
- Nama/merek **Flipper Zero** milik Flipper Devices Inc. Proyek ini **tidak berafiliasi**.
- Gunakan hanya pada perangkat dan jaringan milikmu atau yang kamu punya izinnya.
  Mengirim kode IR ke perangkat orang lain, atau menyuntikkan input ke komputer
  tanpa izin, bisa melanggar hukum.
