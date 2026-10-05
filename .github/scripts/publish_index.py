#!/usr/bin/env python3
"""Publish selected French and multilingual extension APKs and the Mihon v2 index."""

import json
import os
import subprocess
from pathlib import Path

SOURCE_DIR = Path(os.environ["SOURCE_DIR"])
REPO_DIR = Path(os.environ["REPO_DIR"])
REPOSITORY = os.environ["GITHUB_REPOSITORY"]
COMMIT_SHA = os.environ["GITHUB_SHA"]
FINGERPRINT = os.environ["SIGNING_FINGERPRINT"].replace(":", "").lower()
if len(FINGERPRINT) != 64 or any(char not in "0123456789abcdef" for char in FINGERPRINT):
    raise ValueError("SIGNING_FINGERPRINT must be a SHA-256 certificate fingerprint")
RELEASE_TAG = COMMIT_SHA[:7]

EXPECTED_MODULES = {
    "src/fr/animesama",
    "src/fr/bananascan",
    "src/fr/astralmanga",
    "src/fr/dassouscan",
    "src/fr/japscan",
    "src/fr/mangascantrad",
    "src/fr/mangasoriginesfr",
    "src/fr/rimuscans",
    "src/fr/scanreader",
    "src/fr/scanvf",
    "src/fr/sushiscanfr",
    "src/fr/blossomscans",
    "src/fr/pantheonscan",
    "src/fr/softepsilonscan",
    "src/all/manhuarm",
    "src/fr/scanmanga",
    "src/fr/poseidonscans",
    "src/fr/raijinscans",
    "src/fr/banchanscan",
    "src/all/saymanhwa",
    "src/all/kagane",
}


def run_gh(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["gh", *args, "--repo", REPOSITORY],
        check=check,
        text=True,
        capture_output=True,
    )


def find_output(directory: Path, pattern: str, package: str) -> Path:
    matches = list(directory.glob(pattern))
    if not matches:
        raise FileNotFoundError(f"{package}: no file matching {pattern}")
    return sorted(matches)[0]


def main() -> None:
    info_files = sorted(SOURCE_DIR.glob("src/*/*/build/keiyoushi-source-info.json"))
    extensions = []
    assets = []
    outputs_by_package = {}

    for info_file in info_files:
        info = json.loads(info_file.read_text(encoding="utf-8"))
        module = info_file.parent.parent.relative_to(SOURCE_DIR).as_posix()
        if module not in EXPECTED_MODULES:
            continue

        package_name = info["packageName"]
        apk = find_output(info_file.parent, "outputs/apk/release/*.apk", package_name)
        jar = find_output(info_file.parent, "outputs/jar/release/*.jar", package_name)
        assets.extend([apk, jar])
        outputs_by_package[package_name] = (apk, jar)

        source_entries = []
        for source in info["sources"]:
            source_entries.append(
                {
                    "id": str(source["id"]),
                    "name": source["name"],
                    "language": source["lang"],
                    "homeUrl": source["baseUrl"],
                    **(
                        {"mirrorUrls": source["mirrorUrls"]}
                        if source.get("mirrorUrls")
                        else {}
                    ),
                }
            )

        extensions.append(
            {
                "name": info["name"],
                "packageName": package_name,
                "resources": {
                    "apkUrl": "",
                    "iconUrl": (
                        f"https://raw.githubusercontent.com/{REPOSITORY}/main/"
                        f"{module}/res/mipmap-xhdpi/ic_launcher.png"
                    ),
                    "jarUrl": "",
                },
                "extensionLib": str(info["extensionLib"]),
                "versionCode": str(info["versionCode"]),
                "versionName": str(info["versionName"]),
                "contentWarning": {
                    0: "CONTENT_WARNING_UNSPECIFIED",
                    1: "CONTENT_WARNING_SAFE",
                    2: "CONTENT_WARNING_MIXED",
                    3: "CONTENT_WARNING_NSFW",
                }.get(int(info["contentWarning"]), "CONTENT_WARNING_UNSPECIFIED"),
                "sources": source_entries,
            }
        )

    found_modules = {p.parent.parent.relative_to(SOURCE_DIR).as_posix() for p in info_files}
    found_modules &= EXPECTED_MODULES
    missing = EXPECTED_MODULES - found_modules
    if missing:
        raise RuntimeError(f"Build did not produce source metadata for: {', '.join(sorted(missing))}")

    extensions.sort(key=lambda item: item["packageName"])

    release = run_gh("release", "view", RELEASE_TAG, check=False)
    if release.returncode != 0:
        run_gh(
            "release",
            "create",
            RELEASE_TAG,
            "--title",
            f"Collection FR {RELEASE_TAG}",
            "--notes",
            f"Personal extension builds from {REPOSITORY}@{COMMIT_SHA}.",
        )

    run_gh(
        "release",
        "upload",
        RELEASE_TAG,
        *[str(asset) for asset in assets],
        "--clobber",
    )

    release_base = f"https://github.com/{REPOSITORY}/releases/download/{RELEASE_TAG}"
    for extension in extensions:
        apk, jar = outputs_by_package[extension["packageName"]]
        extension["resources"]["apkUrl"] = f"{release_base}/{apk.name}"
        extension["resources"]["jarUrl"] = f"{release_base}/{jar.name}"

    index = {
        "name": "Collection FR",
        "badgeLabel": "CFR",
        "signingKey": FINGERPRINT,
        "contact": {
            "website": f"https://github.com/{REPOSITORY}",
            "discord": None,
        },
        "extensionList": {"extensions": extensions},
    }

    (REPO_DIR / "index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    repo_config = {
        "index_v2": f"https://raw.githubusercontent.com/{REPOSITORY}/repo/index.json?build={COMMIT_SHA[:7]}",
        "meta": {
            "name": "Collection FR",
            "shortName": "CFR",
            "website": f"https://github.com/{REPOSITORY}",
            "signingKeyFingerprint": FINGERPRINT,
        },
    }
    (REPO_DIR / "repo.json").write_text(
        json.dumps(repo_config, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
