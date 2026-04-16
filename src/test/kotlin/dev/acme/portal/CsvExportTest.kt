package dev.acme.portal

import dev.quatrion.portal.model.*
import dev.quatrion.portal.service.GenericCrudService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for CSV export functionality.
 *
 * Tests the CSV generation logic (field escaping, header/row output)
 * without requiring a running Quarkus server.
 */
class CsvExportTest {

    // ── CSV escaping tests ───────────────────────────────────────────────────

    @Test
    fun `escapeCsvField returns plain value for simple string`() {
        assertEquals("hello", escapeCsvField("hello"))
    }

    @Test
    fun `escapeCsvField quotes value containing comma`() {
        assertEquals("\"hello, world\"", escapeCsvField("hello, world"))
    }

    @Test
    fun `escapeCsvField quotes value containing double-quote and escapes it`() {
        assertEquals("\"She said \"\"hello\"\"\"", escapeCsvField("She said \"hello\""))
    }

    @Test
    fun `escapeCsvField quotes value containing newline`() {
        assertEquals("\"line1\nline2\"", escapeCsvField("line1\nline2"))
    }

    @Test
    fun `escapeCsvField quotes value containing carriage return`() {
        assertEquals("\"line1\rline2\"", escapeCsvField("line1\rline2"))
    }

    @Test
    fun `escapeCsvField handles empty string`() {
        assertEquals("", escapeCsvField(""))
    }

    @Test
    fun `escapeCsvField handles value with all special chars`() {
        val input = "a,b\"c\nd"
        val expected = "\"a,b\"\"c\nd\""
        assertEquals(expected, escapeCsvField(input))
    }

    // ── CSV generation tests ─────────────────────────────────────────────────

    @Test
    fun `buildCsv generates header row from field labels`() {
        val columns = listOf(
            makeField("name", "Name"),
            makeField("email", "Email")
        )
        val data = emptyList<EntityData>()
        val csv = buildCsv(columns, data)
        val lines = csv.trim().split("\n")
        assertEquals("Name,Email", lines[0])
    }

    @Test
    fun `buildCsv generates data rows`() {
        val columns = listOf(
            makeField("name", "Name"),
            makeField("count", "Count")
        )
        val data = listOf(
            EntityData(mapOf("name" to "Alice", "count" to 42)),
            EntityData(mapOf("name" to "Bob", "count" to 7))
        )
        val csv = buildCsv(columns, data)
        val lines = csv.trim().split("\n")
        assertEquals(3, lines.size) // header + 2 rows
        assertEquals("Name,Count", lines[0])
        assertEquals("Alice,42", lines[1])
        assertEquals("Bob,7", lines[2])
    }

    @Test
    fun `buildCsv handles null values as empty string`() {
        val columns = listOf(
            makeField("name", "Name"),
            makeField("nullable", "Nullable")
        )
        val data = listOf(
            EntityData(mapOf("name" to "Alice", "nullable" to null))
        )
        val csv = buildCsv(columns, data)
        val lines = csv.trim().split("\n")
        assertEquals("Alice,", lines[1])
    }

    @Test
    fun `buildCsv handles missing fields as empty string`() {
        val columns = listOf(
            makeField("name", "Name"),
            makeField("missing", "Missing")
        )
        val data = listOf(
            EntityData(mapOf("name" to "Alice"))
        )
        val csv = buildCsv(columns, data)
        val lines = csv.trim().split("\n")
        assertEquals("Alice,", lines[1])
    }

    @Test
    fun `buildCsv escapes values with commas`() {
        val columns = listOf(makeField("name", "Name"))
        val data = listOf(
            EntityData(mapOf("name" to "Doe, John"))
        )
        val csv = buildCsv(columns, data)
        val lines = csv.trim().split("\n")
        assertEquals("\"Doe, John\"", lines[1])
    }

    @Test
    fun `buildCsv empty data produces header only`() {
        val columns = listOf(
            makeField("name", "Name"),
            makeField("email", "Email")
        )
        val csv = buildCsv(columns, emptyList())
        val lines = csv.trim().split("\n")
        assertEquals(1, lines.size)
        assertEquals("Name,Email", lines[0])
    }

