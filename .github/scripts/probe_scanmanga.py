"""Compare public desktop/mobile reader responses without accounts or cookies."""
import json
from pathlib import Path
import re
import urllib.error
import urllib.request

OUT = Path('scanmanga-probe')
OUT.mkdir(exist_ok=True)
CHAPTER = '/lecture-en-ligne/High-Martial-World-One-Hand-to-Overwhelm-Three-Thousand-Emperors-Chapitre-70-FR_575344.html'
AGENTS = {
    'desktop': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36',
    'mobile': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36',
}
results = []
for host in ('www.scan-manga.com', 'm.scan-manga.com'):
    for agent, ua in AGENTS.items():
        result = {'host': host, 'agent': agent, 'requested': 'https://' + host + CHAPTER}
        request = urllib.request.Request(result['requested'], headers={
            'User-Agent': ua, 'Accept-Language': 'fr-FR,fr;q=0.9',
            'Referer': 'https://' + host + '/',
        })
        try:
            try:
                response = urllib.request.urlopen(request, timeout=20)
            except urllib.error.HTTPError as exc:
                response = exc
            with response:
                html = response.read().decode('utf-8', errors='replace')
                result.update(status=response.status, final_url=response.url, bytes=len(html))
            (OUT / (host + '-' + agent + '.html')).write_text(html)
            title = re.search(r'<title[^>]*>(.*?)</title>', html, re.S | re.I)
            result.update(title=title.group(1) if title else None,
                          has_chapter_id=bool(re.search(r'\b(?:const|var|let)\s+idc\s*=', html)),
                          script_urls=re.findall(r'<script[^>]+src=["\']([^"\']+)', html))
        except Exception as exc:
            result['error'] = type(exc).__name__ + ': ' + str(exc)
        results.append(result)
        print(json.dumps(result, ensure_ascii=False), flush=True)
(OUT / 'result.json').write_text(json.dumps(results, indent=2))

# Preserve the site's current public reader code for comparison with the extension.
for name in ('lel_head', 'lel', 'main'):
    url = 'https://static.scan-manga.com/js/' + name + '.js?vers=5.8'
    try:
        req = urllib.request.Request(url, headers={'User-Agent': AGENTS['desktop'], 'Referer': 'https://www.scan-manga.com/'})
        try:
            response = urllib.request.urlopen(req, timeout=20)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            body = response.read()
            print('PUBLIC_SCRIPT', name, response.status, len(body), flush=True)
        (OUT / (name + '.js')).write_bytes(body)
    except Exception as exc:
        print('PUBLIC_SCRIPT_ERROR', name, type(exc).__name__, str(exc), flush=True)

# Reproduce the public reader API with its documented-by-code fallback fingerprint.
import base64
import zlib

try:
    html = (OUT / 'www.scan-manga.com-desktop.html').read_text()
    packed = re.search(r'\}\(\s*"([^"]+)"\s*,\s*\d+\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*\d+\s*\)', html)
    encoded, mask, shift, radix = packed.groups()
    radix, shift = int(radix), int(shift)
    unpacked = ''.join(chr(int(''.join(str(mask.index(c)) for c in token), radix) - shift)
                       for token in encoded.split(mask[radix]) if token)
    sme = re.search(r"sme\s*=\s*'([^']+)'", unpacked).group(1)
    sml = re.search(r"sml\s*=\s*'([^']+)'", unpacked).group(1)
    chapter_id = re.search(r'const\s+idc\s*=\s*(\d+)', html).group(1)
    for connection in ('IC', 'cellular'):
        fp = base64.b64encode(json.dumps({'gpu': 'IC', 'connection': connection}, separators=(',', ':')).encode()).decode()
        body = json.dumps({'a': sme, 'b': sml, 'c': fp}, separators=(',', ':')).encode()
        headers = {'User-Agent': AGENTS['desktop'], 'Content-Type': 'application/json; charset=UTF-8',
                   'Accept': '*/*', 'Origin': 'https://www.scan-manga.com',
                   'Referer': 'https://www.scan-manga.com/', 'Source': 'https://www.scan-manga.com' + CHAPTER,
                   'Token': 'yf', 'Sec-Fetch-Site': 'same-site', 'Sec-Fetch-Mode': 'cors', 'Sec-Fetch-Dest': 'empty'}
        req = urllib.request.Request('https://bqj.scan-manga.com/lel/' + chapter_id + '.json', data=body, headers=headers)
        try:
            response = urllib.request.urlopen(req, timeout=20)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            raw = response.read()
            status = response.status
            print('READER_API', connection, status, len(raw), raw[:250].decode(errors='replace'), flush=True)
        (OUT / ('api-' + connection + '.txt')).write_bytes(raw)
        if status == 200 and not raw.startswith(b'{'):
            inflated = zlib.decompress(base64.b64decode(raw)).decode()
            encoded_json = inflated.removesuffix(format(int(chapter_id), 'x'))[::-1]
            payload = json.loads(base64.b64decode(encoded_json))
            print('READER_PAGES', connection, len(payload.get('p', {})), flush=True)
            first = next(iter(payload['p'].values()))
            image_url = 'https://{dN}/{s}/{v}/{c}/'.format(**payload) + first['f'] + '.' + first['e']
            with urllib.request.urlopen(urllib.request.Request(image_url, headers=headers), timeout=20) as image:
                data = image.read()
                print('READER_FIRST_IMAGE', image.status, image.headers.get('Content-Type'), len(data), data[:12].hex(), flush=True)
except Exception as exc:
    print('READER_API_ERROR', type(exc).__name__, str(exc), flush=True)
