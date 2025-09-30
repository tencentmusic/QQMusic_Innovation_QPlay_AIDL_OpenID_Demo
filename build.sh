#!/usr/bin/env bash

set -e

mkdir -p bin
./gradlew clean
./gradlew :demo:build
cp demo/build/outputs/apk/debug/demo-debug.apk bin/


