"""Builds app/src/main/assets/foods.tsv for MacroBro.

Sources (all openly licensed):
  - USDA FoodData Central SR Legacy (public domain), via the tempo-food-db npm package
    (data/tempolife-foods.csv; categories by TempoLife, CC-BY-4.0)
  - Bundeslebensmittelschluessel (BLS) 4.0, Max Rubner-Institut, CC BY 4.0, via the
    @thereforejs/foods npm package (dist/index.mjs, parsed as data, never executed).
    Energy is calculated from the macronutrients with the EU Regulation 1169/2011 factors.
  - tools/world_dishes.py: curated national dishes mapped to entries of the two above.

Usage:
  python3 tools/build_food_db.py tempolife-foods.csv thereforejs-index.mjs app/src/main/assets/foods.tsv
"""
import csv
import sys

CATEGORIES = {
    "Liha ja linnuliha": "Meat & poultry",
    "Köögivili": "Vegetables",
    "Leivad ja pagarittooted": "Bread & bakery",
    "Valmistoidud": "Meals & fast food",
    "Puuviljad ja marjad": "Fruit",
    "Magustoidud ja küpsetised": "Sweets & desserts",
    "Joogid": "Drinks",
    "Piimatooted": "Dairy",
    "Kaunviljad": "Beans & legumes",
    "Kala ja mereannid": "Fish & seafood",
    "Rasvad ja õlid": "Fats & oils",
    "Hommikusöögid": "Breakfast & cereal",
    "Teravili ja pasta": "Grains & pasta",
    "Pähklid ja seemned": "Nuts & seeds",
    "Supid": "Soups",
    "Snäkid ja suupisted": "Snacks",
    "Kartulid ja mugulad": "Potatoes",
    "Kastmed ja maitsed": "Sauces",
    "Vürtsid ja maitseained": "Spices",
    "Munad": "Eggs",
    "Salatid": "Salads",
}

