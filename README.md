# EaglerXPaper

> Paper 1.21.x port of [EaglerXServer](https://github.com/lax1dude/eaglerxserver) — run Eaglercraft (browser) clients on modern Paper servers.

[![Paper](https://img.shields.io/badge/Paper-1.12.2%E2%80%9326.x-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net)
[![Version](https://img.shields.io/badge/version-1.1.1%20Hotfix%205-red)](https://github.com/PlanetDogeCodes/EaglerXPaper)
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

## How 1.17+ Compatibility Was Achieved

Paper 1.17 switched the runtime NMS from CraftBukkit names (`EntityPlayer`, `PlayerConnection`, `NetworkManager`) to Mojang names (`ServerPlayer`, `ServerGamePacketListenerImpl`, `Connection`). EaglerXServer's Bukkit platform uses just reflection, but anchored every reflection on NMS types. Those names changed in 1.17, breaking every reflection site.

EaglerXPaper fixes this with a **multi-version reflection name table** (`NmsNames.java`) that maps each NMS symbol to the set of simple names it has been known by across all supported versions.

## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.12.2+ server's `plugins/` folder
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. Connect with an Eaglercraft client to `ws://yourserver:25565/`

**Dual-stack mode** is enabled by default — EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

## Building from source

```bash
git clone https://github.com/PlanetDogeCodes/EaglerXPaper.git
cd EaglerXPaper
./gradlew :core:shadowJar
# Output: core/build/libs/EaglerXServer.jar (rename to EaglerXPaper.jar)
```

Requires Java 17+ and Gradle 8.5+ (wrapper included).

## Files modified vs regular EaglerXServer

| File | Change |
|------|--------|
| `core/core-platform-bukkit/.../bukkit/NmsNames.java` | **NEW** — multi-version reflection name table |
| `core/core-platform-bukkit/.../bukkit/BukkitUnsafe.java` | Ported all reflection anchors; **Hotfix 5:** `getPropertyName()`, `getPropertyValue()`, `putProfileProperty()`, `removeProfileProperty()` for authlib 6.x; `getEventLoopGroup()` uses `getDeclaredFields()`; `CleanupList.run()` null check |
| `core/core-platform-bukkit/.../bukkit/async/PlayerPostLoginInjector.java` | **Hotfix 5:** `handleLoginEvent()` uses `putProfileProperty()`; proxy catches `NoSuchElementException` from `setupCompression` (NOT unconditional no-op); marker cleanup uses `BukkitUnsafe.getPropertyName()` |
| `core/core-platform-bukkit/.../bukkit/BukkitListener.java` | **Hotfix 5:** null-check `ctx`; `onQuitEvent()` uses reflection helpers |
| `core/src/main/java/.../base/pipeline/BufferUtils.java` | **Hotfix 5:** separate boolean variables |
| `core/src/main/java/.../base/pipeline/WebSocketEaglerFrameCodec.java` | **Hotfix 5:** explicit `retain()` + `release()` |
| `core/src/main/java/.../base/util/ChannelInitializerHijacker.java` | **Hotfix 5:** `impl` is `volatile` |
| `core/src/main/java/.../base/NettyPipelineData.java` | **Hotfix 5:** `disconnectTask` is `volatile` |
| `backend-rpc-core/.../BukkitUnsafe.java` | **Hotfix 5:** `findGameProfileGetter()`, `getPropertyValue()` reflection helper |

## Credits

- **Original EaglerXServer:** [lax1dude](https://github.com/lax1dude) — the entire plugin architecture, Eaglercraft protocol implementation, and dual-stack design.

EaglerXPaper is a derivative work of EaglerXServer. All credit for the plugin's core functionality goes to lax1dude.

## License

Same as EaglerXServer — see [LICENSE](LICENSE).
