"""Package a signed release APK and its updater manifest; never publishes files."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME"))
    args = parser.parse_args()
    if args.version_code <= 0 or not re.fullmatch(r"\d+\.\d+\.\d+", args.version_name):
        parser.error("Use a positive versionCode and a versionName such as 0.2.0")
    if not args.sdk:
        parser.error("Pass --sdk or set ANDROID_HOME")
    root = Path(__file__).resolve().parent.parent
    apk = root / "app/build/outputs/apk/release/app-release.apk"
    tools = Path(args.sdk) / "build-tools/35.0.0"
    badging = subprocess.check_output([str(tools / ("aapt.exe" if os.name == "nt" else "aapt")), "dump", "badging", str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    expected = ("pl.slqpl.rodzina", str(args.version_code), args.version_name)
    if not package or package.groups() != expected or "application-debuggable" in badging:
        raise SystemExit("APK package/version mismatch or a debug APK: refusing to package this release")
    apksigner = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    subprocess.run([str(apksigner), "verify", str(apk)], check=True)
    destination = root / "dist"
    destination.mkdir(exist_ok=True)
    published_apk = destination / "rodzina.apk"
    shutil.copyfile(apk, published_apk)
    with published_apk.open("rb") as apk_stream:
        checksum = hashlib.file_digest(apk_stream, "sha256").hexdigest()
    manifest = {"packageName": expected[0], "versionCode": args.version_code, "versionName": args.version_name,
                "sha256": checksum}
    (destination / "update.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared dist/rodzina.apk and dist/update.json for v{args.version_name}")


if __name__ == "__main__":
    main()
