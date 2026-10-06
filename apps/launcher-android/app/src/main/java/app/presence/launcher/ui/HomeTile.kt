package app.presence.launcher.ui

import androidx.annotation.StringRes
import app.presence.launcher.R

/** The few essential functions shown on the home screen (see docs/vision.md). */
enum class HomeTile(val emoji: String, @StringRes val label: Int) {
    CALL("📞", R.string.tile_call),
    MESSAGES("💬", R.string.tile_messages),
    CAMERA("📷", R.string.tile_camera),
    PHOTOS("🖼️", R.string.tile_photos),
}
