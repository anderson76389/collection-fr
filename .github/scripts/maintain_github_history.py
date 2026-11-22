#!/usr/bin/env python3
"""One-off, fail-closed release / Actions housekeeping.

Preserve every current Mihon download URL, the first four and last four releases,
and all old releases which have ever registered downloads. Old downloaded releases
are only marked as prereleases, which leaves their URLs accessible.
Git commits, tags and branches are never rewritten by this utility.
"""
import base64
import datetime as dt
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote, urlparse, unquote
from urllib.request import Request, urlopen

REPO = os.environ["GITHUB_REPOSITORY"]
TOKEN = os.environ["GH_TOKEN"]
DRY_RUN = os.environ.get("MAINTENANCE_DRY_RUN", "true").lower() in ("1", "yes", "true")
RUN_ID = int(os.environ.get("GITHUB_RUN_ID") or 0)
BASE = f"https://api.github.com/repos/{REPO}"
UTC = dt.timezone.utc
NOW = dt.datetime.now(UTC)
REPORT = {"dry_run": DRY_RUN, "repo": REPO, "checked_at": NOW.isoformat(),
          "archived_releases": [], "pruned_zero_download_releases": [],
          "deleted_action_runs": [], "protected_release_tags": [],
          "skipped": [], "warnings": []}


def api(path: str, method="GET", payload=None):
    url = path if path.startswith("https://") else BASE + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {TOKEN}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "collection-fr-maintenance",
        "Content-Type": "application/json",
    })
    try:
        with urlopen(req, timeout=35) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        message = exc.read().decode("utf-8", "replace")[:500]
        raise RuntimeError(f"{method} {url} failed HTTP {exc.code}: {message}") from exc


def paginated(path: str, limit=1000):
    found = []
    for page in range(1, 1 + (limit + 99) // 100):
        sep = "&" if "?" in path else "?"
        data = api(f"{path}{sep}per_page=100&page={page}")
        elements = data if isinstance(data, list) else data.get("workflow_runs", [])
        found.extend(elements)
        if len(elements) < 100:
            break
    else:
        raise AssertionError(f"Refusing to prune: pagination limit reached for {path}")
    return found


def read_live_index():
    response = api("/contents/index.json?ref=repo")
    content = base64.b64decode(response["content"]).decode("utf-8")
    index = json.loads(content)
    extensions = index["extensionList"]["extensions"]
    assert len(extensions) == 30, "Unexpected Mihon extension count; abort maintenance"
    assert len(index.get("signingKey", "")) == 64, "Unexpected signing key; abort"
    return extensions


def live_assets(extensions, releases):
    tags = set()
    by_tag = {release["tag_name"]: release for release in releases}
    for ext in extensions:
        for field in ("apkUrl", "jarUrl"):
            url = ext["resources"][field]
            parsed = urlparse(url)
            assert parsed.netloc == "github.com", f"External asset URL in index: {url}"
            prefix = f"/{REPO}/releases/download/"
            assert parsed.path.startswith(prefix), f"Unexpected release URL: {url}"
            tag_and_name = parsed.path[len(prefix):].split("/", 1)
            assert len(tag_and_name) == 2, f"Bad asset path: {url}"
            tag, filename = tag_and_name
            release = by_tag.get(tag)
            assert release is not None, f"Indexed release {tag} does not exist"
            names = {asset["name"] for asset in release["assets"]}
            assert unquote(filename) in names, f"Indexed asset missing: {url}"
            tags.add(tag)
    return tags


def age_days(release):
    date = dt.datetime.fromisoformat(release["published_at"].replace("Z", "+00:00"))
    return (NOW - date).total_seconds() / 86400


def maintain_releases():
    extensions = read_live_index()
    all_releases = paginated("/releases")
    if len(all_releases) < 8:
        REPORT["warnings"].append("Fewer than eight releases; no release pruning")
        return
    indexed = live_assets(extensions, all_releases)
    ordered = sorted(all_releases, key=lambda x: (x["published_at"], x["id"]))
    protected = indexed | {r["tag_name"] for r in ordered[:4] + ordered[-4:]}
    REPORT["protected_release_tags"] = sorted(protected)
    for release in ordered:
        tag = release["tag_name"]
        if tag in protected:
            continue
        downloads = sum(asset.get("download_count", 0) for asset in release["assets"])
        # When a release was actually downloaded, keep every historical URL working.
        if downloads > 0:
            if not release.get("prerelease", False) and not release.get("draft", False):
                if not DRY_RUN:
                    api(f"/releases/{release['id']}", method="PATCH", payload={"prerelease": True})
                REPORT["archived_releases"].append(tag)
            continue
        # With no recorded downloads, prune only a sufficiently old release
        # that is not referenced by the live index at deletion time.
        if release.get("draft") or age_days(release) < 7:
            REPORT["skipped"].append(f"Young/draft: {tag}")
            continue
        refreshed = read_live_index()
        current = live_assets(refreshed, all_releases)
        if tag in current:
            REPORT["skipped"].append(f"Re-referenced: {tag}")
            continue
        if not DRY_RUN:
            api(f"/releases/{release['id']}", method="DELETE")
        REPORT["pruned_zero_download_releases"].append(tag)


def maintain_actions():
    runs = paginated("/actions/runs")
    newest_for_workflow = {}
    for run in runs:
        key = run.get("workflow_id")
        if key not in newest_for_workflow:
            newest_for_workflow[key] = run["id"]
    threshold = NOW - dt.timedelta(days=30)
    # Keep the most recent 150 runs, everything under 30 days, active runs,
    # and the latest run for each workflow. Limit deletions per invocation.
    for position, run in enumerate(runs):
        if len(REPORT["deleted_action_runs"]) >= 100:
            break
        created = dt.datetime.fromisoformat(run["created_at"].replace("Z", "+00:00"))
        if (position < 150 or created >= threshold or run.get("status") != "completed"
                or run["id"] == RUN_ID or run["id"] in newest_for_workflow.values()):
            continue
        if not DRY_RUN:
            try:
                api(f"/actions/runs/{run['id']}", method="DELETE")
            except RuntimeError as exc:
                REPORT["warnings"].append(str(exc))
                continue
        REPORT["deleted_action_runs"].append(run["id"])


def main():
    maintain_releases()  # Protect the live catalogue BEFORE touching releases.
    maintain_actions()
    Path("maintenance-report.json").write_text(
        json.dumps(REPORT, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: value if not isinstance(value, list) else len(value)
                      for key, value in REPORT.items()}, indent=2))
    if REPORT["warnings"]:
        print("WARNINGS", "\n".join(REPORT["warnings"]))


if __name__ == "__main__":
    main()
