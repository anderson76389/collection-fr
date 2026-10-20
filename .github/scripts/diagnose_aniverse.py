import json, re, urllib.request, urllib.error
BASE = "https://aniverse.fr"
URLS = [
    "/api/manga?page=1",
    "/api/anime/quicksearch?media=manga&q=one",
    "/manga/one-piece",
    "/read/one-piece/4f6ed791-5599-4667-9d54-06c47c7bd0c1",
]
def sample(v, depth=0):
    if depth > 5:
        return {"type": type(v).__name__, "size": len(v) if hasattr(v, "__len__") else None}
    if isinstance(v, dict):
        return {k: sample(x, depth+1) for k,x in list(v.items())[:45] if not re.search(r"token|secret|cookie|nonce|csrf|password",k,re.I)}
    if isinstance(v,list):
        return {"count":len(v), "first":[sample(x,depth+1) for x in v[:2]]}
    if isinstance(v,str):
        return v[:250]
    return v
def walk(v):
    if isinstance(v,dict):
        yield v
        for x in v.values(): yield from walk(x)
    elif isinstance(v,list):
        for x in v: yield from walk(x)
for path in URLS:
    try:
        response=urllib.request.urlopen(urllib.request.Request(BASE+path,headers={"User-Agent":"Mozilla/5.0","Referer":BASE+"/"}),timeout=30)
    except urllib.error.HTTPError as error:
        response=error
    body=response.read().decode("utf-8","replace")
    print("ENDPOINT",json.dumps({"path":path,"status":response.code,"bytes":len(body),"type":response.headers.get("Content-Type")}),flush=True)
    try:
        print("API_DATA",json.dumps(sample(json.loads(body)),ensure_ascii=False),flush=True)
        continue
    except ValueError: pass
    parts=[]
    for match in re.finditer(r'self\.__next_f\.push\((\[1,"(?:\\.|[^"\\])*"\])\)', body):
        try: parts.append(json.loads(match.group(1))[1])
        except ValueError: pass
    rows="".join(parts)
    print("FLIGHT",len(parts),len(rows),flush=True)
    seen=set()
    for row in rows.splitlines():
        try: obj=json.loads(row.split(":",1)[1])
        except (ValueError,IndexError): continue
        for node in walk(obj):
            keys=frozenset(node)
            if keys in seen: continue
            if not ({"manga","chapters","pages","items","coverImage","slug"} & keys): continue
            seen.add(keys)
            print("DATA_NODE",json.dumps(sample(node),ensure_ascii=False),flush=True)
            if len(seen)>=18: break
        if len(seen)>=18: break
