package app.simple.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.simple.launcher.R
import coil.compose.AsyncImage
import java.io.File

/** A received picture, large. Nothing here can delete, share or edit it: the family's pictures stay as they arrived. */
@Composable
fun ImageViewerScreen(path: String, onBack: () -> Unit) {
    SimpleTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.message_photo),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.Filled.BrokenImage),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            BackButton(onBack)
        }
    }
}
