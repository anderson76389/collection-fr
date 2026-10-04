"""Record public chapter-link markup for a bounded Scan-Manga diagnostic."""
import json
import re
import urllib.request
import urllib.error

UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36'
urls = ['https://www.scan-manga.com/14176-82059/High-Class-Society.html', 'https://bqj.scan-manga.com/search/quick.json?term=High-Martial&16']
for url in urls:
    try:
        request = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept-Language': 'fr-FR,fr;q=0.9', 'Referer': 'https://www.scan-manga.com/'})
        with urllib.request.urlopen(request, timeout=30) as response:
            html = response.read().decode('utf-8', errors='replace')
            print('RESPONSE', response.status, response.url, 'bytes', len(html))
        if 'quick.json' in url:
            print('SEARCH', html[:6000])
        else:
            for pattern in [r'.{0,100}class=["\'][^"\']*chapt_m[^"\']*["\'].{0,4000}', r'.{0,200}404\.html.{0,300}', r'<script[^>]+src=["\']([^"\']+)']:
                print('MATCHES', pattern, json.dumps(re.findall(pattern, html, re.S)[:3], ensure_ascii=False))
    except Exception as exc:
        print('ERROR', type(exc).__name__, str(exc))
