# V1.7 ADDENDUM — COMPACT CATEGORY UX (2026-11-04)

**Current Android source:** `RPSC_Syllabus_Hub_Android_App_v0_3.zip`. The v0.2 file remains historical.

- Home: one search field; **exactly four visible quick tabs — All, Teaching, RAS, More**.
- **All** displays exams under short category headings, not a mixed flat list and not large cards.
- **More** displays only categories that currently have exam entries; selecting one filters to those exams. New populated categories appear automatically from catalog data.
- Full internal taxonomy: Teaching & Education; RAS & Administration; Police & Security; Engineering & Technical; Agriculture & Veterinary; Medical & AYUSH; Law & Legal; Finance & Statistics; IT & Science; Other Departments. These are our app's UI groupings, not official RPSC categories.
- In this pilot, Assistant Professor appears under Teaching; Assistant Engineer under Engineering. Department and exam identifiers remain distinct.
- Search matches Hindi/English names, aliases, post, department and subjects and switches to All for global results.
- Bottom navigation stays Home / Saved / Downloads; **zero unverified PDF links** are released.
- Android source changed and Python catalog tests run, but **APK compilation and phone testing remain pending**.

---

# RPSC Syllabus Hub — Master Plan

**Version:** 1.6 (App-first Android build; add complete exams one at a time)  
**Original plan:** 04 November 2026  
**Revised:** 04 November 2026  
**Status:** Native Android app shell source v0.2 created; Python catalog checks passed. APK compilation/device QA and public syllabus verification remain pending.

## V1.6 priority decision — BUILD APP FIRST, ADD EXAMS ONE BY ONE

- The Android source shell is now in `RPSC_Syllabus_Hub_Android_App_v0_2.zip`.
- Finish Home/Search, Exam Details, Bookmarks, Downloads, and the single hosted JSON catalog **before adding syllabus PDFs**.
- Five planned tracks can appear **only in an internal test build** marked `Syllabus जल्द जोड़ा जाएगा`. They must not be falsely described as verified/current coverage.
- The initial app project intentionally ships with **zero PDFs**. The previously collected 100 URL candidates are not published or bundled.
- Add exams **one whole exam at a time**: every stage, common paper, subject/branch, applicable syllabus and official PDF must be checked. Incomplete 2nd Grade (e.g. English only) is not accepted as a complete published exam.
- After each complete exam has been verified, set its JSON `status` to `published`, add only documents whose `edition_status` is `current_verified`, increment catalog_version and publish static JSON. No app code edits needed for new exam content.
- APK compilation and mobile behavior remain untested until Android SDK/Gradle CI is run. No public Play Store release before device/content QA.

This v1.6 decision overrides any earlier plan that said to verify and load all five exams before building the app.

---

## V1.4 mandatory rule — LATEST APPLICABLE SYLLABUS ONLY (04 November 2026)

**Student-facing app shows only the latest verified, currently applicable RPSC syllabus for each distinct exam track / recruitment stream / stage / paper / subject.** This supersedes any V1.3 instructions implying older edition browsing or an archive in the app.

