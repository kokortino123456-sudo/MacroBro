"""Builds a SQLite database of Czech and Slovak supermarket products from Open Food Facts.

Runs in GitHub Actions (weekly, cached). Downloads the public CSV export, keeps products sold in
Czechia or Slovakia that have usable nutrition values, and writes them to a small SQLite file the
app bundles for offline barcode lookups and name search.

Data: Open Food Facts, https://world.openfoodfacts.org, Open Database License (ODbL).

Usage:
  python3 tools/build_off_products.py OUT.db                 # download the export
  python3 tools/build_off_products.py OUT.db --csv FILE.gz   # use a local export (testing)
"""
import gzip
import os
import re
import sqlite3
import sys
import time
import unicodedata
import urllib.request

EXPORT_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz"
USER_AGENT = "MacroBro/1.0 (https://github.com/kokortino123456-sudo/MacroBro)"
COUNTRIES = {b"en:czech-republic", b"en:slovakia"}
NEEDED = ["code", "product_name", "countries_tags", "proteins_100g", "carbohydrates_100g", "fat_100g"]


def norm(s):
    """Same idea as FoodLibrary.normalize in the app: lower case, no diacritics, words only."""
    s = s.lower()
    for a, b in (("ł", "l"), ("ß", "ss"), ("æ", "ae"), ("ø", "o"), ("œ", "oe"), ("đ", "d")):
        s = s.replace(a, b)
    s = "".join(ch for ch in unicodedata.normalize("NFD", s) if unicodedata.category(ch) != "Mn")
    s = re.sub(r"[^\w%]+", " ", s).replace("_", " ")
    return re.sub(r"\s+", " ", s).strip()


def num(v):
    try:
        x = float(v.replace(",", "."))
        return x if x == x and x >= 0 else None  # drop NaN and negatives
    except (ValueError, AttributeError):
        return None


QTY = re.compile(r"^\s*(?:(\d+)\s*[x×]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|g|l|ml|cl|dl)\b", re.I)
UNIT = {"g": 1, "kg": 1000, "ml": 1, "cl": 10, "dl": 100, "l": 1000}


def parse_quantity(q):
    """'150 g' -> (150, None); '6 x 30 g' -> (180, 30); '0,5 l' -> (500, None)."""
    m = QTY.match(q or "")
    if not m:
        return None, None
    count = int(m.group(1)) if m.group(1) else 1
    each = float(m.group(2).replace(",", ".")) * UNIT[m.group(3).lower()]
    if each <= 0 or each > 20000:
        return None, None
    return count * each, (each if count > 1 else None)


def open_export(csv_path):
    if csv_path:
        return gzip.open(csv_path, "rb")
    req = urllib.request.Request(EXPORT_URL, headers={"User-Agent": USER_AGENT})
    resp = urllib.request.urlopen(req, timeout=120)
    return gzip.GzipFile(fileobj=resp)


def build(out_db, csv_path=None):
    t0 = time.time()
    products = {}
    seen = matched = 0
    with open_export(csv_path) as f:
        header = f.readline().decode("utf-8").rstrip("\n").split("\t")
        col = {name: i for i, name in enumerate(header)}
        missing = [c for c in NEEDED if c not in col]
        if missing:
            raise SystemExit(f"Export format changed, missing columns: {missing}")

        def get(cols, name):
            i = col.get(name)
            return cols[i].strip() if i is not None and i < len(cols) else ""

        for raw in f:
            seen += 1
            if b"en:czech-republic" not in raw and b"en:slovakia" not in raw:
                continue
            cols = raw.decode("utf-8", "replace").rstrip("\n").split("\t")
            if len(cols) != len(header):
                continue
            countries = set(get(cols, "countries_tags").encode().split(b","))
            if not countries & COUNTRIES:
                continue
            matched += 1
            code = re.sub(r"\D", "", get(cols, "code"))
            name = get(cols, "product_name") or get(cols, "generic_name")
            if len(code) < 8 or not name:
                continue
            p, c, fat = (num(get(cols, k)) for k in ("proteins_100g", "carbohydrates_100g", "fat_100g"))
            if p is None or c is None or fat is None:
                continue
            kcal = num(get(cols, "energy-kcal_100g"))
            if not kcal:
                kj = num(get(cols, "energy-kj_100g")) or num(get(cols, "energy_100g"))  # energy_100g is kJ
                kcal = kj / 4.184 if kj else 4 * p + 4 * c + 9 * fat
            sugar = num(get(cols, "sugars_100g")) or 0.0
            fiber = num(get(cols, "fiber_100g")) or 0.0
            # Sanity checks against typos in community data
            if kcal <= 0 or kcal > 950 or p > 100 or c > 100 or fat > 100 or p + c + fat > 105 or sugar > c + 0.5:
                continue
            brand = get(cols, "brands").split(",")[0].strip()
            quantity = get(cols, "quantity")
            pack_g, each_g = parse_quantity(quantity)
            serving_g = num(get(cols, "serving_quantity")) or each_g
            if serving_g is not None and (serving_g <= 0 or serving_g > 2000):
                serving_g = None
            categories = get(cols, "categories_en")[:200]
            modified = num(get(cols, "last_modified_t")) or 0
            prev = products.get(code)
            if prev is not None and prev[-1] >= modified:
                continue
            search = " " + norm(f"{name} {brand} {categories}") + " "
            products[code] = (code, name[:120], brand[:60], quantity[:40], pack_g, serving_g,
                              round(kcal, 1), round(p, 1), round(c, 1), round(fat, 1), round(sugar, 1),
                              round(fiber, 1), search, modified)

    if csv_path is None and len(products) < 100:
        raise SystemExit(f"Only {len(products)} products found, something is wrong with the export")
    tmp = out_db + ".tmp"
    if os.path.exists(tmp):
        os.remove(tmp)
    db = sqlite3.connect(tmp)
    db.executescript("""
        CREATE TABLE products(
            code TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT, quantity TEXT,
            pack_g REAL, serving_g REAL,
            kcal REAL, protein REAL, carbs REAL, fat REAL, sugar REAL, fiber REAL,
            search TEXT NOT NULL
        );
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    """)
    db.executemany("INSERT INTO products VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", [r[:-1] for r in products.values()])
    db.executemany("INSERT INTO meta VALUES (?,?)", [
        ("source", "Open Food Facts (ODbL), https://world.openfoodfacts.org"),
        ("countries", "Czechia, Slovakia"),
        ("built", time.strftime("%Y-%m-%d")),
        ("count", str(len(products))),
    ])
    db.commit()
    db.execute("VACUUM")
    db.close()
    os.replace(tmp, out_db)
    print(f"scanned {seen:,} products, {matched:,} sold in CZ/SK, kept {len(products):,} with usable nutrition "
          f"-> {out_db} ({os.path.getsize(out_db) / 1e6:.1f} MB) in {time.time() - t0:.0f}s")


if __name__ == "__main__":
    args = sys.argv[1:]
    csv = None
    if "--csv" in args:
        i = args.index("--csv")
        csv = args[i + 1]
        del args[i:i + 2]
    out = args[0]
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    build(out, csv)
