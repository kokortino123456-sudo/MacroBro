"""Builds app/src/main/assets/foods.tsv from the USDA SR Legacy rows of the
tempo-food-db npm package (data/tempolife-foods.csv).

Usage: python3 tools/build_food_db.py path/to/tempolife-foods.csv app/src/main/assets/foods.tsv
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


def num(s):
    try:
        return round(float(s), 1)
    except (TypeError, ValueError):
        return 0.0


def fmt(v):
    return ("%g" % v)


def main(src, dst):
    rows = [r for r in csv.DictReader(open(src, encoding="utf-8")) if r["source"] == "usda_sr_legacy"]
    by_name = {}
    for r in rows:
        by_name.setdefault(r["name"], r)  # first wins for duplicate names

    out, used = [], set()
    for rank, (friendly, candidates, plabel, pgrams) in enumerate(POPULAR, start=1):
        r = next((by_name[c] for c in candidates if c in by_name), None)
        if r is None:
            print("MISSING popular:", friendly, candidates, file=sys.stderr)
            continue
        used.add(r["name"])
        out.append((r, friendly, rank, plabel or "", pgrams or 0))
    for name, r in by_name.items():
        if name not in used:
            out.append((r, "", 0, "", 0))

    with open(dst, "w", encoding="utf-8") as f:
        f.write("# Food data: USDA FoodData Central SR Legacy (public domain); categories via TempoLife food dataset (CC-BY-4.0)\n")
        for r, friendly, rank, plabel, pgrams in out:
            cat = CATEGORIES.get(r["category"], "Other")
            vals = [num(r[k]) for k in ("calories", "protein", "carbs", "fat", "fiber", "sugar")]
            f.write("\t".join([r["name"].replace("\t", " "), friendly, cat] + [fmt(v) for v in vals]
                              + [str(rank), plabel, fmt(pgrams)]) + "\n")
    print(f"wrote {len(out)} foods ({sum(1 for o in out if o[2])} popular) to {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
