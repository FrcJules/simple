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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import app.presence.launcher.Contact
import app.presence.launcher.R
import coil.compose.AsyncImage

@Composable
fun CallScreen(contacts: List<Contact>, onBack: () -> Unit, onCall: (Contact) -> Unit) {
    var selected by remember { mutableStateOf<Contact?>(null) }
    val haptic = LocalHapticFeedback.current
    PresenceTheme {
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
            BackButton(onBack)
        }
        selected?.let { c ->
            Dialog(onDismissRequest = { selected = null }) {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.call_confirm, c.name), fontSize = 28.sp, textAlign = TextAlign.Center)
                        Avatar(c, Modifier.fillMaxWidth().aspectRatio(1f))
                        // Full-width stacked buttons: easier targets, no unequal right-aligned pair.
                        Button(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selected = null; onCall(c) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.call_yes), fontSize = 28.sp)
                        }
                        OutlinedButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.call_no), fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Shown between the confirmation and the system call screen: gives clear feedback and a last chance to cancel. */
@Composable
fun CallingScreen(contact: Contact, onCancel: () -> Unit, onLaunch: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(contact.id) {
        kotlinx.coroutines.delay(1_500)
        onLaunch()
    }
    PresenceTheme {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(contact, Modifier.fillMaxWidth(0.7f).aspectRatio(1f))
            Text(stringResource(R.string.calling, contact.name), fontSize = 36.sp, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.call_no), fontSize = 28.sp)
            }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.back), fontSize = 28.sp)
    }
}

@Composable
private fun ContactCard(contact: Contact, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(contact, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(contact.name, fontSize = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The contact's photo, or a large initial when there is none. */
@Composable
internal fun Avatar(contact: Contact, modifier: Modifier, initialSize: TextUnit = 72.sp) {
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
            if (contact.initial.firstOrNull()?.isLetter() == true) {
                Text(contact.initial, fontSize = initialSize)
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.fillMaxSize(0.6f))
            }
        }
    }
}
