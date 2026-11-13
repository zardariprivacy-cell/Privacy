# MUET MCQ Practice — Android App

A native Android app for **MUET (Mehran University of Engineering & Technology)
entry test preparation**: Math, Physics, Chemistry and English MCQs in a
navy + gold premium theme.

- **Package:** `com.nawazishali.muetpractice`
- **Version:** 2.0 (versionCode 5)
- **minSdk:** 24 · **targetSdk:** 34
- **Language:** Java, no external libraries — data is stored on-device (SQLite)

## Features

- Signup / Login (the **first account becomes Admin**)
- Profile with profile picture (photos are auto-shrunk on save)
- Practice mode per subject with instant right/wrong feedback and simple explanations
- Mock Test — 20 mixed questions (5 per subject), 20-minute timer
- Results screen with circular score %, correct/wrong cards and full review
- My Performance history and a Leaderboard (best Mock Test score)
- Admin: add / edit / delete MCQs in the shared question bank
- 40 beginner MCQs included (10 per subject), each with a simple explanation

## Build

1. Generate the icon/emblem PNGs (they are produced by a script, not stored as binaries here):

   ```bash
   pip install Pillow
   python3 tools/generate_icons.py
   ```

2. Open the `android-app` folder in **Android Studio** and run the app,
   or build a release AAB for the Play Store from there.

## Publishing note

Play Store publishing needs your own Google Play developer account
($25 one-time). For release, create your own upload keystore in Android
Studio (Build → Generate Signed Bundle/APK) — never publish a debug build.