# (friendly name, [candidate USDA names, first match wins], portion label, portion grams)
POPULAR = [
    ("Banana", ["Bananas, raw"], "1 medium", 118),
    ("Apple", ["Apples, raw, with skin"], "1 medium", 182),
    ("Orange", ["Oranges, raw"], "1 medium", 131),
    ("Strawberries", ["Strawberries, raw"], None, None),
    ("Blueberries", ["Blueberries, raw"], None, None),
    ("Grapes", ["Grapes, red or green (European type, such as Thompson seedless), raw"], None, None),
    ("Avocado", ["Avocados, raw"], "½ avocado", 100),
    ("Pineapple", ["Pineapple, raw"], None, None),
    ("Watermelon", ["Watermelon, raw"], None, None),
    ("Mango", ["Mangos, raw"], None, None),
    ("Pear", ["Pears, raw"], "1 medium", 178),
    ("Kiwi", ["Kiwifruit, green, raw"], "1 fruit", 69),
    ("Raisins", ["Raisins, dark, seedless"], None, None),
    ("Dates (medjool)", ["Dates, medjool"], "1 date", 24),
    ("White rice (cooked)", ["Rice, white, long-grain, cooked"], "1 cup", 158),
    ("White rice (dry)", ["Rice, white, long-grain, raw"], None, None),
    ("Brown rice (cooked)", ["Rice, brown, long-grain, cooked"], "1 cup", 202),
    ("Pasta (cooked)", ["Pasta, cooked, without added salt"], "1 cup", 140),
    ("Pasta (dry)", ["Pasta, dry"], None, None),
    ("Egg noodles (cooked)", ["Noodles, egg, cooked"], None, None),
    ("Rice noodles (cooked)", ["Rice noodles, cooked"], None, None),
    ("Oats (dry)", ["Cereals, oats, regular and quick, not fortified, dry", "Oats"], "½ cup", 40),
    ("Couscous (cooked)", ["Couscous, cooked"], None, None),
    ("Quinoa (cooked)", ["Quinoa, cooked"], None, None),
    ("White bread", ["Bread, white"], "1 slice", 30),
    ("Whole-wheat bread", ["Bread, whole-wheat"], "1 slice", 30),
    ("Rye bread", ["Bread, rye"], "1 slice", 32),
    ("Bagel", ["Bagels, plain, without calcium propionate", "Bagels, plain, with calcium propionate"], "1 bagel", 105),
    ("Croissant", ["Croissants, butter"], "1 medium", 57),
    ("Flour tortilla", ["Tortillas, ready-to-bake or -fry, flour, refrigerated"], "1 tortilla", 45),
    ("Corn tortilla", ["Tortillas, ready-to-bake or -fry, corn"], "1 tortilla", 26),
    ("Pancakes", ["Pancakes, plain"], None, None),
    ("Potatoes (boiled)", ["Potatoes, boiled, cooked without skin, flesh, without salt"], None, None),
    ("Baked potato", ["Potatoes, baked, flesh and skin, without salt"], None, None),
    ("Sweet potato (baked)", ["Sweet potato, cooked, baked in skin, flesh, without salt"], None, None),
    ("Egg", ["Egg, whole, raw, fresh"], "1 large", 50),
    ("Egg (boiled)", ["Egg, whole, cooked, hard-boiled"], "1 large", 50),
    ("Egg (fried)", ["Egg, whole, cooked, fried"], "1 large", 46),
    ("Whole milk", ["Milk, whole, 3.25% milkfat, with added vitamin D"], "1 cup", 244),
    ("Semi-skimmed milk (2%)", ["Milk, reduced fat, fluid, 2% milkfat, with added vitamin A and vitamin D"], "1 cup", 244),
    ("Greek yogurt (nonfat)", ["Yogurt, Greek, plain, nonfat"], "1 pot", 170),
    ("Plain yogurt", ["Yogurt, plain, whole milk"], None, None),
    ("Cottage cheese", ["Cheese, cottage, lowfat, 2% milkfat"], None, None),
    ("Cheddar", ["Cheese, cheddar"], "1 slice", 20),
    ("Mozzarella", ["Cheese, mozzarella, whole milk"], None, None),
    ("Feta", ["Cheese, feta"], None, None),
    ("Parmesan (grated)", ["Cheese, parmesan, grated"], "1 tbsp", 5),
    ("Cream cheese", ["Cheese, cream"], "1 tbsp", 15),
    ("Butter", ["Butter, salted"], "1 tbsp", 14),
    ("Heavy cream", ["Cream, fluid, heavy whipping"], None, None),
    ("Whey protein powder", ["Beverages, Protein powder whey based"], "1 scoop", 30),
    ("Chicken breast (cooked)", ["Chicken, breast, meat only, cooked, roasted"], None, None),
    ("Chicken thigh (cooked)", ["Chicken, thigh, meat only, cooked, roasted"], None, None),
    ("Ground beef 85% (cooked)", ["Beef, ground, 85% lean meat / 15% fat, patty, cooked"], None, None),
    ("Ground beef 90% (cooked)", ["Beef, ground, 90% lean meat / 10% fat, patty, cooked"], None, None),
    ("Pork loin (cooked)", ["Pork, fresh, loin, whole, cooked"], None, None),
    ("Turkey breast (cooked)", ["Turkey, whole, breast, meat only, cooked"], None, None),
    ("Bacon (cooked)", ["Pork, cured, bacon, cooked, baked"], "1 slice", 8),
    ("Ham (sliced)", ["Ham, sliced, regular (approximately 11% fat)"], "1 slice", 28),
    ("Salmon (cooked)", ["Fish, salmon, Atlantic, farmed, cooked"], None, None),
    ("Tuna (canned in water)", ["Fish, tuna, light, canned in water, drained solids"], None, None),
    ("Cod (cooked)", ["Fish, cod, Atlantic, cooked, dry heat"], None, None),
    ("Shrimp (cooked)", ["Crustaceans, shrimp, cooked"], None, None),
    ("Tofu (firm)", ["Tofu, firm, prepared with calcium sulfate and magnesium chloride (nigari)"], None, None),
    ("Broccoli", ["Broccoli, raw"], None, None),
    ("Carrot", ["Carrots, raw"], "1 medium", 61),
    ("Tomato", ["Tomatoes, red, ripe, raw"], "1 medium", 123),
    ("Cucumber", ["Cucumber, with peel, raw"], None, None),
    ("Lettuce (iceberg)", ["Lettuce, iceberg, raw"], None, None),
    ("Spinach", ["Spinach, raw"], None, None),
    ("Onion", ["Onions, raw"], "1 medium", 110),
    ("Red bell pepper", ["Peppers, sweet, red, raw"], "1 medium", 119),
    ("Sweet corn (canned)", ["Corn, sweet, yellow, canned, brine pack"], None, None),
    ("Green peas", ["Peas, green, frozen, cooked, boiled"], None, None),
    ("Mushrooms", ["Mushrooms, white, raw"], None, None),
    ("Cabbage", ["Cabbage, raw"], None, None),
    ("Zucchini", ["Squash, summer, zucchini, raw"], None, None),
    ("Black beans (cooked)", ["Beans, black, mature seeds, cooked, boiled"], None, None),
    ("Chickpeas (cooked)", ["Chickpeas (garbanzo beans, bengal gram), mature seeds, cooked, boiled"], None, None),
    ("Lentils (cooked)", ["Lentils, mature seeds, cooked, boiled, without salt"], None, None),
    ("Kidney beans (cooked)", ["Beans, kidney, red, mature seeds, cooked"], None, None),
    ("Hummus", ["Hummus, commercial"], "2 tbsp", 30),
    ("Almonds", ["Nuts, almonds"], "1 handful", 28),
    ("Walnuts", ["Nuts, walnuts, english"], "1 handful", 28),
    ("Cashews", ["Nuts, cashew nuts, raw"], "1 handful", 28),
    ("Peanuts (roasted)", ["Peanuts, all types, dry-roasted, without salt"], "1 handful", 28),
    ("Peanut butter", ["Peanut butter, smooth style, without salt"], "1 tbsp", 16),
    ("Chia seeds", ["Seeds, chia seeds, dried"], "1 tbsp", 12),
    ("Olive oil", ["Oil, olive, salad or cooking"], "1 tbsp", 13.5),
    ("Sunflower oil", ["Oil, sunflower, high oleic (70% and over)"], "1 tbsp", 13.6),
    ("Mayonnaise", ["Salad dressing, mayonnaise"], "1 tbsp", 14),
    ("Ketchup", ["Catsup"], "1 tbsp", 17),
    ("Honey", ["Honey"], "1 tbsp", 21),
    ("Sugar", ["Sugars, granulated"], "1 tsp", 4.2),
    ("Jam", ["Jams and preserves"], "1 tbsp", 20),
    ("Dark chocolate 70–85%", ["Chocolate, dark, 70-85% cacao solids"], "1 row", 10),
    ("Vanilla ice cream", ["Ice creams, vanilla"], "1 scoop", 66),
    ("Potato chips", ["Snacks, potato chips, plain, salted"], None, None),
    ("Popcorn (air-popped)", ["Snacks, popcorn, air-popped"], None, None),
    ("Rice cakes", ["Snacks, rice cakes, brown rice, plain, unsalted"], "1 cake", 9),
    ("Chocolate chip cookies", ["Cookies, chocolate chip, soft-type"], "1 cookie", 15),
    ("Cheese pizza", ["Pizza, cheese topping, regular crust, frozen, cooked"], None, None),
    ("Orange juice", ["Orange juice, raw"], "1 glass", 248),
    ("Cola", ["Beverages, carbonated, cola"], "1 can (330 ml)", 340),
    ("Beer", ["Alcoholic beverage, beer, all", "Alcoholic beverage, beer, regular, all"], "0.5 l", 515),
    ("Red wine", ["Alcoholic beverage, wine, table, red"], "1 glass", 147),
]


