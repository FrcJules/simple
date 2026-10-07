package app.presence.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.Contact
import app.presence.launcher.R

/** The family's people as photos, to choose who to write to. */
@Composable
fun PickContactScreen(@StringRes title: Int, contacts: List<Contact>, onPick: (Contact) -> Unit, onBack: () -> Unit) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(title), fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (contacts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_contacts), fontSize = 24.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(contacts, key = { it.id }) { c -> ContactCard(c) { onPick(c) } }
                }
            }
            BackButton(onBack)
        }
    }
}
