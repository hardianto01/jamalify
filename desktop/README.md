# Jamalify Desktop

Versi Windows/Linux dari Jamalify (Kotlin + Compose Multiplatform). Butuh JDK 17.

## Menjalankan

```sh
./gradlew :desktop:run
```

Audio diputar lewat [vlcj](https://github.com/caprica/vlcj), jadi **libVLC harus ada**:

- **Linux**: pasang VLC (`sudo apt install vlc`, `sudo dnf install vlc` dari RPM Fusion, dst).
  Paket `.deb`/`.rpm` sudah mendeklarasikan dependensi ini (`vlc | libvlc5` / `vlc`).
- **Windows**: installer `.msi`/`.exe` dari CI sudah membawa libVLC sendiri, tidak perlu VLC.
  Untuk `:desktop:run` di Windows, pasang VLC 64-bit, atau ekstrak `libvlc.dll`,
  `libvlccore.dll` dan `plugins/` dari zip VLC win64 ke `desktop/resources/windows-x64/vlc/`.

`NativeVlc.configure()` (dipanggil sebelum vlcj dipakai) mengarahkan vlcj ke libVLC
bawaan di `<compose.application.resources.dir>/vlc` bila ada; jika tidak, vlcj memakai VLC
sistem. Lokasi lain bisa dipaksa dengan `-Djamalify.vlc.dir=...` atau `JAMALIFY_VLC_DIR`.

## Membuat paket secara lokal

Paket hanya bisa dibuat untuk OS tempat build dijalankan.

```sh
# Linux
./gradlew :desktop:packageDeb        # butuh dpkg-deb, fakeroot
./gradlew :desktop:packageRpm        # butuh rpmbuild
./gradlew :desktop:packageAppImage   # direktori aplikasi siap jalan (bukan berkas .AppImage)

# Windows (WiX diunduh otomatis)
gradlew :desktop:packageMsi
gradlew :desktop:packageExe
```

Hasilnya ada di `desktop/build/compose/binaries/main/<format>/`.
Ikon berasal dari `desktop/icons/jamalify.svg` (turunan ikon launcher Android);
`jamalify.png` dan `jamalify.ico` dirender darinya dengan `rsvg-convert` + ImageMagick.

## Rilis

Workflow `.github/workflows/desktop.yml` membangun deb, rpm, AppImage (tar.gz), msi dan exe.

```sh
git tag desktop-v3.5.0
git push origin desktop-v3.5.0
```

Versi paket diambil dari tag (harus `MAJOR.MINOR.PATCH`), lalu semua berkas dilampirkan ke
GitHub Release. Workflow juga bisa dijalankan manual (*Run workflow*) — hasilnya hanya
menjadi artifact, tanpa rilis. Build desktop tidak memerlukan `keystore.properties`.
