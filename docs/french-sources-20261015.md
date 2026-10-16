# French sources added on 2026-10-15

Five source modules and their launcher icons were copied from
`keiyoushi/extensions-source` commit
`58fd63ffeee7631436425748c01c1a727b0b6703` under the repository license.
Their implementations and icons are stored locally; no upstream checkout is
needed to build them. Existing package and source identities are preserved.
Android labels and published APK filenames use the existing MAKOFF branding.

| Module | Site | Language |
| --- | --- | --- |
| hentaiorigines | https://hentai-origines.com | fr |
| scanhentaimenu | https://x-manga.org | fr |
| histoiredhentai | https://hhentai.fr | fr |
| ortegascans | https://ortegascans.fr | fr |
| lelmanga | https://www.lelmanga.com | fr |

The source-specific minor-themed category filter was omitted from the import.
Shared engines and existing sources are unchanged.

## Validation limits

Release compilation, lint, APK labels, source languages, versions and signing
are checked by the repository workflows. This does not establish successful
reading on a user's Android device. This environment returned an unavailable
stub for the first four sites, so their live reader flows could not be tested.
Lelmanga returned its MangaThemesia catalogue HTML.

## CrunchyScan remains pending

`https://crunchyscan.st` returned a Cloudflare HTTP 403 challenge. No matching
current implementation was found in the upstream repository. An older parser
for `.fr` is marked broken; a third-party implementation labelled CrunchyScan
actually redirects to Lelmanga. Neither was imported. A real catalogue,
series and reader HTML response is needed before a correct `.st` source can
be implemented. No placeholder or incorrectly routed APK is published.
