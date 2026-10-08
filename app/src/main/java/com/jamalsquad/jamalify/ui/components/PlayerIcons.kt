package com.jamalsquad.jamalify.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

/**
 * Ikon pemutar bergaris tipis dengan ujung membulat, senada dengan
 * [NavIcons]. Tombol transport (prev/next/play) sengaja terisi supaya
 * tetap jadi pusat perhatian di antara ikon garis.
 */
object PlayerIcons {

    val ChevronDown: ImageVector by lazy {
        icon("PlayerChevronDown") {
            stroke(1.8f) { moveTo(6f, 9.5f); lineTo(12f, 15f); lineTo(18f, 9.5f) }
        }
    }

    val Queue: ImageVector by lazy {
        icon("PlayerQueue") {
            stroke {
                moveTo(4f, 6.5f); lineTo(17f, 6.5f)
                moveTo(4f, 11f); lineTo(14f, 11f)
                moveTo(4f, 15.5f); lineTo(10f, 15.5f)
                moveTo(17.5f, 17.5f); lineTo(17.5f, 11f); lineTo(20f, 12f)
            }
            fill { circle(16.2f, 17.6f, 1.5f) }
        }
    }

    val Shuffle: ImageVector by lazy {
        icon("PlayerShuffle") {
            stroke {
                moveTo(3.5f, 7.5f); lineTo(7f, 7.5f)
                curveTo(11.5f, 7.5f, 12.5f, 16.5f, 17f, 16.5f); lineTo(20f, 16.5f)
                moveTo(3.5f, 16.5f); lineTo(7f, 16.5f)
                curveTo(8.6f, 16.5f, 9.6f, 15.3f, 10.4f, 13.8f)
                moveTo(13.6f, 10.2f)
                curveTo(14.4f, 8.7f, 15.4f, 7.5f, 17f, 7.5f); lineTo(20f, 7.5f)
                moveTo(17.8f, 5f); lineTo(20.3f, 7.5f); lineTo(17.8f, 10f)
                moveTo(17.8f, 14f); lineTo(20.3f, 16.5f); lineTo(17.8f, 19f)
            }
        }
    }

    val Repeat: ImageVector by lazy { icon("PlayerRepeat") { stroke { repeatLoop() } } }

    val RepeatOne: ImageVector by lazy {
        icon("PlayerRepeatOne") {
            stroke { repeatLoop() }
            stroke(1.4f) { moveTo(11.2f, 10.6f); lineTo(12.3f, 9.8f); lineTo(12.3f, 14.2f) }
        }
    }

