package app.presence.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import app.presence.launcher.config.TileIcon

fun TileIcon.vector(): ImageVector = when (this) {
    TileIcon.CALL -> Icons.Filled.Call
    TileIcon.SMS -> Icons.Filled.Sms
    TileIcon.CAMERA -> Icons.Filled.PhotoCamera
    TileIcon.PHOTOS -> Icons.Filled.PhotoLibrary
    TileIcon.FAVORITE -> Icons.Filled.Favorite
    TileIcon.HOME -> Icons.Filled.Home
    TileIcon.PERSON -> Icons.Filled.Person
    TileIcon.MUSIC -> Icons.Filled.MusicNote
    TileIcon.TV -> Icons.Filled.Tv
    TileIcon.SUN -> Icons.Filled.WbSunny
    TileIcon.APPS -> Icons.Filled.Apps
}
