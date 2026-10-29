package co.gapfinder.mobile.data.local

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Fechas y horas se guardan como texto ISO ("2026-10-31T05:00", "14:30").
 * El formato ISO ordena igual como texto que como fecha, así que ORDER BY y MAX funcionan.
 */
class RoomConverters {
    @TypeConverter
    fun clockToText(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun textToClock(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun momentToText(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun textToMoment(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}
