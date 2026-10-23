# AncientRPG

Public, source-available Fabric client and pure Java protocol contracts. First-party
code and assets are All Rights Reserved; see LICENSE and THIRD_PARTY_NOTICES.md.
Domain, GitHub remote, copyright holder, and publishing remain TODOs.

## Initial Setup Demo

The client registers `ancientrpg:demo_block` with an `active` state and
`ancientrpg:demo_item`, responds to the server's compatibility challenge during
configuration, and sends activation intent when you right-click the demo block.
The server owns the block state, inventory, permissions, reach, and action results.

Public assets and actual runtime registry IDs participate in the compatibility
fingerprint. Extra content mods that change these IDs will be rejected. Rendering,
Fabric's live registry lifecycle, and authenticated connection still require the
real-client smoke test; serializer tests alone do not verify them.

## Build and Install

Use JDK 25 and the checked-in Gradle 9.6.0 wrapper. From this directory:

```sh
./gradlew build spotlessCheck :client-mod:prepareDemoMods
```

Windows: `gradlew.bat build spotlessCheck :client-mod:prepareDemoMods`.
The generated `client-mod/build/demo-mods/` folder contains the AncientRPG mod JAR
(with protocol/assets nested) and the pinned Fabric API JAR. Copy both JARs into a
clean Minecraft **26.2** profile's `mods/` directory using Fabric Loader **0.19.3**.
Launch through your normal authenticated Minecraft launcher, then join the local
server at `127.0.0.1:25565`. The development release is `0.3.0-dev`.

For client-only development, Loom's `:client-mod:runClient` is available; its default
development identity is unsuitable for the online-authenticated server. Use your
normal launcher for the integration smoke test; do not put access tokens in files
or paste them into logs.

## Checks

`build` runs JUnit suites for public contracts and real Minecraft-buffer codecs.
`spotlessCheck` verifies Java formatting; `spotlessApply` applies formatting.
Dependencies need network access. No private server checkout is required to build
the public client.

The dependency-free protocol runner requires Python 3 and JDK 25+:

```sh
python tools/check_protocol.py --jdk /path/to/jdk-25
```

Omit `--jdk` when the correct `java`/`javac` are on PATH. This runner executes the same
protocol assertions as JUnit but does not test the Fabric adapter or launch Minecraft.

## Structure and CI

Use Serena for Java navigation and reference-aware refactoring. The checked-in
`AncientRPG-Client` profile and [setup/usage guide](docs/serena.md) cover activation,
Java 25 indexing, local overrides, and verification. Read `AGENTS.md` before changes.

- `protocol/`: wire contracts, canonical public-content hashing, and shared client assets.
- `client-mod/`: Fabric registration, configuration/play payloads, and input/rendering adapters.
- `docs/protocol.md`: wire format and compatibility rules.

The prepared GitHub Actions workflow checks Linux and Windows. Hosted execution,
remote setup, branch protection, and artifact publishing remain TODOs. Never add
private server code, encounter logic, or credentials to this repository.

## One-Handed Sword Training

The matching `0.3.0-dev` client/server includes exactly five skills: Quick Cut,
Sweeping Arc, Piercing Thrust, Twin Fang, and Crescent Finish. Select the Practice
Sword in hotbar slot 2, leave the offhand empty, and walk east to the marked area
around x=24–38, z=1–9. The original switch remains at spawn.

Default bindings are **Z / X / C / V / B** in that skill order; **K** opens the
Sword Arts picker. Select a skill card to inspect its damage, timing, carry and
prerequisites, then use **Learn / 1 point** to unlock it. The picker uses custom
navy/teal/gold panels, with a compact list at smaller GUI sizes. Tab and Enter/Space
operate the controls; Escape closes the picker. Rebind keys in Minecraft Controls. Quick Cut is learned initially;
four practice points unlock the other nodes. Crescent Finish requires Twin Fang
and Piercing Thrust. Points/unlocks are session-only and reset on reconnect.
All skills wait for server acceptance, share an action lock and have independent
cooldowns. Left-click with the sword is not an extra attack.

Stationary targets share 100 health and respawn three simulation seconds after
defeat. They do not fight back or grant rewards. Aim at their body centers;
Piercing Thrust is deliberately narrow. The wall lane tests obstruction. This
slice has fixed damage and no leveling, persistence, stamina or PvP.

Build both matching checkouts before testing; older distributions and
client JARs are incompatible. First-person animation, screen layout and two-client
observation still require live verification for this feature.


Each accepted skill carries you forward: Cut 0.7 blocks, Arc 1.1, Thrust 2.8,
Twin Fang 0.75 + 0.85, and Finish 2.2. Start grounded. Movement, jumping,
equipment changes and interactions remain locked until recovery finishes;
looking around stays free. Walls and edges stop travel. Press one learned skill
during the final three ticks to queue a follow-up; it starts only if eligible at
the end. First-person hands and third-person body keyframes follow the strike
phases, with a short blend between chained actions. These visuals still require
real-client acceptance testing, including left-handed mode and latency.
