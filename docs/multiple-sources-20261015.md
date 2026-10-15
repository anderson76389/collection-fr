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
add only the requested new modules; all prior 33 APK entries remain selected. The APK
language check requires exactly one `all` source for Comix, MangaDex and SayManhwa.
GitHub Actions compiles the retained APK set, runs release lint and verifies
labels, package versions and signatures before publication.

MangaDex public API access works from this environment. Comix returns a
Cloudflare challenge; SayManhwa returns an unavailable stub. Successful Android
reading is not claimed for those blocked sites.


## Extended request

- `src/all/flixnetcoffeemanga`: Flixnet (CoffeeManga), one `all` source. The
  supplied `https://flixnet.site/bato/` is an advertising iframe wrapper whose
  main iframe points to `https://www.coffeemanga.art/`, not Batoto. The user
  explicitly chose its currently displayed content. The source reads that
  public site directly, including its existing chapter redirects to ZazaManga;
  it does not run wrapper advertisements or claim to contain the Batoto catalogue.
- `src/fr/banchanscan`: copied from the same pinned upstream commit, version
  1.6.2 with page indices normalized to zero after ordering by `sort_order`.
- `src/fr/mangakawaii`: copied from that pinned upstream commit, version 1.6.2.
  Chapter pagination follows `data-ch-next`, uses the site's `fetch` request
  header, and avoids requesting nonexistent pages. Decimal chapter numbers
  are preserved. Licensed series receive an explicit unavailable message.
- `src/fr/bdplus`: new KeiSource scaffolded with `ext-bootstrap.py`, implemented
  against the current public catalogue JSON, series HTML and per-tome pages
  manifests. Popular uses catalogue ratings; search covers the full catalogue;
  latest uses the site's visible update list. No shared engine is changed.

There are now 39 selected modules. The new standalone icons are unmodified
PNG icons downloaded from their respective sites, stored locally. New French
modules remain `fr`; Flixnet, Comix, MangaDex and existing SayManhwa remain `all`.
All previous source files and libraries are preserved.

Live checks: Banchan home API returns 408 works, 388 published IDs, 21 chapters
and six page URLs on a sample. BDPlus catalogue contains 2486 series; a sample
series has 46 tomes and its first tome has 48 page URLs. CoffeeManga returns
catalogue/search HTML, 34 chapter links on a sample and reader image URLs.
MangaKawaii catalogue/search and initial chapter links are accessible, but its
reader and chapter pagination requests return HTTP 403 in this environment.
No end-to-end Android reading success is claimed.
