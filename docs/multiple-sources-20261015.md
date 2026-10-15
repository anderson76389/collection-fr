# Multiple-only sources — 2026-10-15

SayManhwa already exists under `src/all/saymanhwa`, with one `all` source,
ID `1085765242853690038`, package `all.saymanhwa` and version 1.6.4.
Its files are unchanged.

Comix and MangaDex are copied, including assets and density icons, from
`keiyoushi/extensions-source` commit
`58fd63ffeee7631436425748c01c1a727b0b6703`, under the repository license.
All implementation files live in this repository; upstream is not fetched at build time.

- `src/all/comix`: one `all` source. Original `en.comix` package and source
  ID `7537715367149829912` are preserved; version 1.6.51 exceeds the previous
  published build. MAKOFF APK branding is applied by the existing build.
- `src/all/mangadex`: one `all` source, version 1.6.5 (previous French APK was
  1.6.4). Package `all.mangadex` and its former French source ID
  `4505830566611664829` are preserved so existing library entries remain linked.
  Language constraints are omitted from catalogue, latest, uploader, aggregate
  and chapter-feed requests in Multiple mode. `all` is never sent to MangaDex
  as an ISO translation language. Chapters display their translation language.
  The source uses current abstract/KSP metadata injection and regular DTO classes.
  Its custom JSON configuration is retained for upstream polymorphic serializers.

## Preservation and checks

No existing source module, `lib`, `lib-multisrc`, `vendor`, `core`, `common`,
`compiler`, `gradle`, or settings file is changed. Publication and APK verification
add only the two modules; all prior 33 APK entries remain selected. The APK
language check requires exactly one `all` source for Comix, MangaDex and SayManhwa.
GitHub Actions compiles the retained APK set, runs release lint and verifies
labels, package versions and signatures before publication.

MangaDex public API access works from this environment. Comix returns a
Cloudflare challenge; SayManhwa returns an unavailable stub. Successful Android
reading is not claimed for those blocked sites.
