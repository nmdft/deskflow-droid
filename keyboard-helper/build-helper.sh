#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${ANDROID_HOME:=/opt/homebrew/share/android-commandlinetools}"
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME
mkdir -p "$TASK_ROOT/keyboard-helper/build/classes"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -cp "$ANDROID_HOME/platforms/android-37.0/android.jar" -d "$TASK_ROOT/keyboard-helper/build/classes" "$TASK_ROOT/keyboard-helper/KeyboardServer.java"
"$ANDROID_HOME/build-tools/36.0.0/d8" --lib "$ANDROID_HOME/platforms/android-37.0/android.jar" --min-api 30 --output "$TASK_ROOT/keyboard-helper/build/keyboard.dex.jar" "$TASK_ROOT"/keyboard-helper/build/classes/*.class
