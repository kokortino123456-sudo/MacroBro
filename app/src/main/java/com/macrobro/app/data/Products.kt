package com.macrobro.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Offline database of Czech and Slovak supermarket products (Open Food Facts, ODbL),
 * built weekly by the GitHub build and bundled as assets/products.db.
 * All calls do disk I/O: call them from a background thread.
 */
object Products {
    const val CATEGORY = "Packaged (CZ/SK)"
    private const val ASSET = "products.db"

    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var unavailable = false

    @Synchronized
    private fun open(context: Context): SQLiteDatabase? {
        db?.let { return it }
        if (unavailable) return null
        return try {
            val app = context.applicationContext
            if (app.assets.list("")?.contains(ASSET) != true) {
                unavailable = true
                return null
            }
            // SQLite can't read from inside the APK, so copy once per app version.
            @Suppress("DEPRECATION")
            val stamp = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
            val file = File(app.filesDir, ASSET)
            val prefs = app.getSharedPreferences("macrobro_products", Context.MODE_PRIVATE)
            if (!file.exists() || prefs.getLong("stamp", -1) != stamp) {
                val tmp = File(app.filesDir, "$ASSET.tmp")
                app.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) throw IllegalStateException("copy failed")
                prefs.edit().putLong("stamp", stamp).apply()
            }
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
        } catch (e: Exception) {
            unavailable = true
            null
        }
    }

    /** Number of products, or 0 when the database isn't in this build. */
    fun count(context: Context): Int {
        val d = open(context) ?: return 0
        return try {
            d.rawQuery("SELECT COUNT(*) FROM products", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (e: Exception) { 0 }
    }

    /** Date the database was built, like 2026-11-16. */
    fun builtOn(context: Context): String {
        val d = open(context) ?: return ""
        return try {
            d.rawQuery("SELECT value FROM meta WHERE key = 'built'", null).use { if (it.moveToFirst()) it.getString(0) else "" }
        } catch (e: Exception) { "" }
    }

    fun byBarcode(context: Context, barcode: String): LibFood? {
        val code = barcode.filter { it.isDigit() }
        if (code.isEmpty()) return null
        val d = open(context) ?: return null
        // EAN-13 vs UPC-A: try the code as scanned and with/without a leading zero.
        val variants = listOf(code, "0$code", code.removePrefix("0")).distinct()
        return try {
            variants.firstNotNullOfOrNull { c ->
                d.rawQuery("SELECT $COLUMNS FROM products WHERE code = ?", arrayOf(c)).use { cur ->
                    if (cur.moveToFirst()) toFood(cur) else null
                }
            }
        } catch (e: Exception) { null }
    }

    /** Word-start search over product name, brand and category, without diacritics. */
    fun search(context: Context, query: String, limit: Int = 40): List<LibFood> {
        val tokens = FoodLibrary.normalize(query).split(' ').filter { it.length >= 2 }.take(5)
        if (tokens.isEmpty()) return emptyList()
        val d = open(context) ?: return emptyList()
        val where = tokens.joinToString(" AND ") { "search LIKE ?" }
        val args = tokens.map { "% $it%" } + " ${tokens.first()}%"
        val sql = "SELECT $COLUMNS FROM products WHERE $where " +
            "ORDER BY (search LIKE ?) DESC, length(name) LIMIT $limit"
        return try {
            d.rawQuery(sql, args.toTypedArray()).use { cur ->
                val out = ArrayList<LibFood>()
                while (cur.moveToNext()) out.add(toFood(cur))
                out
            }
        } catch (e: Exception) { emptyList() }
    }

    private const val COLUMNS = "code, name, brand, quantity, pack_g, serving_g, kcal, protein, carbs, fat, sugar, fiber"

    private fun Cursor.dbl(i: Int): Double = if (isNull(i)) 0.0 else getDouble(i)

    private fun toFood(c: Cursor): LibFood {
        val code = c.getString(0)
        val brand = c.getString(2).orEmpty()
        val quantity = c.getString(3).orEmpty()
        val pack = c.dbl(4)
        val serving = c.dbl(5)
        // A small pack (a bag of nuts, a bar) is one portion; for big packs use the serving size.
        val (label, grams) = when {
            pack in 1.0..250.0 -> "1 pack" to pack
            serving > 0 -> "1 serving" to serving
            pack > 0 -> "1 pack" to pack
            else -> "" to 0.0
        }
        return LibFood(
            name = brand,
            display = c.getString(1),
            category = CATEGORY,
            kcal = c.dbl(6), protein = c.dbl(7), carbs = c.dbl(8), fat = c.dbl(9),
            sugar = c.dbl(10), fiber = c.dbl(11),
            portionLabel = label, portionG = grams,
            id = -(code.takeLast(15).toLongOrNull() ?: 0L) - 1_000_000L,
            barcode = code,
            source = "off-cz",
            note = listOf(brand, quantity, "Open Food Facts").filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
}
