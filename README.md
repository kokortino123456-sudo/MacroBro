# MacroBro

A free Android calorie counter with two AI helpers. No account, no ads, no tracking. Your data stays on your phone.

## What it does

- **Today**: a calorie ring, protein, carbs and fat meters, and Breakfast / Lunch / Dinner / Snacks. Tap any logged food to change its amount or meal, or delete it. You can copy a meal from the day before, and a water tracker sits on the same screen.
- **Food library**: 5,900+ foods with known values from the USDA database (kcal, carbs, protein, fat, sugar, fiber per 100 g). Popular foods have portions like "1 medium banana". You see carbs and net carbs before adding anything.
- **Barcode scanner**: scan a packaged food and its label values come from [Open Food Facts](https://world.openfoodfacts.org), which has good Czech and EU coverage. Scanned products are saved to *My foods* so the next scan works offline. If a product is missing, add it from the label once.
- **Online search**: search Open Food Facts by name when a packaged food isn't in the library.
- **My foods**: save your own foods from package labels, optionally with a serving size.
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

1. Get a free API key at <https://aistudio.google.com/apikey>.
2. In the app, open **Settings**, paste the key, tap **Test key**, then **Save**.

The model defaults to `gemini-flash-latest`, which always points to Google's current Flash model. You can type a different model name in Settings.

## Design notes

- Font: [Barlow](https://github.com/jpt/barlow) (SIL Open Font License, see `tools/Barlow-OFL.txt`).
- Protein, carbs and fat use a categorical palette validated for colour-vision deficiency and contrast against the card colour.
- Corners are concentric: cards are 28dp with 16dp padding, so everything inside uses 12dp.
- Cards have alpha borders, brighter on top, and higher surfaces are lighter (dark-mode depth). All icons come from one rounded set. Scrolling rows fade at the edges, and some icons morph with a short blur.

## Tech

Kotlin, Jetpack Compose (Material 3), plain `HttpURLConnection` calls to the Gemini and Open Food Facts APIs, and local JSON storage. Barcode scanning uses Google's code scanner from Play services, so the app needs no camera permission. Minimum Android version: 8.0 (API 26).

## Food data

- `app/src/main/assets/foods.tsv` comes from **USDA FoodData Central, SR Legacy** (public domain), via the [tempo-food-db](https://www.npmjs.com/package/tempo-food-db) dataset by TempoLife (CC-BY-4.0), which supplied the categories. Friendly names and portion sizes for popular foods are added in `tools/build_food_db.py`, which regenerates the file:

  ```
  python3 tools/build_food_db.py path/to/tempolife-foods.csv app/src/main/assets/foods.tsv
  ```

- Packaged foods come from **Open Food Facts** (Open Database License), looked up live.
