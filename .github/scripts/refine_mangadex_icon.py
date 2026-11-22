#!/usr/bin/env python3
"""One-time, verified high-resolution upgrade for the pinned MangaDex logo."""
import hashlib
import io
import json
from pathlib import Path
from urllib.request import Request, urlopen

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
LOGOS = ROOT / "assets/source-logos"
MODULE = ROOT / "src/all/mangadex"
ORIGIN = "https://raw.githubusercontent.com/homarr-labs/dashboard-icons/main/png/manga-dex.png"
ORIGINAL_GIT_BLOB = "6b1daa7d49a48835bedaf45de615076c2ce13893"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def save_png(image: Image.Image, destination: Path) -> None:
    output = io.BytesIO()
    image.save(output, format="PNG", optimize=True)
    payload = output.getvalue()
    assert payload.startswith(b"\x89PNG\r\n\x1a\n")
    if not destination.is_file() or destination.read_bytes() != payload:
        destination.write_bytes(payload)


def main() -> None:
    request = Request(ORIGIN, headers={"User-Agent": "CollectionFR/1.0"})
    with urlopen(request, timeout=30) as response:
        contents = response.read(5_000_001)
    assert 0 < len(contents) < 5_000_000, "Unexpected MangaDex source file size"
    signature = hashlib.sha1(
        f"blob {len(contents)}\0".encode("utf-8") + contents
    ).hexdigest()
    assert signature == ORIGINAL_GIT_BLOB, "Icon source changed: refusing replacement"
    with Image.open(io.BytesIO(contents)) as image:
        image.load()
        assert min(image.size) >= 500, "Not a high-resolution icon"
        original_size = list(image.size)
        rgba = image.convert("RGBA")
    scaled = ImageOps.contain(rgba, (512, 512), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    canvas.alpha_composite(scaled, ((512 - scaled.width) // 2, (512 - scaled.height) // 2))
    save_png(canvas, LOGOS / "mangadex-icon.png")
    for density, dim in DENSITIES.items():
        icon = MODULE / f"res/mipmap-{density}/ic_launcher.png"
        assert icon.is_file(), f"Missing existing Android density {icon}"
        save_png(canvas.resize((dim, dim), Image.Resampling.LANCZOS), icon)

    audit_path = LOGOS / "logo-audit.json"
    audit = json.loads(audit_path.read_text("utf-8"))
    found = [item for item in audit if item["name"] == "mangadex"]
    assert len(found) == 1
    found[0].update({
        "size": [512, 512],
        "original_size": original_size,
        "method": "high-resolution-replacement",
        "source": ORIGIN,
        "note": "MangaDex cat-and-book mark distributed by Homarr Dashboard Icons (Apache-2.0); brand remains MangaDex",
    })
    audit_path.write_text(json.dumps(audit, indent=2, ensure_ascii=False) + "\n", "utf-8")

    provenance_path = LOGOS / "icon-sources.json"
    provenance = json.loads(provenance_path.read_text("utf-8"))
    matching = [item for item in provenance if item["file"] == "assets/source-logos/mangadex-icon.png"]
    assert len(matching) == 1, "MangaDex provenance missing"
    matching[0].update({
        "source": ORIGIN,
        "original_size": original_size,
        "published_size": [512, 512],
        "note": "Verified Git blob " + ORIGINAL_GIT_BLOB + "; Homarr Dashboard Icons Apache-2.0",
    })
    provenance_path.write_text(json.dumps(provenance, indent=2, ensure_ascii=False) + "\n", "utf-8")
    print("MangaDex: verified high-resolution source and updated 512px + five Android mipmaps")


if __name__ == "__main__":
    main()