- **Applicable is more important than the PDF's date.** A syllabus released for an earlier recruitment may remain the one explicitly adopted in a newer recruitment; verify official notices, re-use statements, corrigenda, or revised syllabus publications before excluding it. Keep `recruitment_cycle` and `issue_date` separate.
- **Do not choose a single global latest PDF.** Pick the latest applicable syllabus separately for RAS Prelims and Mains; Senior Teacher common Paper I and all relevant Paper II subjects; School Lecturer departments and subject/special-paper branches; Assistant Professor common Paper III and subjects I/II; and Assistant Engineer Combined stages/branches.
- **Nothing gets tagged `current_verified` from search results, PDF URL discovery, or date alone.** Required: exact RPSC official syllabus document + applicable recruitment/exam mapping + check against later revisions/corrigenda + working official URL + last-checked date.
- **Student catalog is a publish allowlist.** Only entries with `edition_status = current_verified` may be included in a shipped APK or the publicly hosted JSON catalog. Candidate/unverified records remain **internal-only** (the starter catalog is an internal test dataset, not a public catalog). A list of 100 candidate RPSC URLs is NOT 100 current verified PDFs.
- **Old editions are not browseable in V1.** Replace superseded catalog entries at publication; preserve history internally in Git. Local already-downloaded PDFs remain on the user's device until they remove them, with a clear "older download" warning when a replacement is known.
- **If a paper or subject is not verified, do not fill in an outdated PDF as a guess.** Before launch, mark the gap as pending in the internal QA checklist and do not advertise the exam as complete. Prefer a smaller complete pilot set to misleading partial coverage.
- **Never auto-publish based on link-health checking or scraper results.** All updates are human reviewed and merged in Git.
- **Version labels on screen:** show exam name, stage/paper/subject, applicable recruitment cycle (if official), issue/revision date (if known), and `Last checked`. Do not label anything "2026 syllabus" solely because it is still available in 2026.

**Release gate:** fail a production/catalog publish if any document is unverified, superseded, placeholder, or attached to the wrong department/exam. The pilot may be smaller than 5 until all of each chosen exam's papers/subjects are reviewed.

---

## 1. Product vision and launch promise

Build a **native Android app** where students can quickly **find the syllabus of RPSC examinations**, see which edition/paper it belongs to, and open or download the **original official RPSC PDF**.

**V1 path:** Open app → Search/select exam → Select stage/paper/subject → Open official PDF. Aim for 2–3 taps for common cases.

**Important distinction:** “One place for RPSC exam syllabuses” is the long-term goal, **not** a claim that V1 already covers every exam. Initially show only **verified, linked** exams; display actual coverage count rather than an unverified total.

- **Included:** RPSC examinations for which an official syllabus PDF and edition can be independently verified.
- **Excluded:** RSSB-only examinations (such as CET and Patwari), exam preparation notes, tests, PYQs, completion tracking, subscriptions, AI chat, and mandatory student login.
- **Independence:** The app is an independent educational reference, **not** an official RPSC/Rajasthan government app.

## 2. Version boundaries

### V1 — PDF-first syllabus library

**Core features**
1. Home screen with exam list, category filters, and simple search.
2. Search by **exam, post, department and subject** in **Hindi and English**, including tested spelling variants and aliases.
3. Exam details: stage (where applicable), paper/subject, official PDF link, edition/year, original source and verification date.
4. Open the **RPSC-hosted PDF** using an Android-compatible viewer/browser; provide an **in-app Download action** that saves the PDF to the student's device using Android-supported download/storage facilities.
5. Bookmarks/favourites stored locally on the device; optionally show recently viewed exams for convenience.
6. A dedicated **Downloads section** lists PDFs downloaded through the app, with document name, exam/paper, edition, download status and options to open, retry (if failed), or remove from the list/device with confirmation.
7. Cache the most recently **validated catalog JSON** so the exam list can be browsed offline. Indicate clearly that opening a **not-yet-downloaded PDF requires internet**.
8. Show clearly distinguishable **current verified** and **older/superseded** editions only where that status is established.
9. A visible **official-source link**, edition details and independent-app disclaimer.
10. Friendly loading, no-results, offline, broken-link, download-in-progress, download-failed, file-missing, and source-unavailable states.

**Not in V1:** Topic-by-topic rewritten syllabus reader; Room database as a requirement; admin panel; separate CMS; content signing; custom checksums; complex rollback service; automatic syllabus publishing; user accounts; tests/PYQ; notification system.

### V2 — optional later improvements

- Structured **section/topic/subtopic reader for a few popular, manually verified exams**; no automatic transcription accepted without proofing.
- Room database if the content/query needs have grown beyond cached JSON.
- More advanced offline reading (only after official-source use and rights/terms are reviewed).
- Further coverage, personalization, or editorial tooling only if actual demand justifies them.

## 3. User experience / screen plan

