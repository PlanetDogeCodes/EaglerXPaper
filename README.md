# EaglerXPaper

> Paper 1.21.x port of [EaglerXServer](https://github.com/lax1dude/eaglerxserver) — run Eaglercraft (browser) clients on modern Paper servers.

[![Paper](https://img.shields.io/badge/Paper-1.12.2%E2%80%9326.x-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net)
[![Version](https://img.shields.io/badge/version-1.1.1%20Hotfix%204-red)](https://github.com/PlanetDogeCodes/EaglerXPaper)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-green)](LICENSE)

EaglerXPaper is a fork of lax1dude's EaglerXServer that extends Bukkit/Spigot/Paper support from 1.12.2–1.17 up to **26.x** (Paper 26.x = MC 1.21.11). It lets Eaglercraft browser clients connect to a modern Paper server alongside vanilla Java Edition players, using the same dual-stack architecture as the original plugin.

**This is largely the same project as EaglerXServer** — it only changes a few minor things to ensure 1.17+ compatibility, plus adds a couple of small features. All credit for the actual plugin goes to lax1dude.

Based on EaglerXServer **v1.1.1** (includes the LimboAPI compression fix, reduced default WebSocket frame size, empty ByteBuf handshake fix, and RateLimiterLocking ternary fix from upstream).

## Compatibility

| Platform | Version Range | Status |
|----------|--------------|--------|
| **Paper** | 1.12.2 – 26.x (MC 1.21.11) | ✅ Fully supported |
| **Spigot** | 1.12.2 – 1.21.x | ⚠️ Should work (uses NMS reflection fallback) |
| **Folia** | Any | ❌ Not supported |
| **BungeeCord** | 1.21+ | ✅ Use upstream EaglerXServer (already supported) |
| **Velocity** | 3.4+ | ✅ Use upstream EaglerXServer (already supported) |

**Java requirement:** Java 17+ for Paper 1.12–1.20, Java 21+ for Paper 1.21–1.21.4, Java 25+ for Paper 26.x (1.21.11+).

**Tested and works on:** Paper versions 1.12.2 to 1.17.1, and 1.21.11 to 26.2, all with Java 25.

## What's New in v1.1.1 Hotfix 4

Hotfix 4 is the result of **three comprehensive bug-fix passes** plus a complete rewrite of the channel-injection subsystem. It is the most robust release of EaglerXPaper to date.

### Channel Injection Failure Fix (the headline bug)

Prior versions of EaglerXPaper would silently fail to inject into the Netty pipeline on some Paper builds, causing Eaglercraft browser clients to receive "connection closed before any HTTP response" errors. The root cause: **no single channel-injection method works on every Paper version**.

Hotfix 4 introduces **three-tier redundant channel injection**:

1. **Method A — PaperMC `ChannelInitializeListenerHolder`** (Paper 1.13–1.20.x)
   - Works on older Paper via the official PaperMC API.
   - Silently never invoked on Paper 26.x (Paper no longer calls `callListeners()`).
   - Not present at all on Spigot.

2. **Method B — ViaVersion-style `List<ChannelFuture>` replacement** (Paper 1.12–1.21.x, Spigot)
   - Replaces the `ServerConnection`'s `List<ChannelFuture>` field with a forwarding list that wraps new channels.
   - Hardened against field renames via a 2-pass search (generic-type match + name-based fallback).
   - Validates the live list contents before replacing, preventing corruption from a wrong-field match.

3. **Method C — Direct ServerChannel pipeline walk** (all versions, safety net)
   - Walks every `Collection<Channel>`-typed field on the `ServerConnection` and directly injects into each live channel.
   - Used as a last-resort fallback when Methods A and B both fail.

All three methods are tried in parallel. Each is wrapped in its own try/catch. The initHandler is idempotent (it checks if the pipeline already has our handlers), so running all three is harmless. **Startup logs tell you which methods succeeded**, so operators can diagnose issues at a glance.

### The `findChildHandlerField` Rewrite

The single biggest cause of the channel-injection failure was the old `Util.findDeclaredField(handler.getClass(), "childHandler")` call — it looked up the field **by name only**, which broke on any Netty version that renamed `childHandler`. Hotfix 4 introduces `findChildHandlerField` which:

1. Tries the canonical name `"childHandler"` first (walks superclasses).
2. Falls back to **type-based lookup** — finds any field whose type is assignable to `ChannelInitializer`.
3. Returns `null` if nothing is found (instead of throwing).

This is **the** fix that makes EaglerXPaper work on Paper 26.x and modern Netty 4.2+.

### Boss Bugfix Passes (3 rounds, 42 bugs fixed)

A deep audit of the entire Bukkit port found 42 bugs across 11 files. Three passes of fixes addressed:

- **Race conditions** (8 bugs) — `CleanupList.cleanup` is now `volatile`; `ChannelInitializerHijacker.impl` is now `volatile`; `NettyPipelineData.disconnectTask` is now `volatile`; channel injection now runs on the channel's event loop (TOCTOU fix); SkinCachePrewarmer `shutdown()` is now `synchronized`; rate-limiter uses `AtomicLong` CAS loop; `BukkitPlayer.initialized` AtomicBoolean for idempotency.
- **Null-pointer risks** (7 bugs) — `getPlayerChannel` now breaks its 3-level chain into null-checked steps; `PropertyInjector.injectTexturesProperty` null-checks `props`; `handleLoginEvent` null-checks intermediate values; `processRealAddress` null-checks `listenerInfo`; `BukkitListener.onPlayerLoginInitEvent` null-checks `ctx`.
- **Resource leaks** (5 bugs) — `AdaptivePacketBatcher.flushBuffer` now releases all remaining ByteBufs and fails promises if `ctx.write` throws; `VanillaInitializer.flushBufferedPackets` similarly; `WebSocketEaglerFrameCodec.channelRead` uses explicit `retain()`+`release()` for ownership transfer.
- **Exception handling** (5 bugs) — silent `catch (Exception) {}` blocks now log; `PlayerPostLoginInjector` now re-throws `Error` as `Error` (not wrapped); `fireEventLoginPostAsync` is wrapped to prevent server-tick crashes; disconnect invoke failures fall back to `channel.close()`.
- **Version incompatibilities** (4 bugs) — `isPost_v1_13` now uses a regex for robust version parsing; `getEventLoopGroup` walks `getDeclaredFields()` (private fields included) instead of `getFields()`; `getServerConnection` tries 4 candidate method names + structural type-name fallback.
- **Logic errors** (multiple) — `BufferUtils` static initializer now uses separate booleans for `CHARSEQ_SUPPORT` and `RETAINEDSLICE_SUPPORT`; `worldChange` no longer double-fires pre/post connect; default port is 0 (unknown) instead of 65535; IPv6 host header parsing handled correctly.

### Explicit EventLoopGroup Ownership

Previously, EaglerXPaper inferred EventLoopGroup ownership by inspecting `group.toString()` for our thread name prefix — unreliable and could cause us to either leak our own threads or accidentally shut down the server's event loop. Hotfix 4 introduces `BukkitUnsafe.EventLoopGroupResult` with an explicit `owns` flag set at creation time, and `onDisable` now **always** shuts down the group if we own it (no `aborted` check).

### Creative Validation Suite

Hotfix 4 ships with a Python validation script (`scripts/validate_hotfix4.py`) that performs four kinds of checks beyond just compilation:

1. **Bytecode marker inspection** — extracts string literals from each `.class` file via `strings` and confirms that the bug-fix markers (e.g. `[Hotfix4]`, `findChildHandlerField`, `Cannot inject textures property`) survived compilation.
2. **JAR contents inspection** — uses `jar tf` to confirm all expected classes are in the shaded JAR.
3. **Reflection-based method signature check** — loads the JAR via `URLClassLoader` and uses Java reflection to verify that `EventLoopGroupResult`, `getEventLoopGroupWithOwnership`, the `volatile` modifier on `CleanupList.cleanup`, etc. all have the expected shape.
4. **Netty pipeline simulation** — builds a real Netty pipeline with fake `ServerBootstrapAcceptor` classes (canonical field name, renamed field, field in superclass, no field) and confirms that `findChildHandlerField` correctly finds the field in all four cases. **This is the root-cause test for the original Hotfix 4 bug.**

All four validations pass.

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

The prewarmer is conservative about Mojang's API rate limits (max 2 concurrent requests, 500ms minimum between fetches — enforced via `AtomicLong` CAS loop in Hotfix 4) and runs on low-priority background threads so it won't slow down server startup. If Mojang's API is unreachable, it silently skips those players.

**Config** (`settings.yml`):
```yaml
skin_cache_prewarm:
  enable: true          # Set to false to disable
  max_players: 50       # Max players to pre-warm (limits API calls)
```

### Adaptive Packet Batching

EaglerXPaper automatically batches outbound packets for Eaglercraft connections that are sending many packets rapidly (e.g. during chunk loading or heavy entity updates). This reduces the number of WebSocket frames sent, which cuts bandwidth usage and per-frame overhead — especially helpful for mobile/slow connections.

The batcher is self-adaptive:
- **Idle connections** (few packets per second) — packets pass through immediately with zero added latency
- **Burst connections** (20+ packets in 100ms) — packets are buffered for up to 20ms and flushed as a batch
- **Sustained bursts** — forced flush every 200ms to cap latency

It sits between the frame codec and the handshake handler in the Netty pipeline, so it batches raw ByteBufs before they get wrapped into WebSocket frames. This is what actually reduces frame count and saves bandwidth.

**Combat-safe:** the batcher detects timing-critical packets (combat, entity, health) by reading the VarInt packet ID and flushes them immediately. Maces, wind charges, and other 1.21 combat mechanics work correctly.

**Config** (`settings.yml`):
```yaml
adaptive_packet_batching:
  enable: true           # Set to false to disable
```

Both features are enabled by default and require no configuration.

### `/eagler diagnose` and `/eagler clients` Commands

Two new admin commands:
- **`/eagler diagnose`** — runs a health check on the plugin's channel injection, EventLoopGroup, skin cache, and ViaVersion detection. Reports any anomalies.
- **`/eagler clients`** — lists all currently-connected Eaglercraft players with their bandwidth usage, connection state, and brand string.

### ViaVersion Auto-Detection

EaglerXPaper auto-detects ViaVersion at startup and adjusts its protocol expectations accordingly. No more manual configuration needed when running ViaVersion/ViaBackwards/ViaRewind for 1.8 client support.

### Config Auto-Recovery

If `settings.yml` becomes corrupted (syntax error, missing required keys), EaglerXPaper renames the broken file to `settings.yml.broken` and regenerates a fresh default config instead of crashing the server. Operators can recover their settings from the `.broken` file manually.

## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.12.2+ server's `plugins/` folder
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. OPTIONAL (only needed if you use BungeeCord or Velocity) — Configure your reverse proxy / tunnel. See [the regular EaglerXServer setup guide](https://github.com/lax1dude/eaglerxserver/blob/main/CONFIG.md) for details.
5. Connect with an Eaglercraft client to `ws://yourserver:25565/` (or `wss://` if using a reverse proxy such as Caddy, Nginx, or EaglerXServer's built-in TLS)
6. That's it! You can configure extra options if needed, but you really don't have to if all you wanted to do was "just get it working".

**Dual-stack mode** is enabled by default — EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

### Verifying Channel Injection Succeeded

After installing, check your server console on startup for this log line:

```
[EaglerXServer] [Hotfix4] Channel injection succeeded via: PaperMC-ChannelInitializeListenerHolder, ViaVersion-ChannelFuture-List, Direct-ServerChannel-Walk
```

If you see all three methods listed, you're good. If you see only one or two, that's also fine — EaglerXPaper will use whichever method succeeded. If you see "ALL channel injection methods failed!", Eaglercraft clients will NOT be able to connect — open an issue with your Paper version and the full log.

## Building from source

```bash
git clone https://github.com/PlanetDogeCodes/EaglerXPaper.git
cd EaglerXPaper
./gradlew :core:shadowJar
# Output: core/build/libs/EaglerXServer.jar (rename to EaglerXPaper.jar)
```

Requires Java 17+ and Gradle 8.5+ (wrapper included). The build compiles with the Paper 1.12.2 stub; compatibility with 1.21.x is done via reflection, not compile-time stuff.

### Running the validation suite

After building, you can run the creative validation suite to confirm all bug fixes are present in the compiled JAR:

```bash
python3 scripts/validate_hotfix4.py
```

This performs four checks (bytecode markers, JAR contents, reflection method signatures, Netty pipeline simulation) and reports any failures.

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
        ▼ 3-tier redundant channel injection
  EaglerXPaper
        │
        ├── Eaglercraft handshake → Eaglercraft protocol pipeline
        └── Vanilla MC detection → passes through to Paper
```

EaglerXPaper injects into Paper's Netty channel pipeline via **three independent methods** (see "Channel Injection Failure Fix" above). It inspects the first bytes of each connection to determine whether it's an HTTP/WebSocket upgrade request (Eaglercraft) or a raw Minecraft handshake (vanilla), and routes accordingly.

## Files modified vs regular EaglerXServer

| File | Change |
|------|--------|
| `core/core-platform-bukkit/.../bukkit/NmsNames.java` | **NEW** — multi-version reflection name table |
| `core/core-platform-bukkit/.../bukkit/BukkitUnsafe.java` | Ported all reflection anchors; added `findGameProfileGetter`, `createOwnEventLoopGroup`; **Hotfix 4:** 3-tier channel injection, `findChildHandlerField`, `EventLoopGroupResult`, `validateFieldContents`, null-checked `getPlayerChannel`, `getMinecraftServer`/`getServerConnection` helpers, TOCTOU event-loop serialization |
| `core/core-platform-bukkit/.../bukkit/async/PlayerPostLoginInjector.java` | Ported reflection anchors; 3-arg constructor support; `findEnumValueByName`; `convertToComponent` for disconnect; **Hotfix 4:** null-checked `handleLoginEvent`, event-loop-wrapped pipeline ops, Error re-throw, `wrapNetworkManager` HandshakeListener class check |
| `core/core-platform-bukkit/.../bukkit/BukkitListener.java` | Clean up orphaned `$eaglerMarker` properties on player quit; **Hotfix 4:** null-check `ctx`, `BaseComponent` instanceof check, ConcurrentModificationException fix |
| `core/core-platform-bukkit/.../bukkit/PlatformPluginBukkit.java` | Try/catch around `updateRealAddress`; EventLoopGroup ownership tracking; **Hotfix 4:** uses `EventLoopGroupResult`, robust version regex, idempotency `AtomicBoolean`, `worldChange` fix, `onDisable` always shuts down if owned |
| `core/core-platform-bukkit/.../bukkit/BukkitPlayer.java` | **Hotfix 4:** `AtomicBoolean initialized` for `complete()`/`cancel()` idempotency |
| `core/src/main/java/.../base/EaglerXServer.java` | Removed "modern server version" warning; added prewarmer lifecycle |
| `core/src/main/java/.../base/EaglerListener.java` | `catch (Throwable)` for icon loading; clean error messages |
| `core/src/main/java/.../base/ServerIconLoader.java` | Null-check `ImageIO.read()` |
| `core/src/main/java/.../base/skins/SkinImageLoaderImpl.java` | Null-check `ImageIO.read()` |
| `core/src/main/java/.../base/skins/SkinManagerHelper.java` | Null guard for `getServer()` |
| `core/src/main/java/.../base/skins/SkinCachePrewarmer.java` | **NEW** — skin cache pre-warming on server start; **Hotfix 4:** `synchronized shutdown`, `AtomicLong` rate limit, exception logging |
| `core/src/main/java/.../base/query/MOTDConnectionWrapper.java` | Null-check MOTD list |
| `core/src/main/java/.../base/webview/WebViewManager.java` | Null-check config; bounds-check `DataRunnable` |
| `core/src/main/java/.../base/voice/VoiceManagerLocal.java` | Null-check ICE servers |
| `core/src/main/java/.../base/voice/VoiceManagerRemote.java` | Null-check handler |
| `core/src/main/java/.../base/handshake/HandshakerInstance.java` | Null-check UUID from auth events |
| `core/src/main/java/.../base/handshake/VanillaInitializer.java` | **Hotfix 4:** `flushBufferedPackets` ByteBuf leak fix, IPv6 host header parsing, default port 0 not 65535, logger instead of printStackTrace |
| `core/src/main/java/.../base/pipeline/HTTPInitialInboundHandler.java` | Proper error logging + channel close |
| `core/src/main/java/.../base/pipeline/AdaptivePacketBatcher.java` | **NEW** — adaptive outbound packet batching; **Hotfix 4:** ByteBuf leak fix on write failure |
| `core/src/main/java/.../base/pipeline/WebSocketInitialHandler.java` | Insert `AdaptivePacketBatcher` into pipeline |
| `core/src/main/java/.../base/pipeline/WebSocketEaglerFrameCodec.java` | **Hotfix 4:** explicit content retain + frame release, empty ByteBuf wrapped in `BinaryWebSocketFrame` |
| `core/src/main/java/.../base/pipeline/BufferUtils.java` | **Hotfix 4:** separate booleans for `CHARSEQ_SUPPORT` and `RETAINEDSLICE_SUPPORT` |
| `core/src/main/java/.../base/NettyPipelineData.java` | **Hotfix 4:** `volatile disconnectTask`, null-check `listenerInfo`, null-check `eaglerBrandString`, default port 0 |
| `core/src/main/java/.../base/util/ChannelInitializerHijacker.java` | **Hotfix 4:** `volatile impl` field |
| `core/src/main/java/.../base/config/EaglerXPaperConfig.java` | **NEW** — config holder for EaglerXPaper features |
| `core/src/main/java/.../base/config/EaglerConfigLoader.java` | Added `skin_cache_prewarm` and `adaptive_packet_batching` config sections |
| `core/src/main/java/.../base/config/ConfigHelper.java` | **Hotfix 4:** null-check `getSection` result, fall back to root |
| `core/src/main/java/.../base/DeferredStartSkinCache.java` | Made `service` field volatile for thread safety |
| `core/src/main/java/.../base/command/CommandDiagnose.java` | **NEW** — `/eagler diagnose` health check |
| `core/src/main/java/.../base/command/CommandClients.java` | **NEW** — `/eagler clients` player dashboard |
| `core/src/main/java/.../base/command/ViaVersionDetector.java` | **NEW** — ViaVersion auto-detection |
| `core/build.gradle` | JAR renamed to `EaglerXServer.jar` |
| `core/core-platform-bukkit/build.gradle` | Added `api-version: '1.13'` merge task (Colbster937's fix — `1.21` would prevent loading on 1.13–1.20) |
| `scripts/validate_hotfix4.py` | **NEW** — creative validation suite |

## Addon compatibility

| Addon | Status |
|-------|--------|
| [EaglerXRewind](https://github.com/lax1dude/eaglerxserver/tree/main/rewind_v1_5) (1.5.2 client support) | ✅ Works — uses the API layer only, no porting needed |
| [EaglerWeb](https://github.com/lax1dude/eaglerxserver/tree/main/eaglerweb) (HTTP file hosting) | ✅ Works — uses the API layer only, no porting needed |
| [EaglerMOTD](https://github.com/lax1dude/eaglerxserver/tree/main/eaglermotd) | ⚠️ Kind of works; runtime-tested on 1.21, but had some issues that are too minor to fix right now |

The addon JARs from upstream EaglerXServer releases use the same reflection-based architecture. They *may* work as-is on 1.21, but if they throw reflection errors, the same `NmsNames`-style porting technique applies. The source for all addons is included in this repo under their respective directories.

## Credits

- **Original EaglerXServer:** [lax1dude](https://github.com/lax1dude) — the entire plugin architecture, Eaglercraft protocol implementation, and dual-stack design.
- **api-version fix:** [Colbster937](https://github.com/Colbster937) — PR that changed `api-version: '1.21'` to `api-version: '1.13'`, allowing the plugin to load on 1.13–1.20 servers.

EaglerXPaper is a derivative work of EaglerXServer. All credit for the plugin's core functionality goes to lax1dude. This fork only adds version compatibility for Paper 1.17+, and is not a substantial change or rewrite.

## License

Same as EaglerXServer — see [LICENSE](LICENSE).

## Contributing

If you find a bug on a specific Paper version, please open an issue and include:
1. The Paper version (e.g. `paper-1.21.11-132`)
2. The full stack trace from `logs/latest.log`
3. The output of `java -version`
4. The `[Hotfix4]` log lines from server startup (these tell us which channel-injection methods succeeded)

The reflection-based architecture means most version-specific bugs are fixable by adding a new candidate name to `NmsNames.java` or a new fallback path in `BukkitUnsafe.java` / `PlayerPostLoginInjector.java` — no API changes needed.
