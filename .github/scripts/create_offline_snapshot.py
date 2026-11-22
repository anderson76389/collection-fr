#!/usr/bin/env python3
"""Export a self-contained, SHA-256 verified offline copy of public Collection FR data.

Includes all Git refs/history in a bundle plus every APK/JAR referenced by the
snapshot's own Mihon index. Never reads/signs/exports Android keystores or secrets.
"""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from urllib.parse import unquote, urlsplit


REPO = os.environ.get("GITHUB_REPOSITORY", "anderson76389/collection-fr")
OUT = Path(os.environ.get("BACKUP_OUTPUT_DIR", "offline-export")).resolve()
STAMP = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d-%H%M%S")


def cmd(*args, capture=False):
    result = subprocess.run(
        args, check=True, text=True, stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None, timeout=7200,
    )
    return result.stdout.strip() if capture else None


def git_bundle(mirror: Path, archive: Path) -> dict:
    cmd("git", "clone", "--mirror", "--quiet", f"https://github.com/{REPO}.git", str(mirror))
    ref = {}
    for branch in ("main", "repo"):
        ref[branch] = cmd(
            "git", "--git-dir=" + str(mirror), "rev-parse", f"refs/heads/{branch}",
            capture=True,
        )
    bundle = archive / "collection-fr.bundle"
    cmd("git", "--git-dir=" + str(mirror), "bundle", "create", str(bundle), "--all")
    cmd("git", "--git-dir=" + str(mirror), "bundle", "verify", str(bundle))
    refs = cmd("git", "bundle", "list-heads", str(bundle), capture=True)
    for branch in ("main", "repo"):
        assert f"refs/heads/{branch}" in refs, f"Missing branch in Git bundle: {branch}"
    with tempfile.TemporaryDirectory(prefix="collection-fr-restore-") as temp:
        restored = Path(temp) / "restored"
        cmd("git", "clone", "--quiet", "--branch", "main", str(bundle), str(restored))
        assert (restored / ".github/workflows/build-and-publish.yml").is_file()
    return ref


def index_from_mirror(mirror: Path, archive: Path) -> list[dict]:
    for filename in ("index.json", "repo.json"):
        contents = cmd(
            "git", "--git-dir=" + str(mirror), "show", f"refs/heads/repo:{filename}",
            capture=True,
        )
        (archive / filename).write_text(contents + "\n", encoding="utf-8")
    index = json.loads((archive / "index.json").read_text(encoding="utf-8"))
    extensions = index["extensionList"]["extensions"]
    assert len(extensions) == 30, f"Unexpected published extension count: {len(extensions)}"
    assert len({x["packageName"] for x in extensions}) == 30
    assert len(index["signingKey"]) == 64
    return extensions


def release_downloads(archive: Path, extensions: list[dict]) -> list[str]:
    tags = set()
    expected_files = set()
    for ext in extensions:
        for kind in ("apkUrl", "jarUrl"):
            url = urlsplit(ext["resources"][kind])
            prefix = f"/{REPO}/releases/download/"
            assert url.scheme == "https" and url.netloc == "github.com"
            assert url.path.startswith(prefix), url.path
            remainder = url.path[len(prefix):].split("/")
            assert len(remainder) == 2, url.path
            tag, name = remainder[0], unquote(remainder[1])
            assert tag and name == Path(name).name and name
            assert name.endswith(".apk" if kind == "apkUrl" else ".jar")
            tags.add(tag)
            expected_files.add((tag, name))
    for tag in sorted(tags):
        folder = archive / "releases" / tag
        folder.mkdir(parents=True, exist_ok=True)
        cmd("gh", "release", "download", tag, "--repo", REPO, "--dir", str(folder))
    for tag, name in sorted(expected_files):
        asset = archive / "releases" / tag / name
        assert asset.is_file() and asset.stat().st_size > 512, (
            f"Indexed release file missing: {tag}/{name}"
        )
    assert len(expected_files) == 60, f"Expected 60 APK/JAR URLs, got {len(expected_files)}"
    return sorted(tags)


def hashes(archive: Path) -> None:
    lines = []
    for path in sorted(archive.rglob("*")):
        if not path.is_file() or path.name == "SHA256SUMS.txt":
            continue
        relative = path.relative_to(archive).as_posix()
        assert not relative.lower().endswith((".jks", ".keystore", ".p12")), (
            "Signing secret detected in public backup"
        )
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f"{digest}  {relative}")
    assert len(lines) >= 65, f"Incomplete backup file inventory: {len(lines)}"
    manifest = archive / "SHA256SUMS.txt"
    manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
    for line in lines:
        digest, name = line.split("  ", 1)
        assert hashlib.sha256((archive / name).read_bytes()).hexdigest() == digest


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="collection-fr-offline-") as temporary:
        work = Path(temporary)
        mirror = work / "mirror.git"
        archive = work / "snapshot"
        archive.mkdir()
        branches = git_bundle(mirror, archive)
        packages = index_from_mirror(mirror, archive)
        tags = release_downloads(archive, packages)
        info = {
            "repository": REPO,
            "created_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "commits": branches,
            "tags": tags,
            "extensions": len(packages),
            "includes_git_history": True,
            "includes_mihon_index": True,
            "includes_indexed_apks_and_jars": True,
            "contains_android_signing_key": False,
            "note": "Signing keystore and passwords must be backed up separately offline.",
        }
        (archive / "BACKUP_INFO.json").write_text(
            json.dumps(info, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        (archive / "RESTAURATION.txt").write_text(
            "SAUVEGARDE COLLECTION FR\n\n"
            "1. Decompresser cette archive sur un support personnel hors GitHub.\n"
            "2. Depuis le dossier decompresse, verifier : sha256sum -c SHA256SUMS.txt\n"
            "3. Restaurer l'historique et le code :\n"
            "   git clone collection-fr.bundle collection-fr-restaure\n"
            "   cd collection-fr-restaure && git checkout main\n"
            "4. L'index Mihon est index.json, repo.json ; APK/JAR dans releases/<tag>.\n"
            "5. Conserver des copies sur deux supports distincts, dont un hors ligne.\n"
            "IMPORTANT : le keystore .jks original et ses mots de passe ne se trouvent\n"
            "pas dans cette archive. Sauvegardez-les SEPAREMENT, sous chiffrement.\n",
            encoding="utf-8",
        )
        lines = []
        for path in sorted(archive.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(archive).as_posix()
            assert not rel.lower().endswith((".jks", ".keystore", ".p12"))
            lines.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {rel}")
        assert len(lines) >= 65, "Incomplete SHA-256 inventory"
        (archive / "SHA256SUMS.txt").write_text(
            "\n".join(lines) + "\n", encoding="utf-8"
        )
        for line in lines:
            digest, rel = line.split("  ", 1)
            assert hashlib.sha256((archive / rel).read_bytes()).hexdigest() == digest
        prefix = f"collection-fr-offline-{STAMP}"
        archive_path = shutil.make_archive(
            str(OUT / prefix), "gztar", root_dir=str(archive)
        )
        size = Path(archive_path).stat().st_size
        assert size > 100_000, "Backup unexpectedly small"
        (OUT / f"{prefix}.sha256").write_text(
            f"{hashlib.sha256(Path(archive_path).read_bytes()).hexdigest()}  "
            f"{Path(archive_path).name}\n", encoding="utf-8"
        )
        print(json.dumps({
            "backup": archive_path, "archive_bytes": size, "indexed_assets": 60,
            "branches": branches, "tags": tags, "manifest_entries": len(lines)
        }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
