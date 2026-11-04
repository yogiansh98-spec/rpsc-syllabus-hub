# App-first rollout
1. **App shell (v0.2):** home/search, exam details with empty state, bookmarks, downloads, JSON catalog, Android project + APK CI source.
2. **First complete exam:** check official stage/paper/subject coverage, every current PDF, notices and link health, then add to catalog; do not ship partial subject listings.
3. **Next exam:** same verification workflow. Keep 5 placeholders until verified, then add more exam tracks.
4. **QA:** GitHub Actions APK build, install on Android 7 and modern Android, test Hindi/English search, open/download/offline/delete/bookmark, invalid JSON fallback, and no misleading official branding.
5. **Publish:** only after content QA; remote JSON on static hosting; Play Store policy review and disclaimer.

## v0.3 compact category layout decision (2026-11-04)
- Home: Search, four compact tabs (`All / Teaching / RAS / More`), category-based sections in All, and small exam rows.
- In More show only categories with exams; the full ten-category internal taxonomy is retained for future batches.
- Assistant Professor belongs in Teaching & Education (college education remains in department metadata).
- Assistant Engineer Combined belongs in Engineering & Technical.
- All 5 remain planned until complete latest verified syllabus is added by exam.
