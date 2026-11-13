#!/usr/bin/env python3
"""Print only selectors and response metadata for public catalogue and free chapter pages."""
import requests
from bs4 import BeautifulSoup

URLS = [
    ("solaris-catalog", "https://solaris-scans.fr/manga/"),
    ("solaris-detail", "https://solaris-scans.fr/manga/shadow-slave/"),
    ("solaris-reader", "https://solaris-scans.fr/manga/shadow-slave/chapitre-16/"),
    ("crunchy-catalog", "https://crunchyscan.st/catalogue"),
]
SELECTORS = [
    "article.solaris-catalog-card", "h2 a", "a[href^='/manga/']",
    ".sz-ri-info-card h1", "#swChapterGrid", "a.sz-chapter-card",
    "[data-hero-synopsis-full]", "#chapter_preloaded_images",
    "[data-page-img]", ".reading-content img.wp-manga-chapter-img",
    "img[alt*='Page']", "img[data-src]", "img[src]",
    "a[data-card-slug]", "a[data-chapter-slug]", "script#reader-history",
    ".cf-turnstile",
]
s = requests.Session()
s.headers.update({"User-Agent":"Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36", "Accept-Language":"fr-FR,fr;q=0.9"})
for label,url in URLS:
    try:
        r=s.get(url,timeout=20)
        soup=BeautifulSoup(r.text,"html.parser")
        print(label,"status",r.status_code,"url",r.url,"length",len(r.content),"type",r.headers.get("content-type"))
        print("selectors",[(sel,len(soup.select(sel))) for sel in SELECTORS if soup.select(sel)])
        for t in ["article","a","section"]:
            keys={}
            for el in soup.find_all(t,limit=1200):
                classstr=" ".join(el.get("class",[]))
                if (t=="article" or (t=="a" and "/manga/" in el.get("href","")) or (t=="section" and ("catalog" in classstr or "chapter" in classstr))):
                    keys[classstr]=keys.get(classstr,0)+1
            print("tag",t,"classes",sorted(keys.items(),key=lambda k:-k[1])[:12])
        scripts=soup.find_all("script")
        print("script_ids",[(a.get("id"),a.get("type"),len(a.get_text())) for a in scripts if a.get("id") or a.get("type")=="application/json"][:30])
        if "reader" in label:
            print("reader_image_sample",[(el.get("alt"), sorted(el.attrs.keys()),(el.get("src") or "")[:65]) for el in soup.select("img")[:8]])
    except Exception as e:
        print(label,"ERROR",type(e).__name__,str(e)[:300])
