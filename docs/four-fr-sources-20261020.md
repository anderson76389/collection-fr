# French catalogue fixes — 2026-10-20

## Changes

- AnimeSama 1.4.21: support redesigned catalogue cards, pagination and detail selectors. Ignore empty placeholders instead of dereferencing missing elements. Selector changes adapted from upstream commit d86850c24c56fd9b70be5166cf5961842eb923a2; the existing 1.4 API, identity and chapter reader remain intact.
- Astral-Manga 1.4.52: return stable cover identifiers immediately. Resolve the site's signed image URL only when Mihon requests that cover through the source client. The catalogue no longer waits for one signing request per entry. Preserve image headers, rate limiting, ordinary URLs and chapter handling.
- Poseidon Scans 1.4.54: use the full `/series?sortBy=popular&page=N` catalogue instead of the small homepage selection. Parse pagination and original cover paths; ignore incomplete cards.
- Raijin Scans 1.4.71: use the site's paginated `/api/manga?page=N` catalogue instead of the homepage selection. The site API uses its default catalogue order, not a documented popularity ranking.

## Verification

Live HTTP checks on October 20 returned 200 for AnimeSama's catalogue, recent page and detail page; Astral's catalogue and cover signing endpoint; Poseidon's popular catalogue pages 1 and 2; and Raijin's API pages 1 and 2.

- AnimeSama catalogue: 48 redesigned cards and no legacy `card-title` elements. The unchanged chapter-count API returns 1,197 chapters for One Piece and 202 for Solo Leveling. The 07 Ghost scan page loads, but the site API itself reports that work as missing; fixing catalogue selectors cannot restore content missing from the site backend.
- Astral catalogue: 12 entries, total 68. A sampled cover signing request took 7.13 seconds; the returned image subsequently returned HTTP 200 with PNG bytes. This measurement is a single request, not an end-to-end Mihon benchmark.
- Poseidon: 20 distinct cards on each of pages 1 and 2, no overlap between those samples, and working next-page links.
- Raijin: 28 entries and `hasNextPage=true` on each sample. One title overlaps the samples fetched at different times; the site can reorder entries as updates arrive.

AGP enables unit tests for the debug build type by default; CI runs those tasks separately from release compilation and lint. Nine JVM regression tests cover redesigned and legacy AnimeSama cards, recent URL normalization, Poseidon cards/pagination, deferred Astral cover signing, exact key/header preservation, ordinary requests and signing failures. GitHub Actions runs these tests, builds every retained APK, verifies local API checksums and lints release variants.

No Android device is attached: these checks do not claim a completed reading session in the user's Mihon installation. No other source, shared library, source identity, language, signing key or dependency repository is changed.
