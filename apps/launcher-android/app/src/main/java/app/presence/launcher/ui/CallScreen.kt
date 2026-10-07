package app.presence.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.Contact
import app.presence.launcher.R
import coil.compose.AsyncImage

@Composable
fun CallScreen(contacts: List<Contact>, onBack: () -> Unit, onCall: (Contact) -> Unit) {
    var selected by remember { mutableStateOf<Contact?>(null) }
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.call_title), fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                    items(contacts, key = { it.id }) { c -> ContactCard(c) { selected = c } }
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text("⬅  " + stringResource(R.string.back), fontSize = 28.sp)
            }
        }
        selected?.let { c ->
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(stringResource(R.string.call_confirm, c.name), fontSize = 28.sp) },
                text = { Avatar(c, Modifier.fillMaxWidth().aspectRatio(1f)) },
                confirmButton = {
                    Button(onClick = { selected = null; onCall(c) }, modifier = Modifier.heightIn(min = 72.dp)) {
                        Text("📞  " + stringResource(R.string.call_yes), fontSize = 24.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { selected = null }, modifier = Modifier.heightIn(min = 72.dp)) {
                        Text(stringResource(R.string.call_no), fontSize = 24.sp)
                    }
                },
            )
        }
    }
}

@Composable
private fun ContactCard(contact: Contact, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(contact, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(contact.name, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The contact's photo, or a large initial when there is none. */
@Composable
private fun Avatar(contact: Contact, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    if (contact.photoUri != null) {
        AsyncImage(
            model = contact.photoUri,
            contentDescription = contact.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(contact.initial, fontSize = 72.sp)
        }
    }
}
