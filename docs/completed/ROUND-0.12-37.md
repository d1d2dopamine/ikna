# Round 37 — smaller README banners and blue badges

2026-10-12. A narrow README presentation update on top of the full Round 36 source checkpoint.

## Changes

- Both English and Russian header banners use a 600 px display width instead of 900 px. The original raster is unchanged; no fixed height is added, so its aspect ratio is preserved.
- All fourteen badges use the same blue, `#0078D4`, already present in the Windows badge. Both label and value backgrounds are explicitly blue. Release and build badges retain their live endpoints and text.
- README prose, navigation, download links, logos, alt text and ordering are unchanged.

## Verification

- Repository preflight completed: application version and database schema unchanged.
- `python3 tools/check_text.py` passed before and after the edit: 799 text files and seven localization tables.
- Parsed both HTML header sections: exactly two 600 px banners and fourteen badges with the same explicit color and labelColor.
- All 54 local README link/image references resolve. All HTML link destinations and all prose match Round 36.
- Byte comparison against Round 36 confirmed README was the only changed existing source file before this report and manifest refresh. Application code, image assets, signing material and prior stage reports are unchanged.

A local HTML preview was prepared with a Markdown renderer, but cloud Chromium could not start because this execution environment denied a socket operation. No rendered screenshot was obtained. Live GitHub rendering, external badge availability and mobile layout remain unverified. GitHub's normal responsive image styling is relied on to constrain the 600 px banner on narrow screens; no inline CSS or fixed height is added to the README.

This is a documentation-only stage. No application build, device test, CI run or publication was performed. The separate interface audit has not been applied to application code.

## Source delivery

The cumulative ZIP preserves every Round 36 source member and its corpus/sample exclusions. It adds this report and refreshes SOURCE-PACKAGE-MANIFEST.json. No corpora, real-card samples, local preview or browser screenshots are included. The archive remains below 20 MB.
