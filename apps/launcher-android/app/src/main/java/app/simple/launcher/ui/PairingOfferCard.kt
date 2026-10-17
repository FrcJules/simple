package app.simple.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simple.launcher.R
import app.simple.launcher.notifications.PairingConfig
import app.simple.launcher.notifications.PairingOffer

/**
 * What a pairing QR code offers, shown for the family to check against the home display's own text. The host is the one that will
 * really be contacted (rebuilt from the parsed address) and the certificate fingerprint is shown in groups of four: a link from
 * anywhere else (a web page, a message) cannot know the real hub's fingerprint.
 */
@Composable
fun PairingOfferCard(offer: PairingOffer, replacing: PairingConfig?, busy: Boolean, status: String?, onPair: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pair_offer_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pair_offer_address), fontSize = 18.sp)
            Text(offer.baseUrl, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pair_offer_fingerprint), fontSize = 18.sp)
            offer.fingerprintLines().forEach { Text(it, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            Text(stringResource(R.string.pair_offer_compare), fontSize = 18.sp)
            if (replacing != null) {
                Text(stringResource(R.string.pair_offer_replaces, replacing.baseUrl), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                PairingOffer.fingerprintLines(replacing.certSha256).forEach { Text(it, fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
            }
            if (status != null) Text(status, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Button(onClick = onPair, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(if (busy) R.string.pair_offer_working else if (replacing != null) R.string.pair_offer_replace else R.string.pair_offer_pair), fontSize = 18.sp)
            }
            // Still available while a request is in flight: stopping is always possible, and then nothing is saved.
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.pair_offer_cancel), fontSize = 18.sp)
            }
        }
    }
}