### Screen A — Home / All exams
- Clear title such as **RPSC Syllabus Hub** (final name subject to independent-identity review).
- Search bar: “Exam, Post, Department या Subject खोजें”.
- Simple categories: Teaching, Administrative, College Education, Technical, Legal, Other (exact taxonomy determined by catalog).
- Search results with exam name (Hindi/English as appropriate) and clear syllabus availability indicator.
- Easy access to **Bookmarks** and **Downloads** from the home/navigation area; if useful, a compact “Recently viewed” area; no distracting dashboard.
- Do not display invented exam counts or label unverified material as latest.

### Screen B — Exam details
- Official exam name, post/department and common aliases.
- Stages (Pre/Mains, if applicable), papers and subjects in the **actual exam-specific order**, not a forced generic hierarchy.
- One clearly labeled official document entry per verified PDF, with edition/year, issue/revision information if known, and **last checked** date.
- **Open official PDF** and **Download official PDF** actions (both point to the original RPSC resource).
- Do not display archived, uncertain or superseded syllabus documents as selectable entries in V1; show a clear error if an official current link is unavailable.

### Screen C — PDF access / viewing
- Prefer a robust Android PDF-opening flow via Android intents/system viewer or browser; test devices with and without dedicated PDF apps.
- The in-app Download button starts a tracked Android download from the **original official URL**. Show progress/status as available; mark it completed only after checking Android reports a successful download and that the stored file is accessible.
- PDFs downloaded via external browsers or third-party apps may not appear in this app's Downloads section; explain this clearly.
- Keep document source/title and version understandable before opening.
- If the official link is broken, show “Official link unavailable — being reviewed” rather than silently redirecting to an unverified mirror.

### Screen D — Bookmarks / Saved items
- Bookmarked exams are instantly accessible.
- A saved **bookmark** is not the same thing as a downloaded **PDF**; label them separately.
- Offline catalog browsing works from last validated cache; PDF offline access depends on the actual download and external viewer behavior.

### Screen E — Downloads / Offline PDFs
- **Dedicated Downloads entry** in the app's simple navigation (e.g. Home, Bookmarks, Downloads); it is a screen, not a separate hosting service.
- List **only downloads started and tracked inside this app**, grouped or searchable by exam, paper/subject and syllabus edition. Show document title, download state (downloading/completed/failed/missing) and saved date where available.
- Actions: **Open** downloaded PDF using a local Android content URI and a compatible viewer; **Retry** failed download; **Remove** with an explicit distinction between removing the history entry and deleting a file (where supported).
- A completed, accessible local PDF can open **without internet**. If it was deleted or moved outside the app, show **File not found** and offer a fresh download when online. If a newer applicable official edition exists, indicate that the saved PDF is an older download without deleting it automatically.
- **No login** needed. Metadata lives locally in small app storage; no need for Room in V1.
- Do not claim to list **all files in the phone's Downloads folder**: the app only knows its own tracked downloads. PDFs downloaded from an external browser are outside this list unless explicitly imported in a future version.

**Design rules:** Clean and professional Material 3, good Hindi fonts, readable type, predictable back navigation, large touch targets, low visual clutter and good performance on entry-level Android phones. In V1 do not add unnecessary animations, AI-looking dashboards, or complex cards.

## 4. Android technical choices — lean V1

| Part | V1 decision | Notes |
|---|---|---|
| Platform | Native Android | Android first, no PWA/iOS requirement |
| Language and UI | **Kotlin + Jetpack Compose + Material 3** | Follow established native workflow |
| Architecture | Simple MVVM + Repository if useful | Do not over-engineer |
| Content | **One versioned JSON catalog** | Published on free/low-cost static hosting |
| Sync | Simple HTTPS fetch + safe local cache | Read on app start/refresh when online; sensible caching |
| Local storage | JSON cache + DataStore/SharedPreferences for bookmarks **and tracked-download metadata** | **Room not required** in V1 |
| PDF | **Original RPSC URL** + Android DownloadManager/system viewer with safe content URI opening | No PDF rehosting on our servers; local device copies are user-requested |
| Minimum Android | **Suggested minSdk 24 (Android 7.0)** | Confirm dependency/support behavior on real devices |
| Target Android | Current Play Store-required target API at submission time | Independent of minimum SDK; verify policy before release |
| Builds | Gradle + **GitHub Actions** | Build installable test APK first |
| Publishing | APK testing first; Play Store later | APK and Play Store release workflows are distinct |

