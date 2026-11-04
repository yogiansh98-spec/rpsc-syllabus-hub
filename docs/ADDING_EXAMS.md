# Adding RPSC exams — one complete exam at a time

## 0. The rule
Only the **latest currently applicable** official RPSC syllabus, including any corrigendum. Do not show old batches or guess. Current candidate 100-PDF inventory is internal and must not be copied wholesale into this app.

## 1. Choose an exam
For example `senior-teacher`. Consult the official RPSC exam syllabus index + official recruitment notice. Record the complete stage, common paper and subject-specific paper count. Check if School Education, Sanskrit Education or other department variants require separate exam tracks.

## 2. Make coverage complete
For 2nd Grade, Paper I (common GK) plus **all 10 Subject-specific Paper II** entries must be audited; never publish only English. Add each official document under `documents` with the correct stage, paper and subject. A few subjects may share one official combined PDF: record that fact rather than inventing a separate link.

## 3. Verify each PDF
Confirm actual source URL is HTTPS on `rpsc.rajasthan.gov.in/Static/Syllabus/*.pdf`, opens as PDF, matches official listing/title, is the latest applicable document, and that all corrigenda have been considered. Record `verified_at` (date of manual check), `edition_label` (issue/recruitment year), and `edition_status: current_verified` only if these checks passed.

## 4. Release one exam
Change `status` from `planned` to `published` ONLY when the full agreed paper/subject coverage for this exam is verified. Increment top-level `catalog_version`, update `published_at`. Update both `catalog/catalog.json` and `app/src/main/assets/catalog.json` for future APKs; only remote `catalog/catalog.json` is needed for already-installed devices after static hosting is configured.

Example document (illustrative schema ONLY, NOT a real PDF URL):

```json
{
  "id": "example-exam-stage-paper-subject-2026",
  "stage": "Written",
  "paper": "Paper II",
  "subject": "English",
  "edition_label": "Recruitment 2026 / issued YYYY-MM-DD",
  "verified_at": "YYYY-MM-DD",
  "edition_status": "current_verified",
  "official_url": "<COPY_VERIFIED_OFFICIAL_RPSC_HTTPS_PDF_URL>",
  "notes": ""
}
```

## 5. Test and publish
```bash
python3 scripts/validate_catalog.py
python3 scripts/test_catalog.py
python3 scripts/check_links.py  # Network required; manual review if warnings
```

App downloads official documents directly to the user's device. It does not host/republish RPSC PDF content.
