"""Check the actual Android APK labels and package versions before publication."""
import ast
import json
import os
from pathlib import Path
import re
import subprocess

root = Path(os.environ["SOURCE_DIR"])
sdk = Path(os.environ["ANDROID_HOME"])
aapt = sorted((sdk / "build-tools").glob("*/aapt2"))[-1]
verified = 0
publisher = ast.parse((root / ".github/scripts/publish_index.py").read_text())
modules = next(ast.literal_eval(node.value) for node in publisher.body
               if isinstance(node, ast.Assign)
               and any(isinstance(t, ast.Name) and t.id == "EXPECTED_MODULES" for t in node.targets))
for metadata_file in sorted(root.glob("src/*/*/build/keiyoushi-source-info.json")):
    if metadata_file.parent.parent.relative_to(root).as_posix() not in modules:
        continue
    info = json.loads(metadata_file.read_text())
    expected_languages = {
        "eu.kanade.tachiyomi.extension.all.saymanhwa": {"fr"},
        "eu.kanade.tachiyomi.extension.all.webtoons": {"fr"},
        "eu.kanade.tachiyomi.extension.en.mangadotnet": {"fr"},
        "eu.kanade.tachiyomi.extension.all.mangadex": {"fr"},
    }.get(info["packageName"])
    if expected_languages is not None:
        assert {s["lang"] for s in info["sources"]} == expected_languages, info["packageName"]
        assert len(info["sources"]) == len(expected_languages), info["packageName"]
    apks = list(metadata_file.parent.glob("outputs/apk/release/*.apk"))
    for apk in apks:
        output = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
        label = re.search(r"^application-label:'(.*)'$", output, re.MULTILINE)
        package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", output, re.MULTILINE)
        assert label and label[1] == "MAKOFF: " + info["name"], (apk.name, label)
        assert package and package[1] == info["packageName"], apk.name
        assert int(package[2]) == int(info["versionCode"]), apk.name
        assert "name='tachiyomi.extension'" in output, apk.name
        certificate = subprocess.check_output(
            [str(aapt.with_name("apksigner")), "verify", "--print-certs", str(apk)], text=True,
        )
        assert os.environ["SIGNING_FINGERPRINT"].lower() in certificate.lower().replace(":", ""), apk.name
        print("VERIFIED_APK", package[1], package[2], label[1], flush=True)
        verified += 1
assert verified == len(modules), f"Expected {len(modules)} release APKs, verified {verified}"
