set shell := ["bash", "-euo", "pipefail", "-c"]

_default:
    @just --list

build:
    ./gradlew build

# Runs all tests, or only the given modules' tests.
test *modules:
    if [ -z "{{ modules }}" ]; then ./gradlew test; else ./gradlew $(printf ':%s:test ' {{ modules }}); fi

# Runs the survival server on localhost:25565. Pass e.g. `-Dvibenilla.seed=42 -Dvibenilla.port=25566`.
run *args:
    ./gradlew :server:run {{ args }}

format:
    ./gradlew spotlessApply

check:
    ./gradlew spotlessCheck

ready:
    ./gradlew spotlessCheck build
