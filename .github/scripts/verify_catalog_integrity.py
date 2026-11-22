#!/usr/bin/env python3
"""Check the selected modules and locally pinned PNG logos before publishing.

This script deliberately does not fetch anything from the network.
"""
import ast
import json
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PUBLISHER = ROOT / ".github/scripts/publish_index.py"
LOGOS = ROOT / "assets/source-logos"
PNG_HEADER = b"\x89PNG\r\n\x1a\n"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
RETIRED = {"epsilonscan", "softepsilonscan", "bananascan", "crunchyscan"}


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as fh:
        header = fh.read(24)
    if len(header) != 24 or not header.startswith(PNG_HEADER) or header[12:16] != b"IHDR":
        raise AssertionError(f"Not a valid PNG header: {path}")
    return struct.unpack(">II", header[16:24])


def expected_modules() -> set[str]:
    match = re.search(
        r"^EXPECTED_MODULES\s*=\s*(\{.*?\})",
        PUBLISHER.read_text(encoding="utf-8"),
        re.MULTILINE | re.DOTALL,
    )
    if not match:
        raise AssertionError("Cannot parse EXPECTED_MODULES")
    return set(ast.literal_eval(match.group(1)))


def main() -> None:
    expected = expected_modules()
    actual = {
        item.parent.relative_to(ROOT).as_posix()
        for item in ROOT.glob("src/*/*/build.gradle.kts")
    }
    assert expected == actual, (
        f"Publisher modules differ from checkout: missing={expected - actual}, extra={actual - expected}"
    )
    assert len(expected) == 30, f"Expected 30 selected extensions, found {len(expected)}"
    assert not RETIRED.intersection({Path(x).name for x in expected}), "Retired source reintroduced"
    logo_files = {f.name for f in LOGOS.glob("*-icon.png")}
    needed = {f"{Path(module).name}-icon.png" for module in expected}
    assert logo_files == needed, (
        f"Unexpected local logos: missing={needed - logo_files}, extra={logo_files - needed}"
    )
    audit = json.loads((LOGOS / "logo-audit.json").read_text(encoding="utf-8"))
    assert {entry["name"] for entry in audit} == {
        Path(module).name for module in expected
    }, "Logo audit does not cover exactly the published modules"
    for module in expected:
        slug = Path(module).name
        source = ROOT / module
        logo = LOGOS / f"{slug}-icon.png"
        assert png_size(logo) == (512, 512), f"Published logo must be 512x512: {logo}"
        assert logo.stat().st_size < 4_000_000, f"Logo too large: {logo}"
        for density, dimension in DENSITIES.items():
            icon = source / f"res/mipmap-{density}/ic_launcher.png"
            assert icon.is_file(), f"Missing Android icon: {icon}"
            assert png_size(icon) == (dimension, dimension), f"Bad Android icon dimensions: {icon}"
    print(f"OK: {len(expected)} extension modules, local pinned 512px logos, five Android densities")
    print("OK: no retired sources, unapproved logo files or network image fetches")


if __name__ == "__main__":
    main()
