#!/usr/bin/env bash
# Run this once after unzipping the project to download the Gradle wrapper JAR.
set -e
echo "Downloading Gradle wrapper JAR..."
curl -L "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar" \
     -o gradle/wrapper/gradle-wrapper.jar
echo "Done. You can now run: ./gradlew assembleDebug"
