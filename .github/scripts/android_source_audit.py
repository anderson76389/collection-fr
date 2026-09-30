"""Exercise published source APKs in a disposable Android emulator, without accounts.

Catalog attempts are recorded, never reported as successful chapter reading.
Only our verified signing certificate is trusted in this fresh test installation.
"""
import base64
import io
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

from PIL import Image

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
        image = Image.open(io.BytesIO(raw)).convert("RGB")
        image.thumbnail((540, 1000))
        buff = io.BytesIO()
        image.save(buff, format="JPEG", quality=65)
        print("AUDIT_SCREENSHOT " + label + " " + base64.b64encode(buff.getvalue()).decode(), flush=True)
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
    for ext in extensions[:int(os.environ.get("SOURCE_AUDIT_LIMIT", "16"))]:
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
        results.append({"source": slug, "status": "catalog_attempted" if state is not None else "ui_unavailable"})
    (OUT / "result.json").write_text(json.dumps({"app": "Mihon 0.20.4", "android": 35,
        "chapter_reading_validated": False, "sources": results}, indent=2))
    logs = adb("logcat", "-d", "-v", "threadtime", timeout=30)
    (OUT / "logcat.txt").write_text(logs)
    for line in logs.splitlines():
        if any(word in line for word in ("Exception", "Error", "HTTP FAILED", "<-- 4", "<-- 5", "Failed to load", "Lib version")):
            print("RUNTIME_LOG", line, flush=True)
    print("AUDIT_RESULT", json.dumps(results), flush=True)


if __name__ == "__main__":
    main()
