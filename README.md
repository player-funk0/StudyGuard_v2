# StudyGuard — Book Summariser Edition

An Android app combining on-device AI (MediaPipe Gemma 2B-IT), an extractive
TextRank summariser, a study-session tracker, Islamic content, and wellbeing
tools.

---

## Project structure

```
StudyGuard/
├── app/
│   ├── build.gradle.kts              # App-module dependencies & build config
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/com/obrynex/studyguard/
│       │       ├── MainActivity.kt
│       │       ├── StudyGuardApplication.kt
│       │       ├── ai/               # On-device AI engine (MediaPipe)
│       │       ├── booksummarizer/   # PDF / text book summariser
│       │       ├── data/db/          # Room database + DAO + use-cases
│       │       ├── data/prefs/       # DataStore preferences
│       │       ├── di/               # Manual DI (ServiceLocator)
│       │       ├── islamic/          # Hadith viewer
│       │       ├── navigation/       # NavGraph (Compose Navigation)
│       │       ├── summarizer/       # Quick text summariser
│       │       ├── textrank/         # Offline TextRank algorithm
│       │       ├── tracker/          # Study-session tracker
│       │       ├── ui/theme/         # Material3 theme
│       │       ├── ui/onboarding/    # First-launch onboarding
│       │       └── wellbeing/        # Box-breathing + motivation
│       └── test/kotlin/…             # Unit tests (JUnit4 + Turbine)
├── gradle/
│   ├── libs.versions.toml            # Version catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar        # ⚠ see "First-time setup" below
│       └── gradle-wrapper.properties
├── build.gradle.kts                  # Root build (plugin declarations only)
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── setup.sh                          # One-time wrapper-jar downloader
```

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| JDK  | 17 |
| Android SDK | API 26 (Android 8.0) |
| Android Build Tools | 35 |
| Kotlin | 1.9.24 (via Gradle plugin) |

---

## First-time setup

### 1. Download the Gradle wrapper JAR

The `gradle-wrapper.jar` (~60 KB) is not committed to the repository.
Run the helper script once:

```bash
./setup.sh        # macOS / Linux
# or manually:
curl -L https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar \
     -o gradle/wrapper/gradle-wrapper.jar
```

### 2. Set your Android SDK path

Edit `local.properties`:

```properties
sdk.dir=/Users/you/Library/Android/sdk        # macOS
sdk.dir=/home/you/Android/Sdk                 # Linux
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk   # Windows
```

### 3. Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # unit tests
./gradlew assembleRelease        # release APK (requires signing config)
```

The debug APK lands at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## On-device AI model

The app uses **Gemma 2B-IT** via MediaPipe LlmInference.

1. Download the model from [Kaggle — Gemma 2B-IT](https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4)
2. Push it to the device:

```bash
adb push gemma-2b-it-gpu-int4.bin /sdcard/Android/data/com.obrynex.studyguard/files/models/
```

The expected path is defined in `AIEngineManager.MODEL_RELATIVE_PATH`.

The app works without the model — all AI features degrade gracefully to
TextRank-based offline summarisation.

---

## Running tests

```bash
./gradlew test --tests "com.obrynex.studyguard.*"
```

All unit tests are pure JVM (no Android emulator needed):

| Test class | Coverage |
|---|---|
| `TextRankSummarizerTest` | TextRank algorithm |
| `TextChunkerTest` | Document chunking |
| `BookSummarizerViewModelTest` | Summariser state machine |
| `AiTutorViewModelStateTest` | AI tutor state machine |
| `AIEngineManagerInitFlowTest` | Engine lifecycle |
| `ModelHashCacheTest` | SHA-256 cache logic |

---

## Dependency highlights

| Library | Purpose |
|---|---|
| `com.google.mediapipe:tasks-genai` | On-device Gemma inference |
| `androidx.room` | SQLite persistence for study sessions |
| `androidx.datastore:datastore-preferences` | DataStore for onboarding & model hash cache |
| `androidx.navigation:navigation-compose` | Compose navigation |
| `app.cash.turbine` | Kotlin Flow testing |
