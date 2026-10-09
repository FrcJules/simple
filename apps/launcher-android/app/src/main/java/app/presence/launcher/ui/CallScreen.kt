package app.presence.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.automirrored.filled.Backspace
import app.presence.launcher.ContactSearch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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

private enum class CallTab { FAVORITES, ALL }

/**
 * Who to call: the family's favourites first, or everybody ("Général"), or a search by the first letters of the first name on a big
 * on-screen keyboard (a phone keyboard has keys far too small). Touching a person only opens the confirmation; nothing is dialled here.
 */
@Composable
fun CallScreen(favorites: List<Contact>, all: List<Contact>, onBack: () -> Unit, onCall: (Contact) -> Unit) {
    var selected by remember { mutableStateOf<Contact?>(null) }
    var chosenTab by remember { mutableStateOf<CallTab?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val tab = chosenTab ?: if (favorites.isEmpty()) CallTab.ALL else CallTab.FAVORITES // with no favourite yet, show everybody
    val haptic = LocalHapticFeedback.current
    PresenceTheme {
        if (searching) {
            SearchPanel(
                all = all,
                query = query,
                onQuery = { query = it },
                onPick = { selected = it },
                onClose = { searching = false; query = "" },
            )
        } else {
            val shown = if (tab == CallTab.FAVORITES) favorites else all
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.call_title), fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TabButton(stringResource(R.string.call_tab_favorites), tab == CallTab.FAVORITES, Modifier.weight(1f)) { chosenTab = CallTab.FAVORITES }
                    TabButton(stringResource(R.string.call_tab_all), tab == CallTab.ALL, Modifier.weight(1f)) { chosenTab = CallTab.ALL }
                }
                OutlinedButton(onClick = { searching = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.call_search), fontSize = 28.sp)
                }
                if (shown.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (tab == CallTab.FAVORITES) R.string.no_favorites else R.string.no_contacts),
                            fontSize = 24.sp, textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(shown, key = { it.id }) { c -> ContactCard(c) { selected = c } }
                    }
                }
                BackButton(onBack)
            }
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

/** The selected tab is filled and carries a tick: the choice never depends on colour alone. */
@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 28.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
    val tabModifier = modifier.heightIn(min = 72.dp).semantics { this.selected = selected; role = Role.Tab }
    if (selected) Button(onClick, tabModifier) { content() } else OutlinedButton(onClick, tabModifier) { content() }
}

private const val SEARCH_KEY_COLUMNS = 5
private const val SEARCH_SLOTS = 2
private val SEARCH_LETTERS = ('A'..'Z').toList()

/**
 * Typed letters and the people they match stay fixed at the top (their place never moves while typing); only the keys scroll.
 * At most [SEARCH_SLOTS] people are shown, with the count of the others.
 */
@Composable
private fun SearchPanel(all: List<Contact>, query: String, onQuery: (String) -> Unit, onPick: (Contact) -> Unit, onClose: () -> Unit) {
    val matches = remember(all, query) { ContactSearch.filter(all, query) }
    val haptic = LocalHapticFeedback.current
    val type: (String) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onQuery(it) }
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.call_search_close), fontSize = 24.sp)
        }
        val typedLabel = stringResource(R.string.call_search_typed, query.ifEmpty { "-" })
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { contentDescription = typedLabel; liveRegion = LiveRegionMode.Polite },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (query.isEmpty()) Text(stringResource(R.string.call_search_title), fontSize = 24.sp, textAlign = TextAlign.Center)
                else Text(query, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            }
        }
        // A fixed zone: one slot per person shown, empty slots keep the keys where the finger expects them.
        Column(Modifier.fillMaxWidth().height((SEARCH_SLOTS * 80 + 36).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (query.isNotEmpty()) {
                matches.take(SEARCH_SLOTS).forEach { c -> ResultRow(c) { onPick(c) } }
                val extra = matches.size - SEARCH_SLOTS
                val note = when {
                    matches.isEmpty() -> stringResource(R.string.call_search_none)
                    extra > 0 -> pluralStringResource(R.plurals.call_search_more, extra, extra)
                    else -> null
                }
                if (note != null) Text(note, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SEARCH_LETTERS.chunked(SEARCH_KEY_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { letter -> SearchKey(letter.toString(), Modifier.weight(1f)) { type(query + letter) } }
                    repeat(SEARCH_KEY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) } // the last row (Z) keeps the same key width
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SearchAction(stringResource(R.string.call_search_erase), Icons.AutoMirrored.Filled.Backspace, Modifier.weight(1f)) { type(query.dropLast(1)) }
                SearchAction(stringResource(R.string.call_search_clear), Icons.Filled.Close, Modifier.weight(1f)) { type("") }
            }
        }
    }
}

@Composable
private fun SearchAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 72.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val spoken = stringResource(R.string.call_search_letter, label)
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 72.dp).semantics { contentDescription = spoken }, contentPadding = PaddingValues(0.dp)) {
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultRow(contact: Contact, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(8.dp).heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(contact, Modifier.size(64.dp), initialSize = 32.sp)
            Text(contact.name, fontSize = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
internal fun ContactCard(contact: Contact, onClick: () -> Unit) {
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
