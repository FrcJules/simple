package app.presence.launcher.ui

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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

private enum class PickTab { FAVORITES, ALL }

/**
 * Choosing a person, to call or to write to: the family's favourites first, or everybody ("Général"), or a search by the first letters
 * of the first name on a big on-screen keyboard (a phone keyboard has keys far too small). Touching a person only reports the choice;
 * the caller asks for the confirmation. With no favourite yet, everybody is shown at once.
 */
@Composable
fun PersonPicker(
    @StringRes title: Int,
    favorites: List<Contact>,
    all: List<Contact>,
    onBack: () -> Unit,
    allLoaded: Boolean = true,
    onPick: (Contact) -> Unit,
) {
    var chosenTab by remember { mutableStateOf<PickTab?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val tab = chosenTab ?: if (favorites.isEmpty()) PickTab.ALL else PickTab.FAVORITES
    PresenceTheme {
        if (searching) {
            SearchPanel(all = all, allLoaded = allLoaded, query = query, onQuery = { query = it }, onPick = onPick, onClose = { searching = false; query = "" })
        } else {
            val shown = if (tab == PickTab.FAVORITES) favorites else all
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TabButton(stringResource(R.string.call_tab_favorites), tab == PickTab.FAVORITES, Modifier.weight(1f)) { chosenTab = PickTab.FAVORITES }
                    TabButton(stringResource(R.string.call_tab_all), tab == PickTab.ALL, Modifier.weight(1f)) { chosenTab = PickTab.ALL }
                }
                OutlinedButton(onClick = { searching = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.call_search), fontSize = 28.sp)
                }
                if (shown.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (tab == PickTab.FAVORITES) R.string.no_favorites else R.string.no_contacts),
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
                        items(shown, key = { it.id }) { c -> ContactCard(c) { onPick(c) } }
                    }
                }
                BackButton(onBack)
            }
        }
    }
}

/** Who to call: the picker, then a confirmation; nothing is dialled before "Oui, appeler". */
@Composable
fun CallScreen(favorites: List<Contact>, all: List<Contact>, onBack: () -> Unit, allLoaded: Boolean = true, onCall: (Contact) -> Unit) {
    var selected by remember { mutableStateOf<Contact?>(null) }
    val haptic = LocalHapticFeedback.current
    PresenceTheme {
        PersonPicker(R.string.call_title, favorites, all, onBack, allLoaded) { selected = it }
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

private const val SEARCH_KEY_COLUMNS = 6
private val SEARCH_LETTERS = ('A'..'Z').toList()

/** Height of one letter key: smaller than the 64 dp of buttons elsewhere on purpose (26 keys must leave room for the results). */
private val SEARCH_KEY_HEIGHT = 48.dp

/**
 * Top: back, the letters typed, and the two correction keys. Middle: every person who matches, as a list that scrolls (all of them,
 * not two). Bottom, in reach of the thumb: the 26 letter keys, 5 per row, compact so that the results keep most of the screen.
 */
@Composable
private fun SearchPanel(all: List<Contact>, allLoaded: Boolean, query: String, onQuery: (String) -> Unit, onPick: (Contact) -> Unit, onClose: () -> Unit) {
    val matches = remember(all, query) { if (query.isEmpty()) emptyList() else ContactSearch.filter(all, query) }
    val haptic = LocalHapticFeedback.current
    val type: (String) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onQuery(it) }
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val typedLabel = stringResource(R.string.call_search_typed, query.ifEmpty { "-" })
        val closeText = stringResource(R.string.call_search_close)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // The arrow is the usual "back"; it leaves the search and keeps nothing.
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.size(64.dp).semantics { contentDescription = closeText },
                contentPadding = PaddingValues(0.dp),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp).semantics { contentDescription = typedLabel; liveRegion = LiveRegionMode.Polite },
            ) {
                Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(query.ifEmpty { "…" }, fontSize = if (query.length > 9) 24.sp else 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            EditKey(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.call_search_erase), filled = true) { type(query.dropLast(1)) }
            Spacer(Modifier.width(24.dp)) // wide gap: "one letter back" and "start again" are never confused
            EditKey(Icons.Filled.Delete, stringResource(R.string.call_search_clear), filled = false) { type("") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                query.isEmpty() -> Text(
                    stringResource(R.string.call_search_title), fontSize = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                )
                !allLoaded -> Text(
                    stringResource(R.string.call_search_loading), fontSize = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                )
                all.isEmpty() -> Text(
                    stringResource(R.string.call_search_no_contacts), fontSize = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                )
                matches.isEmpty() -> Text(
                    pluralStringResource(R.plurals.call_search_none_of, all.size, all.size), fontSize = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // With more people than fit, the count says there are more below.
                    if (matches.size > 3) item { Text(pluralStringResource(R.plurals.call_search_count, matches.size, matches.size), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    items(matches.take(SEARCH_MAX_RESULTS), key = { it.id }) { c -> ResultRow(c) { onPick(c) } }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SEARCH_LETTERS.chunked(SEARCH_KEY_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { letter -> SearchKey(letter.toString(), Modifier.weight(1f)) { type(query + letter) } }
                    repeat(SEARCH_KEY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) } // the last row (Z) keeps the same key width
                }
            }
        }
    }
}

private const val SEARCH_MAX_RESULTS = 100

/** A correction key: an icon (and its spoken name), 64 dp square. Backspace is filled (the usual one), "start again" is only outlined. */
@Composable
private fun EditKey(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, filled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.size(64.dp).semantics { contentDescription = name }
    if (filled) {
        Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(0.dp)) { Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp)) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(0.dp)) { Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
private fun SearchKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val spoken = stringResource(R.string.call_search_letter, label)
    FilledTonalButton(onClick = onClick, modifier = modifier.height(SEARCH_KEY_HEIGHT).semantics { contentDescription = spoken }, contentPadding = PaddingValues(0.dp)) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultRow(contact: Contact, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(horizontal = 4.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(contact, Modifier.size(56.dp), initialSize = 28.sp)
            Text(contact.name, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