**V1 DownloadManager implementation note:** On tapping Download, save the document stable ID, edition, original URL, download identifier, and local status in an app-owned metadata store. Check the actual download state and openability before showing “Saved.” Prefer Android-supported storage APIs appropriate to the OS version; Android 7–9 behavior and any legacy storage permission must be tested, and avoid broad storage permission on modern Android versions. Do not maintain a separate cloud PDF library. Device-local downloaded copies are not the same as re-hosting official PDFs.

**Cost posture:** Build tools and Git-based content publishing can start free. Monitor hosting limits, traffic, Play developer account fees and ongoing maintenance rather than promising zero cost.

## 5. Phase 0: count, inventory and source verification

**Do not assume how many syllabus PDFs exist.** The total number of official RPSC syllabus documents, unique exams and available editions is **unverified**.

Start by **manually auditing** RPSC's official syllabus page and relevant exam-specific notices:

- Primary source: https://rpsc.rajasthan.gov.in/
- Syllabus section to verify: https://rpsc.rajasthan.gov.in/syllabus

Create a separate internal inventory (CSV or Markdown in Git is sufficient) containing:
`exam_id, exam_name_hi, exam_name_en, post, department, category, stage, paper, subject, year_or_edition, issue_date, official_pdf_url, checked_at, result, notes`.

**Audit method**
1. Count the accessible syllabus PDF entries **by hand**, noting date of count and limits (pagination, duplicates, inaccessible documents).
2. Deduplicate by exam + edition + stage/paper/subject; don't confuse number of PDFs with number of exams.
3. Follow each link and verify it genuinely serves the expected syllabus from an official RPSC location.
4. Record actual edition/year/notice if available. If not, use “date not stated” rather than making one up.
5. Select approximately **five verified exams** covering varied syllabus structures for the first pilot.
6. Expand in audited batches after pilot QA. Only promise the number actually published and verified.

**Source quality rules:** Do not invent syllabus names, papers, topic structure, publication dates or official status. Prefer human review over automatic PDF extraction. Preserve exact official document links; if terms restrict usage or embedding, use permitted linking/access methods only.

## 6. Single JSON catalog: data model

In V1 there is **one public versioned catalog JSON** (plus the separate internal audit file). Keep it human-editable in Git, and publish only reviewed records.

**Top-level fields:**
- `schema_version`: small integer for app compatibility.
- `catalog_version`: incremented on every approved data update.
- `published_at`: ISO 8601 UTC timestamp.
- `exams`: array of verified exam entries.

**Each exam:** stable `id`, `name_hi`, `name_en`, `aliases_hi[]`, `aliases_en[]`, `category`, `post[]`, `department[]`, `subjects[]` (search metadata), `documents[]`.

**Each PDF document:** stable `id`, `stage` (nullable), `paper` (nullable), `subject` (nullable), `edition_label`, `issue_date` (nullable), `official_url`, `verified_at`, `edition_status` (`current_verified` or `superseded_verified`) and optional `notes`.

The `current_verified` label means **latest applicable version confirmed against the official RPSC exam-specific notices and revisions as of the recorded last check**; it must be rechecked periodically and is not a guarantee against future publications.

**Illustrative schema only — no official syllabus content is asserted below:**

