#!/usr/bin/env python3
"""
Download OGG music files from the Old School RuneScape Wiki.
Saves them into the music/ directory alongside this script, named by song title
(spaces replaced with underscores, matching the wiki convention).
"""

import json
import os
import sys
import time
import urllib.request
import urllib.parse
import urllib.error

WIKI_API = "https://oldschool.runescape.wiki/api.php"
MUSIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "music")
USER_AGENT = "RuneLite-MuteJinglesPlugin/1.0 (music downloader)"


def api_get(params):
    params["format"] = "json"
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())


def strip_disambig(title):
    """Strip wiki disambiguation suffixes like '(music track)' from a title."""
    import re
    # Also skip Category: pages
    if title.startswith("Category:"):
        return None
    return re.sub(r"\s*\(music track\)\s*$", "", title)


def get_all_track_names():
    """Get all music track names from the Music tracks category."""
    tracks = []
    params = {
        "action": "query",
        "list": "categorymembers",
        "cmtitle": "Category:Music_tracks",
        "cmlimit": "500",
    }
    while True:
        data = api_get(params)
        for member in data["query"]["categorymembers"]:
            name = strip_disambig(member["title"])
            if name:
                tracks.append(name)
        if "continue" in data:
            params["cmcontinue"] = data["continue"]["cmcontinue"]
        else:
            break
    return tracks


def get_ogg_urls(track_names):
    """Batch-query the wiki for OGG file URLs. Returns {track_name: url}."""
    results = {}
    # imageinfo API accepts up to 50 titles at a time
    batch_size = 50
    for i in range(0, len(track_names), batch_size):
        batch = track_names[i:i + batch_size]
        titles = "|".join(f"File:{name}.ogg" for name in batch)
        params = {
            "action": "query",
            "titles": titles,
            "prop": "imageinfo",
            "iiprop": "url",
        }
        data = api_get(params)
        pages = data["query"]["pages"]
        for page_id, page in pages.items():
            if "imageinfo" in page:
                # Extract track name from "File:Track_Name.ogg"
                file_title = page["title"]
                track_name = file_title[len("File:"):-len(".ogg")]
                # Map back to original name (wiki normalizes underscores/spaces)
                url = page["imageinfo"][0]["url"]
                results[track_name] = url
        # Be polite to the wiki
        time.sleep(0.2)
    return results


def download_file(url, output_path):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req) as resp:
        with open(output_path, "wb") as f:
            f.write(resp.read())


def sanitize_filename(name):
    """Convert a track name to a safe filename matching wiki convention."""
    return name.replace(" ", "_")


def main():
    os.makedirs(MUSIC_DIR, exist_ok=True)

    print("Fetching track list from wiki...")
    tracks = get_all_track_names()
    print(f"Found {len(tracks)} tracks")

    print("Querying OGG file URLs...")
    ogg_urls = get_ogg_urls(tracks)
    print(f"Found OGG files for {len(ogg_urls)} tracks")

    downloaded = 0
    skipped = 0
    failed = 0
    missing = 0

    for track in tracks:
        safe_name = sanitize_filename(track)
        output_path = os.path.join(MUSIC_DIR, safe_name + ".ogg")

        if os.path.exists(output_path):
            skipped += 1
            continue

        # Try the exact name first, then the sanitized version
        url = ogg_urls.get(track) or ogg_urls.get(safe_name)
        if not url:
            print(f"  NO OGG: {track}")
            missing += 1
            continue

        try:
            download_file(url, output_path)
            downloaded += 1
            if downloaded % 25 == 0:
                print(f"  Downloaded {downloaded}...")
            # Rate limit
            time.sleep(0.1)
        except urllib.error.HTTPError as e:
            print(f"  FAILED ({e.code}): {track}")
            failed += 1
        except Exception as e:
            print(f"  ERROR: {track}: {e}")
            failed += 1

    print(f"\nDone! Downloaded: {downloaded}, Skipped (existing): {skipped}, "
          f"Missing: {missing}, Failed: {failed}")


if __name__ == "__main__":
    main()
