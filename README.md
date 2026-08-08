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

Hotfix 4 is built on top of Hotfix 3 and fixes two critical Eaglercraft login edge cases on Paper 26.x (MC 1.21.11), plus includes a comprehensive bug-fix pass.

### Bug Fix 1: `NoSuchMethodError: Property.getName()` on authlib 6.x

**Root cause:** Paper 26.x ships authlib 6.x, which renamed `Property.getName()` to `Property.name()` (record-style accessor) and `Property.getValue()` to `Property.value()`. EaglerXServer called `marker.getName()` directly during the login event to insert a marker property into the player's GameProfile — this threw `NoSuchMethodError` and prevented Eaglercraft players from logging in.

**Fix:** Added reflection-based helper methods to `BukkitUnsafe` that try `getName()` first (old authlib), then fall back to `name()` (new authlib 6.x). The helpers are cached for performance and null-safe. All call sites that previously used `property.getName()` or `property.getValue()` now route through `BukkitUnsafe.getPropertyName()` / `BukkitUnsafe.getPropertyValue()`.

**Files changed:**
- `BukkitUnsafe.java` — added `getPropertyName()`, `getPropertyValue()`, `putProfileProperty()`, `removeProfileProperty()` helpers
- `PlayerPostLoginInjector.java` — `handleLoginEvent()` and the proxy's `InvocationHandler` now use the helpers
- `BukkitListener.java` — `onQuitEvent()` now uses the helpers for marker cleanup
- `backend-rpc-core/.../BukkitUnsafe.java` — same fix applied to the backend-rpc module's copy

### Bug Fix 2: `NoSuchElementException: splitter` during `setupCompression`

**Root cause:** When the vanilla Minecraft server calls `Connection.setupCompression(threshold)` on EaglerXServer's proxied NetworkManager, the method internally tries to `pipeline.addAfter("splitter", ...)` — but the Eaglercraft pipeline doesn't have a `"splitter"` handler at that point (it uses WebSocket-level compression instead of Minecraft packet compression). This caused `NoSuchElementException` and broke the login flow.

**Fix:** The proxy's `InvocationHandler` now intercepts `setupCompression` / `setCompressionThreshold` method calls and no-ops them. This is correct because:
1. Eaglercraft clients use WebSocket-level compression (deflate-frame extension)
2. The existing code already skips sending the `ClientboundLoginCompressionPacket` to Eaglercraft clients
3. Minecraft packet-level compression is redundant and unnecessary for Eaglercraft connections

**Redundancy:** The generic passthrough `meth.invoke(netManager, args)` is also wrapped in a try-catch that catches `NoSuchElementException` and `NullPointerException` from any other pipeline-state issues, logging them instead of crashing.

### Boss Bugfix Passes (3 rounds, 22 bugs fixed)

A deep audit found 22 bugs across the codebase. The critical and high-severity ones were fixed:

- **Critical: backend-rpc-core `BukkitUnsafe.getValue()` and `getProfile()` bugs** — the backend-rpc module's copy of BukkitUnsafe had the same authlib 6.x issues as the main module. Fixed with `findGameProfileGetter()` (checks return type is GameProfile, not ResolvableProfile) and reflection-based `getPropertyValue()`.
- **BufferUtils static initializer** — reused a single boolean variable for two capability checks, causing `RETAINEDSLICE_SUPPORT` to be incorrectly true on older Netty. Fixed with separate variables.
- **WebSocketEaglerFrameCodec ByteBuf leak** — `channelRead` forwarded `msg1.content()` without retaining, causing potential leaks. Fixed with explicit `retain()` + `release()`.
- **ChannelInitializerHijacker race** — `impl` field was not volatile, causing a data race between `deactivate()` and the event loop. Fixed with `volatile`.
- **NettyPipelineData race** — `disconnectTask` was not volatile, causing stale reads that could disconnect already-logged-in players. Fixed with `volatile`.
- **BukkitListener null check** — `ctx` could be null for non-Eagler connections, causing NPE. Fixed with null check.
- **CleanupList null check** — `run()` could NPE if called twice. Fixed with null check and per-element try-catch.
- **getEventLoopGroup** — used `getFields()` (public only) instead of `getDeclaredFields()` (private included), missing private EventLoopGroup fields on Paper 1.17+. Fixed with `getDeclaredFields()` + `setAccessible(true)`.

### Creative Validation Suite

Hotfix 4 ships with a Python validation script (`scripts/validate_hotfix4.py`) that performs:
1. **Bytecode marker inspection** — confirms bug-fix code survived compilation
2. **JAR contents inspection** — confirms all expected classes are in the JAR
3. **Reflection-based method verification** — loads the JAR via URLClassLoader and verifies `getPropertyName`, `getPropertyValue`, `putProfileProperty`, `removeProfileProperty` exist and work correctly, `ChannelInitializerHijacker.impl` is volatile, `NettyPipelineData.disconnectTask` is volatile, backend-rpc `findGameProfileGetter` exists, version is `1.1.1`
4. **authlib simulation** — creates a real `Property` object and verifies `getPropertyName` / `getPropertyValue` return the correct values via reflection

All validations pass.

## How 1.17+ Compatibility Was Achieved

