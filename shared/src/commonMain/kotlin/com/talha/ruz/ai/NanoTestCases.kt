package com.talha.ruz.ai

/**
 * Gemini Nano'nun Türkçe kalitesini ölçmek için deneme seti.
 * Her iş hem Türkçe hem İngilizce yönergeyle denenir; hangisi daha iyiyse istemler ona göre yazılacak.
 */
data class NanoTestCase(
    val group: String,
    val variant: String,
    val input: String,
    val prompt: String,
)

object NanoTestCases {
    private const val CATEGORIES = "İş, Alışveriş, Fikirler, Ödemeler, Genel"

    val notes = listOf(
        "yarın sabah vergi dairesine gidip KDV beyannamesini teslim etmem lazım",
        "süt yumurta ekmek ve deterjan al",
        "balkon için sürgülü cam sistemi yapsak güzel olur, fiyat araştır",
        "şantiyedeki beton dökümü için pompa firmasını ara",
        "elektrik faturası son gün 15'i",
        "annemin doğum günü için hediye düşün",
        "proje hakedişini cuma gününe kadar hazırla",
        "Suç ve Ceza'yı okumaya başla",
    )

    val planContext = """
        - Vergi dairesi, KDV beyannamesi teslimi (Ödemeler) — 28 Kasım Cuma 10:00
        - Elektrik faturası son ödeme günü (Ödemeler) — 15 Aralık
        - Hakediş raporu hazırla (İş) — 1 Aralık Pazartesi 09:00-12:00
        - Süt, yumurta, ekmek al (Alışveriş)
        - Balkon için sürgülü cam fiyatı araştır (Fikirler)
    """.trimIndent()

    val questions = listOf(
        "Vergi dairesine ne zaman gitmem lazım?",
        "Bu hafta hangi ödemelerim var?",
        "Alışveriş listemde ne var?",
        "Pazartesi sabah ne yapacağım?",
    )

    val dumps = listOf(
        "akşam markete uğra süt al, sonra annemi ara, hafta sonu arabanın yağını değiştirt ve aklıma gelmişken mutfak dolabı için usta bul",
    )

    fun titlePromptTr(note: String) =
        "Bir not uygulamasına şu not yazıldı: \"$note\"\n" +
            "Bu not için en fazla 6 kelimelik kısa bir başlık yaz ve şu kategorilerden birini seç: $CATEGORIES.\n" +
            "Sadece tek satır cevap ver, biçim: başlık | kategori"

    fun titlePromptEn(note: String) =
        "A user wrote this note in Turkish: \"$note\"\n" +
            "Write a short Turkish title (max 6 words) and pick exactly one category from: $CATEGORIES.\n" +
            "Answer with a single line only, format: title | category"

    fun questionPromptTr(question: String) =
        "Bugün 27 Kasım 2026 Perşembe. Kullanıcının notları ve planı:\n$planContext\n" +
            "Sadece bu bilgilere dayanarak soruyu kısa ve Türkçe cevapla. Bilgi yoksa \"Bu konuda notun yok.\" de.\n" +
            "Soru: $question"

    fun questionPromptEn(question: String) =
        "Today is Thursday, 27 November 2026. The user's notes and plan (in Turkish):\n$planContext\n" +
            "Answer the question briefly in Turkish, using only this information. " +
            "If the answer is not there, say \"Bu konuda notun yok.\"\n" +
            "Question: $question"

    fun splitPromptTr(text: String) =
        "Aşağıdaki metni ayrı yapılacak işlere böl. Her işi ayrı satıra, başına \"- \" koyarak yaz. Başka bir şey yazma.\n" +
            "Metin: $text"

    fun splitPromptEn(text: String) =
        "Split the following Turkish text into separate to-do items. Write each item in Turkish on its own line, " +
            "starting with \"- \". Write nothing else.\nText: $text"

    val all: List<NanoTestCase> = buildList {
        notes.forEach { n ->
            add(NanoTestCase("Başlık ve kategori", "TR yönerge", n, titlePromptTr(n)))
            add(NanoTestCase("Başlık ve kategori", "EN yönerge", n, titlePromptEn(n)))
        }
        questions.forEach { q ->
            add(NanoTestCase("Soru-cevap", "TR yönerge", q, questionPromptTr(q)))
            add(NanoTestCase("Soru-cevap", "EN yönerge", q, questionPromptEn(q)))
        }
        dumps.forEach { d ->
            add(NanoTestCase("Bölme", "TR yönerge", d, splitPromptTr(d)))
            add(NanoTestCase("Bölme", "EN yönerge", d, splitPromptEn(d)))
        }
    }
}
