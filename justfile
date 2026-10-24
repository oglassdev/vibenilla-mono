set shell := ["bash", "-euo", "pipefail", "-c"]

_default:
    @just --list

build:
    ./gradlew build

# Runs all tests, or only the given modules' tests.
test *modules:
    if [ -z "{{ modules }}" ]; then ./gradlew test; else ./gradlew $(printf ':%s:test ' {{ modules }}); fi

format:
    ./gradlew spotlessApply

check:
    ./gradlew spotlessCheck

ready:
    ./gradlew spotlessCheck build
