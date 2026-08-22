# APK version verification (required before Founder download)
unzip -p app-debug.apk assets/index.html | grep -E 'v0\.[0-9.]+'
# Must show v0.3.1 — if v0.2.x, DO NOT release
