# RPSC Hub Admin Draft Editor (v1)

Open `admin/index.html` in a current browser. It is a **local draft editor**, not an authenticated hosted admin service. It makes no write calls to GitHub or a production backend.

## Features
- Load the current main-branch catalog over HTTPS (requires internet), or import your own schema v2 JSON.
- Create/edit exam tracks and online categories, including the required `other` category.
- Edit subjects, names, departments, aliases, exam coverage and Paper PDF details.
- Independently track official RPSC PDF URLs and author-translated Hindi PDF release URLs.
- Run basic fail-closed structural checks, then export a draft JSON; never publish straight from the browser.
- No tokens, passwords, credentials, or privileged access are requested or stored.

## Publishing workflow
1. Open the catalog, edit a **copy**, and save your draft JSON.
2. Ensure every PDF subject/edition matches the **official RPSC syllabus** and note the verification date.
3. For independently translated Hindi PDFs: verify all topics, spelling, pages and translated meaning against the source. Upload reviewed PDFs to a GitHub Release **first**; do not enter a URL for a non-existent asset.
4. Prepare a GitHub PR to update **both** `catalog/catalog.json` and `app/src/main/assets/catalog.json` with the same approved JSON. Never overwrite existing data without reviewing its current changes.
5. Run `python3 scripts/validate_catalog.py` and `python3 scripts/test_catalog.py` and manually inspect the diff and source PDFs.
6. Merge only after review and passing checks. The mobile app will update its remote catalog only if its remote-catalog URL is configured.

## Limitations
- The editor does not upload PDF binaries, verify their full text, log in, publish automatically or host any backend.
- Local validation is intentionally not a substitute for the repository's Python checks and manual source review.
- New document entries start as `draft`; the catalog's current schema disallows draft documents in published catalog. Complete source verification before export.
- Public hosting of this page without a separate authenticated backend must **not** be represented as a secure admin panel. The shipped HTML only creates local drafts.

## Next step
After reviewing v1, consider a trusted server/GitHub App with granular authorization and a Pull Request publishing API. Do **not** embed GitHub access tokens in the HTML.