```json
{
  "schema_version": 1,
  "catalog_version": 1,
  "published_at": "2026-11-04T00:00:00Z",
  "exams": [
    {
      "id": "example-exam",
      "name_hi": "उदाहरण परीक्षा",
      "name_en": "Example Examination",
      "aliases_hi": ["उदाहरण"],
      "aliases_en": ["example exam"],
      "category": "other",
      "post": ["Example Post"],
      "department": ["Example Department"],
      "subjects": ["Example Subject"],
      "documents": [
        {
          "id": "example-exam-paper-1-edition-a",
          "stage": null,
          "paper": "Paper I",
          "subject": "Example Subject",
          "edition_label": "Example edition A",
          "issue_date": null,
          "official_url": null,
          "verified_at": null,
          "edition_status": "current_verified",
          "notes": "Placeholder only: never publish until a real verified RPSC PDF URL is provided"
        }
      ]
    }
  ]
}
```

**Publishing rule:** Example/placeholder records and `null` official links are **invalid in the public release**. This example documents only the shape; real records require a verified working official URL, verification date and matching document identity.

**Search implementation:** Normalize casing/whitespace, support Hindi and English names, and manually curated abbreviations/spelling variants. Search across exam name, aliases, post, department and subject. Show relevant matches without producing duplicate exam cards for every matched PDF.

## 7. Safe update workflow — Git only

**Goal:** Syllabus **data** can change without releasing a new APK, while the app remains useful if the update fails.

1. **Discover:** Manually check official RPSC pages (and review any automated link-check flags).
2. **Verify:** Open official document, record exact exam/paper/subject/edition and date details.
3. **Edit:** Change the **single JSON catalog in Git**: add/update documents, remove/replace the superseded public entry (retaining Git history), and increment `catalog_version`.
4. **Validate:** Run schema checks, stable-ID uniqueness checks, required fields, date formats, `https` official URL host/redirect policy, and duplicate detection. Human reviewer confirms the paper/document match; passing automation does not establish official authenticity.
5. **Publish:** Merge approved Git change and deploy JSON to static hosting.
6. **Sync:** The app fetches JSON via HTTPS, parses and checks required fields/compatibility; **write cache only after successful full validation**. On failure, continue using the last good local catalog with last-updated information.
7. **Rollback:** If a published catalog is wrong, **revert its Git commit and republish**. Confirm static CDN caches refresh. No V1 signing/checksum subsystem or full rollback service.

**No V1 admin panel:** Content is managed via Git commits and pull-request/review procedure. Restrict write access to maintainers.

**New APK needed when:** App logic/UI changes, an incompatible schema is introduced, or Android/Play distribution requirements change. **Not needed** for ordinary approved JSON content updates.

### Weekly broken-link check

A scheduled GitHub Actions script (or similar) runs **weekly**, checks official links conservatively and records failures, redirects, timeouts or content-type surprises for **manual review**. It must **never auto-edit or auto-publish** catalog data.

Because some official sites reject `HEAD` requests or have rate limits, use careful retries and, if necessary, small `GET` checks; do not treat one temporary error as proof that an RPSC document was removed. Store check time/results and avoid excessive requests.

## 8. Legal / trust / distribution safeguards

- Use an **independent app name and original icon**; do not use the RPSC emblem, government seals or RPSC logo, and do not imply affiliation or endorsement.
- Show a clear disclaimer in the app and Play listing, e.g. **“This is an independent educational app. It is not affiliated with or endorsed by RPSC or the Government of Rajasthan.”**
- Attribute the official source clearly, link to the original RPSC syllabus URL, and provide the latest checked date.
- **Link, don't re-host:** Do not store or serve copies of RPSC PDFs from our own server until source terms/permissions have been reviewed. Let users open/save them from the official URL on their own device.
- Before Play release, check Google's **current government-related, misleading claims, minimum functionality and target API** policies. A PDF link list alone may be too thin; useful search, filtering, local bookmarks, offline catalog and genuine metadata are intentional V1 value.
- No claim that being an educational app guarantees Play Store approval.

## 9. Search, accessibility and performance checks

