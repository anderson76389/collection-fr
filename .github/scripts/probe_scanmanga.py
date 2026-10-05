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
