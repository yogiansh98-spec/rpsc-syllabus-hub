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

## v1.1 secure review workflow (not live until configured)
The `admin/api` Vercel Functions add GitHub OAuth and session-cookie login restricted to GitHub user ID `340794886`. Changes create **draft Pull Requests**, not direct production changes. The PDF stage API accepts PDFs up to 2 MB and creates a separate draft PR with a proposed source file at `syllabus/hindi/<filename>`. This is **not** a Release upload, and does not activate the PDF in the catalog. Review the file, merge the PDF PR, attach the reviewed PDF to a GitHub Release using a reviewed release workflow, then set the actual Release PDF URL in the catalog draft and create a catalog PR.

Before exposing the UI as an operational authenticated admin:
1. Create a GitHub OAuth App. Callback: `https://<admin-host>/api/callback`. Use an HTTPS custom/production hostname and confirm the exact deployed origin.
2. Store **only in Vercel environment secrets**, never in GitHub/repo/HTML: `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`, `ADMIN_SESSION_SECRET` (at least 32 random characters). Set `ADMIN_BASE_URL` to the exact HTTPS origin. A new deployment is needed for new env variables.
3. Confirm the GitHub OAuth app's `public_repo` permission scope is acceptable; this requests access to public repositories accessible by the admin user. For production, prefer a fine-grained GitHub App restricted to this repo.
4. Configure Vercel Git integration for this specific repository (current integration was not installed). Review and merge the admin PR, deploy from the reviewed commit, and restrict access appropriately. Before config, login returns HTTP 503 and all write APIs fail closed.
5. Test wrong-owner login rejection, missing OAuth state, session expiration, missing/mismatched Origin, changed main catalog SHA/version, invalid PDF and malicious filenames, and CI checks; do not merge any generated PR automatically.

The browser displays an admin Login/Upload/Review UI but does not silently auto-release or auto-publish. The current catalog validator still requires current_verified for included documents, so a new draft PDF needs actual human verification before inclusion. Do not mark a source verified just to pass validation.

Known limits: no direct Release asset upload, no complete independent line-by-line translation verification, no rollback UI, and no live-to-app publish without reviewed merge plus mobile remote catalog configuration. The server-side validator checks structure and URL format, not the contents or existence of remote PDFs.