    @Test
    fun `buildCsv header labels with comma are escaped`() {
        val columns = listOf(makeField("name", "Full Name, First Last"))
        val csv = buildCsv(columns, emptyList())
        val lines = csv.trim().split("\n")
        assertEquals("\"Full Name, First Last\"", lines[0])
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeField(name: String, label: String) = FieldMetadata(
        name = name, label = label, tab = "", renderer = "TEXT", order = 0,
        readonly = false, hidden = false, showInTable = true, showInFilter = true,
        required = false, placeholder = "", tooltip = "", width = 0, group = "",
        filterType = "AUTO", selectOptions = emptyList(),
        maxLength = 0, regex = "", regexMessage = "",
        relationMeta = null
    )

    /** Mirrors the CSV escape logic from GenericCrudResource. */
    private fun escapeCsvField(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /** Mirrors the CSV build logic from GenericCrudResource. */
    private fun buildCsv(columns: List<FieldMetadata>, data: List<EntityData>): String {
        return buildString {
            appendLine(columns.joinToString(",") { escapeCsvField(it.label) })
            for (row in data) {
                appendLine(columns.joinToString(",") { field ->
                    escapeCsvField(row[field.name]?.toString() ?: "")
                })
            }
        }
    }

    // ── CSV parsing tests (import) ───────────────────────────────────────────

    @Test
    fun `parseCsvLines parses simple CSV`() {
        val csv = "name,count\nAlice,5\nBob,3"
        val lines = parseCsvLines(csv)
        assertEquals(3, lines.size)
        assertEquals(listOf("name", "count"), lines[0])
        assertEquals(listOf("Alice", "5"), lines[1])
        assertEquals(listOf("Bob", "3"), lines[2])
    }

    @Test
    fun `parseCsvLines handles quoted fields`() {
        val csv = "name,notes\nAlice,\"hello, world\"\nBob,simple"
        val lines = parseCsvLines(csv)
        assertEquals(3, lines.size)
        assertEquals("hello, world", lines[1][1])
        assertEquals("simple", lines[2][1])
    }

    @Test
    fun `parseCsvLines handles escaped double quotes`() {
        val csv = "name\n\"She said \"\"hello\"\"\""
        val lines = parseCsvLines(csv)
        assertEquals(2, lines.size)
        assertEquals("She said \"hello\"", lines[1][0])
    }

    @Test
    fun `parseCsvLines handles newlines inside quoted fields`() {
        val csv = "name,notes\nAlice,\"line1\nline2\"\nBob,ok"
        val lines = parseCsvLines(csv)
        assertEquals(3, lines.size)
        assertEquals("line1\nline2", lines[1][1])
    }

    @Test
    fun `parseCsvLines handles CRLF line endings`() {
        val csv = "name,count\r\nAlice,5\r\nBob,3"
        val lines = parseCsvLines(csv)
        assertEquals(3, lines.size)
        assertEquals(listOf("Alice", "5"), lines[1])
    }

    @Test
    fun `parseCsvLines handles empty fields`() {
        val csv = "a,b,c\n1,,3"
        val lines = parseCsvLines(csv)
        assertEquals(2, lines.size)
        assertEquals(listOf("1", "", "3"), lines[1])
    }

    @Test
    fun `parseCsvLines handles trailing newline`() {
        val csv = "name\nAlice\n"
        val lines = parseCsvLines(csv)
        assertEquals(2, lines.size)
        assertEquals(listOf("Alice"), lines[1])
    }

    /**
     * Mirrors the CSV parsing logic from GenericCrudResource.parseCsvLines.
     */
    private fun parseCsvLines(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < csv.length) {
            val c = csv[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < csv.length && csv[i + 1] == '"') {
                            currentField.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        currentField.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                c == '\n' || (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    rows.add(currentRow.toList())
                    currentRow.clear()
                    if (c == '\r') i++
                }
                c == '\r' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }
                else -> currentField.append(c)
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            rows.add(currentRow.toList())
        }

        return rows
    }
}
