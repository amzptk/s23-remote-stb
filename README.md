# S23 Remote STB

Remote Bluetooth untuk Samsung Galaxy S23 ke Android TV Box (STB) biasa.
S23 menjadi keyboard Bluetooth (profil Bluetooth HID Device), jadi tombol
arah, OK, Back, Home, Menu, Volume, dan Play/Pause dikirim lewat Bluetooth
ke STB — tanpa Wi-Fi dan tanpa aplikasi pendamping di STB.

Bluetooth remote for Samsung Galaxy S23 to a generic Android TV Box (STB).
The S23 acts as a Bluetooth HID keyboard, so D-pad, OK, Back, Home, Menu,
Volume and Play/Pause are sent over Bluetooth to the STB — no Wi-Fi and no
companion app on the STB needed.

- App: **S23 Remote STB**
- Package: `com.amz.s23remote`
- Language: Java, single Activity (`MainActivity.java`)
- minSdk 28, targetSdk 34, compileSdk 34
- Android Gradle Plugin 8.2.2, Gradle 8.2

## Cara build di Android Studio / How to build in Android Studio

1. Buka Android Studio, pilih **Open**, lalu pilih folder project ini
   (`s23-remote/`).
2. Tunggu Gradle sync selesai (Android Studio akan mengunduh Gradle 8.2
   dan Android SDK Platform 34 jika belum ada).
3. Pilih **Build > Make Project**.
4. Untuk APK: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
   APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`.

   Atau dari terminal (setelah JDK 17 + Android SDK terpasang):
   ```sh
   ./gradlew assembleDebug
   ```

> Catatan: tree ini hanya berisi source. `gradle-wrapper.jar` (file biner)
> tidak disertakan; Android Studio akan menyediakannya saat sync pertama.
> Jangan mengunduh APK dari situs pihak ketiga.

## Cara install di Samsung S23 / How to install on Samsung S23

1. Salin `app-debug.apk` ke S23, lalu buka file tersebut.
2. Izinkan **Install unknown apps** untuk aplikasi yang membuka APK
   (misalnya Files / Chrome) saat diminta.
3. Buka **S23 Remote STB**, izinkan izin **Bluetooth / Nearby devices**
   dan **Location** saat diminta (diperlukan untuk scan Bluetooth di
   Android 12+).
4. Nyalakan Bluetooth di S23.

## Cara pairing ke Android TV Box / How to pair with an Android TV Box

1. Di STB, buka **Settings > Remotes & Accessories > Add accessory**
   (nama menu bisa sedikit berbeda tiap STB) agar STB bisa ditemukan.
2. Di aplikasi S23 Remote STB, tekan **Scan STB**.
3. Pilih STB dari daftar, lalu tekan **Connect**.
4. Kalau diminta, konfirmasi pairing di STB dan/atau di S23.
5. Setelah status menunjukkan **Terhubung**, tombol arah, OK, Back, Home,
   Menu, Volume, dan Play/Pause sudah bisa dipakai. Kolom teks + **Kirim**
   bisa dipakai untuk mengetik (huruf a-z, A-Z, angka, spasi, `.`, `-`, `_`).

## Keterbatasan / Limitations

- **Dukungan HID berbeda-beda.** Tidak semua HP dan tidak semua STB
  mendukung profil Bluetooth HID Device. Kalau S23 atau STB menolak
  profil ini, aplikasi akan menampilkan status gagal daftar/connect.
- **Tidak bisa menyalakan STB yang mati total.** Bluetooth tidak bisa
  membangunkan STB yang sudah mati (power-off) — remote ini bekerja
  saat STB sudah menyala / standby.
- Tombol Power tidak disertakan karena alasan di atas; gunakan tombol
  power di STB / remote aslinya untuk menyalakan.
- Jangkauan Bluetooth biasa (sekitar 10 meter, tanpa penghalang besar).

## Struktur project / Project structure

```
s23-remote/
├── README.md
├── settings.gradle
├── build.gradle                 # root, AGP 8.2.2
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties   # Gradle 8.2
├── .gitignore
└── app/
    ├── build.gradle             # minSdk 28, targetSdk 34, compileSdk 34
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml  # izin Bluetooth + MainActivity
        ├── java/com/amz/s23remote/MainActivity.java
        └── res/
            ├── layout/activity_main.xml
            ├── values/strings.xml
            ├── values/colors.xml
            ├── values/themes.xml
            └── drawable/ic_launcher.xml
```
