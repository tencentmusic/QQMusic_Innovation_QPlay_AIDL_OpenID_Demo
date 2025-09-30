#!/usr/bin/env bash

set -e

mkdir -p bin
./gradlew clean
./gradlew :demo:build
cp sdk/build/outputs/apk/debug/demo-debug.apk bin/


