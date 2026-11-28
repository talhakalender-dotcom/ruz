package com.talha.ruz.money

/** Tutarlar kuruş olarak (Long) tutulur; yuvarlama hatası olmaz. */
object Money {
    /** 125050 → "1.250,50 ₺", 35000 → "350 ₺". */
    fun format(kurus: Long): String {
        val negative = kurus < 0
        val abs = if (negative) -kurus else kurus
        val lira = abs / 100
        val rest = abs % 100
        val grouped = lira.toString().reversed().chunked(3).joinToString(".").reversed()
        val text = if (rest == 0L) grouped else "$grouped,${rest.toString().padStart(2, '0')}"
        return (if (negative) "-" else "") + text + " ₺"
    }

    /** "1.250,50" / "1250.5" / "350" → kuruş; okunamazsa null. Kullanıcının elle girdiği tutar için. */
    fun parse(input: String): Long? {
        val t = input.trim().replace("₺", "").replace("TL", "", ignoreCase = true).replace(" ", "")
        if (t.isEmpty()) return null
        val m = Regex("^(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?$").find(t) ?: return null
        val lira = m.groupValues[1].replace(".", "").toLongOrNull() ?: return null
        val frac = m.groupValues[2].let { if (it.isEmpty()) 0L else it.padEnd(2, '0').toLong() }
        return lira * 100 + frac
    }
}
