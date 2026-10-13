package app.simple.launcher.messages

import java.text.Normalizer

/** Compares notification titles with contact names: ignores case, accents and surrounding spaces. */
fun normalizeName(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
