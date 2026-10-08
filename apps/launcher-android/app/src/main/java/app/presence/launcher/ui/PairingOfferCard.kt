package app.presence.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R
import app.presence.launcher.notifications.PairingOffer

/**
 * What a pairing QR code offers, shown for the family to check against the home display's own text. The host is the one that will
 * really be contacted (rebuilt from the parsed address) and the certificate fingerprint is shown in groups of four: a link from
 * anywhere else (a web page, a message) cannot know the real hub's fingerprint.
 */
@Composable
fun PairingOfferCard(offer: PairingOffer, replacing: String?, busy: Boolean, status: String?, onPair: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pair_offer_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pair_offer_address), fontSize = 20.sp)
            Text(offer.baseUrl, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pair_offer_fingerprint), fontSize = 20.sp)
            Text(offer.groupedFingerprint(), fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pair_offer_compare), fontSize = 20.sp)
            if (replacing != null) Text(stringResource(R.string.pair_offer_replaces, replacing), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (status != null) Text(status, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onPair, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.pair_offer_working else R.string.pair_offer_pair), fontSize = 22.sp)
            }
            OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pair_offer_cancel), fontSize = 22.sp)
            }
        }
    }
}
