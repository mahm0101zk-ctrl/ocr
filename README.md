# Bubble OCR

Floating bubble -> tap -> frozen screen -> drag/adjust selection -> offline OCR (English) -> Copy.

## Build the APK
Option A - Android Studio: File > Open this folder, wait for Gradle sync, then Build > Build APK(s).
APK: app/build/outputs/apk/debug/app-debug.apk

Option B - no PC tools (GitHub): create a repo, upload this folder, open the Actions tab,
run "Build APK", then download the artifact BubbleOCR-apk.

## Use
Install, open the app, enable the service in Accessibility settings (Android 13+: first
App info > menu > Allow restricted settings). Requires Android 11+.
