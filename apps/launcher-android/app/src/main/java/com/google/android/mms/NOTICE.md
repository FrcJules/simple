# Vendored MMS PDU code

`com/google/android/mms/**` is copied from **android-smsmms** by Jacob Klinker
(https://github.com/klinker41/android-smsmms, commit f6fa7d3, 2020-09-11), itself derived from the Android Open Source
Project's MMS library. Licence: **Apache-2.0** (headers kept in every file; same licence as this repository).

We use only the PDU parser and composer (`pdu_alt`): decoding a received MMS and building the small PDUs we send.

Changes made to the original (nothing else was edited):
- `import com.klinker.android.logger.Log` → `app.presence.launcher.mms.vendor.Log`, and
  `com.android.mms.util.ExternalLogger` → `app.presence.launcher.mms.vendor.ExternalLogger`: both are **no-ops**, so the contents
  and addresses of a message can never reach the system log.
- `SendReq.prepareFromAddress(...)` removed: it read the user's own phone number (a permission we do not want).
- Not copied: `PduPersister` and everything that writes to the system MMS provider or does its own networking
  (downloading and sending use the platform's `SmsManager`, see `app/presence/launcher/mms/`).
