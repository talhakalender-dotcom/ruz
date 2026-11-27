package com.talha.ruz.ui.plan

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

val TurkishDayShort = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

/** Verilen günün haftasının pazartesisi. */
fun weekStart(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** Pazartesiden başlayarak haftanın 7 günü. */
fun weekDays(date: LocalDate): List<LocalDate> {
    val monday = weekStart(date)
    return (0 until 7).map { monday.plus(it, DateTimeUnit.DAY) }
}

/** Günün içindeki konum, 0.0 (gece yarısı) ile 1.0 arası. */
fun dayFraction(hour: Int, minute: Int): Float = (hour * 60 + minute) / 1440f
