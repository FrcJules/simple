# Vendored MMS PDU code

`com/google/android/mms/**` is copied from **android-smsmms** by Jacob Klinker
(https://github.com/klinker41/android-smsmms, commit f6fa7d3, 2020-09-11), itself derived from the Android Open Source
Project's MMS library. Licence: **Apache-2.0** (headers kept in every file; same licence as this repository).

We use only the PDU parser and composer (`pdu_alt`): decoding a received MMS and building the small PDUs we send.

Changes made to the original (nothing else was edited; every change in `PduParser.java` is marked with a `presence:` comment):
- `import com.klinker.android.logger.Log` → `app.presence.launcher.mms.vendor.Log`, and
  `com.android.mms.util.ExternalLogger` → `app.presence.launcher.mms.vendor.ExternalLogger`: both are **no-ops**, so the contents
  and addresses of a message can never reach the system log. The three `printStackTrace()` calls (`EncodedStringValue`,
  `PduComposer`) were removed for the same reason (they wrote to `System.err`, i.e. logcat).
- `SendReq.prepareFromAddress(...)` removed: it read the user's own phone number (a permission we do not want).
- `PduParser` hardened against hostile messages: the number of parts is capped (64); a header or data length larger than what
  is left in the message is refused instead of being allocated (`parseParts`, `skipWapValue`, the "skip other parameters"
  loop); `multipart/alternative` nesting is capped at 4 levels; an empty alternative is refused.
- Not copied: `PduPersister` and everything that writes to the system MMS provider or does its own networking
  (downloading and sending use the platform's `SmsManager`, see `app/presence/launcher/mms/`).
