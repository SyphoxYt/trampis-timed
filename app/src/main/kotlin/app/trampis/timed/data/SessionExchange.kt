package app.trampis.timed.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SessionWithSolves(val session: SessionEntity, val solves: List<SolveEntity>)

/** CSV and csTimer formats used by CubeTime, with tolerant import for standard csTimer exports. */
object SessionExchange {
    private const val ODS_MIMETYPE = "application/vnd.oasis.opendocument.spreadsheet"
    private const val BACKUP_FORMAT = "trampis-timed-backup"

    /** Lossless app backup, including session settings, comments, dates, penalties and phases. */
    fun backup(sessions: List<SessionWithSolves>): String = com.google.gson.Gson().toJson(
        TrampisBackup(format = BACKUP_FORMAT, version = 1, sessions = sessions.map { item ->
            BackupSession(
                name = item.session.name, eventId = item.session.eventId, sessionType = item.session.sessionType,
                phaseCount = item.session.phaseCount, targetMillis = item.session.targetMillis,
                createdAt = item.session.createdAt, pinned = item.session.pinned,
                solves = item.solves.map { solve -> BackupSolve(solve.timeMillis, solve.penalty, solve.scramble, solve.createdAt, solve.comment, solve.phaseTimesJson) },
            )
        }),
    )

    fun parseBackup(text: String): List<BackupSession>? = runCatching {
        val root = JsonParser.parseString(text).asJsonObject
        if (root.get("format")?.asString != BACKUP_FORMAT || root.get("version")?.asInt != 1) return null
        val backup = com.google.gson.Gson().fromJson(root, TrampisBackup::class.java)
        require(backup.sessions.isNotEmpty()) { "The backup contains no sessions." }
        backup.sessions
    }.getOrNull()

