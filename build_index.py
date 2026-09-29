#!/usr/bin/env python3
"""Build Collection FR's Mihon store from Keiyoushi's published extensions."""
import json
import urllib.request
from pathlib import Path

UPSTREAM_INDEX = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json"
STORE_NAME = "Collection FR"
STORE_BADGE = "CFR"
STORE_WEBSITE = "https://github.com/anderson76389/collection-fr"
REPO_JSON = {
    "index_v2": "https://raw.githubusercontent.com/anderson76389/collection-fr/main/index.json",
    "meta": {
        "name": STORE_NAME,
        "shortName": STORE_BADGE,
        "website": STORE_WEBSITE,
    },
}
PACKAGE_NAMES = [
    "eu.kanade.tachiyomi.extension.fr.animesama",
    "eu.kanade.tachiyomi.extension.fr.astralmanga",
    "eu.kanade.tachiyomi.extension.fr.dassouscan",
    "eu.kanade.tachiyomi.extension.fr.japscan",
    "eu.kanade.tachiyomi.extension.fr.mangascantrad",
    "eu.kanade.tachiyomi.extension.fr.mangasoriginesfr",
    "eu.kanade.tachiyomi.extension.fr.rimuscans",
    "eu.kanade.tachiyomi.extension.fr.scanmanga",
    "eu.kanade.tachiyomi.extension.fr.scanreader",
    "eu.kanade.tachiyomi.extension.fr.scanvf",
    "eu.kanade.tachiyomi.extension.fr.sushiscanfr"
]

def main():
    request = urllib.request.Request(
        UPSTREAM_INDEX,
        headers={"User-Agent": "Collection-FR-Mihon-Index/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        upstream = json.load(response)

    by_package = {
        extension["packageName"]: extension
        for extension in upstream["extensionList"]["extensions"]
    }
    missing = sorted(set(PACKAGE_NAMES) - by_package.keys())
    if missing:
        raise SystemExit("Keiyoushi index no longer contains: " + ", ".join(missing))

    extensions = [by_package[package] for package in PACKAGE_NAMES]
    extensions.sort(key=lambda extension: extension["name"].casefold())

    signing_key = upstream["signingKey"]
    store = {
        "name": STORE_NAME,
        "badgeLabel": STORE_BADGE,
        "signingKey": signing_key,
        "contact": {
            "website": STORE_WEBSITE,
            "discord": None,
        },
        "extensionList": {
            "extensions": extensions,
        },
    }
    repo = {
        **REPO_JSON,
        "meta": {
            **REPO_JSON["meta"],
            "signingKeyFingerprint": signing_key,
        },
    }
    directory = Path(__file__).parent
    (directory / "index.json").write_text(
        json.dumps(store, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (directory / "repo.json").write_text(
        json.dumps(repo, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(extensions)} extensions")

if __name__ == "__main__":
    main()
