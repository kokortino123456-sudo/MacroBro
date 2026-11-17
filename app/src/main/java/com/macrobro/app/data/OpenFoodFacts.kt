package com.macrobro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class BarcodeResult {
    data class Found(val food: LibFood) : BarcodeResult()
    data class NoNutrition(val name: String) : BarcodeResult()
    data object NotFound : BarcodeResult()
}

/**
 * Open Food Facts: a free, open database of packaged foods (good coverage of Czech and EU products).
 * Values are per 100 g and come from product labels entered by volunteers.
 */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val UA = "MacroBro/1.0 (Android; https://github.com/kokortino123456-sudo/MacroBro)"
    private const val FIELDS =
        "code,product_name,product_name_en,product_name_cs,generic_name,brands,nutriments,serving_quantity"

    suspend fun lookup(barcode: String): BarcodeResult = withContext(Dispatchers.IO) {
        val code = barcode.filter { it.isDigit() }
        if (code.isEmpty()) return@withContext BarcodeResult.NotFound
        val (status, body) = get("$BASE/api/v2/product/$code.json?fields=$FIELDS")
        if (status == 404) return@withContext BarcodeResult.NotFound
        if (status !in 200..299) throw IOException("Open Food Facts error (HTTP $status). Try again later.")
        val root = try { JSONObject(body) } catch (e: Exception) {
            throw IOException("Open Food Facts sent back something unreadable.")
        }
        if (root.optInt("status", 0) != 1) return@withContext BarcodeResult.NotFound
        val product = root.optJSONObject("product") ?: return@withContext BarcodeResult.NotFound
        val food = parse(product, code)
        when {
            food == null -> BarcodeResult.NotFound
            food.kcal <= 0 && food.protein + food.carbs + food.fat <= 0 -> BarcodeResult.NoNutrition(food.display)
            else -> BarcodeResult.Found(food)
        }
    }

    suspend fun search(query: String): List<LibFood> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val (status, body) = get(
            "$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=30&fields=$FIELDS"
        )
        if (status == 429) throw IOException("Too many online searches. Wait a minute and try again.")
        if (status !in 200..299) throw IOException("Open Food Facts error (HTTP $status). Try again later.")
        val root = try { JSONObject(body) } catch (e: Exception) {
            throw IOException("Open Food Facts sent back something unreadable.")
        }
        val arr = root.optJSONArray("products") ?: return@withContext emptyList()
        (0 until arr.length())
            .mapNotNull { i -> arr.optJSONObject(i)?.let { parse(it, it.optString("code", "")) } }
            .filter { it.kcal > 0 || it.protein + it.carbs + it.fat > 0 }
            .distinctBy { it.barcode.ifBlank { it.display + it.name } }
    }

    private fun parse(p: JSONObject, code: String): LibFood? {
        val name = listOf("product_name", "product_name_cs", "product_name_en", "generic_name")
            .map { p.optString(it, "").trim() }
            .firstOrNull { it.isNotEmpty() } ?: return null
        val brand = p.optString("brands", "").split(',').firstOrNull()?.trim().orEmpty()
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        fun num(key: String): Double {
            val v = n.optDouble(key, Double.NaN)
            return if (v.isNaN() || v < 0) 0.0 else v
        }
        var kcal = num("energy-kcal_100g")
        if (kcal <= 0) kcal = num("energy_100g") / 4.184 // kJ -> kcal
        val serving = p.optDouble("serving_quantity", Double.NaN).let { if (it.isNaN() || it <= 0) 0.0 else it }
        return LibFood(
            name = listOf(brand, "Open Food Facts").filter { it.isNotBlank() }.joinToString(" · "),
            display = name,
            category = "Packaged",
            kcal = kcal,
            protein = num("proteins_100g"),
            carbs = num("carbohydrates_100g"),
            fat = num("fat_100g"),
            fiber = num("fiber_100g"),
            sugar = num("sugars_100g"),
            portionLabel = if (serving > 0) "1 serving" else "",
            portionG = serving,
            barcode = code,
            source = "off",
        )
    }

    private fun get(url: String): Pair<Int, String> {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw IOException("No internet connection.")
        }
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } catch (e: IOException) {
            throw IOException("Couldn't reach Open Food Facts. Check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }
}
