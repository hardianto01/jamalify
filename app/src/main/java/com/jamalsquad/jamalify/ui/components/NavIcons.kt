package com.jamalsquad.jamalify.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ikon bottom bar bergaris tipis seperti desain acuan. Material Icons
 * bergaris tebal dan bersudut, jadi digambar sendiri di kanvas 24×24.
 * Warna di sini hanya penanda; [androidx.compose.material3.Icon] menimpanya
 * dengan tint.
 */
object NavIcons {

    val Home: ImageVector by lazy { icon("NavHome") { stroke { house() } } }

    val HomeFilled: ImageVector by lazy {
        icon("NavHomeFilled") {
            path(fill = SolidColor(Color.Black)) { house() }
            stroke { house() }
        }
    }

    val Search: ImageVector by lazy { icon("NavSearch") { stroke { magnifier() } } }

    val SearchBold: ImageVector by lazy { icon("NavSearchBold") { stroke(2.4f) { magnifier() } } }

    val Library: ImageVector by lazy {
        icon("NavLibrary") {
            stroke { card(); sideBars() }
            stroke { note() }
        }
    }

    /** Badan ikon library yang terisi; notnya ditumpuk terpisah ([LibraryNote]). */
    val LibraryFilled: ImageVector by lazy {
        icon("NavLibraryFilled") {
            path(fill = SolidColor(Color.Black)) { card() }
            stroke { card(); sideBars() }
        }
    }

    /** Not untuk ditumpuk di atas [LibraryFilled] dengan warna latar. */
    val LibraryNote: ImageVector by lazy { icon("NavLibraryNote") { stroke(1.8f) { note() } } }

    private fun PathBuilder.house() {
        moveTo(4.5f, 10.2f)
        lineTo(12f, 4f)
        lineTo(19.5f, 10.2f)
        lineTo(19.5f, 18.3f)
        quadTo(19.5f, 20.5f, 17.3f, 20.5f)
        lineTo(6.7f, 20.5f)
        quadTo(4.5f, 20.5f, 4.5f, 18.3f)
        close()
    }

    private fun PathBuilder.magnifier() {
        moveTo(17.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, true, true, 4.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, true, true, 17.5f, 11f)
        close()
        moveTo(16f, 16f)
        lineTo(20.5f, 20.5f)
    }

    private fun PathBuilder.card() {
        moveTo(9f, 3.5f)
        lineTo(15f, 3.5f)
        quadTo(17.5f, 3.5f, 17.5f, 6f)
        lineTo(17.5f, 18f)
        quadTo(17.5f, 20.5f, 15f, 20.5f)
        lineTo(9f, 20.5f)
        quadTo(6.5f, 20.5f, 6.5f, 18f)
        lineTo(6.5f, 6f)
        quadTo(6.5f, 3.5f, 9f, 3.5f)
        close()
    }

    private fun PathBuilder.sideBars() {
        moveTo(3.5f, 7.5f)
        lineTo(3.5f, 16.5f)
        moveTo(20.5f, 7.5f)
        lineTo(20.5f, 16.5f)
    }

    private fun PathBuilder.note() {
        moveTo(13f, 14.3f)
        lineTo(13f, 8.5f)
        lineTo(15f, 9.3f)
        moveTo(13f, 14.3f)
        arcTo(1.4f, 1.4f, 0f, true, true, 10.2f, 14.3f)
        arcTo(1.4f, 1.4f, 0f, true, true, 13f, 14.3f)
    }
}

internal fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

internal fun ImageVector.Builder.stroke(width: Float = 1.6f, block: PathBuilder.() -> Unit) = path(
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block
)

internal fun ImageVector.Builder.fill(block: PathBuilder.() -> Unit) = path(
    fill = SolidColor(Color.Black),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 1.2f,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block
)
