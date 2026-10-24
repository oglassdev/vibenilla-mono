# vibenilla-mono

Vanilla Minecraft features for [Minestom](https://minestom.net), as a single Gradle build.

This is a fork of the [vibenilla](https://github.com/vibenilla) repositories, combined into one repository with their full git history preserved. Files have been modified from upstream; all credit for the original work goes to its authors.

| Module | Description | Upstream | License |
|---|---|---|---|
| [`pvp`](pvp) | Combat | [vibenilla/pvp](https://github.com/vibenilla/pvp) | Apache-2.0 |
| [`worldgen`](worldgen) | World generation | [vibenilla/worldgen](https://github.com/vibenilla/worldgen) | Apache-2.0 |
| [`placement`](placement) | Block placement | [vibenilla/placement](https://github.com/vibenilla/placement) | Apache-2.0 |
| [`crafting`](crafting) | Crafting | [vibenilla/crafting](https://github.com/vibenilla/crafting) | Apache-2.0 |
| [`fluids`](fluids) | Water and lava physics | [vibenilla/fluids](https://github.com/vibenilla/fluids) | MIT |
| [`loot`](loot) | Loot tables | [vibenilla/loot](https://github.com/vibenilla/loot) | MIT |

Each module is licensed under the `LICENSE` file in its own directory.

## Development

Tool versions are pinned in `mise.toml` (`mise install`), and tasks run through `just`:

```sh
just build              # build everything
just test pvp loot      # test specific modules
just format             # apply formatting
just ready              # formatting check + full build
```

## Usage

Modules are published as `dev.oglass.vibenilla:<module>`. To use them locally, run `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.
