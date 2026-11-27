package com.talha.ruz.parse

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrDateParserTest {
    /** 25 Kasım 2026 Çarşamba, 10:00. Bu hafta: Pzt 23 – Paz 29 Kasım. */
    private val now = LocalDateTime(2026, 11, 25, 10, 0)
    private val cats = listOf("Genel", "İş", "Alışveriş", "Fikirler", "Ödemeler")

    private fun p(text: String, ignore: Set<ChipKind> = emptySet()) = TrDateParser.parse(text, now, cats, ignore)
    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate(year, month, day)
    private fun t(h: Int, m: Int = 0) = LocalTime(h, m)

    @Test fun tomorrowWithTimeAndCategory() {
        val r = p("yarın 14:00 vergi dairesi #iş")
        assertEquals(d(11, 26), r.date)
        assertEquals(t(14), r.time)
        assertEquals("İş", r.categoryName)
        assertFalse(r.categoryIsNew)
        assertEquals("vergi dairesi", r.title)
    }

    @Test fun categoryOnly() {
        val r = p("süt al #alışveriş")
        assertEquals("Alışveriş", r.categoryName)
        assertNull(r.date)
        assertEquals("süt al", r.title)
    }

    @Test fun categoryPrefixAndCase() {
        assertEquals("Alışveriş", p("#alış ekmek").categoryName)
        assertEquals("Alışveriş", p("#alis ekmek").categoryName)
        assertEquals("İş", p("#İŞ rapor").categoryName)
        assertEquals("Ödemeler", p("#odeme kira").categoryName)
    }

    @Test fun newCategory() {
        val r = p("#kitaplar Suç ve Ceza")
        assertEquals("Kitaplar", r.categoryName)
        assertTrue(r.categoryIsNew)
        assertEquals("Suç ve Ceza", r.title)
        assertEquals("Okunacak kitaplar", p("#okunacak_kitaplar x").categoryName)
        assertEquals("İzlenecekler", p("#izlenecekler x").categoryName)
    }

    @Test fun weekdayAllDay() {
        val r = p("cuma hakedişi teslim et")
        assertEquals(d(11, 27), r.date)
        assertNull(r.time)
        assertTrue(r.allDay)
        assertEquals("hakedişi teslim et", r.title)
        assertEquals(d(11, 27), p("cuma günü hakediş").date)
        assertEquals("rapor", p("cumaya kadar rapor").title)
        assertEquals(d(11, 27), p("cumaya kadar rapor").date)
    }

    @Test fun weekdayVariants() {
        assertEquals(d(11, 25), p("bu çarşamba toplantı").date)
        assertEquals(d(11, 25), p("çarşamba toplantı").date)
        assertEquals(d(12, 1), p("salı toplantı").date)
        assertEquals(d(12, 4), p("haftaya cuma toplantı").date)
        assertEquals(d(11, 28), p("cumartesi araba").date)
        assertEquals(d(11, 29), p("pazar günü kahvaltı").date)
        assertEquals(d(11, 30), p("pazartesi rapor").date)
    }

    @Test fun pazarAsMarketIsNotADate() {
        assertNull(p("pazardan domates al").date)
        assertNull(p("pazarlık yap").date)
        assertNull(p("salıncak tamir").date)
    }

    @Test fun mondayMorning() {
        val r = p("pazartesi sabah hakediş")
        assertEquals(d(11, 30), r.date)
        assertEquals(t(9), r.time)
        assertEquals("hakediş", r.title)
    }

    @Test fun monthNames() {
        assertEquals(d(12, 15), p("15 aralık elektrik").date)
        assertEquals(d(12, 15), p("15 Aralık'ta elektrik").date)
        assertEquals(d(1, 3, 2027), p("3 ocak 2027 tapu").date)
        assertEquals(d(10, 10, 2027), p("10 ekim bakım").date) // geçti → gelecek yıl
        assertEquals("tapu", p("3 ocak 2027 tapu").title)
    }

    @Test fun numericDates() {
        assertEquals(d(12, 15), p("15.12 fatura").date)
        assertEquals(d(12, 15), p("15/12/2026 fatura").date)
        assertEquals(d(1, 2, 2027), p("2.1.27 fatura").date)
    }

    @Test fun clockTimes() {
        val r = p("toplantı 14.30")
        assertEquals(d(11, 25), r.date)
        assertEquals(t(14, 30), r.time)
        assertEquals("toplantı", r.title)
        // 9:00 geçti → yarın
        assertEquals(d(11, 26), p("9:00 toplantı").date)
    }

    @Test fun pricesAreNotTimes() {
        val r = p("süt 12.50 tl")
        assertNull(r.time)
        assertNull(r.date)
        assertEquals("süt 12.50 tl", r.title)
    }

    @Test fun deadlineDayOfMonth() {
        val r = p("elektrik faturası son gün 15'i")
        assertEquals(d(12, 15), r.date)
        assertTrue(r.isMilestone)
        assertEquals("elektrik faturası", r.title)
        assertEquals(d(11, 28), p("ayın 28'i kira").date)
        assertEquals(d(12, 15), p("15'ine kadar öde").date)
    }

    @Test fun bareHours() {
        assertEquals(t(15), p("saat 3 dişçi").time)
        assertEquals(t(9), p("9'da toplantı").time)
        assertEquals(d(11, 26), p("9'da toplantı").date)
        assertEquals(t(14), p("saat 14'te toplantı").time)
    }

    @Test fun dayParts() {
        val r = p("akşam markete uğra")
        assertEquals(d(11, 25), r.date)
        assertEquals(t(19), r.time)
        assertEquals("markete uğra", r.title)
        assertEquals(t(19), p("akşam 7 sinema").time)
        assertEquals(t(23), p("gece 11 ilaç").time)
        assertEquals(t(15), p("öğleden sonra 3 banka").time)
        assertEquals(t(20, 30), p("yarın akşam 8:30 yemek").time)
        assertEquals(t(9), p("yarın sabah vergi dairesi").time)
    }

    @Test fun ranges() {
        val r = p("yarın 14:00-16:00 toplantı")
        assertEquals(d(11, 26), r.date)
        assertEquals(t(14), r.time)
        assertEquals(120, r.durationMin)
        assertEquals("toplantı", r.title)
        val b = p("saat 14-16 arası beton dökümü")
        assertEquals(t(14), b.time)
        assertEquals(120, b.durationMin)
        assertEquals("beton dökümü", b.title)
    }

    @Test fun durations() {
        val r = p("2 saat ders çalış")
        assertEquals(120, r.durationMin)
        assertNull(r.date)
        assertEquals("ders çalış", r.title)
        assertEquals(90, p("1.5 saat yürüyüş").durationMin)
        assertNull(p("1.5 saat yürüyüş").date)
        assertEquals(45, p("45 dk koşu").durationMin)
        assertEquals(30, p("yarım saat kitap").durationMin)
    }

    @Test fun relative() {
        val r = p("yarım saat sonra çayı kapat")
        assertEquals(d(11, 25), r.date)
        assertEquals(t(10, 30), r.time)
        assertEquals("çayı kapat", r.title)
        assertEquals(d(11, 28), p("3 gün sonra ara").date)
        assertEquals(d(12, 2), p("bir hafta sonra kontrol").date)
        assertEquals(d(11, 27), p("öbür gün ara").date)
    }

    @Test fun weekAndMonthEnds() {
        assertEquals(d(11, 28), p("hafta sonu araba yıkat").date)
        assertEquals(d(11, 30), p("ay sonu kira").date)
    }

    @Test fun question() {
        val r = p("Vergiyi ne zaman ödeyeceğim?")
        assertTrue(r.isQuestion)
        assertNull(r.date)
    }

    @Test fun ignoredChipsStayInTitle() {
        val r = p("yarın 14:00 toplantı #iş", ignore = setOf(ChipKind.Date, ChipKind.Category))
        assertEquals(t(14), r.time)
        assertEquals(d(11, 25), r.date) // gün kaldırıldı: 14:00 bugün
        assertNull(r.categoryName)
        assertEquals("yarın toplantı #iş", r.title)
    }

    @Test fun plainTextUntouched() {
        val r = p("balkon için sürgülü cam sistemi yapsak güzel olur, fiyat araştır")
        assertTrue(r.found.isEmpty())
        assertEquals("balkon için sürgülü cam sistemi yapsak güzel olur, fiyat araştır", r.title)
        assertEquals("Suç ve Ceza'yı okumaya başla", p("Suç ve Ceza'yı okumaya başla").title)
    }

    @Test fun turkishLowercase() {
        assertEquals("istanbul", trLower("İSTANBUL"))
        assertEquals("ışık", trLower("IŞIK"))
    }
}
