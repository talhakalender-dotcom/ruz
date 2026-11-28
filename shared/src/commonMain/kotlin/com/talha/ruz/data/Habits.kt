package com.talha.ruz.data

import kotlinx.datetime.LocalDate

/** Alışkanlık. [weekdays]: yapılacağı günler (ISO, 1 = pazartesi); boşsa her gün. */
data class RuzHabit(val id: String, val name: String, val weekdays: Set<Int>, val order: Double)

/** Bir alışkanlığın işaretlendiği gün. */
data class HabitCheck(val habitId: String, val day: LocalDate)

internal fun encodeWeekdays(days: Set<Int>): String = days.sorted().joinToString(",")

internal fun decodeWeekdays(s: String): Set<Int> = s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
