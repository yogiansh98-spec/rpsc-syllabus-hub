# Syllabus Hub — Native Android app shell v0.3

**The app shell comes first; verified RPSC exams are added one-by-one.** This is a development build, not a public release or official government app.

## Current scope
- Kotlin + Jetpack Compose, Android minSdk 24 (Android 7.0).
- Three bottom tabs: **Home / Saved / Downloads**.
- Search by Hindi/English exam, post, department, alias or subject. Search resets the category to All for global results.
- **Compact home:** Search → only four quick tabs (All / Teaching / RAS / More); All groups exams under short category headings. More lists only non-empty other categories, and category selection filters exams.
- **10 stable internal categories:** Teaching & Education, RAS & Administration, Police & Security, Engineering & Technical, Agriculture & Veterinary, Medical & AYUSH, Law & Legal, Finance & Statistics, IT & Science, Other Departments. Empty categories stay hidden until exams are added.
- 5 planned exam tracks are displayed as **Coming soon**: RAS, Senior Teacher, School Lecturer (School Education), Assistant Professor (College Education), Assistant Engineer Combined.
- Exam page is ready to group verified PDFs by **Stage / Paper / Subject** once those are entered.
- Native Android DownloadManager: open, download, reopen offline, remove downloaded PDFs.
- Local bookmarks with no login.
- **Zero PDFs in v0.3.** Previous 100 unverified candidate links are deliberately excluded from the app and catalog. Do not confuse planned exam tracks with verified syllabus coverage.
- Official-source and independent-app disclaimer.

## Next: add one fully verified exam
See `docs/ADDING_EXAMS.md`. For each exam: review all stages, common papers and subject-specific papers; verify official latest-applicable PDF(s); change its status from `planned` to `published` and add all approved PDFs; validate; publish new JSON catalog. Do **not** mark a partial exam `published` just to fill the screen.

## Local/CI build
Android SDK 36 and JDK 17 are needed. Set the app catalog URL only after verified JSON has been hosted on a static HTTPS URL.

```bash
python3 scripts/validate_catalog.py
python3 scripts/test_catalog.py
gradle --no-daemon :app:assembleDebug
# For remotely updated, hosted catalog (configure after hosting):
gradle --no-daemon :app:assembleDebug -PcatalogUrl=https://YOUR-STATIC-HOST/catalog.json
```

Or push source to **a new GitHub repository**, open Actions, and run **Build Android debug APK**. The debug APK is available as a workflow artifact once the build passes. No APK is included in this ZIP. There is no `gradlew` binary wrapper in this zip, because an unverified wrapper binary must not be added; CI installs Gradle 8.13 with `gradle/actions/setup-gradle`.

**This environment has no Android SDK/Gradle available; APK compilation and device testing have not been performed.**

## V1 source of truth
- `catalog/catalog.json` contains exactly the public/prototype catalog to publish (not unverified audit inventory).
- `app/src/main/assets/catalog.json` is the offline starter copy.
- `scripts/validate_catalog.py` fails on unverified documents and non-official URLs.
- `.github/workflows/catalog-check.yml` monitors links weekly, without auto-publishing.
- Configure GitHub Actions repository variable `CATALOG_URL` when static hosting is ready, then rebuild the APK once. Future catalog changes require no APK update.

This is independent educational software, not affiliated with RPSC or the Government of Rajasthan. It is not yet ready for public Play Store release.

## v0.6 online exam categories (2026-11-04)
- `exam_categories` in the remote catalog defines Home's category taxonomy (not just Explore cards). New IDs are no longer hardcoded in Kotlin.
- Quick tabs stay All, Teaching, RAS, More; all other nonempty categories appear in More automatically.
- Catalog updates are parsed atomically: exams, category labels and Explore cards must all validate before the cache is replaced.
- Legacy cached catalogs without `exam_categories` use the bundled 10 categories for backward compatibility.
- For dynamic categories, use the v0.6 or newer APK. This release does not add translated PDFs.