- Searches should handle Hindi/English title variations, exam/post/department/subject terms, common abbreviations and minor spelling variants based on observed user searches.
- Verify search results are correct and don't mix different recruiting bodies or unrelated subjects.
- Use appropriate Hindi rendering, high contrast, large readable text and sensible touch target sizes.
- Test on low-end devices including Android 7 (API 24), and a range of newer devices.
- Show last verified date; when offline, make it explicit that catalog information could be older.
- Measure app cold start, search responsiveness, in-app download completion/failure handling, offline PDF reopening, and repeated PDF opening without excessive data usage.
- Confirm browser/Android viewer and download behavior with real RPSC PDF URLs, including errors and moved links.

## 10. Phases and launch milestones

| Phase | Deliverable | Exit condition |
|---|---|---|
| **0 — Manual audit** | Count official RPSC syllabus PDFs and build deduplicated inventory | Count date/method documented; **~5 pilot exams** fully source-verified |
| **1 — APK skeleton** | Native UI, exam list/search, details, official PDF open/download + basic Downloads screen | Pilot users can locate/open selected verified PDFs; Android 7+ basic testing |
| **2 — Catalog publishing** | Single versioned JSON, static hosting, safe cache, Git updates | New verified PDF entry visible without APK rebuild; invalid update preserves last-good cache |
| **3 — Pilot QA** | Search aliases, bookmarks, offline catalog, tracked Downloads screen, link checks, disclaimers, device testing | No unverified placeholders or mislabeled editions; weekly link checker flags issues |
| **4 — Content batches** | Add verified exams/documents in manageable groups | Publish counts reflect actual coverage; no false “all exams” assertion |
| **5 — Distribution** | GitHub Actions test APK, release checks, then optional Play Store submission | App identity/content/policy/device QA passed before Play submission |
| **V2 (later)** | Structured topic reader for selected popular exams | Manual content verification and genuine user demand |

## 11. V1 release checklist

### Content and provenance
- [ ] Manual official-page **PDF count** completed, with audit date and method; exam-count and PDF-count distinguished.
- [ ] Around **five pilot exams** have verified working official PDF links and correct paper/stage/subject mapping.
- [ ] Every published record has a stable ID, exam identity, edition/year label, official URL and `verified_at`.
- [ ] No sample data, placeholder PDF URLs, wrong board/RSSB items, duplicate records or unsupported “latest” claims.
- [ ] Additional exam batches published only after source verification.

### App and user experience
- [ ] Search works for exam, post, department and subject in Hindi/English, aliases and tested spelling variants.
- [ ] Search + filters, official links, local bookmarks, friendly empty/error states and offline catalog work.
- [ ] Official PDFs can be opened and downloaded **from inside the app**; Downloads screen shows only tracked PDFs, accurate status, open/retry/remove actions and reliable offline reopening.
- [ ] Test Android 7–9 and current Android storage/download differences; verify missing/deleted file behavior, failed downloads, and no unnecessary broad storage permission.
- [ ] Bookmarks and Downloads are clearly different; offline PDF availability is never falsely promised.
- [ ] Low-end Android devices tested, including **minSdk 24** if retained.
- [ ] App is readable, uncluttered and usable without login.

### Publishing and safety
- [ ] JSON schema and required-field validator rejects invalid content; failed fetch/parse retains previous valid cache.
- [ ] Git-based update/revert flow manually tested, including static hosting cache refresh.
- [ ] Weekly non-publishing link checker operational with actionable review output.
- [ ] Independent logo/name and non-affiliation disclaimer present in app and store listing.
- [ ] Current Play Store policy/target API requirements verified before submission.
- [ ] GitHub Actions builds/signing and installable APK smoke-test completed; Play Store only after content/QA readiness.

## 12. Confirmed decisions and out-of-scope items

**Confirmed planning decisions:**
- **Android APK app**, not website/PWA.
- **Kotlin + Jetpack Compose**.
- **PDF-first V1**, with a separate **Downloads/Offline PDFs section**; structured topic reader deferred to **V2**.
- **One versioned JSON catalog** on static hosting; Git-managed updates, no admin panel.
- **~5 verified exams as the pilot**, expanding toward broader RPSC coverage in batches.
- **Official PDF URLs only**; no republishing/hosting PDFs before terms review.
- **Android 7/API 24 suggested minimum**; confirm during compatibility tests.
- **APK first via GitHub Actions**, Play Store after QA and policy check.

