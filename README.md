# EaglerXPaper

> Paper 1.21.x port of [EaglerXServer](https://github.com/lax1dude/eaglerxserver) — run modern Paper servers that can interface with Eaglercraft (browser) clients.

[![Paper](https://img.shields.io/badge/Paper-1.12+-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-green)](LICENSE)

EaglerXPaper is a fork of lax1dude's EaglerXServer that extends Bukkit/Spigot/Paper support from 1.12.2–1.17 up to **1.21.x** (Paper 26.x). It lets Eaglercraft browser clients connect to a modern Paper server alongside vanilla Java Edition players, using the same dual-stack architecture as the original plugin.

**This is largely the same project as EaglerXServer** — it only changes a few minor things to ensure 1.17+ compatibility, plus adds a couple of small features. All credit for the actual plugin goes to lax1dude.

> [!NOTE]
> This plugin requires [ViaVersion](https://hangar.papermc.io/ViaVersion/ViaVersion/versions), [ViaBackwards](https://hangar.papermc.io/ViaVersion/ViaBackwards/versions), and [ViaRewind](https://hangar.papermc.io/ViaVersion/ViaRewind/versions) to function properly. Due to legal restrictions, we are not allowed to bundle any ViaVersion plugins with EaglerXPaper. Please double-check that you have those installed before opening an issue.

## Compatibility

| Platform | Version Range | Status |
|----------|--------------|--------|
| **Paper** | 1.12.2 – 1.21.11+ | ✅ Fully supported - Needs ViaVersion/ViaBackwards/ViaRewind|
| **Spigot** | 1.12.2 – 1.21.x | ⚠️ Should work (uses NMS reflection fallback) |
| **Folia** | Any | ❌ Not supported |
| **BungeeCord** | 1.21+ | ✅ Use upstream EaglerXServer (already supported) |
| **Velocity** | 3.4+ | ✅ Use upstream EaglerXServer (already supported) |

**Java requirement:** Java 17+ for Paper 1.12–1.20, Java 21+ for Paper 1.21–1.21.4, Java 25+ for Paper 26.x (1.21.11+).

**Tested and works on:** Paper versions 1.12.2 to 1.17.1, and 1.21.11 to 26.2, all with Java 25.

## How 1.17+ Compatibility Was Achieved

Paper 1.17 switched the runtime NMS from CraftBukkit names (`EntityPlayer`, `PlayerConnection`, `NetworkManager`) to Mojang names (`ServerPlayer`, `ServerGamePacketListenerImpl`, `Connection`). EaglerXServer's Bukkit platform uses just reflection, but anchored every reflection on NMS types. Those names changed in 1.17, breaking every reflection site.

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
- **BungeeCord/Velocity modules** — untouched (it already supports 1.21 on those platforms, so no need to change any of that).

## EaglerXPaper-Exclusive Features

These are features added by EaglerXPaper that are not in upstream EaglerXServer:

### Skin Cache Pre-warming

On server start, EaglerXPaper reads `usercache.json` and asynchronously pre-downloads skins for recently-seen players from Mojang's sessionserver API. This means when a player joins for the first time, their skin is already cached and displays instantly — no 2-3 second stall on first connect.

The prewarmer is conservative about Mojang's API rate limits (max 2 concurrent requests, 500ms minimum between fetches) and runs on low-priority background threads so it won't slow down server startup. If Mojang's API is unreachable, it silently skips those players.

**Config** (`settings.yml`):
```yaml
skin_cache_prewarm:
  enable: true          # Set to false to disable
  max_players: 50       # Max players to pre-warm (limits API calls)
```

### Adaptive Packet Batching

EaglerXPaper automatically batches outbound packets for Eaglercraft connections that are sending many packets rapidly (e.g. during chunk loading or heavy entity updates). This reduces the number of TCP flushes, which cuts per-frame overhead — especially helpful for mobile/slow connections.

The batcher is self-adaptive:
- **Idle connections** (few packets per second) — packets pass through immediately with zero added latency
- **Burst connections** (20+ packets in 100ms) — packets are buffered for up to 2ms and flushed as a batch
- **Sustained bursts** — forced flush every 50ms to cap latency
- **Timing-critical packets** (combat, entity velocity, health updates, particles, etc.) — detected by reading the 1.8 protocol packet ID and flushed immediately with zero delay. This ensures mace smash attacks, wind charges, and other combat mechanics work without latency.

It sits after the frame codec in the Netty pipeline, so it sees raw ByteBufs and can identify packet types. It also caps the buffer at 256KB to prevent memory pressure from large chunk data packets.

**Config** (`settings.yml`):
```yaml
adaptive_packet_batching:
  enable: true           # Set to false to disable
```

Both features are enabled by default and require no configuration.

### Diagnostic Commands

EaglerXPaper adds two commands to help operators manage and debug their server:

**`/eaglerdiagnose`** (alias: `/eaglerdiag`) — runs a full health check and prints:
- Plugin version and platform
- TLS status (enabled/disabled)
- IP forwarding status
- Dual-stack mode
- WebSocket frame size limit
- Allowed Minecraft protocol range
- Skin prewarming and adaptive batching status
- ViaVersion/ViaBackwards/ViaRewind installation status
- Number of Eaglercraft players online

Requires permission: `eaglercraft.command.diagnose`

**`/eaglerclients`** (alias: `/eaglerclientlist`) — shows a live table of all connected Eaglercraft players:
- Player name
- Client brand (EaglercraftX, Resent, etc.)
- Minecraft version (1.8, 1.7, 1.12.2, etc.)
- Eaglercraft protocol version (v3/v4/v5, with 1.5.2 indicator for Rewind players)
- Real IP address (if IP forwarding is enabled)

Requires permission: `eaglercraft.command.clients`

### ViaVersion Auto-Detection

On server startup, EaglerXPaper automatically checks if ViaVersion, ViaBackwards, and ViaRewind are installed. If ViaVersion is missing, it prints a prominent warning in the console explaining that Eaglercraft clients won't be able to join. If ViaBackwards or ViaRewind are missing, it prints a recommendation to install them.

This check runs after all plugins have loaded (via `softdepend`), so it won't produce false warnings about plugins that are actually installed.

### Config Auto-Recovery

If any config file (settings.yml, listener.yml, etc.) has a YAML syntax error — whether from a version mismatch, a corrupted edit, or a bad comment insertion — EaglerXPaper will automatically:

1. Log a warning identifying the broken file
2. Rename the broken file to `<filename>.broken` (preserving your old config for reference)
3. Regenerate a fresh config with all default values
4. Continue startup normally

This means a broken config file will never prevent your server from starting. You can compare the `.broken` file with the new one to see what changed, and manually re-apply any custom settings.

## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.21.x server's `plugins/` folder along with [ViaVersion](https://hangar.papermc.io/ViaVersion/ViaVersion/versions), [ViaBackwards](https://hangar.papermc.io/ViaVersion/ViaBackwards/versions), and [ViaRewind](https://hangar.papermc.io/ViaVersion/ViaRewind/versions).
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. OPTIONAL (only needed if you use BungeeCord or Velocity) — Configure your reverse proxy / tunnel. See [the regular EaglerXServer setup guide](https://github.com/lax1dude/eaglerxserver/blob/main/CONFIG.md) for details.
5. Connect with an Eaglercraft client to `ws://yourserver:25565/` (or `wss://` if using a reverse proxy such as Caddy, Nginx, or EaglerXServer's built-in TLS)
6. That's it! You can configure extra options if needed, but you really don't have to if all you wanted to do was "just get it working".

**Dual-stack mode** is enabled by default — EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

## Building from source

```bash
git clone https://github.com/PlanetDogeCodes/eaglerxpaper.git
cd eaglerxpaper
./gradlew :core:shadowJarBukkit
# Output: core/build/libs/EaglerXPaper.jar
```

Requires Java 17+ and Gradle 8.5+ (wrapper included). The build compiles with the Paper 1.12.2 stub; compatibility with 1.21.x is done via reflection, not compile-time stuff.

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

## Files modified vs regular EaglerXServer

| File | Change |
|------|--------|
| `core/core-platform-bukkit/.../bukkit/NmsNames.java` | **NEW** — multi-version reflection name table |
| `core/core-platform-bukkit/.../bukkit/BukkitUnsafe.java` | Ported all reflection anchors; added `findGameProfileGetter`, `createOwnEventLoopGroup`; synchronized `PropertyInjector` |
| `core/core-platform-bukkit/.../bukkit/async/PlayerPostLoginInjector.java` | Ported reflection anchors; 3-arg constructor support; `findEnumValueByName`; `convertToComponent` for disconnect; GameProfile sync; transferred flag passthrough |
| `core/core-platform-bukkit/.../bukkit/BukkitListener.java` | Clean up orphaned `$eaglerMarker` properties on player quit |
| `core/src/main/java/.../base/EaglerXServer.java` | Removed "modern server version" warning; added prewarmer lifecycle |
| `core/src/main/java/.../base/EaglerListener.java` | `catch (Throwable)` for icon loading; clean error messages |
| `core/src/main/java/.../base/ServerIconLoader.java` | Null-check `ImageIO.read()` |
| `core/src/main/java/.../base/skins/SkinImageLoaderImpl.java` | Null-check `ImageIO.read()` |
| `core/src/main/java/.../base/skins/SkinManagerHelper.java` | Null guard for `getServer()` |
| `core/src/main/java/.../base/skins/SkinCachePrewarmer.java` | **NEW** — skin cache pre-warming on server start |
| `core/src/main/java/.../base/query/MOTDConnectionWrapper.java` | Null-check MOTD list |
| `core/src/main/java/.../base/webview/WebViewManager.java` | Null-check config; bounds-check `DataRunnable` |
| `core/src/main/java/.../base/voice/VoiceManagerLocal.java` | Null-check ICE servers |
| `core/src/main/java/.../base/voice/VoiceManagerRemote.java` | Null-check handler |
| `core/src/main/java/.../base/handshake/HandshakerInstance.java` | Null-check UUID from auth events |
| `core/src/main/java/.../base/pipeline/HTTPInitialInboundHandler.java` | Proper error logging + channel close |
| `core/src/main/java/.../base/pipeline/AdaptivePacketBatcher.java` | **NEW** — adaptive outbound packet batching |
| `core/src/main/java/.../base/pipeline/WebSocketInitialHandler.java` | Insert `AdaptivePacketBatcher` into pipeline |
| `core/src/main/java/.../base/config/EaglerXPaperConfig.java` | **NEW** — config holder for EaglerXPaper features |
| `core/src/main/java/.../base/config/EaglerConfigLoader.java` | Added `skin_cache_prewarm` and `adaptive_packet_batching` config sections |
| `core/src/main/java/.../base/command/CommandDiagnose.java` | **NEW** — `/eaglerdiagnose` health check command |
| `core/src/main/java/.../base/command/CommandClients.java` | **NEW** — `/eaglerclients` player dashboard command |
| `core/src/main/java/.../base/command/ViaVersionDetector.java` | **NEW** — ViaVersion detection holder |
| `core/core-platform-bukkit/.../bukkit/PlatformPluginBukkit.java` | Try/catch `updateRealAddress`; EventLoopGroup ownership; ViaVersion detection; softdepend for Via* |
| `core/src/main/java/.../base/DeferredStartSkinCache.java` | Made `service` field volatile for thread safety |
| `core/core-platform-bukkit/.../bukkit/PlatformPluginBukkit.java` | Try/catch around `updateRealAddress`; EventLoopGroup ownership tracking + shutdown |
| `core/build.gradle` | JAR renamed to `EaglerXPaper.jar` |
| `core/core-platform-bukkit/build.gradle` | Added `api-version: '1.21'` merge task |

## Addon compatibility

| Addon | Status |
|-------|--------|
| [EaglerXRewind](https://github.com/lax1dude/eaglerxserver/tree/main/rewind_v1_5) (1.5.2 client support) | ⚠️ Should work but not runtime-tested on 1.21 |
| [EaglerWeb](https://github.com/lax1dude/eaglerxserver/tree/main/eaglerweb) (HTTP file hosting) | ⚠️ Should work but not runtime-tested on 1.21 |
| [EaglerMOTD](https://github.com/lax1dude/eaglerxserver/tree/main/eaglermotd) | ⚠️ Kind of works; runtime-tested on 1.21, but had some issues that are too minor to fix right now |

The addon JARs from upstream EaglerXServer releases use the same reflection-based architecture. They *may* work as-is on 1.21, but if they throw reflection errors, the same `NmsNames`-style porting technique applies. The source for all addons is included in this repo under their respective directories.

## Credits

- **Original EaglerXServer:** [lax1dude](https://github.com/lax1dude) — the entire plugin architecture, Eaglercraft protocol implementation, and dual-stack design.

EaglerXPaper is a derivative work of EaglerXServer. All credit for the plugin's core functionality goes to lax1dude. This fork only adds version compatibility for Paper 1.17+, and is not a substantial change or rewrite.

## License

Same as EaglerXServer — see [LICENSE](LICENSE).

## Contributing

If you find a bug on a specific Paper version, please open an issue and include:
1. The Paper version (e.g. `paper-1.21.11-132`)
2. The full stack trace from `logs/latest.log`
3. The output of `java -version`

Pull requests are allowed as long as you consent to your code inheriting the same license as EaglerXPaper. 
AI-generated code is accepted as long as it isn't complete slop and you know what you're doing with it.
