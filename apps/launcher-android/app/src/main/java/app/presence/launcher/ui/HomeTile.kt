package app.presence.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sms
import androidx.compose.ui.graphics.vector.ImageVector
import app.presence.launcher.R

/** The few essential functions shown on the home screen (see docs/vision.md). */
enum class HomeTile(val icon: ImageVector, @StringRes val label: Int) {
    CALL(Icons.Filled.Call, R.string.tile_call),
    MESSAGES(Icons.Filled.Sms, R.string.tile_messages),
    CAMERA(Icons.Filled.PhotoCamera, R.string.tile_camera),
    PHOTOS(Icons.Filled.PhotoLibrary, R.string.tile_photos),
}