**Still open (not blockers for starting Phase 0):**
- Final independent product name, icon, and Android package identifier.
- Which verified five exams best represent the pilot (decide **after** official inventory).
- Exact static hosting provider/repository and cache refresh strategy.
- Whether V1 should add any on-device “recently viewed” UI beyond bookmarks.

## 13. Change log

- **v1.0 — 04 Nov 2026:** Full topic reader, Room, manifest/rollback complexity and potential admin panel proposed.
- **v1.1 input — 04 Nov 2026:** Adopted PDF-first launch, one JSON catalog, Git-only editing, five-exam pilot, official-link policy, weekly link checks and APK-first distribution.
- **v1.2 — 04 Nov 2026:** Finalized practical V1 Search + filters + bookmarks (not a bare PDF list); verified pilot before broader coverage; Android 7 minimum separate from Play target API; original identity and independent-app disclaimer. Rewrote release phases/checklist accordingly.
- **v1.3 — 04 Nov 2026:** Added a dedicated **Downloads** screen for app-tracked official PDF downloads, offline open, retry/remove, accurate completion/missing-file states and Android-version-aware local storage. Kept separate from bookmarks and avoided a new database/admin panel.

---

**Immediate next task:** Complete **Phase 0 manually**: inventory official RPSC syllabus PDFs, record a date-stamped count and distinct exams, then select and verify approximately five pilot exams. Only then populate the public JSON catalog and build the first Android screens.


## Phase 0 execution note — 04 November 2026

Work was begun after v1.3 planning. A **pre-release starter project** was created separately in `RPSC_Syllabus_Hub_Starter/` and includes:

- A **five-track, six-PDF candidate catalog** using RPSC-hosted syllabus URLs found in official PDF search results (RAS Pre + Mains, Senior Teacher English, Lecturer Sanskrit Education Paper I, Assistant Professor Public Administration Paper I, Assistant Engineer Civil LSG).
- A CSV evidence/review inventory with explicit unverified newest-version and live-link status.
- A Kotlin + Jetpack Compose **starter source** with Home, Search, Exam Details, Bookmarks, Downloads, local catalog and future HTTPS refresh hook.
- JSON schema/host checks, weekly read-only link check script, and GitHub Actions debug-APK build workflow.

**Not completed:** A count of all syllabus PDFs, full official site enumeration, definitive newest editions for four tracks, live HTTP verification on the candidate URLs, Android compilation, installation testing, GitHub repository deployment, and static hosting. The starter is **not a verified APK or published catalog**. Do not mark Phase 0 complete until the audit checklist passes.


## Phase 0 official RPSC index audit — 04 Nov 2026 (v1.5)

The RPSC Exam Dashboard now provides official exam-specific syllabus pages for the five chosen pilot tracks. The five official listings comprise **104 syllabus/document entries** (RAS 2026: 5; 2nd Grade 2025: 11; 1st Grade School Education 2025: 26; Assistant Professor College Education 2025: 60; AE Combined 2024: 2). This is **not** the count for all RPSC exams.

The internal starter candidate JSON currently contains **100 direct official RPSC URL candidates**. Four extra listing entries await exact PDF URLs (RAS Pre Hindi, Mains Hindi, separate Mains scheme; School Lecturer Agriculture Paper II). The inventory CSV tracks all 104. The 2025 Assistant Professor cycle has been corrected against RPSC's actual listing; AE Combined has two separate PDFs.

**No student release is approved yet.** PDF link health, direct title checks, revisions/corrigenda and Android QA are pending. Only `current_verified` documents may enter a future public latest-only catalog. See `RPSC_Syllabus_Hub_Starter/audit/OFFICIAL_INDEX_AUDIT_2026-11-04.md` for official links and evidence.
