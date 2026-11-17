# MacroBro 💪

A free Android calorie counter with two AI helpers, both running on Google Gemini:

- **Snap Food AI**: take or pick a photo of a meal. The AI lists each food with grams, kcal and macros. You can edit names and grams, untick wrong items, and add the rest to a meal.
- **Gym Bro AI**: a hype gym-bro coach to chat with. It can see your targets and today's log, so it can answer "what should I eat to hit my protein?".
- **Food library**: 5,900+ foods with known values from the USDA database (kcal, carbs, protein, fat, sugar, fiber per 100 g). Search it, pick a portion like "1 medium banana" or type grams, and see carbs and net carbs before adding. You can also save your own foods from package labels under *My foods*.
- **Calorie log**: a daily calorie ring, protein/carbs/fat bars, Breakfast/Lunch/Dinner/Snacks, manual entries with recent-food shortcuts, and browsing past days.
- **Targets calculator**: Mifflin–St Jeor estimate from your age, height, weight, activity and goal (lose fat, maintain, build muscle).

Everything is stored only on your phone. Photos and chat messages go to Google Gemini to get answers.

## Get the APK

1. Create a new **empty** GitHub repo, e.g. `MacroBro`.
2. Upload everything in this folder to it, including the hidden `.github` folder. Either:
   - use `git push`, or
   - in the browser, open the repo, choose *Add file → Upload files* and drag in the whole folder contents.
3. Open the **Actions** tab. The *Build APK* run takes about 5 minutes.
4. On your phone, open the repo's **Releases** page, download `MacroBro.apk` and install it. Allow "install unknown apps" for your browser if asked.

Every later push builds a new release. Because all builds share the same signing key (`app/signing.jks`), each one installs as an update and keeps your data.

> The signing key is in the repo on purpose so GitHub can build without any setup. That's fine for a personal app. If you ever publish it on the Play Store, make a private key instead.

## Set up the AI

1. Get a free API key at <https://aistudio.google.com/apikey>.
2. In the app, open **Settings**, paste the key, tap **Test key**, then **Save**.

The model defaults to `gemini-flash-latest`, which always points to Google's current Flash model. You can type a different model name in Settings.

## Tech

Kotlin, Jetpack Compose (Material 3), plain `HttpURLConnection` calls to the Gemini REST API, and local JSON storage. There's no account, no analytics and no third-party SDK. Minimum Android version: 8.0 (API 26).

## Food data

The food library (`app/src/main/assets/foods.tsv`) comes from **USDA FoodData Central, SR Legacy** (public domain), via the [tempo-food-db](https://www.npmjs.com/package/tempo-food-db) dataset by TempoLife (CC-BY-4.0, which supplied the categories). Friendly names and portion sizes for popular foods are added in `tools/build_food_db.py`, which regenerates the file:

```
python3 tools/build_food_db.py path/to/tempolife-foods.csv app/src/main/assets/foods.tsv
```
