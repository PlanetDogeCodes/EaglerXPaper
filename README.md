# EaglerXPaper

[![Paper](https://img.shields.io/badge/Paper-1.8+-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-green)](LICENSE)

EaglerXPaper is a forward port of lax1dude's EaglerXServer Spigot functionalities that extends Bukkit/Spigot/Paper support **up to 26.x** and **down to 1.8.x**

**This is largely the same project as EaglerXServer** — it only changes a few minor things to ensure 1.17+ and 1.8.x compatibility, plus adds a couple of small features. All credit for the actual plugin goes to lax1dude.

Currently based on EaglerXServer **v1.1.1**

## Compatibility

| Platform | Version Range | Status |
|----------|--------------|--------|
| **Paper** | 1.12.2 – 1.21.11+ | ✅ Fully supported |
| **Paper** | 1.8.x | ⚠️ Currently in Beta - should work |
| **Spigot** | 1.12.2 – 1.21.x | ⚠️ Should work (uses NMS reflection fallback) |
| **Folia** | Any | ❌ Not supported |
| **BungeeCord** | 1.21+ | ✅ Use upstream EaglerXServer (already supported) |
| **Velocity** | 3.4+ | ✅ Use upstream EaglerXServer (already supported) |

**Java requirement:** Java 17+ for Paper 1.12–1.20, Java 21+ for Paper 1.21–1.21.4, Java 25+ for Paper 26.x (1.21.11+).

**Tested and works on:** Paper versions 1.12.2 to 1.17.1, and 1.21.11 to 26.2, all with Java 25.

## How 1.17+ Compatibility Was Achieved

Paper 1.17 switched the runtime NMS from CraftBukkit names (`EntityPlayer`, `PlayerConnection`, `NetworkManager`) to Mojang names (`ServerPlayer`, `ServerGamePacketListenerImpl`, `Connection`). EaglerXServer anchored every reflection on NMS. Those names changed in 1.17, breaking everything.

EaglerXPaper fixes this with a **multi-version reflection name table** (`NmsNames.java`) that maps each NMS symbol to the set of simple names it has been known by across all supported versions. It's not perfect or efficient, but it works.

### The core technique

```java
// Before (broke on 1.17+):
if (f.getType().getSimpleName().equals("PlayerConnection")) { ... }

// After (works on all versions):
if (NmsNames.matches(f.getType(), NmsNames.PLAYER_CONNECTION)) { ... }
```

where `NmsNames.PLAYER_CONNECTION = Set.of("ServerGamePacketListenerImpl", "PlayerConnection")`.

### What was NOT changed

- **Config structure** — identical to regular EaglerXServer. Existing `plugins/EaglercraftXServer/` configs work without any changes.
- **Plugin name** — still technically `"EaglercraftXServer"` internally, mostly to maintain compatibility with the base EaglerXServer API.
- **BungeeCord/Velocity support** — untouched (it already supports 1.21 on those platforms, so no need to change any of that).

## EaglerXPaper-Exclusive Features

These are features added by EaglerXPaper that are not in upstream EaglerXServer:

### Skin Cache Pre-warming

On server start, EaglerXPaper pre-downloads skins for recently-seen players from Mojang's sessionserver API. This means when a player joins for the first time, their skin is already cached and displays instantly — however, it is unreliable and often doesn't work. It's cool when it does work though.

### Adaptive Packet Batching

EaglerXPaper automatically batches data to and from connections that are sending many packets per flush (e.g. during chunk loading). Each packet is still technically its own WebSocket frame but the flushes happen together, which cuts resource usage and channel flood.

The batcher has 3 "modes":
- **Idle connections** - packets pass through immediately with zero added latency
- **Burst connections** (16+ packets in a 100ms window) — packets are buffered for ~2ms and flushed together 
- **Sustained bursts** — a forced flush every 200ms caps added latency, and the timer resets after each flush so batching stays working.

Both features are enabled by default and require no extra config.


## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.17+ server's `plugins/` folder
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. OPTIONAL (only needed if you use BungeeCord or Velocity) — Configure your reverse proxy / tunnel. See [the regular EaglerXServer setup guide](https://github.com/lax1dude/eaglerxserver/blob/main/CONFIG.md) for details.
5. Connect with an Eaglercraft client to `ws://yourserver:25565/` (or `wss://` if using a reverse proxy)
6. That's it! You can configure extra options if needed, but you really don't have to if all you wanted to do was "just get it working".

**Dual-stack mode** is enabled by default so EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

## Building from source

```bash
git clone https://github.com/PlanetDogeCodes/eaglerxpaper.git
cd eaglerxpaper
./gradlew :core:shadowJarBukkit
# Output: core/build/libs/EaglerXPaper.jar
```

Requires Java 25 and Gradle 8.5+ (wrapper included). The build compiles with the Paper 1.12.2 stub but will still work with 1.21.11

## Architecture

```
Eaglercraft Client (ws:// or wss://)
        │
        ▼
  [Reverse Proxy / Tunnel]     ← TLS termination (Caddy, nginx, playit.gg, CloudFlare, etc.)
        │
        ▼ regular unsecure WebSocket
  Paper 1.12+
        │
        ▼ ChannelInitializeListener injection
  EaglerXPaper
        │
        ├── Eaglercraft handshake → Eaglercraft protocol pipeline
        └── Vanilla MC detection → passes through to Paper
```

EaglerXPaper injects into Paper's Netty channel pipeline via Paper's `ChannelInitializeListener` API (the supported, stable injection method). It inspects the first bytes of each connection to determine whether it's an HTTP/WebSocket upgrade request (Eaglercraft) or a raw Minecraft handshake (vanilla), and routes accordingly.


## Addon compatibility

| Addon | Status |
|-------|--------|
| [EaglerXRewind](https://github.com/lax1dude/eaglerxserver/tree/main/rewind_v1_5) (1.5.2 client support) | ⚠️ Should work but not runtime-tested on 1.21 |
| [EaglerWeb](https://github.com/lax1dude/eaglerxserver/tree/main/eaglerweb) (HTTP file hosting) | ⚠️ Should work but not runtime-tested on 1.21 |
| [EaglerMOTD](https://github.com/lax1dude/eaglerxserver/tree/main/eaglermotd) | ⚠️ Kind of works; runtime-tested on 1.21, but had some issues that are too minor to fix right now |

## Credits

- **Original EaglerXServer:** [lax1dude](https://github.com/lax1dude) — the entire plugin architecture, Eaglercraft protocol implementation, and dual-stack design.


EaglerXPaper is a derivative work of EaglerXServer. All credit for the plugin's core functionality goes to lax1dude. This fork only adds version compatibility for Paper 1.17+, and is not a big change or rewrite.

## License

Same as EaglerXServer — see [LICENSE](LICENSE).

## Contributing

If you find a bug on a specific Paper version, please open an issue and include:
1. The Paper version (e.g. `paper-1.21.11-132`)
2. The full error from `logs/latest.log`
3. The output of `java -version`

## LLM Usage Disclaimer 
GLM 5.3 was used to generate portions of this README that I was too lazy to make myself, and also helped with code checks to eliminate some of my stupid mistakes (like forgetting to include connection headers or accidentally breaking the timer for all packet batching tasks). It is my personal belief that AI is best used as a code reviewer and not writer, so I have acted in accordance to that belief. This project and all code is 100% still managed and created by a human.
