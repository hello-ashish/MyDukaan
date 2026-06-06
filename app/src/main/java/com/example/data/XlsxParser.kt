package com.example.data

import android.util.Log
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

object XlsxParser {
    private const val TAG = "XlsxParser"

    fun parseXlsx(inputStream: InputStream): String {
        val sharedStrings = mutableListOf<String>()
        var sheetBytes: ByteArray? = null
        var sharedStringsBytes: ByteArray? = null

        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name == "xl/sharedstrings.xml") {
                        sharedStringsBytes = zip.readBytes()
                    } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        if (sheetBytes == null) {
                            sheetBytes = zip.readBytes()
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading XLSX ZIP structure: ${e.message}")
            return ""
        }

        if (sheetBytes == null) {
            Log.e(TAG, "No sheet found in XLSX")
            return ""
        }

        // Parse shared strings
        sharedStringsBytes?.let { bytes ->
            try {
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val doc: Document = builder.parse(bytes.inputStream())
                val tList: NodeList = doc.getElementsByTagName("t")
                for (i in 0 until tList.length) {
                    sharedStrings.add(tList.item(i).textContent ?: "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing shared strings: ${e.message}")
            }
        }

        // Parse sheet1.xml
        val csvBuilder = StringBuilder()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc: Document = builder.parse(sheetBytes!!.inputStream())
            val rows: NodeList = doc.getElementsByTagName("row")
            for (i in 0 until rows.length) {
                val rowEl = rows.item(i) as Element
                val cells: NodeList = rowEl.getElementsByTagName("c")
                
                val rowCells = mutableMapOf<Int, String>()
                var maxColIdx = -1

                for (j in 0 until cells.length) {
                    val cellEl = cells.item(j) as Element
                    val cellRef = cellEl.getAttribute("r") ?: ""
                    val cellType = cellEl.getAttribute("t") ?: ""
                    val colName = cellRef.filter { it.isLetter() }
                    val colIdx = colNameToIndex(colName)

                    if (colIdx >= 0) {
                        maxColIdx = maxColIdx.coerceAtLeast(colIdx)
                        var value = ""
                        val vList = cellEl.getElementsByTagName("v")
                        if (vList.length > 0) {
                            val vVal = vList.item(0).textContent ?: ""
                            if (cellType == "s") {
                                val strIdx = vVal.toIntOrNull()
                                if (strIdx != null && strIdx >= 0 && strIdx < sharedStrings.size) {
                                    value = sharedStrings[strIdx]
                                }
                            } else {
                                value = vVal
                            }
                        } else {
                            val tList = cellEl.getElementsByTagName("t")
                            if (tList.length > 0) {
                                value = tList.item(0).textContent ?: ""
                            }
                        }
                        rowCells[colIdx] = value
                    }
                }

                // Append cells to csv only if the row has any content
                if (rowCells.isNotEmpty()) {
                    val rowStrings = ArrayList<String>()
                    for (c in 0..maxColIdx) {
                        val rawVal = rowCells[c] ?: ""
                        val escaped = if (rawVal.contains(",") || rawVal.contains("\"") || rawVal.contains("\n")) {
                            "\"" + rawVal.replace("\"", "\"\"") + "\""
                        } else {
                            rawVal
                        }
                        rowStrings.add(escaped)
                    }
                    csvBuilder.append(rowStrings.joinToString(",")).append("\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sheet cells: ${e.message}")
        }

        return csvBuilder.toString()
    }

    private fun colNameToIndex(name: String): Int {
        var idx = 0
        for (i in 0 until name.length) {
            val char = name[i].uppercaseChar()
            if (char in 'A'..'Z') {
                idx *= 26
                idx += (char - 'A' + 1)
            }
        }
        return idx - 1
    }
}