Paper 1.17 switched the runtime NMS from CraftBukkit names (`EntityPlayer`, `PlayerConnection`, `NetworkManager`) to Mojang names (`ServerPlayer`, `ServerGamePacketListenerImpl`, `Connection`). EaglerXServer's Bukkit platform uses just reflection, but anchored every reflection on NMS types. Those names changed in 1.17, breaking every reflection site.

EaglerXPaper fixes this with a **multi-version reflection name table** (`NmsNames.java`) that maps each NMS symbol to the set of simple names it has been known by across all supported versions.

### The core technique

```java
// Before (broke on 1.17+):
if (f.getType().getSimpleName().equals("PlayerConnection")) { ... }

// After (works on all versions):
if (NmsNames.matches(f.getType(), NmsNames.PLAYER_CONNECTION)) { ... }
```

where `NmsNames.PLAYER_CONNECTION = Set.of("ServerGamePacketListenerImpl", "PlayerConnection")`.

## EaglerXPaper-Exclusive Features

These are features added by EaglerXPaper that are not in upstream EaglerXServer:

### Skin Cache Pre-warming

On server start, EaglerXPaper reads `usercache.json` and asynchronously pre-downloads skins for recently-seen players from Mojang's sessionserver API. This means when a player joins for the first time, their skin is already cached and displays instantly — no 2-3 second stall on first connect.

### Adaptive Packet Batching

EaglerXPaper automatically batches outbound packets for Eaglercraft connections that are sending many packets rapidly. This reduces the number of WebSocket frames sent, which cuts bandwidth usage and per-frame overhead — especially helpful for mobile/slow connections.

### Config Auto-Recovery

If `settings.yml` becomes corrupted (syntax error, missing required keys), EaglerXPaper renames the broken file to `settings.yml.broken` and regenerates a fresh default config instead of crashing the server.

## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.12.2+ server's `plugins/` folder
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. OPTIONAL (only needed if you use BungeeCord or Velocity) — Configure your reverse proxy / tunnel. See [the regular EaglerXServer setup guide](https://github.com/lax1dude/eaglerxserver/blob/main/CONFIG.md) for details.
5. Connect with an Eaglercraft client to `ws://yourserver:25565/` (or `wss://` if using a reverse proxy such as Caddy, Nginx, or EaglerXServer's built-in TLS)
6. That's it!

**Dual-stack mode** is enabled by default — EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

## Building from source

```bash
git clone https://github.com/PlanetDogeCodes/EaglerXPaper.git
cd EaglerXPaper
./gradlew :core:shadowJar
# Output: core/build/libs/EaglerXServer.jar (rename to EaglerXPaper.jar)
```

Requires Java 17+ and Gradle 8.5+ (wrapper included).

### Running the validation suite

```bash
python3 scripts/validate_hotfix4.py
```

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

## Files modified vs regular EaglerXServer

| File | Change |
|------|--------|
| `core/core-platform-bukkit/.../bukkit/NmsNames.java` | **NEW** — multi-version reflection name table |
| `core/core-platform-bukkit/.../bukkit/BukkitUnsafe.java` | Ported all reflection anchors; **Hotfix 4:** added `getPropertyName()`, `getPropertyValue()`, `putProfileProperty()`, `removeProfileProperty()` for authlib 6.x; fixed `getTexturesProperty()` to use reflection; `getEventLoopGroup()` uses `getDeclaredFields()`; `CleanupList.run()` null check + try-catch |
| `core/core-platform-bukkit/.../bukkit/async/PlayerPostLoginInjector.java` | Ported reflection anchors; **Hotfix 4:** `handleLoginEvent()` uses `putProfileProperty()`; proxy intercepts `setupCompression`/`setCompressionThreshold`; proxy catches pipeline `NoSuchElementException`/`NPE`; marker cleanup uses `BukkitUnsafe.getPropertyName()` |
| `core/core-platform-bukkit/.../bukkit/BukkitListener.java` | **Hotfix 4:** `onPlayerLoginInitEvent()` null-checks `ctx`; `onQuitEvent()` uses `BukkitUnsafe.getPropertyName()`/`removeProfileProperty()` and collects-to-remove pattern |
| `core/src/main/java/.../base/pipeline/BufferUtils.java` | **Hotfix 4:** separate boolean variables for `CHARSEQ_SUPPORT` and `RETAINEDSLICE_SUPPORT` |
| `core/src/main/java/.../base/pipeline/WebSocketEaglerFrameCodec.java` | **Hotfix 4:** explicit `content.retain()` + `frame.release()`; empty ByteBufs wrapped in `BinaryWebSocketFrame` |
| `core/src/main/java/.../base/util/ChannelInitializerHijacker.java` | **Hotfix 4:** `impl` field is now `volatile` |
| `core/src/main/java/.../base/NettyPipelineData.java` | **Hotfix 4:** `disconnectTask` field is now `volatile` |
| `backend-rpc-core/backend-rpc-core-platform-bukkit/.../BukkitUnsafe.java` | **Hotfix 4:** `findGameProfileGetter()` checks return type; `getPropertyValue()` reflection helper; Paper Profile API checks both old and new package locations |
| `scripts/validate_hotfix4.py` | **NEW** — creative validation suite |

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
