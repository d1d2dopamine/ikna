# Third-party UI fonts

Ikna ships its built-in typography inside the repository for both Android and desktop.
The build does **not** fetch fonts from the network. The source files are pinned and
verified here by their Git blob hashes, and the app never downloads fonts at runtime.

## Geologica

- Use: main interface face.
- Source: Google Fonts `ofl/geologica/Geologica[CRSV,SHRP,slnt,wght].ttf`.
- Source revision: `23e54b51ddffbc7713c583748e3bd86f62b1fa4a`.
- Source Git blob hash: `9e7771e32575873bba48b16b6ef1696b63087a2a`.
- Source size: 348640 bytes.
- Copyright: The Geologisk Project Authors.
- License: SIL Open Font License 1.1.

The checked-in runtime faces are static instances produced from that official variable
font with `CRSV=0`, `SHRP=0`, `slnt=0` and weights 400, 500 and 600. Their repository
resource names and Git blob hashes are:

- `geologica_regular.ttf` — 400 — `8c758f7e4ad82c6cb816180362d9ef0e349f8a06`
- `geologica_medium.ttf` — 500 — `8498eb3fb0486ae0bda35dc351b468572e13885a`
- `geologica_semibold.ttf` — 600 — `45393942c9b98275bc699f51c2b2694e7aa2b7e0`

Static instances are intentional. Compose Desktop's file-font loader uses the declared
weight to choose a face but does not apply a variable font's `wght` axis while loading
that file. Shipping 400/500/600 instances therefore keeps Android and desktop visually
aligned instead of letting the variable font fall back to its default axis.

## IBM Plex Mono

- Use: service labels and numeric/data readouts.
- Source: Google Fonts `ofl/ibmplexmono`.
- Source revision: `23e54b51ddffbc7713c583748e3bd86f62b1fa4a`.
- Files: `IBMPlexMono-Medium.ttf`, `IBMPlexMono-SemiBold.ttf`.
- Git blob hashes: `33c546f68b6007a45a3f10e845523abb2db25399`,
  `8f8998a139d8f61d3b96bdd3b512fca501a1f6b9`.
- Copyright: IBM Corp.; Reserved Font Name "Plex".
- License: SIL Open Font License 1.1.

The upstream font files retain their own copyright and license metadata. Do not replace
them without updating the pinned revision, hashes, attribution and visual checks together.