    /** CubeTime-compatible OpenDocument workbook: one sheet per selected session. */
    fun ods(sessions: List<SessionWithSolves>): ByteArray {
        require(sessions.isNotEmpty()) { "Select at least one session to export." }
        val dateIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val dateDisplay = java.text.SimpleDateFormat("M/d/yyyy h:mm a", Locale.US)
        val usedNames = mutableSetOf<String>()
        fun sheetName(raw: String): String {
            val base = raw.replace(Regex("[\\[\\]:*?/\\\\]"), "_").trim().ifBlank { "Session" }.take(31)
            var name = base
            var n = 2
            while (!usedNames.add(name.lowercase(Locale.ROOT))) {
                val suffix = " ($n)"
                name = base.take(31 - suffix.length) + suffix
                n++
            }
            return name
        }
        fun stringCell(value: String): String = "<table:table-cell office:value-type=\"string\" calcext:value-type=\"string\"><text:p>${xml(value)}</text:p></table:table-cell>"
        val content = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" xmlns:calcext=\"urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0\" xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" office:version=\"1.2\"><office:automatic-styles>")
            append("<style:style style:name=\"COL_DATE\" style:family=\"table-column\"><style:table-column-properties style:column-width=\"100pt\"/></style:style>")
            append("<style:style style:name=\"COL_SCR\" style:family=\"table-column\"><style:table-column-properties style:column-width=\"200pt\"/></style:style>")
            append("<style:style style:name=\"CELL_DATE\" style:family=\"table-cell\" style:data-style-name=\"NUMFMT_DATE\"/></office:automatic-styles><office:body><office:spreadsheet><table:calculation-settings table:use-regular-expressions=\"false\"/>")
            sessions.forEach { item ->
                append("<table:table table:name=\"").append(xml(sheetName(item.session.name))).append("\">")
                append("<table:table-column table:number-columns-repeated=\"3\"/><table:table-column table:style-name=\"COL_SCR\"/><table:table-column table:style-name=\"COL_DATE\"/>")
                item.solves.sortedBy { it.createdAt }.forEach { solve ->
                    val seconds = solve.timeMillis / 1000.0
                    val penalty = when (solve.penalty) { 1 -> "PlusTwo"; 2 -> "DNF"; else -> "" }
                    val instant = Date(solve.createdAt)
                    append("<table:table-row><table:table-cell office:value-type=\"float\" office:value=\"").append(seconds).append("\" calcext:value-type=\"float\"><text:p>").append(seconds).append("</text:p></table:table-cell>")
                    append(stringCell(penalty)).append(stringCell(solve.comment)).append(stringCell(solve.scramble))
                    append("<table:table-cell table:style-name=\"CELL_DATE\" office:value-type=\"date\" office:date-value=\"").append(xml(dateIso.format(instant))).append("\" calcext:value-type=\"date\"><text:p>").append(xml(dateDisplay.format(instant))).append("</text:p></table:table-cell></table:table-row>")
                }
                append("</table:table>")
            }
            append("</office:spreadsheet></office:body></office:document-content>")
        }
        val styles = """<?xml version="1.0" encoding="UTF-8"?><office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:number="urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0" office:version="1.2"><office:styles><number:date-style style:name="NUMFMT_DATE"><number:month/><number:text>/</number:text><number:day/><number:text>/</number:text><number:year number:style="long"/><number:text> </number:text><number:hours/><number:text>:</number:text><number:minutes number:style="long"/><number:text> </number:text><number:am-pm/></number:date-style></office:styles></office:document-styles>"""
        val manifest = """<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2"><manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="$ODS_MIMETYPE"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/></manifest:manifest>"""
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val mimeBytes = ODS_MIMETYPE.toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(mimeBytes) }
            zip.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = mimeBytes.size.toLong(); compressedSize = mimeBytes.size.toLong(); this.crc = crc.value })
            zip.write(mimeBytes); zip.closeEntry()
            listOf("content.xml" to content, "styles.xml" to styles, "META-INF/manifest.xml" to manifest).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun xml(value: String): String = buildString(value.length) {
        value.forEach { char -> append(when (char) { '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '"' -> "&quot;"; '\'' -> "&apos;"; else -> char.toString() }) }
    }

    fun csv(session: SessionWithSolves): String = buildString {
        appendLine("Time,Comment,Scramble,Date,Penalty")
        session.solves.sortedBy { it.createdAt }.forEach { solve ->
            append(csvField(solve.timeMillis / 1000.0))
            append(','); append(csvField(solve.comment))
            append(','); append(csvField(solve.scramble))
            append(','); append(csvField(Instant.ofEpochMilli(solve.createdAt).toString()))
            append(','); append(csvField(when (solve.penalty) { 1 -> "+2"; 2 -> "DNF"; else -> "none" }))
            append('\n')
        }
    }

    fun csTimer(sessions: List<SessionWithSolves>): String {
        val root = JsonObject()
        val sessionData = JsonObject()
        sessions.forEachIndexed { index, item ->
            val key = (index + 1).toString()
            val event = item.session.eventId.toCsTimerEvent()
            val entries = JsonArray()
            item.solves.sortedBy { it.createdAt }.forEach { solve ->
                val penalty = when (solve.penalty) {
                    1 -> 2000
                    2 -> -1
                    else -> 0
                }
                val result = JsonArray().apply {
                    add(penalty)
                    add(solve.timeMillis.toInt())
                }
                val entry = JsonArray().apply {
                    add(result)
                    add(solve.scramble)
                    add(solve.comment)
                    add((solve.createdAt / 1000L).toInt())
                }
                entries.add(entry)
            }
            root.add("session$key", entries)
            sessionData.add(key, JsonObject().apply {
                addProperty("name", "Trampis Timed - ${item.session.name}")
                addProperty("scrType", event)
            })
        }
        root.add("properties", JsonObject().apply {
            addProperty("sessionData", sessionData.toString())
        })
        return root.toString()
    }

    fun parse(text: String, suggestedName: String): List<ImportedSession> {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        require(trimmed.isNotEmpty()) { "The selected file is empty." }
        return if (trimmed.startsWith("{")) parseCsTimer(trimmed) else listOf(parseCsv(trimmed, suggestedName))
    }

    private fun parseCsv(text: String, suggestedName: String): ImportedSession {
        val rows = parseCsvRows(text)
        require(rows.isNotEmpty()) { "The CSV file has no rows." }
        val header = rows.first().map { it.trim().lowercase() }
        val timeCol = header.indexOf("time")
        val commentCol = header.indexOf("comment")
        val scrambleCol = header.indexOf("scramble")
        val dateCol = header.indexOf("date")
        val penaltyCol = header.indexOf("penalty")
        require(timeCol >= 0 && scrambleCol >= 0) { "CSV needs Time and Scramble columns." }
        val solves = rows.drop(1).filter { it.any(String::isNotBlank) }.mapIndexedNotNull { index, row ->
            fun col(i: Int): String = row.getOrNull(i).orEmpty()
            val seconds = col(timeCol).trim().toDoubleOrNull() ?: return@mapIndexedNotNull null
            require(seconds >= 0.0) { "CSV contains a negative solve time on row ${index + 2}." }
            val penalty = when (col(penaltyCol).trim().lowercase()) {
                "+2", "plustwo", "2000" -> 1
                "dnf", "-1" -> 2
                else -> 0
            }
            ImportedSolve(
                timeMillis = (seconds * 1000.0).toLong(),
                penalty = penalty,
                scramble = col(scrambleCol),
                comment = col(commentCol),
                createdAt = parseDate(col(dateCol)) ?: System.currentTimeMillis() + index,
            )
        }
        return ImportedSession(suggestedName.substringBeforeLast('.', suggestedName).ifBlank { "Imported CSV" }, "333", solves)
    }

    private fun parseCsTimer(text: String): List<ImportedSession> {
        val root = JsonParser.parseString(text).asJsonObject
        val propertyText = root.getAsJsonObject("properties")?.get("sessionData")?.let {
            if (it.isJsonPrimitive) it.asString else it.toString()
        }
        val metadata = propertyText?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
        val sessions = root.entrySet().filter { it.key.matches(Regex("session\\d+")) }
            .sortedBy { it.key.removePrefix("session").toIntOrNull() ?: Int.MAX_VALUE }
        require(sessions.isNotEmpty()) { "No csTimer sessions were found in this JSON file." }
        return sessions.mapIndexed { index, (key, value) ->
            val number = key.removePrefix("session")
            val info = metadata?.getAsJsonObject(number)
            val name = info?.get("name")?.asString?.removePrefix("CubeTime Export - ")
                ?.removePrefix("Trampis Timed - ")?.ifBlank { null } ?: "Imported Session $number"
            val eventId = info?.get("scrType")?.asString?.toEventId() ?: "333"
            val entries = value.asJsonArray
            val solves = entries.mapNotNull { entryElement ->
                val entry = entryElement.asJsonArray
                if (entry.size() < 4 || !entry[0].isJsonArray) return@mapNotNull null
                val result = entry[0].asJsonArray
                if (result.size() < 2) return@mapNotNull null
                val penaltyValue = result[0].asInt
                val rawTime = result[1].asLong
                if (rawTime < 0) return@mapNotNull null
                ImportedSolve(
                    timeMillis = rawTime,
                    penalty = when (penaltyValue) { -1 -> 2; 2000 -> 1; else -> 0 },
                    scramble = entry[1].takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    comment = entry[2].takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    createdAt = entry[3].asLong * 1000L,
                )
            }
            ImportedSession(name, eventId, solves)
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                quoted && ch == '"' && text.getOrNull(index + 1) == '"' -> { cell.append('"'); index++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row.add(cell.toString()); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    row.add(cell.toString()); cell.setLength(0)
                    rows.add(row); row = mutableListOf()
                    if (ch == '\r' && text.getOrNull(index + 1) == '\n') index++
                }
                else -> cell.append(ch)
            }
            index++
        }
        require(!quoted) { "CSV contains an unterminated quoted field." }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private fun csvField(value: Any): String {
        val text = value.toString()
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${text.replace("\"", "\"\"")}\"" else text
    }

    private fun parseDate(text: String): Long? = try {
        Instant.parse(text.trim()).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try { OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { null }
    }

    // These are the CubeTime csTimer event IDs from its original PUZZLE_TYPES table.
    private fun String.toCsTimerEvent(): String = when (this) {
        "222" -> "222so"; "333" -> "333"; "444" -> "444wca"; "555" -> "555wca"
        "666" -> "666wca"; "777" -> "777wca"; "sq1" -> "sqrs"; "minx" -> "mgmp"
        "pyram" -> "pyrso"; "clock" -> "clkwca"; "skewb" -> "skbso"; "333oh" -> "333oh"
        "333bld" -> "333bld"; "444bld" -> "444bld"; "555bld" -> "555bld"; else -> "333"
    }

    private fun String.toEventId(): String = when (this) {
        "333" -> "333"; "222", "222so" -> "222"; "444", "444wca" -> "444"; "555", "555wca" -> "555"
        "666", "666wca" -> "666"; "777", "777wca" -> "777"; "clock", "clkwca" -> "clock"
        "minx", "mgmp" -> "minx"; "pyram", "pyrso" -> "pyram"; "skewb", "skbso" -> "skewb"
        "sq1", "sqrs" -> "sq1"; "333oh" -> "333oh"; "333bf", "333ni", "333bld" -> "333bld"
        "444bf", "444ni", "444bld" -> "444bld"; "555bf", "555ni", "555bld" -> "555bld"
        else -> "333"
    }
}

data class TrampisBackup(val format: String, val version: Int, val sessions: List<BackupSession>)
data class BackupSession(val name: String, val eventId: String, val sessionType: String, val phaseCount: Int, val targetMillis: Long?, val createdAt: Long, val pinned: Boolean, val solves: List<BackupSolve>)
data class BackupSolve(val timeMillis: Long, val penalty: Int, val scramble: String, val createdAt: Long, val comment: String, val phaseTimesJson: String)
data class ImportedSession(val name: String, val eventId: String, val solves: List<ImportedSolve>)
data class ImportedSolve(val timeMillis: Long, val penalty: Int, val scramble: String, val comment: String, val createdAt: Long)
