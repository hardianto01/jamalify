import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
}

/*
 * Kode yang tidak bergantung pada Android dipakai bersama langsung dari
 * sumber aplikasi Android, bukan disalin. Daftar di bawah sengaja eksplisit:
 * berkas baru di :app tidak ikut terbawa ke desktop sebelum dipastikan
 * portabel, jadi build Android tidak pernah bisa merusak build desktop
 * secara diam-diam (dan sebaliknya).
 */
val sharedFromApp = listOf(
    "com/jamalsquad/jamalify/ui/components/PetKind.kt",
    "com/jamalsquad/jamalify/ui/components/PetCat.kt",
    "com/jamalsquad/jamalify/ui/components/PetGenius.kt",
    "com/jamalsquad/jamalify/ui/components/PetDetective.kt",
    "com/jamalsquad/jamalify/ui/components/PetMage.kt",
    "com/jamalsquad/jamalify/ui/components/PetStage.kt",
    "com/jamalsquad/jamalify/data/Models.kt",
    // [playback] tambahkan berkas bersama untuk YouTube/playback di bawah baris ini
    "com/jamalsquad/jamalify/util/Thumbnails.kt",
    "com/jamalsquad/jamalify/data/youtube/YouTubeService.kt",
    "com/jamalsquad/jamalify/data/youtube/JamalifyDownloader.kt",
    "com/jamalsquad/jamalify/data/youtube/StreamHeadersInterceptor.kt",
    "com/jamalsquad/jamalify/data/lyrics/LyricsService.kt",
    // [data] tambahkan berkas bersama untuk database/rekomendasi di bawah baris ini
    // Skema & query sama persis dengan Android, jadi data bisa dipindah antar-perangkat.
    "com/jamalsquad/jamalify/data/db/Entities.kt",
    "com/jamalsquad/jamalify/data/db/MusicDao.kt",
    "com/jamalsquad/jamalify/data/repo/RecommendationSource.kt",
    "com/jamalsquad/jamalify/data/repo/RecommendationEngine.kt",
    // [ui] tambahkan berkas bersama untuk tema/komponen di bawah baris ini
    // Theme.kt & Common.kt tidak ikut: masing-masing butuh ThemeManager (SharedPreferences) dan Coil 2.
    "com/jamalsquad/jamalify/ui/components/Glass.kt",
    "com/jamalsquad/jamalify/ui/components/NavIcons.kt",
    "com/jamalsquad/jamalify/ui/components/PlayerIcons.kt",
    "com/jamalsquad/jamalify/ui/components/AudioVisualizer.kt",
    "com/jamalsquad/jamalify/ui/components/NewPlaylistDialog.kt",
)

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include(sharedFromApp)
        kotlin.include("com/jamalsquad/jamalify/desktop/**")
        // Salinan beberapa ikon Material (Apache 2.0) pengganti material-icons-extended,
        // yang sendirian menyumbang ~38 MB ke installer.
        kotlin.include("androidx/compose/material/icons/**")
        // [data] implementasi DAO bersama hasil Room/KSP (paketnya di luar desktop/**)
        kotlin.include("com/jamalsquad/jamalify/data/db/*_Impl.kt")
    }
}

// [data] KSP (Room) membaca srcDir mentah tanpa filter include di atas, lalu tersandung
// berkas khusus Android seperti JamalifyDatabase.kt. Beri KSP sumber yang sudah tersaring.
ksp {
    excludedSources.from("../app/src/main/java")
}
tasks.matching { it.name == "kspKotlin" }.configureEach {
    (this as org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>)
        .setSource(fileTree("../app/src/main/java") { include(sharedFromApp) })
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    // [playback] dependensi
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // org.json bawaan Android; di JVM perlu ditambahkan untuk LyricsService.
    implementation("org.json:json:20240303")
    implementation("uk.co.caprica:vlcj:4.8.3")
    // [data] dependensi
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.sqlite:sqlite-bundled:2.6.2")
    add("ksp", "androidx.room:room-compiler:2.7.2")
    // [ui] dependensi
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("dev.chrisbanes.haze:haze:1.1.1")
}

compose.desktop {
    application {
        mainClass = "com.jamalsquad.jamalify.desktop.MainKt"

        // Tanpa ProGuard: NewPipeExtractor, Room, OkHttp dan vlcj/JNA banyak
        // memakai refleksi, dan ukuran installer didominasi runtime + libVLC.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage,
                TargetFormat.Msi, TargetFormat.Exe,
            )
            packageName = "Jamalify"
            // CI menurunkan versi dari tag desktop-vX.Y.Z (-Pjamalify.version=X.Y.Z).
            // Msi menuntut format angka MAJOR.MINOR.BUILD.
            packageVersion = (findProperty("jamalify.version") as String?) ?: "3.5.0"
            vendor = "JamalSquad"
            description = "Pemutar musik YouTube Jamalify untuk desktop"
            copyright = "© 2026 JamalSquad"

            // Hasil ./gradlew :desktop:suggestRuntimeModules (java.instrument,
            // java.net.http, jdk.dynalink, jdk.unsupported), ditambah modul yang
            // hanya dimuat lewat refleksi/ServiceLoader: jdk.crypto.ec (TLS ECDHE
            // ke YouTube), java.sql/java.naming/java.management (pustaka pihak ketiga).
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http",
                "java.sql", "jdk.crypto.ec", "jdk.dynalink", "jdk.unsupported",
            )

            // libVLC Windows dibundel CI ke resources/windows-x64/vlc
            // (lihat .github/workflows/desktop.yml dan NativeVlc.kt).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            linux {
                iconFile.set(project.file("icons/jamalify.png"))
                packageName = "jamalify"
                // jpackage menulis "<vendor> <debMaintainer>", jadi cukup alamat email.
                debMaintainer = "antojago5@gmail.com"
                menuGroup = "AudioVideo;Audio;Player"
                appCategory = "sound"
                rpmLicenseType = "Proprietary"
                shortcut = true
            }
            windows {
                iconFile.set(project.file("icons/jamalify.ico"))
                // Tetap selamanya: installer versi baru menimpa versi lama.
                upgradeUuid = "a0451bed-3cd8-4d40-a02f-7822da5012f7"
                menuGroup = "Jamalify"
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}

// Dependensi paket Linux: vlcj butuh libVLC dari sistem.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    when (targetFormat) {
        // Tanpa spasi: argumen jpackage dipecah per spasi lewat berkas @args.
        TargetFormat.Deb -> freeArgs.addAll("--linux-package-deps", "vlc|libvlc5")
        TargetFormat.Rpm -> freeArgs.addAll("--linux-package-deps", "vlc")
        else -> Unit
    }
}
