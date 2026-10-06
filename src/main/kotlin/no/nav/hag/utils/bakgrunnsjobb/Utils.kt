package no.nav.hag.utils.bakgrunnsjobb

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

internal fun LocalDateTime.toTimestamp(): Timestamp = Timestamp.valueOf(this)

internal fun String.readString(rs: ResultSet): String = rs.getString(this)

internal fun String.readInt(rs: ResultSet): Int = rs.getInt(this)

internal fun String.readTime(rs: ResultSet): LocalDateTime = rs.getTimestamp(this).toLocalDateTime()

internal fun String.readTimeNullable(rs: ResultSet): LocalDateTime? = rs.getTimestamp(this)?.toLocalDateTime()

internal fun String.trimExcessWhitespace() = replace(Regex("\\s+"), " ").trim()
