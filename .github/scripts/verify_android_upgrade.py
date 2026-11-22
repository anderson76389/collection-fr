#!/usr/bin/env python3
"""Compare every Mihon extension across two releases, then optionally test adb upgrades.

Old and current indexes/APKs come from published GitHub releases. No secrets needed.
Mihon's reading list is in Mihon's own database; this does NOT claim to test it.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import unquote, urlsplit


def command(*args: str) -> str:
    proc = subprocess.run(args, text=True, capture_output=True, check=False, timeout=120)
    if proc.returncode != 0:
        raise RuntimeError(
            f"Command failed {args[0]}: {proc.stdout[-600:]} {proc.stderr[-600:]}"
        )
    return proc.stdout.strip()


def tools():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    dirs = sorted((sdk / "build-tools").glob("*")) if sdk.is_dir() else []
    if not dirs:
        raise RuntimeError("Android SDK build-tools not found")
    folder = dirs[-1]
    apksigner = folder / "apksigner"
    aapt = folder / "aapt"
    assert apksigner.is_file() and aapt.is_file(), "Android APK inspection tools missing"
    return str(apksigner), str(aapt)


def get_asset(root: Path, record: dict, field: str, expected_repo: str, expected_tag: str) -> Path:
    url = urlsplit(record["resources"][field])
    parts = url.path.split("/")
    assert url.scheme == "https" and url.netloc == "github.com", "Unexpected APK host"
    assert parts[1:5] == expected_repo.split("/") + ["releases", "download"], url.path
    assert parts[5] == expected_tag, f"Wrong release: {url.path}"
    assert len(parts) == 7, f"Unexpected APK URL: {url.path}"
    name = unquote(parts[6])
    assert name == Path(name).name and name, "Unexpected asset path"
    file = root / name
    assert file.is_file() and file.stat().st_size > 512, f"Missing release asset: {name}"
    return file


def compare(old_index: dict, new_index: dict, old_dir: Path, new_dir: Path, repo: str,
            old_tag: str, new_tag: str) -> list[dict]:
    assert old_index["signingKey"] == new_index["signingKey"], "Repository signing key changed"
    expected_cert = new_index["signingKey"].lower()
    assert re.fullmatch(r"[a-f0-9]{64}", expected_cert), "Invalid signing certificate digest"
    old = {r["packageName"]: r for r in old_index["extensionList"]["extensions"]}
    new = {r["packageName"]: r for r in new_index["extensionList"]["extensions"]}
    assert len(old) == len(new) == 30 and old.keys() == new.keys(), (
        "The published package set changed; this upgrade test needs matching package sets"
    )
    apksigner, aapt = tools()
    entries = []
    for pkg in sorted(old):
        prior, current = old[pkg], new[pkg]
        old_ids = {str(s["id"]) for s in prior["sources"]}
        new_ids = {str(s["id"]) for s in current["sources"]}
        assert old_ids == new_ids, f"Source IDs changed for {pkg}"
        previous_code, next_code = int(prior["versionCode"]), int(current["versionCode"])
        assert previous_code <= next_code, f"Downgrade detected for {pkg}"
        old_apk = get_asset(old_dir, prior, "apkUrl", repo, old_tag)
        new_apk = get_asset(new_dir, current, "apkUrl", repo, new_tag)
        for label, apk, expected_version in (
            ("old", old_apk, previous_code), ("new", new_apk, next_code)
        ):
            output = command(apksigner, "verify", "--print-certs", str(apk))
            match = re.search(
                r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", output
            )
            actual = match.group(1).replace(":", "").lower() if match else "MISSING"
            assert actual == expected_cert, (
                f"{pkg} {label}: signing certificate mismatch on {apk.name}; "
                f"expected={expected_cert}, actual={actual}; "
                f"apksigner-output={output[:1000]!r}"
            )
            details = command(aapt, "dump", "badging", str(apk))
            found = re.search(
                r"^package: name='([^']+)' versionCode='(\d+)'", details, re.MULTILINE
            )
            assert found and found.group(1) == pkg, f"Unexpected Android package: {apk}"
            assert int(found.group(2)) == expected_version, (
                f"{pkg} {label}: versionCode does not match Mihon index"
            )
        entries.append({
            "package": pkg,
            "old_version": previous_code,
            "new_version": next_code,
            "source_ids": sorted(old_ids),
            "old_apk": old_apk.name,
            "new_apk": new_apk.name,
            "signature_sha256": expected_cert,
        })
        print("VERIFIED", pkg, previous_code, "->", next_code, flush=True)
    return entries


def install_updates(entries: list[dict], old_dir: Path, new_dir: Path):
    adb = shutil.which("adb")
    if not adb:
        raise RuntimeError("Android Debug Bridge (adb) not installed")
    command(adb, "wait-for-device")
    command(adb, "shell", "getprop", "sys.boot_completed")
    for i, entry in enumerate(entries, 1):
        pkg = entry["package"]
        # adb install -r uses Android's in-place upgrade path, never uninstall.
        command(adb, "install", "-r", str(old_dir / entry["old_apk"]))
        command(adb, "install", "-r", str(new_dir / entry["new_apk"]))
        current = command(adb, "shell", "dumpsys", "package", pkg)
        installed = re.search(r"\bversionCode=(\d+)", current)
        assert installed and int(installed.group(1)) == entry["new_version"], (
            f"{pkg}: installed Android versionCode mismatch"
        )
        print(f"ADB_UPDATE_OK {i}/{len(entries)} {pkg}", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--old-index", type=Path, required=True)
    parser.add_argument("--new-index", type=Path, required=True)
    parser.add_argument("--old-apks", type=Path, required=True)
    parser.add_argument("--new-apks", type=Path, required=True)
    parser.add_argument("--old-tag", required=True)
    parser.add_argument("--new-tag", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--emulator", action="store_true")
    parser.add_argument("--report", type=Path, default=Path("upgrade-test-report.json"))
    args = parser.parse_args()
    old_index = json.loads(args.old_index.read_text(encoding="utf-8"))
    new_index = json.loads(args.new_index.read_text(encoding="utf-8"))
    report = {
        "repository": args.repository,
        "old_tag": args.old_tag,
        "new_tag": args.new_tag,
        "signature": new_index["signingKey"],
        "method": "metadata, signed APK verification and Android in-place installation",
    }
    entries = compare(
        old_index, new_index, args.old_apks, args.new_apks, args.repository,
        args.old_tag, args.new_tag,
    )
    report["packages"] = entries
    report["metadata_checked"] = len(entries)
    report["emulator_installs"] = 0
    if args.emulator:
        install_updates(entries, args.old_apks, args.new_apks)
        report["emulator_installs"] = len(entries)
    report["mihon_library_migration_tested"] = False
    report["note"] = (
        "The Mihon manga library/history is stored by Mihon, not by extensions. "
        "This verifies APK update acceptance and stable source identities, "
        "not the user's personal Mihon database or live reader functionality."
    )
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print(f"PASS: verified {len(entries)} signed updates; emulator={args.emulator}")


if __name__ == "__main__":
    main()
