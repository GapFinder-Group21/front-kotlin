package co.gapfinder.mobile.foundation

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------- Lectura segura de JSON (equivalente a json['x'] ?? default en Dart) ----------

fun JSONObject.textOr(key: String, fallback: String = ""): String =
    if (has(key) && !isNull(key)) optString(key, fallback) else fallback

fun JSONObject.textOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

fun JSONObject.intOr(key: String, fallback: Int = 0): Int =
    if (has(key) && !isNull(key)) optInt(key, fallback) else fallback

fun JSONObject.intOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

fun JSONObject.decimal(key: String): Double = optDouble(key, 0.0)

fun JSONObject.flagOr(key: String, fallback: Boolean = false): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key, fallback) else fallback

fun JSONObject.flagOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null

fun JSONObject.nodeOrNull(key: String): JSONObject? =
    if (has(key) && !isNull(key)) optJSONObject(key) else null

inline fun <T> JSONObject.nodeList(key: String, mapper: (JSONObject) -> T): List<T> {
    if (!has(key) || isNull(key)) return emptyList()
    val arr = optJSONArray(key) ?: return emptyList()
    return arr.mapNodes(mapper)
}

inline fun <T> JSONArray.mapNodes(mapper: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(mapper) }

/** Construye un JSONObject a partir de pares, convirtiendo null en JSONObject.NULL. */
fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
    pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
}

fun <T> List<T>.toJsonArray(encoder: (T) -> Any?): JSONArray =
    JSONArray().also { arr -> forEach { arr.put(encoder(it) ?: JSONObject.NULL) } }

/** Cuerpo vacío o "null" que manda el backend cuando no hay resultado. */
fun String.isBlankPayload(): Boolean = isBlank() || trim() == "null"

// ---------- Fechas y horas ----------

object Chrono {
    private val isoLocal: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val hms: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Igual que DateTime.parse de Dart: si trae zona, se lleva a la hora local. */
    fun readMoment(raw: String): LocalDateTime =
        try {
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } catch (ignored: Exception) {
            LocalDateTime.parse(raw.removeSuffix("Z"))
        }

    fun writeMoment(moment: LocalDateTime): String = moment.format(isoLocal)

    /** "HH:mm:ss" o "HH:mm" → LocalTime. */
    fun readClock(raw: String): LocalTime {
        val parts = raw.split(":")
        return LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }

    /** LocalTime → "HH:mm:00" para el backend. */
    fun writeClock(time: LocalTime): String = time.withSecond(0).format(hms)

    fun isoDay(date: LocalDate): String = date.toString() // yyyy-MM-dd

    fun now(): LocalDateTime = LocalDateTime.now()
}