import json
import os
import re

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from world_dishes import CUISINES  # noqa: E402


def num(s):
    try:
        return round(float(s), 1)
    except (TypeError, ValueError):
        return 0.0


def fmt(v):
    return ("%g" % v)


def clean(s):
    return (s or "").replace("\t", " ").replace("\n", " ").strip()


# ---------- BLS 4.0 ----------

def parse_bls(path):
    src = open(path, encoding="utf-8").read()
    foods = []
    for m in re.finditer(r'^const ([A-Z][A-Z0-9_]*) = (\{.*?^\});', src, re.S | re.M):
        try:
            foods.append(json.loads(m.group(2)))
        except json.JSONDecodeError:
            pass
    return foods


def bls_value(f, key):
    v = f["per100g"].get(key)
    if v is None:
        # A mark (<LOD, <LOQ, TR) means "too little to measure": count it as 0.
        return 0.0 if key in f.get("marks", {}) else None
    return float(v)


def bls_nutrition(f):
    p, c, fat = (bls_value(f, k) for k in ("protein", "carbohydrate", "fat"))
    if p is None or c is None or fat is None:
        return None
    g = lambda k: bls_value(f, k) or 0.0
    polyols = g("polyols")
    # EU Regulation 1169/2011 Annex XIV energy conversion factors
    kcal = 4 * (c - polyols) + 2.4 * polyols + 4 * p + 9 * fat + 7 * g("alcohol") + 3 * g("organicAcids") + 2 * g("fibre")
    return dict(kcal=kcal, protein=p, carbs=c, fat=fat, fiber=g("fibre"), sugar=g("sugars"))


