#!/usr/bin/env python3
"""Import original user-provided 5-track ZIP to Fabric music assets (stdlib only)."""
import argparse
from pathlib import Path
import zipfile

TARGET = Path("src/main/resources/assets/throwingdiscs/sounds/music")
ORIGINALS = {
    "copper": "Toby-Fox-Acid-Tunnel-of-Love-_hitmos.fm_.ogg",
    "iron": "DROWNgod_-_Metalheart_5_78862801-_2_.ogg",
    "golden": "Toby-Fox-Now_s-Your-Chance-To-Be-A-_hitmos.fm_.ogg",
    "diamond": "Toby-Fox-and-Lena-Raine_-Marcy-Nabors-Attack-of-the-Killer-Queen-_hitmos.fm_.ogg",
    "netherite": "Heaven_Pierce_Her_-_Into_the_Fire_76171013.ogg",
}

def import_archive(path):
    with zipfile.ZipFile(path) as z:
        found = {Path(x).name: x for x in z.namelist() if x.endswith(".ogg")}
        missing = [n for n in ORIGINALS.values() if n not in found]
        if missing:
            raise ValueError("Not all tracks found: " + ", ".join(missing))
        data = {disc: z.read(found[name]) for disc, name in ORIGINALS.items()}
        if not all(d.startswith(b"OggS") for d in data.values()):
            raise ValueError("Invalid OGG header")
        TARGET.mkdir(parents=True, exist_ok=True)
        for disc, audio in data.items():
            (TARGET / (disc + ".ogg")).write_bytes(audio)
            print(f"Imported {disc}: {len(audio)} bytes")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", nargs="?", type=Path)
    parser.add_argument("--search", type=Path)
    args = parser.parse_args()
    if args.archive:
        import_archive(args.archive)
        return
    if args.search:
        for candidate in sorted(args.search.glob("*.zip")):
            try:
                import_archive(candidate)
                return
            except (ValueError, zipfile.BadZipFile) as exc:
                print(f"Skipping {candidate}: {exc}")
        raise SystemExit("No compatible 5-track ZIP in repository root.")
    parser.error("provide a ZIP file or --search directory")

if __name__ == "__main__":
    main()
