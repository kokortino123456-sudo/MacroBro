# MacroBro

A free Android calorie counter with two AI helpers. No account, no ads, no tracking. Your data stays on your phone.

## What it does

- **Today**: a calorie ring, protein, carbs and fat meters, and Breakfast / Lunch / Dinner / Snacks. Tap any logged food to change its amount or meal, or delete it. You can copy a meal from the day before, and a water tracker sits on the same screen.
- **Food library**: 13,000+ foods with known values per 100 g (kcal, carbs, protein, fat, sugar, fiber):
  - about 5,900 foods from the USDA database
  - about 7,100 foods and dishes from Germany's official BLS database
  - 247 curated national dishes from 21 cuisines, including Czech, Slovak, Polish, German, Austrian, Hungarian, Italian, Greek, Turkish, Indian, Chinese, Japanese and Mexican

  Search works with or without accents, so "knedlik" finds *Houskový knedlík*, "zurek" finds *Żurek* and "knoedel" finds *Knödel*. Popular foods and dishes have portions like "1 medium banana" or "4 slices".
- **Barcode scanner**: scan a packaged food and its label values come from [Open Food Facts](https://world.openfoodfacts.org), which has good Czech and EU coverage. Scanned products are saved to *My foods* so the next scan works offline. If a product is missing, add it from the label once.
- **Online search**: search Open Food Facts by name when a packaged food isn't in the library.
- **My foods**: save your own foods from package labels, optionally with a serving size.
- **Two AI providers**: Google Gemini (free key) or OpenRouter (one key for hundreds of models). The OpenRouter default `openrouter/free` picks a free model that can read photos. A model picker filters by free and photo support.
- **Snap or describe (AI)**: take a photo, type what you ate ("2 eggs and toast with butter"), or both. Gemini lists each food with grams, kcal and macros. You check and fix them before adding.
- **Gym Bro (AI)**: a hype gym-bro coach to chat with. It can see your targets, today's log, the last 7 days, your weigh-ins and your water.
- **Progress**: a calorie chart against your goal (7 or 30 days), a weight log with a trend chart, your logging streak, and 7-day averages.
- **Targets calculator**: Mifflin–St Jeor estimate from your age, height, weight, activity and goal (lose fat, maintain, build muscle).
- **Backups**: export everything to a JSON file and import it on a new phone. The API key is never included.

## Get the APK

Every push to `main` builds a new release. On your phone, open the repo's **Releases** page, download `MacroBro.apk` and install it. Allow "install unknown apps" for your browser if asked.

All builds share one signing key (`app/signing.jks`), so each one installs as an update and keeps your data. The key is in the repo on purpose, so GitHub can build without any setup. That's fine for a personal app; make a private key if you ever publish on the Play Store.

Pushes to other branches build too (check the Actions tab) but don't publish a release.

## Set up the AI

Pick a provider in **Settings → AI**.

**Google Gemini**
1. Get a free key at <https://aistudio.google.com/apikey>.
2. Paste it, tap **Test key**, then **Save**.

The model defaults to `gemini-flash-latest`, which always points to Google's current Flash model.

**OpenRouter**
1. Get a key at <https://openrouter.ai/keys>.
2. Paste it, tap **Test key**, then **Save**.

The model defaults to `openrouter/free`, the Free Models Router. It picks a free model that supports what each request needs, including photos for Snap. **Choose a model** lists every OpenRouter model, filterable by free and photo support. Free models have daily request limits.

## Design notes

- Font: [Barlow](https://github.com/jpt/barlow) (SIL Open Font License, see `tools/Barlow-OFL.txt`).
- Protein, carbs and fat use a categorical palette validated for colour-vision deficiency and contrast against the card colour.
- Corners are concentric: cards are 28dp with 16dp padding, so everything inside uses 12dp.
- Cards have alpha borders, brighter on top, and higher surfaces are lighter (dark-mode depth). All icons come from one rounded set. Scrolling rows fade at the edges, and some icons morph with a short blur.

## Tech

Kotlin, Jetpack Compose (Material 3), plain `HttpURLConnection` calls to the Gemini and Open Food Facts APIs, and local JSON storage. Barcode scanning uses Google's code scanner from Play services, so the app needs no camera permission. Minimum Android version: 8.0 (API 26).

## Food data

Each dish in `tools/world_dishes.py` points to one official entry. A dish no database has exactly (svíčková or żurek, for example) uses the closest comparable dish and shows "≈ <that dish>" in the app.

Carbohydrates follow each source's convention. USDA counts fiber inside carbs, so the app shows net carbs for those foods. BLS and EU labels already exclude fiber.

- **USDA FoodData Central, SR Legacy** (public domain), via the [tempo-food-db](https://www.npmjs.com/package/tempo-food-db) dataset by TempoLife (CC-BY-4.0), which supplied the categories.
- **Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0, German Nutrient Database.** Karlsruhe. DOI: 10.25826/Data20251217-134202-0. Licensed CC BY 4.0, via the [@thereforejs/foods](https://www.npmjs.com/package/@thereforejs/foods) extract. Changes: only the nutrition-declaration nutrients are kept, and energy is calculated from them with the EU Regulation 1169/2011 factors.

`tools/build_food_db.py` regenerates `app/src/main/assets/foods.tsv` from these sources. It adds friendly names and portions for popular foods and merges in the curated dishes:

```
python3 tools/build_food_db.py tempolife-foods.csv thereforejs-foods/dist/index.mjs app/src/main/assets/foods.tsv
```

- Packaged foods come from **Open Food Facts** (Open Database License), looked up live.
