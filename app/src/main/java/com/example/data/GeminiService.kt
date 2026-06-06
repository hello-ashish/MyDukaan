package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun extractItemsFromBill(bitmap: Bitmap): List<ExtractedItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is missing or not configured!")
            return@withContext emptyList()
        }

        // 1. Double the resolution boundary to retain pristine text detail from blurry/low-quality camera prints
        val maxDim = 2048
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val (newWidth, newHeight) = if (originalWidth > originalHeight) {
            if (originalWidth > maxDim) {
                Pair(maxDim, (originalHeight * (maxDim.toFloat() / originalWidth)).toInt())
            } else {
                Pair(originalWidth, originalHeight)
            }
        } else {
            if (originalHeight > maxDim) {
                Pair((originalWidth * (maxDim.toFloat() / originalHeight)).toInt(), maxDim)
            } else {
                Pair(originalWidth, originalHeight)
            }
        }
        
        // 1b. Create high-contrast enhanced bitmap to elevate faded grey printed texts over noisy backgrounds
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val enhanced = try {
            val dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(dest)
            val paint = android.graphics.Paint()
            // Highly optimized ColorMatrix to enhance print-text contrast (1.5f) and elevate background brightness (20f)
            val contrast = 1.5f
            val brightness = 20f
            val cm = android.graphics.ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Failed enhancing bitmap, using scaled fallback: ${e.message}")
            scaled
        }

        // 2. Compress to JPEG Base64
        val outputStream = ByteArrayOutputStream()
        enhanced.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        // 3. Build highly resilient OCR instructions
        val promptText = """
        You are an elite billing OCR system specializing in low-quality, blurry, or faint thermal receipt prints, tilted cameras, or hand-drawn invoices.
        Output a structured JSON array from the cropped invoice or bill image. 
        Ignore table grids, background scan noise, signatures, and stamps. Parse each item row by row. 
        Strictly ignore irrelevant totals, taxes, discounts, or summary terms.
        
        Understand column titles semantically and group them accordingly:
        - Product name of goods: can be titled 'Name', 'Product Name', 'Goods', 'Item Name', 'Particulars', 'Description', 'Item', 'Brand' etc.
        - Quantity: can be titled 'Qty', 'Quantity', 'Pcs', 'Strips', 'Vol', 'Units', 'Box' etc.
        - Expiry date: can be titled 'Exp', 'Exp Date', 'Expiry', 'Expiry Date', 'Expiry (MM-YYYY)', 'Use Before', 'Best Before', 'E.Date' etc.
        - Selling price (Retail MRP): can be titled 'MRP', 'Selling Price', 'Price', 'Sale Rate', 'S.Rate' etc.
        - Purchase price (Cost): can be titled 'Cost', 'Purchase Price', 'Rate' (if wholesale unit price/rate), 'Pur Price', 'Taxable Amt / unit' etc.
        - Batch Number: can be titled 'Batch', 'B.No', 'Batch No', 'Batch ID', 'Batch Number' etc.
        - HSN Code: can be titled 'HSN', 'HSN Code', 'Tariff Code', 'HSN/SAC' etc.
        
        OCR Error Correction and Resiliency Rules:
        - Spell-Correction: Receipts often have blurry letters. Use medical/pharma/retail knowledge to correct typos or guess missing letters (e.g., if you see "Arncxici1in", correct to "Amoxicillin". If you see "P@racetamcI", correct to "Paracetamol").
        - Price Sanitization: Clean up any non-numeric noise in pricing. Strip away currency symbols (₹, $, Rs.), commas, trailing dots. If character 'o' is erroneously detected instead of '0', or 'l'/'I' instead of '1', correct them seamlessly in numeric fields (e.g. '1o.0o' -> 10.00, 'l2.5' -> 12.5, 'S0' -> 50.0).
        - Format expiry dates strictly as 'MM-YYYY'. If you see '06/27', format as '06-2027'. If you see 'Jan-28' or '1/28', format as '01-2028'. Convert two-digit years to solar four-digit years. Default to "" if completely unreadable.
        - Ensure numeric fields (quantity, purchasePrice, sellingPrice) are parsed as valid double numbers.
        - If a row definitely exists but has a faded field (e.g. name and quantity are clear, but purchasePrice is mildly blurred), estimate a logical purchase value based on sellingPrice (e.g. purchasePrice = 75% of MRP) rather than omitting the whole row.
        - Output EVERY recognizable line Item. Do not skip low-quality rows; do your absolute best to reconstruct them so the user can edit/review them.
        
        Strictly output a clean, valid JSON array of objects. Use this exact key naming:
        [
          {
            "productName": "Item Name",
            "quantity": 10.0,
            "unit": "Units",
            "purchasePrice": 12.5,
            "sellingPrice": 22.0,
            "expiryDate": "10-2026",
            "batchNumber": "AMX-402",
            "hsnCode": "3004",
            "supplier": "Cipla Pharmaceuticals",
            "notes": "Any other descriptive detail"
          }
        ]
        """.trimIndent()

        // 4. Construct Request Payload using org.json.JSONObject directly
        try {
            val root = JSONObject()
            
            val contentParts = JSONArray()
            
            // Add prompt text part
            contentParts.put(JSONObject().put("text", promptText))
            
            // Add image inlineData part
            val inlineData = JSONObject()
            inlineData.put("mimeType", "image/jpeg")
            inlineData.put("data", base64Data)
            contentParts.put(JSONObject().put("inlineData", inlineData))
            
            val contentObj = JSONObject()
            contentObj.put("parts", contentParts)
            
            val contentsArray = JSONArray()
            contentsArray.put(contentObj)
            
            root.put("contents", contentsArray)

            // Setup Generation Config with JSON enforcement mime-type
            val generationConfig = JSONObject()
            generationConfig.put("temperature", 0.1)
            val responseFormat = JSONObject()
            responseFormat.put("responseMimeType", "application/json")
            generationConfig.put("responseFormat", responseFormat)
            root.put("generationConfig", generationConfig)

            // POST to the Gemini API endpoint
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(mediaType)
            
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Request failed code: ${response.code}")
                return@withContext emptyList()
            }

            val responseBodyString = response.body?.string() ?: return@withContext emptyList()
            Log.d(TAG, "Gemini Response: $responseBodyString")

            val responseJson = JSONObject(responseBodyString)
            val candidates = responseJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textPart = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (textPart.isNotBlank()) {
                val arrayStart = textPart.indexOf('[')
                val arrayEnd = textPart.lastIndexOf(']')
                val cleanedText = if (arrayStart in 0 until arrayEnd) {
                    textPart.substring(arrayStart, arrayEnd + 1)
                } else {
                    textPart.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                }

                val extractedList = mutableListOf<ExtractedItem>()
                val jsonArray = JSONArray(cleanedText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    extractedList.add(
                        ExtractedItem(
                            productName = obj.optString("productName", ""),
                            category = obj.optString("category", "General"),
                            quantity = obj.optDouble("quantity", 1.0),
                            unit = obj.optString("unit", "Units"),
                            purchasePrice = obj.optDouble("purchasePrice", 0.0),
                            sellingPrice = obj.optDouble("sellingPrice", 0.0),
                            minStock = obj.optDouble("minStock", 10.0),
                            barcode = obj.optString("barcode", ""),
                            batchNumber = obj.optString("batchNumber", ""),
                            hsnCode = obj.optString("hsnCode", ""),
                            manufacturingDate = obj.optString("manufacturingDate", ""),
                            expiryDate = obj.optString("expiryDate", ""),
                            supplier = obj.optString("supplier", ""),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
                return@withContext extractedList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini extraction service: ", e)
        }
        return@withContext emptyList()
    }
}

data class ExtractedItem(
    val productName: String = "",
    val category: String = "General",
    val quantity: Double = 1.0,
    val unit: String = "Units",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val minStock: Double = 5.0,
    val barcode: String = "",
    val batchNumber: String = "",
    val hsnCode: String = "",
    val manufacturingDate: String = "",
    val expiryDate: String = "",
    val supplier: String = "",
    val notes: String = ""
)
