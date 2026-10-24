# vibenilla-mono

The [vibenilla](https://github.com/vibenilla) libraries in a single Gradle build, with each repository's full git history preserved.

| Module | Upstream | License |
|---|---|---|
| [`pvp`](pvp) | [vibenilla/pvp](https://github.com/vibenilla/pvp) | Apache-2.0 |
| [`worldgen`](worldgen) | [vibenilla/worldgen](https://github.com/vibenilla/worldgen) | Apache-2.0 |
| [`placement`](placement) | [vibenilla/placement](https://github.com/vibenilla/placement) | Apache-2.0 |
| [`crafting`](crafting) | [vibenilla/crafting](https://github.com/vibenilla/crafting) | Apache-2.0 |
| [`fluids`](fluids) | [vibenilla/fluids](https://github.com/vibenilla/fluids) | MIT |
| [`loot`](loot) | [vibenilla/loot](https://github.com/vibenilla/loot) | MIT |

Each module is licensed under the `LICENSE` file in its own directory. All credit goes to the upstream authors.

## Building

```sh
./gradlew build              # everything
./gradlew :pvp:build         # one module
```

## Syncing upstream

Module directories are imported as git subtrees and kept identical to upstream, so syncing doesn't conflict:

```sh
scripts/sync.sh              # all modules
scripts/sync.sh pvp loot     # specific modules
```
