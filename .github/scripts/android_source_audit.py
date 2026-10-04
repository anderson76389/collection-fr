"""Exercise published source APKs in a disposable Android emulator, without accounts.

Reader screenshots require manual review; workflow success is not source success.
Only our verified signing certificate is trusted in this fresh test installation.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

OUT = Path("android-audit")
OUT.mkdir(exist_ok=True)
PACKAGE = "app.mihon"
ACTIVITY = PACKAGE + "/eu.kanade.tachiyomi.ui.main.MainActivity"
INDEX_URL = "https://raw.githubusercontent.com/anderson76389/collection-fr/repo/index.json"
APP_URL = "https://github.com/mihonapp/mihon/releases/download/v0.20.4/mihon-x86_64-v0.20.4.apk"
SIGNING_KEY = "2ec986fe48cf05af20c75337668597fc505b2b2c872070bf74667406d58c043a"


def adb(*args, binary=False, check=True, timeout=45):
    return subprocess.run(["adb", *args], check=check, capture_output=True,
                          text=not binary, timeout=timeout).stdout


def download(url, path):
    with urllib.request.urlopen(url, timeout=60) as response:
        path.write_bytes(response.read())


def ui(label, screenshot=False):
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/audit.xml", timeout=20)
        xml = adb("shell", "cat", "/sdcard/audit.xml")
        root = ET.fromstring(xml)
    except Exception as exc:
        print("UI_ERROR", label, str(exc), flush=True)
        return None
    (OUT / (label + ".xml")).write_text(xml)
    texts = [{"text": n.get("text"), "description": n.get("content-desc"),
              "bounds": n.get("bounds"), "class": n.get("class")}
             for n in root.iter("node") if n.get("text") or n.get("content-desc")]
    print("UI_STATE " + json.dumps({"label": label, "nodes": texts}), flush=True)
    if screenshot:
        raw = adb("exec-out", "screencap", "-p", binary=True)
        (OUT / (label + ".png")).write_bytes(raw)
    return root


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def source_screen():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", ACTIVITY, "-a", "eu.kanade.tachiyomi.SHOW_CATALOGUES")
    time.sleep(4)


def main():
    assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Emulator required"
    adb("root")
    adb("wait-for-device")
    adb("shell", "input", "keyevent", "82")
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", "420")
    download(APP_URL, OUT / "mihon.apk")
    print("INSTALL_APP", adb("install", "-r", str(OUT / "mihon.apk"), timeout=120), flush=True)
    download(INDEX_URL, OUT / "index.json")
    index = json.loads((OUT / "index.json").read_text())
    assert index["signingKey"] == SIGNING_KEY
    extensions = index["extensionList"]["extensions"]
    sdk = Path(os.environ["ANDROID_HOME"])
    signer = sorted((sdk / "build-tools").glob("*/apksigner"))[-1]
    for ext in extensions:
        path = OUT / (ext["packageName"] + ".apk")
        download(ext["resources"]["apkUrl"], path)
        signatures = subprocess.check_output([str(signer), "verify", "--print-certs", str(path)], text=True)
        assert SIGNING_KEY in signatures.lower(), ext["packageName"]
        print("INSTALL_EXTENSION", ext["name"], ext["versionName"],
              adb("install", "-r", str(path), timeout=90).strip(), flush=True)
        path.unlink()
    (OUT / "mihon.apk").unlink()

    # Initialize app migrations before setting test preferences.
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(8)
    adb("shell", "am", "force-stop", PACKAGE)
    directory = "/data/data/" + PACKAGE + "/shared_prefs"
    pref = directory + "/" + PACKAGE + "_preferences.xml"
    raw = adb("shell", "cat", pref, check=False)
    root = ET.fromstring(raw) if raw.strip().startswith("<?xml") else ET.Element("map")
    def preference(tag, name, value=None, items=None):
        for old in list(root):
            if old.get("name") == name:
                root.remove(old)
        node = ET.SubElement(root, tag, name=name)
        if value is not None:
            node.set("value", value)
        for item in items or []:
            ET.SubElement(node, "string").text = item
    preference("boolean", "__APP_STATE_onboarding_complete", "true")
    preference("boolean", "__APP_STATE_donation_campaign_shown", "true")
    preference("boolean", "verbose_logging", "true")
    preference("set", "source_languages", items=["fr", "all"])
    preference("set", "__APP_STATE_trusted_extensions", items=[
        e["packageName"] + ":" + e["versionCode"] + ":" + SIGNING_KEY for e in extensions])
    test_preferences = OUT / "test-preferences.xml"
    ET.ElementTree(root).write(test_preferences, encoding="utf-8", xml_declaration=True)
    adb("push", str(test_preferences), "/data/local/tmp/audit-prefs.xml")
    uid = adb("shell", "stat", "-c", "%u", "/data/data/" + PACKAGE).strip()
    adb("shell", "mkdir", "-p", directory)
    adb("shell", "cp", "/data/local/tmp/audit-prefs.xml", pref)
    adb("shell", "chown", uid + ":" + uid, pref)
    adb("shell", "chmod", "660", pref)
    adb("shell", "restorecon", pref)
    adb("logcat", "-c")
    source_screen()
    ui("initial-sources", screenshot=True)
    results = []
    priority = {"scanmanga": 0, "japscan": 1, "dassouscan": 2}
    extensions.sort(key=lambda e: priority.get(e["packageName"].split(".")[-1], 10))
    selected = os.environ.get("SOURCE_AUDIT_ONLY", "").split(",")
    if selected != [""]:
        extensions = [e for e in extensions if e["packageName"].split(".")[-1] in selected]
    def checkpoint():
        (OUT / "result.json").write_text(json.dumps({
            "app": "Mihon 0.20.4", "android": 35,
            "chapter_reading_validated": False, "sources": results,
            "note": "Reader screenshots require manual review; this is a sampled audit.",
        }, indent=2))
    for ext in extensions[:int(os.environ.get("SOURCE_AUDIT_LIMIT", "16"))]:
        checkpoint()
        (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime", timeout=30))
        slug = ext["packageName"].split(".")[-1]
        names = {s["name"] for s in ext["sources"] if s["language"] in ("fr", "all")}
        source_screen()
        found = None
        for step in range(5):
            state = ui(slug + "-sources-" + str(step))
            if state is None:
                break
            found = next((n for n in state.iter("node") if n.get("text") in names), None)
            if found is not None:
                break
            adb("shell", "input", "swipe", "540", "1550", "540", "550", "500")
            time.sleep(1)
        if found is None:
            results.append({"source": slug, "status": "source_not_visible"})
            continue
        tap(found)
        time.sleep(12)
        state = ui(slug + "-catalog", screenshot=True)
        result = {"source": slug, "version": ext["versionName"], "stages": {}}
        results.append(result)
        def texts(tree):
            return [n.get("text") for n in tree.iter("node") if n.get("text")] if tree is not None else []
        def cards(tree):
            return [n for n in tree.iter("node") if n.get("clickable") == "true"
                    and n.get("long-clickable") == "true"
                    and int(re.findall(r"\d+", n.get("bounds"))[1]) > 350
                    and any(c.get("text") for c in n.iter("node"))] if tree is not None else []
        def find(tree, text=None, description=None):
            return next((n for n in tree.iter("node") if
                        (text is not None and n.get("text") == text) or
                        (description is not None and n.get("content-desc") == description)), None) if tree is not None else None
        result["stages"]["popular"] = texts(state)
        if not cards(state):
            web = find(state, text="Open in WebView")
            if web is not None:
                tap(web)
                time.sleep(25)
                ui(slug + "-webview", screenshot=True)
                adb("shell", "input", "keyevent", "4")
                time.sleep(2)
                state = ui(slug + "-returned")
                retry = find(state, text="Retry")
                if retry is not None:
                    tap(retry)
                    time.sleep(15)
                    state = ui(slug + "-retried", screenshot=True)
                    result["stages"]["retry"] = texts(state)
        latest = find(state, text="Latest")
        if latest is not None:
            tap(latest)
            time.sleep(12)
            state = ui(slug + "-latest", screenshot=True)
            result["stages"]["latest"] = texts(state)
        search = find(state, description="Search")
        if search is not None:
            tap(search)
            adb("shell", "input", "text", "High" if slug == "scanmanga" else "One")
            adb("shell", "input", "keyevent", "66")
            time.sleep(12)
            state = ui(slug + "-search", screenshot=True)
            result["stages"]["search"] = texts(state)
        candidates = cards(state)
        if not candidates:
            # Search failures must not hide a working popular catalogue.
            reset = find(state, description="Reset")
            if reset is not None:
                tap(reset)
                time.sleep(2)
            state = ui(slug + "-search-closed")
            popular = find(state, text="Popular")
            if popular is not None:
                tap(popular)
                time.sleep(12)
                state = ui(slug + "-popular-fallback", screenshot=True)
                candidates = cards(state)
        if not candidates:
            result["status"] = "no_manga_card_available"
            continue
        result["sample_title"] = texts(candidates[0])
        tap(candidates[0])
        time.sleep(18)
        state = ui(slug + "-details", screenshot=True)
        result["stages"]["details"] = texts(state)
        start = find(state, text="Start")
        if start is None:
            start = find(state, text="Resume")
        if start is None:
            for scroll in range(5):
                start = next((n for n in state.iter("node") if re.match(r"^(?:Chapitre|Ch\.|Chapter)\s*\d", n.get("text", ""))), None) if state is not None else None
                if start is not None:
                    break
                adb("shell", "input", "swipe", "540", "1500", "540", "500", "500")
                time.sleep(2)
                state = ui(slug + "-chapters-" + str(scroll), screenshot=True)
        if start is None:
            result["status"] = "no_visible_chapter"
            continue
        result["sample_chapter"] = start.get("text")
        tap(start)
        time.sleep(55)
        state = ui(slug + "-reader", screenshot=True)
        result["stages"]["reader"] = texts(state)
        result["status"] = "reader_attempted_requires_visual_review"
        adb("shell", "input", "swipe", "540", "1500", "540", "450", "500")
        time.sleep(5)
        ui(slug + "-reader-scrolled", screenshot=True)
    checkpoint()
    logs = adb("logcat", "-d", "-v", "threadtime", timeout=30)
    (OUT / "logcat.txt").write_text(logs)
    for line in logs.splitlines():
        if any(word in line for word in ("Exception", "Error", "HTTP FAILED", "<-- 4", "<-- 5", "Failed to load", "Lib version")):
            print("RUNTIME_LOG", line, flush=True)
    print("AUDIT_RESULT", json.dumps(results), flush=True)


if __name__ == "__main__":
    main()
