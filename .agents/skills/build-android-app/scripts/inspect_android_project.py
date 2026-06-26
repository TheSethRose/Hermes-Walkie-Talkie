#!/usr/bin/env python3
import pathlib
import re
import sys


def read(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def first_match(text, pattern):
    match = re.search(pattern, text)
    return match.group(1) if match else None


QUOTED_VALUE = r'\s*=\s*"([^"]+)"'


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    app = root / "app" if (root / "app").is_dir() else root
    project = app.parent if app.name == "app" else root

    app_gradle = next((p for p in [app / "build.gradle.kts", app / "build.gradle"] if p.exists()), None)
    manifest = app / "src/main/AndroidManifest.xml"
    versions = project / "gradle/libs.versions.toml"
    gradlew = project / "gradlew"

    gradle_text = read(app_gradle) if app_gradle else ""
    manifest_text = read(manifest)

    print(f"project={project}")
    print(f"app={app}")
    print(f"gradle_wrapper={'yes' if gradlew.exists() else 'no'}")
    print(f"version_catalog={'yes' if versions.exists() else 'no'}")
    print(f"app_gradle={app_gradle if app_gradle else 'missing'}")
    print(f"manifest={manifest if manifest.exists() else 'missing'}")
    print(f"namespace={first_match(gradle_text, 'namespace' + QUOTED_VALUE) or 'unknown'}")
    print(f"application_id={first_match(gradle_text, 'applicationId' + QUOTED_VALUE) or 'unknown'}")
    print(f"min_sdk={first_match(gradle_text, r'minSdk\s*=\s*(\d+)') or 'unknown'}")
    print(f"target_sdk={first_match(gradle_text, r'targetSdk\s*=\s*(\d+)') or 'unknown'}")
    print(f"internet_permission={'yes' if 'android.permission.INTERNET' in manifest_text else 'no'}")
    print(f"compose={'yes' if 'compose' in gradle_text.lower() else 'unknown'}")
    print(f"room={'yes' if 'room' in gradle_text.lower() else 'no'}")

    if gradlew.exists():
        print("suggested_checks=./gradlew testDebugUnitTest; ./gradlew assembleDebug")
    else:
        print("suggested_checks=use repo documented Gradle/Android Studio validation")


if __name__ == "__main__":
    main()
