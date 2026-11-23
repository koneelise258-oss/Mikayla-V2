package com.example.mikayala.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Jeu d'icônes SVG géométriques inspirées des SF Symbols Apple :
 * traits fins arrondis (1.85 dp), proportions équilibrées sur grille 24x24,
 * teintées dynamiquement par MaterialTheme / MikayalaColors.
 */
object AppleSvgIcons {

    private fun sfStrokeIcon(
        name: String,
        strokeWidth: Float = 1.85f,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

    private val strokeBrush = SolidColor(Color.Black)

    val Chat: ImageVector by lazy {
        sfStrokeIcon("AppleChat") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(6.9f, 3.5f, 3f, 6.9f, 3f, 11.2f)
                curveTo(3f, 13.4f, 4.0f, 15.3f, 5.7f, 16.7f)
                lineTo(4.6f, 20.3f)
                lineTo(8.5f, 18.6f)
                curveTo(9.6f, 18.9f, 10.8f, 19.1f, 12f, 19.1f)
                curveTo(17.1f, 19.1f, 21f, 15.6f, 21f, 11.2f)
                curveTo(21f, 6.9f, 17.1f, 3.5f, 12f, 3.5f)
                close()
            }
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(8.5f, 10.6f)
                lineTo(15.5f, 10.6f)
                moveTo(8.5f, 13.6f)
                lineTo(13.2f, 13.6f)
            }
        }
    }

    val ChatFilled: ImageVector by lazy {
        sfStrokeIcon("AppleChatFilled") {
            path(fill = strokeBrush, pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 3.5f)
                curveTo(6.9f, 3.5f, 3f, 6.9f, 3f, 11.2f)
                curveTo(3f, 13.4f, 4.0f, 15.3f, 5.7f, 16.7f)
                lineTo(4.6f, 20.3f)
                lineTo(8.5f, 18.6f)
                curveTo(9.6f, 18.9f, 10.8f, 19.1f, 12f, 19.1f)
                curveTo(17.1f, 19.1f, 21f, 15.6f, 21f, 11.2f)
                curveTo(21f, 6.9f, 17.1f, 3.5f, 12f, 3.5f)
                close()
            }
        }
    }

    val Phone: ImageVector by lazy {
        sfStrokeIcon("ApplePhone") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.6f, 4.2f)
                curveTo(7.4f, 3.8f, 8.3f, 4.1f, 8.8f, 4.8f)
                lineTo(10.2f, 7.1f)
                curveTo(10.6f, 7.8f, 10.4f, 8.7f, 9.8f, 9.2f)
                lineTo(8.7f, 10.1f)
                curveTo(9.5f, 11.9f, 11.1f, 13.5f, 12.9f, 14.3f)
                lineTo(13.8f, 13.2f)
                curveTo(14.3f, 12.6f, 15.2f, 12.4f, 15.9f, 12.8f)
                lineTo(18.2f, 14.2f)
                curveTo(18.9f, 14.7f, 19.2f, 15.6f, 18.8f, 16.4f)
                curveTo(18.0f, 18.3f, 16.0f, 19.6f, 13.8f, 19.4f)
                curveTo(8.9f, 18.8f, 4.6f, 14.5f, 4.0f, 9.6f)
                curveTo(3.8f, 7.4f, 4.8f, 5.1f, 6.6f, 4.2f)
                close()
            }
        }
    }

    val Video: ImageVector by lazy {
        sfStrokeIcon("AppleVideo") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.8f, 6.5f)
                lineTo(13.5f, 6.5f)
                curveTo(14.9f, 6.5f, 15.8f, 7.4f, 15.8f, 8.8f)
                lineTo(15.8f, 15.2f)
                curveTo(15.8f, 16.6f, 14.9f, 17.5f, 13.5f, 17.5f)
                lineTo(4.8f, 17.5f)
                curveTo(3.4f, 17.5f, 2.5f, 16.6f, 2.5f, 15.2f)
                lineTo(2.5f, 8.8f)
                curveTo(2.5f, 7.4f, 3.4f, 6.5f, 4.8f, 6.5f)
                close()
                moveTo(15.8f, 10.2f)
                lineTo(20.6f, 7.4f)
                curveTo(21.1f, 7.1f, 21.6f, 7.5f, 21.6f, 8.1f)
                lineTo(21.6f, 15.9f)
                curveTo(21.6f, 16.5f, 21.1f, 16.9f, 20.6f, 16.6f)
                lineTo(15.8f, 13.8f)
            }
        }
    }

    val Heart: ImageVector by lazy {
        sfStrokeIcon("AppleHeart") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 19.8f)
                curveTo(12f, 19.8f, 3.5f, 14.3f, 3.5f, 8.7f)
                curveTo(3.5f, 6.1f, 5.5f, 4.2f, 8.0f, 4.2f)
                curveTo(9.6f, 4.2f, 11.1f, 5.0f, 12f, 6.3f)
                curveTo(12.9f, 5.0f, 14.4f, 4.2f, 16.0f, 4.2f)
                curveTo(18.5f, 4.2f, 20.5f, 6.1f, 20.5f, 8.7f)
                curveTo(20.5f, 14.3f, 12f, 19.8f, 12f, 19.8f)
                close()
            }
        }
    }

    val HeartFilled: ImageVector by lazy {
        sfStrokeIcon("AppleHeartFilled") {
            path(fill = strokeBrush) {
                moveTo(12f, 19.8f)
                curveTo(12f, 19.8f, 3.5f, 14.3f, 3.5f, 8.7f)
                curveTo(3.5f, 6.1f, 5.5f, 4.2f, 8.0f, 4.2f)
                curveTo(9.6f, 4.2f, 11.1f, 5.0f, 12f, 6.3f)
                curveTo(12.9f, 5.0f, 14.4f, 4.2f, 16.0f, 4.2f)
                curveTo(18.5f, 4.2f, 20.5f, 6.1f, 20.5f, 8.7f)
                curveTo(20.5f, 14.3f, 12f, 19.8f, 12f, 19.8f)
                close()
            }
        }
    }

    val Lock: ImageVector by lazy {
        sfStrokeIcon("AppleLock") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.8f, 10.5f)
                lineTo(17.2f, 10.5f)
                curveTo(18.2f, 10.5f, 19f, 11.3f, 19f, 12.3f)
                lineTo(19f, 18.4f)
                curveTo(19f, 19.4f, 18.2f, 20.2f, 17.2f, 20.2f)
                lineTo(6.8f, 20.2f)
                curveTo(5.8f, 20.2f, 5f, 19.4f, 5f, 18.4f)
                lineTo(5f, 12.3f)
                curveTo(5f, 11.3f, 5.8f, 10.5f, 6.8f, 10.5f)
                close()
                moveTo(8.3f, 10.5f)
                lineTo(8.3f, 7.6f)
                curveTo(8.3f, 5.4f, 9.9f, 3.8f, 12f, 3.8f)
                curveTo(14.1f, 3.8f, 15.7f, 5.4f, 15.7f, 7.6f)
                lineTo(15.7f, 10.5f)
            }
        }
    }

    val LockOpen: ImageVector by lazy {
        sfStrokeIcon("AppleLockOpen") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.8f, 10.5f)
                lineTo(17.2f, 10.5f)
                curveTo(18.2f, 10.5f, 19f, 11.3f, 19f, 12.3f)
                lineTo(19f, 18.4f)
                curveTo(19f, 19.4f, 18.2f, 20.2f, 17.2f, 20.2f)
                lineTo(6.8f, 20.2f)
                curveTo(5.8f, 20.2f, 5f, 19.4f, 5f, 18.4f)
                lineTo(5f, 12.3f)
                curveTo(5f, 11.3f, 5.8f, 10.5f, 6.8f, 10.5f)
                close()
                moveTo(8.3f, 10.5f)
                lineTo(8.3f, 7.2f)
                curveTo(8.3f, 5.0f, 10.0f, 3.4f, 12.2f, 3.5f)
                curveTo(13.9f, 3.6f, 15.3f, 4.7f, 15.6f, 6.3f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        sfStrokeIcon("AppleSettings") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8.8f)
                arcTo(3.2f, 3.2f, 0f, true, true, 11.99f, 8.8f)
                close()
                moveTo(19.4f, 13.1f)
                lineTo(20.6f, 12.3f)
                curveTo(20.9f, 12.1f, 20.9f, 11.9f, 20.6f, 11.7f)
                lineTo(19.4f, 10.9f)
                lineTo(19.1f, 9.5f)
                lineTo(19.9f, 8.3f)
                curveTo(20.1f, 8.0f, 19.9f, 7.7f, 19.6f, 7.5f)
                lineTo(18.2f, 7.1f)
                lineTo(17.3f, 5.9f)
                lineTo(17.3f, 4.5f)
                curveTo(17.3f, 4.1f, 17.0f, 3.9f, 16.6f, 4.0f)
                lineTo(15.2f, 4.4f)
                lineTo(13.8f, 3.8f)
                lineTo(12.8f, 2.7f)
                curveTo(12.4f, 2.4f, 11.6f, 2.4f, 11.2f, 2.7f)
                lineTo(10.2f, 3.8f)
                lineTo(8.8f, 4.4f)
                lineTo(7.4f, 4.0f)
                curveTo(7.0f, 3.9f, 6.7f, 4.1f, 6.7f, 4.5f)
                lineTo(6.7f, 5.9f)
                lineTo(5.8f, 7.1f)
                lineTo(4.4f, 7.5f)
                curveTo(4.1f, 7.7f, 3.9f, 8.0f, 4.1f, 8.3f)
                lineTo(4.9f, 9.5f)
                lineTo(4.6f, 10.9f)
                lineTo(3.4f, 11.7f)
                curveTo(3.1f, 11.9f, 3.1f, 12.1f, 3.4f, 12.3f)
                lineTo(4.6f, 13.1f)
                lineTo(4.9f, 14.5f)
                lineTo(4.1f, 15.7f)
                curveTo(3.9f, 16.0f, 4.1f, 16.3f, 4.4f, 16.5f)
                lineTo(5.8f, 16.9f)
                lineTo(6.7f, 18.1f)
                lineTo(6.7f, 19.5f)
                curveTo(6.7f, 19.9f, 7.0f, 20.1f, 7.4f, 20.0f)
                lineTo(8.8f, 19.6f)
                lineTo(10.2f, 20.2f)
                lineTo(11.2f, 21.3f)
                curveTo(11.6f, 21.6f, 12.4f, 21.6f, 12.8f, 21.3f)
                lineTo(13.8f, 20.2f)
                lineTo(15.2f, 19.6f)
                lineTo(16.6f, 20.0f)
                curveTo(17.0f, 20.1f, 17.3f, 19.9f, 17.3f, 19.5f)
                lineTo(17.3f, 18.1f)
                lineTo(18.2f, 16.9f)
                lineTo(19.6f, 16.5f)
                curveTo(19.9f, 16.3f, 20.1f, 16.0f, 19.9f, 15.7f)
                lineTo(19.1f, 14.5f)
                close()
            }
        }
    }

    val Search: ImageVector by lazy {
        sfStrokeIcon("AppleSearch") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(10.6f, 4.3f)
                arcTo(6.3f, 6.3f, 0f, true, true, 10.59f, 4.3f)
                close()
                moveTo(15.3f, 15.3f)
                lineTo(20.0f, 20.0f)
            }
        }
    }

    val Mic: ImageVector by lazy {
        sfStrokeIcon("AppleMic") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.2f)
                curveTo(10.3f, 3.2f, 9f, 4.5f, 9f, 6.2f)
                lineTo(9f, 11.2f)
                curveTo(9f, 12.9f, 10.3f, 14.2f, 12f, 14.2f)
                curveTo(13.7f, 14.2f, 15f, 12.9f, 15f, 11.2f)
                lineTo(15f, 6.2f)
                curveTo(15f, 4.5f, 13.7f, 3.2f, 12f, 3.2f)
                close()
                moveTo(6.2f, 11.2f)
                curveTo(6.2f, 14.4f, 8.8f, 17f, 12f, 17f)
                curveTo(15.2f, 17f, 17.8f, 14.4f, 17.8f, 11.2f)
                moveTo(12f, 17f)
                lineTo(12f, 20.5f)
                moveTo(9.2f, 20.5f)
                lineTo(14.8f, 20.5f)
            }
        }
    }

    val Paperclip: ImageVector by lazy {
        sfStrokeIcon("ApplePaperclip") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.6f, 7.6f)
                lineTo(9.2f, 14.0f)
                curveTo(8.1f, 15.1f, 8.1f, 16.9f, 9.2f, 18.0f)
                curveTo(10.3f, 19.1f, 12.1f, 19.1f, 13.2f, 18.0f)
                lineTo(19.0f, 12.2f)
                curveTo(20.8f, 10.4f, 20.8f, 7.5f, 19.0f, 5.7f)
                curveTo(17.2f, 3.9f, 14.3f, 3.9f, 12.5f, 5.7f)
                lineTo(6.4f, 11.8f)
                curveTo(4.0f, 14.2f, 4.0f, 18.1f, 6.4f, 20.5f)
            }
        }
    }

    val Camera: ImageVector by lazy {
        sfStrokeIcon("AppleCamera") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.8f, 7.2f)
                lineTo(7.4f, 7.2f)
                lineTo(8.8f, 4.8f)
                lineTo(15.2f, 4.8f)
                lineTo(16.6f, 7.2f)
                lineTo(19.2f, 7.2f)
                curveTo(20.5f, 7.2f, 21.5f, 8.2f, 21.5f, 9.5f)
                lineTo(21.5f, 17.2f)
                curveTo(21.5f, 18.5f, 20.5f, 19.5f, 19.2f, 19.5f)
                lineTo(4.8f, 19.5f)
                curveTo(3.5f, 19.5f, 2.5f, 18.5f, 2.5f, 17.2f)
                lineTo(2.5f, 9.5f)
                curveTo(2.5f, 8.2f, 3.5f, 7.2f, 4.8f, 7.2f)
                close()
                moveTo(12f, 9.8f)
                arcTo(3.4f, 3.4f, 0f, true, true, 11.99f, 9.8f)
                close()
            }
        }
    }

    val Emoji: ImageVector by lazy {
        sfStrokeIcon("AppleEmoji") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, true, true, 11.99f, 3.2f)
                close()
                moveTo(9.0f, 10.0f)
                lineTo(9.0f, 10.4f)
                moveTo(15.0f, 10.0f)
                lineTo(15.0f, 10.4f)
                moveTo(8.6f, 14.3f)
                curveTo(9.4f, 15.7f, 10.6f, 16.4f, 12f, 16.4f)
                curveTo(13.4f, 16.4f, 14.6f, 15.7f, 15.4f, 14.3f)
            }
        }
    }

    val Send: ImageVector by lazy {
        sfStrokeIcon("AppleSend") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.6f, 4.0f)
                lineTo(3.4f, 10.6f)
                lineTo(10.2f, 13.6f)
                lineTo(13.2f, 20.4f)
                close()
                moveTo(10.2f, 13.6f)
                lineTo(20.2f, 4.4f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        sfStrokeIcon("ApplePlus") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Palette: ImageVector by lazy {
        sfStrokeIcon("ApplePalette") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.2f)
                curveTo(7.0f, 3.2f, 3.2f, 7.0f, 3.2f, 11.8f)
                curveTo(3.2f, 16.0f, 6.3f, 19.0f, 9.6f, 19.0f)
                curveTo(10.7f, 19.0f, 11.4f, 18.2f, 11.4f, 17.2f)
                curveTo(11.4f, 16.3f, 12.0f, 15.6f, 13.0f, 15.6f)
                lineTo(15.2f, 15.6f)
                curveTo(18.3f, 15.6f, 20.8f, 13.2f, 20.8f, 10.2f)
                curveTo(20.8f, 6.2f, 17.0f, 3.2f, 12f, 3.2f)
                close()
                moveTo(8.0f, 10.2f)
                lineTo(8.1f, 10.2f)
                moveTo(11.0f, 7.4f)
                lineTo(11.1f, 7.4f)
                moveTo(15.2f, 8.2f)
                lineTo(15.3f, 8.2f)
                moveTo(16.4f, 11.4f)
                lineTo(16.5f, 11.4f)
            }
        }
    }

    val Sparkles: ImageVector by lazy {
        sfStrokeIcon("AppleSparkles") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11.2f, 3.2f)
                lineTo(12.6f, 8.2f)
                lineTo(17.6f, 9.6f)
                lineTo(12.6f, 11.0f)
                lineTo(11.2f, 16.0f)
                lineTo(9.8f, 11.0f)
                lineTo(4.8f, 9.6f)
                lineTo(9.8f, 8.2f)
                close()
                moveTo(17.6f, 15.0f)
                lineTo(18.3f, 17.3f)
                lineTo(20.6f, 18.0f)
                lineTo(18.3f, 18.7f)
                lineTo(17.6f, 21.0f)
                lineTo(16.9f, 18.7f)
                lineTo(14.6f, 18.0f)
                lineTo(16.9f, 17.3f)
                close()
            }
        }
    }

    val Shield: ImageVector by lazy {
        sfStrokeIcon("AppleShield") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.2f)
                lineTo(19.2f, 6.2f)
                lineTo(19.2f, 11.5f)
                curveTo(19.2f, 16.1f, 16.0f, 19.6f, 12f, 21.0f)
                curveTo(8.0f, 19.6f, 4.8f, 16.1f, 4.8f, 11.5f)
                lineTo(4.8f, 6.2f)
                close()
                moveTo(9.3f, 12.0f)
                lineTo(11.2f, 13.9f)
                lineTo(15.0f, 10.1f)
            }
        }
    }

    val Bell: ImageVector by lazy {
        sfStrokeIcon("AppleBell") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.2f, 15.8f)
                lineTo(6.2f, 10.2f)
                curveTo(6.2f, 6.9f, 8.8f, 4.2f, 12f, 4.2f)
                curveTo(15.2f, 4.2f, 17.8f, 6.9f, 17.8f, 10.2f)
                lineTo(17.8f, 15.8f)
                lineTo(19.5f, 17.6f)
                lineTo(4.5f, 17.6f)
                close()
                moveTo(10.0f, 19.6f)
                curveTo(10.4f, 20.5f, 11.1f, 21.0f, 12f, 21.0f)
                curveTo(12.9f, 21.0f, 13.6f, 20.5f, 14.0f, 19.6f)
            }
        }
    }

    val Location: ImageVector by lazy {
        sfStrokeIcon("AppleLocation") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20.8f)
                curveTo(12f, 20.8f, 18.2f, 15.0f, 18.2f, 10.0f)
                curveTo(18.2f, 6.5f, 15.4f, 3.8f, 12f, 3.8f)
                curveTo(8.6f, 3.8f, 5.8f, 6.5f, 5.8f, 10.0f)
                curveTo(5.8f, 15.0f, 12f, 20.8f, 12f, 20.8f)
                close()
                moveTo(12f, 7.8f)
                arcTo(2.2f, 2.2f, 0f, true, true, 11.99f, 7.8f)
                close()
            }
        }
    }

    val Document: ImageVector by lazy {
        sfStrokeIcon("AppleDocument") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.0f, 3.5f)
                lineTo(14.0f, 3.5f)
                lineTo(18.5f, 8.0f)
                lineTo(18.5f, 19.0f)
                curveTo(18.5f, 19.9f, 17.8f, 20.5f, 17.0f, 20.5f)
                lineTo(7.0f, 20.5f)
                curveTo(6.2f, 20.5f, 5.5f, 19.9f, 5.5f, 19.0f)
                lineTo(5.5f, 5.0f)
                curveTo(5.5f, 4.1f, 6.2f, 3.5f, 7.0f, 3.5f)
                close()
                moveTo(14.0f, 3.5f)
                lineTo(14.0f, 8.0f)
                lineTo(18.5f, 8.0f)
                moveTo(8.8f, 12.2f)
                lineTo(15.2f, 12.2f)
                moveTo(8.8f, 15.6f)
                lineTo(13.6f, 15.6f)
            }
        }
    }

    val Contact: ImageVector by lazy {
        sfStrokeIcon("AppleContact") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5.2f)
                arcTo(3.4f, 3.4f, 0f, true, true, 11.99f, 5.2f)
                close()
                moveTo(5.2f, 19.5f)
                curveTo(5.6f, 15.9f, 8.4f, 14.0f, 12f, 14.0f)
                curveTo(15.6f, 14.0f, 18.4f, 15.9f, 18.8f, 19.5f)
            }
        }
    }

    val Back: ImageVector by lazy {
        sfStrokeIcon("AppleBack") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.0f, 5.5f)
                lineTo(8.5f, 12.0f)
                lineTo(15.0f, 18.5f)
            }
        }
    }

    val More: ImageVector by lazy {
        sfStrokeIcon("AppleMore") {
            path(fill = strokeBrush) {
                moveTo(12f, 5.2f)
                arcTo(1.4f, 1.4f, 0f, true, true, 11.99f, 5.2f)
                close()
                moveTo(12f, 10.6f)
                arcTo(1.4f, 1.4f, 0f, true, true, 11.99f, 10.6f)
                close()
                moveTo(12f, 16.0f)
                arcTo(1.4f, 1.4f, 0f, true, true, 11.99f, 16.0f)
                close()
            }
        }
    }

    val Cloud: ImageVector by lazy {
        sfStrokeIcon("AppleCloud") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.4f, 18.2f)
                lineTo(17.2f, 18.2f)
                curveTo(19.6f, 18.2f, 21.2f, 16.4f, 21.2f, 14.2f)
                curveTo(21.2f, 12.1f, 19.6f, 10.4f, 17.5f, 10.2f)
                curveTo(16.9f, 6.8f, 13.8f, 4.6f, 10.5f, 5.2f)
                curveTo(7.8f, 5.7f, 5.9f, 7.9f, 5.7f, 10.6f)
                curveTo(3.9f, 11.1f, 2.8f, 12.7f, 2.8f, 14.5f)
                curveTo(2.8f, 16.6f, 4.8f, 18.2f, 7.4f, 18.2f)
                close()
            }
        }
    }

    val Down: ImageVector by lazy {
        sfStrokeIcon("AppleDown") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.5f, 9.5f)
                lineTo(12f, 15.0f)
                lineTo(17.5f, 9.5f)
            }
        }
    }

    val Star: ImageVector by lazy {
        sfStrokeIcon("AppleStar") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.6f)
                lineTo(14.5f, 8.7f)
                lineTo(20.1f, 9.5f)
                lineTo(16.0f, 13.5f)
                lineTo(17.0f, 19.1f)
                lineTo(12f, 16.4f)
                lineTo(7.0f, 19.1f)
                lineTo(8.0f, 13.5f)
                lineTo(3.9f, 9.5f)
                lineTo(9.5f, 8.7f)
                close()
            }
        }
    }

    val Calendar: ImageVector by lazy {
        sfStrokeIcon("AppleCalendar") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.2f, 5.5f)
                lineTo(18.8f, 5.5f)
                curveTo(19.9f, 5.5f, 20.6f, 6.2f, 20.6f, 7.3f)
                lineTo(20.6f, 18.5f)
                curveTo(20.6f, 19.6f, 19.9f, 20.3f, 18.8f, 20.3f)
                lineTo(5.2f, 20.3f)
                curveTo(4.1f, 20.3f, 3.4f, 19.6f, 3.4f, 18.5f)
                lineTo(3.4f, 7.3f)
                curveTo(3.4f, 6.2f, 4.1f, 5.5f, 5.2f, 5.5f)
                close()
                moveTo(8.2f, 3.4f)
                lineTo(8.2f, 6.8f)
                moveTo(15.8f, 3.4f)
                lineTo(15.8f, 6.8f)
                moveTo(3.4f, 10.2f)
                lineTo(20.6f, 10.2f)
            }
        }
    }

    val Gamepad: ImageVector by lazy {
        sfStrokeIcon("AppleGamepad") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.8f, 8.0f)
                lineTo(17.2f, 8.0f)
                curveTo(19.8f, 8.0f, 21.4f, 10.4f, 20.8f, 13.8f)
                lineTo(20.1f, 17.0f)
                curveTo(19.7f, 18.4f, 18.2f, 18.8f, 17.2f, 17.8f)
                lineTo(14.8f, 15.6f)
                lineTo(9.2f, 15.6f)
                lineTo(6.8f, 17.8f)
                curveTo(5.8f, 18.8f, 4.3f, 18.4f, 3.9f, 17.0f)
                lineTo(3.2f, 13.8f)
                curveTo(2.6f, 10.4f, 4.2f, 8.0f, 6.8f, 8.0f)
                close()
                moveTo(8.2f, 10.4f)
                lineTo(8.2f, 13.2f)
                moveTo(6.8f, 11.8f)
                lineTo(9.6f, 11.8f)
                moveTo(15.2f, 11.2f)
                lineTo(15.3f, 11.2f)
                moveTo(17.0f, 12.6f)
                lineTo(17.1f, 12.6f)
            }
        }
    }

    val Storage: ImageVector by lazy {
        sfStrokeIcon("AppleStorage") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 5.2f)
                lineTo(19.5f, 5.2f)
                curveTo(20.3f, 5.2f, 20.8f, 5.7f, 20.8f, 6.5f)
                lineTo(20.8f, 9.8f)
                curveTo(20.8f, 10.6f, 20.3f, 11.1f, 19.5f, 11.1f)
                lineTo(4.5f, 11.1f)
                curveTo(3.7f, 11.1f, 3.2f, 10.6f, 3.2f, 9.8f)
                lineTo(3.2f, 6.5f)
                curveTo(3.2f, 5.7f, 3.7f, 5.2f, 4.5f, 5.2f)
                close()
                moveTo(4.5f, 13.2f)
                lineTo(19.5f, 13.2f)
                curveTo(20.3f, 13.2f, 20.8f, 13.7f, 20.8f, 14.5f)
                lineTo(20.8f, 17.8f)
                curveTo(20.8f, 18.6f, 20.3f, 19.1f, 19.5f, 19.1f)
                lineTo(4.5f, 19.1f)
                curveTo(3.7f, 19.1f, 3.2f, 18.6f, 3.2f, 17.8f)
                lineTo(3.2f, 14.5f)
                curveTo(3.2f, 13.7f, 3.7f, 13.2f, 4.5f, 13.2f)
                close()
                moveTo(16.6f, 8.1f)
                lineTo(17.6f, 8.1f)
                moveTo(16.6f, 16.1f)
                lineTo(17.6f, 16.1f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        sfStrokeIcon("AppleWifi") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3.8f, 9.2f)
                curveTo(8.4f, 5.1f, 15.6f, 5.1f, 20.2f, 9.2f)
                moveTo(6.8f, 12.6f)
                curveTo(9.8f, 10.0f, 14.2f, 10.0f, 17.2f, 12.6f)
                moveTo(9.8f, 16.0f)
                curveTo(11.1f, 14.9f, 12.9f, 14.9f, 14.2f, 16.0f)
                moveTo(12f, 19.0f)
                lineTo(12.05f, 19.0f)
            }
        }
    }

    val Close: ImageVector by lazy {
        sfStrokeIcon("AppleClose") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        sfStrokeIcon("AppleTrash") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.8f, 6.8f)
                lineTo(19.2f, 6.8f)
                moveTo(9.0f, 6.8f)
                lineTo(9.5f, 4.2f)
                lineTo(14.5f, 4.2f)
                lineTo(15.0f, 6.8f)
                moveTo(6.6f, 6.8f)
                lineTo(7.4f, 18.6f)
                curveTo(7.5f, 19.6f, 8.2f, 20.3f, 9.2f, 20.3f)
                lineTo(14.8f, 20.3f)
                curveTo(15.8f, 20.3f, 16.5f, 19.6f, 16.6f, 18.6f)
                lineTo(17.4f, 6.8f)
            }
        }
    }

    val Check: ImageVector by lazy {
        sfStrokeIcon("AppleCheck") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.0f, 12.6f)
                lineTo(9.8f, 17.4f)
                lineTo(19.2f, 7.2f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        sfStrokeIcon("ApplePin") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.2f, 4.2f)
                lineTo(19.8f, 8.8f)
                lineTo(15.6f, 12.4f)
                lineTo(15.2f, 15.6f)
                lineTo(8.4f, 8.8f)
                lineTo(11.6f, 8.4f)
                close()
                moveTo(11.8f, 12.2f)
                lineTo(5.2f, 18.8f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        sfStrokeIcon("AppleCopy") {
            path(
                fill = null,
                stroke = strokeBrush,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9.0f, 8.5f)
                lineTo(17.5f, 8.5f)
                curveTo(18.6f, 8.5f, 19.5f, 9.4f, 19.5f, 10.5f)
                lineTo(19.5f, 18.5f)
                curveTo(19.5f, 19.6f, 18.6f, 20.5f, 17.5f, 20.5f)
                lineTo(9.0f, 20.5f)
                curveTo(7.9f, 20.5f, 7.0f, 19.6f, 7.0f, 18.5f)
                lineTo(7.0f, 10.5f)
                curveTo(7.0f, 9.4f, 7.9f, 8.5f, 9.0f, 8.5f)
                close()
                moveTo(15.5f, 8.5f)
                lineTo(15.5f, 5.5f)
                curveTo(15.5f, 4.4f, 14.6f, 3.5f, 13.5f, 3.5f)
                lineTo(6.5f, 3.5f)
                curveTo(5.4f, 3.5f, 4.5f, 4.4f, 4.5f, 5.5f)
                lineTo(4.5f, 14.5f)
            }
        }
    }

    val Person: ImageVector by lazy { Contact }
}
