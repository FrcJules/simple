package app.simple.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.simple.launcher.Contact

/** Who to write to: the same picker as for calling (favourites, everybody, search by letters). */
@Composable
fun PickContactScreen(
    @StringRes title: Int,
    favorites: List<Contact>,
    all: List<Contact>,
    onPick: (Contact) -> Unit,
    onBack: () -> Unit,
    allLoaded: Boolean = true,
) {
    PersonPicker(title, favorites, all, onBack, allLoaded, onPick)
}
