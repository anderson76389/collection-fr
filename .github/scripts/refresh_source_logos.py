#!/usr/bin/env python3
"""Generate local 512px logos for all published Mihon sources, then Android mipmaps."""
import ast
import io
import json
import re
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlsplit

import requests
from PIL import Image, ImageFilter, ImageOps, UnidentifiedImageError

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'assets/source-logos'
SESSION = requests.Session()
SESSION.headers['User-Agent'] = 'Mozilla/5.0 CollectionFR/1.0 (icon maintenance)'
SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
Image.MAX_IMAGE_PIXELS = 16_000_000


class IconLinks(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.manifests = []

    def handle_starttag(self, tag, attrs):
        d = dict(attrs)
        if tag == 'link':
            rel = d.get('rel', '').lower()
            if 'manifest' in rel:
                self.manifests.append(d.get('href', ''))
            elif 'icon' in rel:
                self.links.append((d.get('href', ''), 60))
        if tag == 'img' and any('logo' in d.get(k, '').lower() for k in ('alt', 'id', 'class')):
            self.links.append((d.get('src', ''), 20))


def remote(url, max_size=8_000_000):
    try:
        with SESSION.get(url, timeout=(4, 6), stream=True) as response:
            response.raise_for_status()
            content = bytearray()
            for chunk in response.iter_content(65536):
                content.extend(chunk)
                if len(content) > max_size:
                    return None
            return bytes(content), response.url
    except (requests.RequestException, ValueError):
        return None


def allowed(base, path):
    url = urljoin(base, path)
    host = urlsplit(url).hostname or ''
    return url if urlsplit(url).scheme in ('http', 'https') and host not in ('', 'localhost') and not host.endswith(('.local', '.internal')) else None


def open_image(content):
    try:
        if b'<svg' in content[:512]:
            import cairosvg
            content = cairosvg.svg2png(bytestring=content, output_width=1024, output_height=1024)
        with Image.open(io.BytesIO(content)) as image:
            w, h = image.size
            if min(w, h) < 64 or max(w, h) > 4096 or max(w, h) / min(w, h) > 2.3:
                return None
            return ImageOps.exif_transpose(image).convert('RGBA')
    except (ImportError, OSError, ValueError, UnidentifiedImageError, Image.DecompressionBombError):
        return None


def official_icon(site, preferred=None):
    urls = [(preferred, 200)] if preferred and preferred.startswith('https://') else []
    page = remote(site, 500_000)
    base = page[1] if page else site
    if page:
        parser = IconLinks()
        try:
            parser.feed(page[0].decode('utf-8', 'replace'))
        except ValueError:
            pass
        urls += [(allowed(base, url), priority) for url, priority in parser.links]
        for manifest in parser.manifests[:2]:
            url = allowed(base, manifest)
            result = remote(url, 100_000) if url else None
            if result:
                try:
                    for icon in json.loads(result[0]).get('icons', []):
                        urls.append((allowed(result[1], icon.get('src', '')), 90))
                except (ValueError, TypeError, AttributeError):
                    pass
    urls += [(allowed(base, path), score) for path, score in [
        ('/android-chrome-512x512.png', 70), ('/icon-512.png', 70),
        ('/apple-touch-icon.png', 45), ('/favicon.ico', 0),
    ]]
    best = None
    seen = set()
    for url, priority in urls[:24]:
        if url and any(token in url.lower() for token in ('challenge-assets', '/cdn-cgi/', 'challenge-platform')):
            continue
        if not url or url in seen:
            continue
        seen.add(url)
        result = remote(url)
        image = open_image(result[0]) if result else None
        if image is None:
            continue
        candidate = (min(image.size) + priority, image, result[1])
        if best is None or candidate[0] > best[0]:
            best = candidate
        if candidate[0] >= 1300:
            break
    return (best[1], best[2]) if best else None


def square(image):
    if image.size == (512, 512):
        return image
    centered = ImageOps.contain(image, (512, 512), Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    canvas.alpha_composite(centered, ((512 - centered.width) // 2, (512 - centered.height) // 2))
    return canvas


def store(image, path):
    output = io.BytesIO()
    image.save(output, 'PNG', optimize=True)
    blob = output.getvalue()
    if not path.exists() or path.read_bytes() != blob:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(blob)


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    publisher = (ROOT / '.github/scripts/publish_index.py').read_text('utf-8')
    matching = re.search(r'EXPECTED_MODULES\s*=\s*(\{.*?\})', publisher, re.S)
    if not matching:
        raise RuntimeError('No EXPECTED_MODULES found in publisher')
    modules = sorted(ast.literal_eval(matching.group(1)))
    previous = ASSETS / 'icon-sources.json'
    provenance = json.loads(previous.read_text('utf-8')) if previous.exists() else []
    known = {Path(entry['file']).name: entry.get('source', '') for entry in provenance}
    audit = []
    for module in modules:
        folder = ROOT / module
        slug = folder.name
        target = ASSETS / f'{slug}-icon.png'
        src = folder / 'res/mipmap-xxxhdpi/ic_launcher.png'
        baseline = open_image(src.read_bytes())
        if baseline is None:
            raise ValueError(f'Invalid or missing base icon: {src}')
        selected, method, origin = baseline, 'upscaled-legacy', str(src.relative_to(ROOT))
        existing = open_image(target.read_bytes()) if target.exists() else None
        if existing is not None and min(existing.size) >= 500 and slug != 'mangadex':
            selected, method, origin = existing, 'preserved-local', str(target.relative_to(ROOT))
        else:
            gradle = (folder / 'build.gradle.kts').read_text('utf-8')
            match = re.search(r'baseUrl\s*=\s*"(https?://[^" ]+)"', gradle)
            if match:
                found = official_icon(match.group(1), known.get(target.name))
                if found and min(found[0].size) > min(baseline.size):
                    selected, origin = found
                    method = 'original-official'
        raw_size = list(selected.size)
        icon512 = square(selected)
        if method == 'upscaled-legacy':
            icon512 = icon512.filter(ImageFilter.UnsharpMask(radius=0.9, percent=85, threshold=3))
        store(icon512, target)
        if method != 'upscaled-legacy':
            for density, pixels in SIZES.items():
                mipmap = folder / f'res/mipmap-{density}/ic_launcher.png'
                if mipmap.exists():
                    store(icon512.resize((pixels, pixels), Image.Resampling.LANCZOS), mipmap)
        audit.append({'name': slug, 'size': [512, 512], 'original_size': raw_size,
                      'method': method, 'source': origin,
                      'note': 'Upscaling cannot restore details missing from the original.' if method == 'upscaled-legacy' else ''})
        print(f'{slug}: {method} ({raw_size[0]}x{raw_size[1]})', flush=True)
    (ASSETS / 'logo-audit.json').write_text(json.dumps(audit, indent=2, ensure_ascii=False) + '\n', 'utf-8')
    if len(audit) != 31 or any(not (ASSETS / f"{d['name']}-icon.png").exists() for d in audit):
        raise RuntimeError('Incomplete published logo set')
    print(f'Validated {len(audit)} local 512x512 logos')


if __name__ == '__main__':
    main()