LEGUME_WORDS = ("bean", "lentil", "pea ", "peas", "chickpea", "soy", "soya", "tofu", "tempeh", "lupin", "hummus")


def bls_category(code, en):
    a, b = code[0], code[1] if len(code) > 1 else ""
    if a == "B": return "Bread & bakery"
    if a == "C": return "Grains & pasta"
    if a == "D": return "Sweets & desserts"
    if a == "E": return "Eggs" if b in "123" else "Grains & pasta"
    if a == "F": return "Fruit"
    if a == "G": return "Vegetables"
    if a == "H": return "Beans & legumes" if any(w in en.lower() + " " for w in LEGUME_WORDS) else "Nuts & seeds"
    if a == "K": return "Vegetables" if b == "7" else "Potatoes"
    if a == "M": return "Dairy"
    if a in "NP": return "Drinks"
    if a == "Q": return "Fats & oils"
    if a == "R": return "Sauces" if b == "1" else "Spices"
    if a == "S": return "Sweets & desserts"
    if a == "T": return "Fish & seafood"
    if a in "UVW": return "Meat & poultry"
    if a == "X":
        return {"1": "Salads", "2": "Salads", "3": "Sauces", "4": "Soups", "5": "Vegetables",
                "6": "Potatoes", "7": "Grains & pasta"}.get(b, "Meals & fast food")
    if a == "Y": return "Sweets & desserts" if b == "8" else "Meals & fast food"
    return "Other"


# ---------- build ----------

COLUMNS = ["name", "display", "category", "kcal", "protein", "carbs", "fat", "fiber", "sugar",
           "popular", "portionLabel", "portionG", "source", "aliases", "note"]


def row(name, display, category, n, popular=0, plabel="", pgrams=0, source="", aliases="", note=""):
    return [clean(name), clean(display), category,
            fmt(round(n["kcal"])), fmt(num(n["protein"])), fmt(num(n["carbs"])), fmt(num(n["fat"])),
            fmt(num(n["fiber"])), fmt(num(n["sugar"])),
            str(popular), clean(plabel), fmt(pgrams or 0), source, clean(aliases), clean(note)]