    val SkipPrevious: ImageVector by lazy {
        icon("PlayerSkipPrevious") {
            stroke(2.2f) { moveTo(6f, 5.5f); lineTo(6f, 18.5f) }
            fill {
                moveTo(18.5f, 6.2f); lineTo(18.5f, 17.8f)
                quadTo(18.5f, 19f, 17.4f, 18.3f)
                lineTo(9.3f, 12.8f)
                quadTo(8.3f, 12f, 9.3f, 11.2f)
                lineTo(17.4f, 5.7f)
                quadTo(18.5f, 5f, 18.5f, 6.2f)
                close()
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        icon("PlayerSkipNext") {
            stroke(2.2f) { moveTo(18f, 5.5f); lineTo(18f, 18.5f) }
            fill {
                moveTo(5.5f, 6.2f); lineTo(5.5f, 17.8f)
                quadTo(5.5f, 19f, 6.6f, 18.3f)
                lineTo(14.7f, 12.8f)
                quadTo(15.7f, 12f, 14.7f, 11.2f)
                lineTo(6.6f, 5.7f)
                quadTo(5.5f, 5f, 5.5f, 6.2f)
                close()
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("PlayerPlay") {
            fill {
                moveTo(8f, 6.3f); lineTo(8f, 17.7f)
                quadTo(8f, 19f, 9.1f, 18.3f)
                lineTo(17.8f, 12.9f)
                quadTo(18.9f, 12f, 17.8f, 11.1f)
                lineTo(9.1f, 5.7f)
                quadTo(8f, 5f, 8f, 6.3f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("PlayerPause") {
            stroke(3.2f) {
                moveTo(8.5f, 6.5f); lineTo(8.5f, 17.5f)
                moveTo(15.5f, 6.5f); lineTo(15.5f, 17.5f)
            }
        }
    }

    val Heart: ImageVector by lazy { icon("PlayerHeart") { stroke { heart() } } }

    val HeartFilled: ImageVector by lazy { icon("PlayerHeartFilled") { fill { heart() } } }

    val More: ImageVector by lazy {
        icon("PlayerMore") {
            stroke(1.4f) {
                circle(5.5f, 12f, 1.6f)
                circle(12f, 12f, 1.6f)
                circle(18.5f, 12f, 1.6f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("PlayerChevronLeft") {
            stroke(1.8f) { moveTo(14.5f, 6f); lineTo(9f, 12f); lineTo(14.5f, 18f) }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("PlayerChevronRight") {
            stroke(1.6f) { moveTo(9.5f, 6f); lineTo(15f, 12f); lineTo(9.5f, 18f) }
        }
    }

    val Download: ImageVector by lazy {
        icon("PlayerDownload") {
            stroke {
                circle(12f, 12f, 8.5f)
                moveTo(12f, 7.8f); lineTo(12f, 15.5f)
                moveTo(8.8f, 12.5f); lineTo(12f, 15.7f); lineTo(15.2f, 12.5f)
            }
        }
    }

    /** Segitiga play dalam lingkaran garis, untuk tombol "Putar" berlabel. */
    val PlayCircle: ImageVector by lazy {
        icon("PlayerPlayCircle") {
            stroke { circle(12f, 12f, 8.5f) }
            fill {
                moveTo(10.2f, 8.9f); lineTo(10.2f, 15.1f); lineTo(15.3f, 12f); close()
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("PlayerSettings") {
            stroke {
                // Roda gigi: delapan gigi membulat di sekeliling lingkaran.
                val teeth = 8
                for (i in 0 until teeth) {
                    val a = (i * 2 * Math.PI / teeth).toFloat()
                    val cos = kotlin.math.cos(a); val sin = kotlin.math.sin(a)
                    moveTo(12f + cos * 6.6f, 12f + sin * 6.6f)
                    lineTo(12f + cos * 8.8f, 12f + sin * 8.8f)
                }
                circle(12f, 12f, 6.4f)
                circle(12f, 12f, 2.6f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("PlayerPlus") {
            stroke(1.8f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
        }
    }

    val PlusCircle: ImageVector by lazy {
        icon("PlayerPlusCircle") {
            stroke { circle(12f, 12f, 8.5f) }
            stroke { moveTo(12f, 8.5f); lineTo(12f, 15.5f); moveTo(8.5f, 12f); lineTo(15.5f, 12f) }
        }
    }

    val Check: ImageVector by lazy {
        icon("PlayerCheck") {
            stroke(1.8f) { moveTo(5f, 12.5f); lineTo(9.8f, 17f); lineTo(19f, 7.5f) }
        }
    }

    val Edit: ImageVector by lazy {
        icon("PlayerEdit") {
            stroke {
                moveTo(4.5f, 19.5f); lineTo(5.2f, 15.6f); lineTo(15.6f, 5.2f)
                quadTo(16.8f, 4f, 18f, 5.2f); lineTo(18.8f, 6f)
                quadTo(20f, 7.2f, 18.8f, 8.4f); lineTo(8.4f, 18.8f); close()
                moveTo(13.8f, 7f); lineTo(17f, 10.2f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("PlayerTrash") {
            stroke {
                moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
                moveTo(9.5f, 6.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.5f)
                moveTo(6.5f, 6.5f); lineTo(7.4f, 18.4f)
                quadTo(7.6f, 20f, 9.2f, 20f); lineTo(14.8f, 20f)
                quadTo(16.4f, 20f, 16.6f, 18.4f); lineTo(17.5f, 6.5f)
                moveTo(10.3f, 10f); lineTo(10.5f, 16.5f)
                moveTo(13.7f, 10f); lineTo(13.5f, 16.5f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("PlayerRefresh") {
            stroke {
                moveTo(19f, 12f)
                arcTo(7f, 7f, 0f, true, true, 16.9f, 7f)
                moveTo(17.2f, 3.5f); lineTo(17.2f, 7.3f); lineTo(13.4f, 7.3f)
            }
        }
    }

    /** Kotak koleksi dengan tanda tambah: simpan playlist ke Library. */
    val SaveToLibrary: ImageVector by lazy {
        icon("PlayerSaveToLibrary") {
            stroke {
                moveTo(9f, 3.5f); lineTo(15f, 3.5f)
                quadTo(17.5f, 3.5f, 17.5f, 6f); lineTo(17.5f, 18f)
                quadTo(17.5f, 20.5f, 15f, 20.5f); lineTo(9f, 20.5f)
                quadTo(6.5f, 20.5f, 6.5f, 18f); lineTo(6.5f, 6f)
                quadTo(6.5f, 3.5f, 9f, 3.5f); close()
                moveTo(3.5f, 7.5f); lineTo(3.5f, 16.5f)
                moveTo(20.5f, 7.5f); lineTo(20.5f, 16.5f)
                moveTo(12f, 9f); lineTo(12f, 15f); moveTo(9f, 12f); lineTo(15f, 12f)
            }
        }
    }

    private fun PathBuilder.repeatLoop() {
        moveTo(17.5f, 4.5f); lineTo(20f, 7f); lineTo(17.5f, 9.5f)
        moveTo(20f, 7f); lineTo(8f, 7f)
        quadTo(4f, 7f, 4f, 11f); lineTo(4f, 11.5f)
        moveTo(6.5f, 19.5f); lineTo(4f, 17f); lineTo(6.5f, 14.5f)
        moveTo(4f, 17f); lineTo(16f, 17f)
        quadTo(20f, 17f, 20f, 13f); lineTo(20f, 12.5f)
    }

    private fun PathBuilder.heart() {
        moveTo(12f, 19.5f)
        curveTo(12f, 19.5f, 3.5f, 14.6f, 3.5f, 9f)
        curveTo(3.5f, 6.4f, 5.5f, 4.5f, 7.9f, 4.5f)
        curveTo(9.6f, 4.5f, 11.1f, 5.4f, 12f, 6.9f)
        curveTo(12.9f, 5.4f, 14.4f, 4.5f, 16.1f, 4.5f)
        curveTo(18.5f, 4.5f, 20.5f, 6.4f, 20.5f, 9f)
        curveTo(20.5f, 14.6f, 12f, 19.5f, 12f, 19.5f)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        close()
    }
}
