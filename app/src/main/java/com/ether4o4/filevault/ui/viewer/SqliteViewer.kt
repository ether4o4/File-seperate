package com.ether4o4.filevault.ui.viewer

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ROW_LIMIT = 200

private data class TableData(
    val columns: List<String>,
    val rows: List<List<String>>,
    val truncated: Boolean,
)

/** Read-only browser for SQLite database files: pick a table, view its rows. */
@Composable
fun SqliteViewer(file: File) {
    val db = remember(file) {
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull()
    }
    DisposableEffect(db) { onDispose { runCatching { db?.close() } } }

    if (db == null) {
        Centered("Could not open this database.")
        return
    }

    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<TableData?>(null) }

    LaunchedEffect(db) {
        tables = withContext(Dispatchers.IO) { queryTables(db) }
        selected = tables.firstOrNull()
    }
    LaunchedEffect(selected) {
        val t = selected ?: return@LaunchedEffect
        data = null
        data = withContext(Dispatchers.IO) { queryTable(db, t) }
    }

    Column(Modifier.fillMaxSize()) {
        if (tables.isEmpty()) {
            Centered("No tables in this database.")
            return@Column
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(8.dp),
        ) {
            items(tables, key = { it }) { name ->
                FilterChip(
                    selected = name == selected,
                    onClick = { selected = name },
                    label = { Text(name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        HorizontalDivider()

        val d = data
        if (d == null) {
            Centered("Loading…")
        } else {
            TableGrid(d)
        }
    }
}

@Composable
private fun TableGrid(d: TableData) {
    val hScroll = rememberScrollState()
    val cellWidth = 160.dp

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.horizontalScroll(hScroll)) {
                d.columns.forEach { col ->
                    Text(
                        col,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(cellWidth).padding(8.dp),
                    )
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(d.rows.size) { r ->
                Row(Modifier.horizontalScroll(hScroll)) {
                    d.rows[r].forEach { cell ->
                        Text(
                            cell,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(cellWidth).padding(8.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
            if (d.truncated) {
                item {
                    Text(
                        "Showing first $ROW_LIMIT rows",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

private fun queryTables(db: SQLiteDatabase): List<String> {
    val out = mutableListOf<String>()
    db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        null,
    ).use { c ->
        while (c.moveToNext()) out += c.getString(0)
    }
    return out
}

private fun queryTable(db: SQLiteDatabase, table: String): TableData {
    val safe = "`" + table.replace("`", "``") + "`"
    return db.rawQuery("SELECT * FROM $safe LIMIT ${ROW_LIMIT + 1}", null).use { c ->
        val columns = c.columnNames.toList()
        val rows = mutableListOf<List<String>>()
        var count = 0
        while (c.moveToNext() && count < ROW_LIMIT) {
            rows += (0 until c.columnCount).map { i -> cellToString(c, i) }
            count++
        }
        val truncated = c.moveToNext() // there was at least one more row
        TableData(columns, rows, truncated)
    }
}

private fun cellToString(c: Cursor, i: Int): String = when (c.getType(i)) {
    Cursor.FIELD_TYPE_NULL -> ""
    Cursor.FIELD_TYPE_BLOB -> "<blob>"
    else -> runCatching { c.getString(i) }.getOrDefault("<blob>") ?: ""
}