def main(usda_csv, bls_mjs, dst):
    usda_rows = [r for r in csv.DictReader(open(usda_csv, encoding="utf-8")) if r["source"] == "usda_sr_legacy"]
    usda = {}
    for r in usda_rows:
        usda.setdefault(r["name"], r)
    usda_n = {k: dict(kcal=num(r["calories"]), protein=num(r["protein"]), carbs=num(r["carbs"]), fat=num(r["fat"]),
                      fiber=num(r["fiber"]), sugar=num(r["sugar"])) for k, r in usda.items()}

    bls = {}
    for f in parse_bls(bls_mjs):
        if not f["sources"] or f["sources"][0]["source"] != "bls-4.0":
            continue
        n = bls_nutrition(f)
        if n is not None:
            bls[f["code"]] = (f, n)

    out = []
    # 1) popular USDA foods with friendly names
    used = set()
    for rank, (friendly, candidates, plabel, pgrams) in enumerate(POPULAR, start=1):
        name = next((c for c in candidates if c in usda), None)
        if name is None:
            print("MISSING popular:", friendly, file=sys.stderr)
            continue
        used.add(name)
        out.append(row(name, friendly, CATEGORIES.get(usda[name]["category"], "Other"), usda_n[name],
                       popular=rank, plabel=plabel or "", pgrams=pgrams or 0, source="usda"))

    # 2) curated national dishes
    missing = 0
    dishes = 0
    for cuisine, items in CUISINES:
        for display, english, aliases, src, plabel, pgrams, similar in items:
            kind, key = src.split(":", 1)
            if kind == "B":
                if key not in bls:
                    print("MISSING dish source:", display, src, file=sys.stderr); missing += 1; continue
                f, n = bls[key]
                src_name, src_label, origin = f["name"]["en"], "BLS", "dish:bls"
                src_alias = "" if similar else f["name"]["de"] + " " + f["name"]["en"]
            else:
                if key not in usda_n:
                    print("MISSING dish source:", display, src, file=sys.stderr); missing += 1; continue
                n = usda_n[key]
                src_name, src_label, origin = key, "USDA", "dish:usda"
                src_alias = "" if similar else key
            note = f"{cuisine} · {english}"
            note += f" · ≈ {src_name} ({src_label})" if similar else f" · {src_label}"
            out.append(row(english, display, cuisine, n, plabel=plabel or "", pgrams=pgrams or 0, source=origin,
                           aliases=" ".join(x for x in (english, aliases, src_alias) if x), note=note))
            dishes += 1

    # 3) all other USDA foods
    for name, r in usda.items():
        if name not in used:
            out.append(row(name, "", CATEGORIES.get(r["category"], "Other"), usda_n[name], source="usda"))

    # 4) all BLS foods (English display, German name searchable)
    for code, (f, n) in bls.items():
        de, en = f["name"]["de"], f["name"]["en"]
        out.append(row(de, en or de, bls_category(code, en), n, source="bls", note=f"{de} · BLS (DE)"))

    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("# MacroBro foods v2 | columns: " + ",".join(COLUMNS) + "\n")
        fh.write("# USDA FoodData Central SR Legacy (public domain); categories via TempoLife food dataset (CC-BY-4.0)\n")
        fh.write("# Max Rubner-Institut (2025): Bundeslebensmittelschluessel (BLS), Version 4.0, CC BY 4.0, "
                 "DOI 10.25826/Data20251217-134202-0; energy calculated with EU 1169/2011 factors\n")
        for r in out:
            fh.write("\t".join(r) + "\n")
    print(f"wrote {len(out)} foods: {len(POPULAR)} popular, {dishes} dishes, {len(usda) - len(used)} other USDA, "
          f"{len(bls)} BLS; missing dish sources: {missing}")
    if missing:
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
