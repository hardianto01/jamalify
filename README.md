# Jamalify

Pemutar musik Android bergaya Spotify dengan sumber lagu dari YouTube.

## Fitur

- **Cari & putar** — pencarian memakai filter YouTube Music dulu agar hasilnya
  relevan untuk musik, dengan fallback ke video biasa.
- **Playback di background** — Media3 `MediaSessionService`, lengkap dengan
  notifikasi, kontrol lock screen, headset, dan audio focus.
- **Antrean** — putar berikutnya, tambah ke antrean, acak, ulangi, mode radio
  yang menyambung lagu terkait.
- **Library lokal** — playlist, favorit, riwayat putar (Room).
- **Unduh offline** — audio disimpan ke penyimpanan internal aplikasi; lagu yang
  sudah diunduh diputar dari berkas, tanpa menyentuh jaringan.
- **Impor playlist YouTube** — seluruh isi playlist/album, termasuk halaman
  berikutnya.
- **Impor playlist Spotify** — Spotify tidak menyediakan audio, jadi yang
  diimpor adalah daftar judul + artis; tiap baris dicocokkan otomatis ke YouTube.

## Stack

| Bagian | Teknologi |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Playback | Media3 ExoPlayer + MediaSession |
| Ekstraksi YouTube | NewPipeExtractor |
| Database | Room |
| Jaringan | OkHttp |
| Gambar | Coil |

## Build

Toolchain sudah disiapkan di `~/AndroidDev`:

```bash
export JAVA_HOME=~/AndroidDev/jdk17
export ANDROID_HOME=~/AndroidDev/sdk
cd ~/projects/Jamalify
./gradlew assembleDebug
```

APK hasilnya: `app/build/outputs/apk/debug/app-debug.apk`

Pasang ke HP yang tersambung USB (aktifkan USB debugging):

```bash
~/AndroidDev/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Atau salin APK-nya ke HP dan pasang manual.

## Cara kerja pemutaran

`MediaItem` hanya menyimpan `jamalsquad://song/<videoId>`. URL stream asli baru
dicari oleh `StreamResolver` tepat saat ExoPlayer mulai memuat, karena URL
YouTube kedaluwarsa dalam hitungan jam — kalau disimpan di antrean, lagu akan
gagal diputar setelah aplikasi lama dibuka. Resolver juga yang memilih berkas
lokal kalau lagunya sudah diunduh.

## Impor Spotify

Tanpa kredensial, aplikasi membaca halaman embed publik Spotify — jalan tanpa
setup apa pun, tapi Spotify hanya mengembalikan sekitar 100 lagu pertama.

Untuk playlist penuh, isi Client ID dan Secret dari
[Spotify Developer Dashboard](https://developer.spotify.com/dashboard) di
**Library → Impor → Opsi lanjutan Spotify**. Kredensial disimpan di
SharedPreferences perangkat dan hanya dipakai untuk Client Credentials flow
(baca-saja, tanpa login akun).

## Catatan

Ekstraksi audio langsung dari YouTube melanggar Persyaratan Layanan YouTube,
sama seperti NewPipe, ViMusic, dan InnerTune. Aplikasi ini untuk pemakaian
pribadi — jangan diedarkan lewat Play Store.

YouTube rutin mengubah cara stream-nya diserahkan. Kalau suatu saat lagu gagal
diputar, biasanya yang perlu dilakukan adalah menaikkan versi NewPipeExtractor
di `app/build.gradle.kts`.

## Struktur

```
app/src/main/java/id/digitaldesa/jamalsquad/
├── data/
│   ├── youtube/     NewPipeExtractor + jembatan OkHttp
│   ├── spotify/     pembaca playlist Spotify
│   ├── db/          Room: lagu, playlist, riwayat, unduhan
│   ├── download/    foreground service pengunduh
│   └── repo/        MusicRepository — satu pintu ke data
├── playback/        PlaybackService, StreamResolver, PlayerConnection
└── ui/
    ├── screens/     Beranda, Cari, Library, Playlist, Pemutar, Impor
    ├── components/  SongRow, MiniPlayer, action sheet
    └── viewmodel/
```
