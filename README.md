# EaglerXPaper

> Paper 1.21.x port of [EaglerXServer](https://github.com/lax1dude/eaglerxserver) — run Eaglercraft (browser) clients on modern Paper servers.

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-green)](LICENSE)

EaglerXPaper is a fork of lax1dude's EaglerXServer that extends Bukkit/Spigot/Paper support from 1.12.2–1.17 up to Paper 26.x - it lets Eaglercraft browser clients connect to a modern Paper server alongside vanilla Java Edition players, using the same dual-stack architecture as the original plugin.

**THIS IS LARGELY THE SAME PROJECT AS EAGLERXSERVER, AND IT ONLY CHANGED A FEW MINOR THINGS TO ENSURE 1.17+ COMPATABILITY**

## Compatibility

| Platform | Version Range | Status |
|----------|--------------|--------|
| **Paper** | 1.12.2 – 1.21.11+ | ✅ Fully supported |
| **Spigot** | 1.12.2 – 1.21.x | ⚠️ Should work |
| **Folia** | Any | ❌ Not supported |
| **BungeeCord** | 1.21+ | ✅ Use upstream EaglerXServer (already supported) |
| **Velocity** | 3.4+ | ✅ Use upstream EaglerXServer (already supported) |

**Java requirement:** Java 17+ for Paper 1.12–1.20, Java 21+ for Paper 1.21–1.21.4, Java 25+ for Paper 26.x (1.21.11+).

**Tested and works on:** Paper versions 1.12.2 to 1.17.1, and 1.21.11 to 26.2 all with Java 25.

## How 1.17+ Compatibility Was Achieved

Paper 1.17 switched the runtime NMS from CraftBukkit (`EntityPlayer`, `PlayerConnection`, `NetworkManager`) to Mojang (`ServerPlayer`, `ServerGamePacketListenerImpl`, `Connection`). EaglerXServer's Bukkit platform uses just reflection, but anchored every reflection on NMS types. Those names changed in 1.17, breaking every reflection site.

EaglerXPaper fixes this with a **multi-version reflection name table** (`NmsNames.java`) that maps each NMS symbol to the set of simple names it has been known by across all supported versions. It's not perfect nor efficient, but it works.

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

## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.21.x server's `plugins/` folder
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. OPTIONAL (Do this if you use BungeeCord or Velocity) - Configure your reverse proxy / tunnel (see [the regular EaglerXServer setup guide](https://github.com/lax1dude/eaglerxserver/blob/main/CONFIG.md) for details)
5. Connect with an Eaglercraft client to `ws://yourserver:25565/` (or `wss://` if using a reverse proxy such as Caddy, Nginx, or have configured EaglerXServer's built-in TLS)
6. That's it! You can configure extra options if needed, but you really don't have to if all you wanted to do was 'just get it working'.

**Dual-stack mode** is enabled by default — EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

## Building from source

```bash
git clone https://github.com/PlanetDogeCodes/eaglerxpaper.git
cd eaglerpaper
./gradlew :core:shadowJarBukkit
# Output: core/build/libs/EaglerXPaper.jar
```

Requires Java 17+ and Gradle 8.5+ - The build compiles with the Paper 1.12.2 stub; compatibility with 1.21.x is done via reflection, not compile-time stuff.

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
| `core/core-platform-bukkit/.../bukkit/BukkitUnsafe.java` | Ported all reflection anchors; added `findGameProfileGetter`, `createOwnEventLoopGroup`; synchronized `PropertyInjector` |
| `core/core-platform-bukkit/.../bukkit/async/PlayerPostLoginInjector.java` | Ported reflection anchors; 3-arg constructor support; `findEnumValueByName`; GameProfile sync |
| `core/src/main/java/.../base/EaglerXServer.java` | Removed "modern server version" warning |
| `core/src/main/java/.../base/EaglerListener.java` | `catch (Throwable)` for icon loading |
| `core/src/main/java/.../base/ServerIconLoader.java` | Null-check `ImageIO.read()` |
| `core/src/main/java/.../base/skins/SkinImageLoaderImpl.java` | Null-check `ImageIO.read()` |
| `core/src/main/java/.../base/skins/SkinManagerHelper.java` | Null guard for `getServer()` |
| `core/src/main/java/.../base/query/MOTDConnectionWrapper.java` | Null-check MOTD list |
| `core/src/main/java/.../base/webview/WebViewManager.java` | Null-check config; bounds-check `DataRunnable` |
| `core/src/main/java/.../base/voice/VoiceManagerLocal.java` | Null-check ICE servers |
| `core/src/main/java/.../base/voice/VoiceManagerRemote.java` | Null-check handler |
| `core/src/main/java/.../base/handshake/HandshakerInstance.java` | Null-check UUID from auth events |
| `core/src/main/java/.../base/pipeline/HTTPInitialInboundHandler.java` | Proper error logging + channel close |
| `core/core-platform-bukkit/.../bukkit/PlatformPluginBukkit.java` | Try/catch around `updateRealAddress` |
| `core/build.gradle` | JAR renamed to `EaglerXPaper.jar` |
| `core/core-platform-bukkit/build.gradle` | Added `api-version: '1.21'` merge task |

## Addon compatibility

| Addon | Status |
|-------|--------|
| [EaglerXRewind](https://github.com/lax1dude/eaglerxserver/tree/main/rewind_v1_5) (1.5.2 client support) | ⚠️ Should work but not runtime-tested on 1.21 |
| [EaglerWeb](https://github.com/lax1dude/eaglerxserver/tree/main/eaglerweb) (HTTP file hosting) | ⚠️ Should work but not runtime-tested on 1.21 |
| [EaglerMOTD](https://github.com/lax1dude/eaglerxserver/tree/main/eaglermotd) | ⚠️ KIND OF WORKS; runtime-tested on 1.21, but it had some issues that I'm too lazy to diagnose or fix |


## Credits

- **Original EaglerXServer:** [lax1dude](https://github.com/lax1dude) — the entire plugin architecture

EaglerXPaper is a derivative work of EaglerXServer. All credit for the plugin's core functionality goes to lax1dude. This fork only adds version compatibility for Paper 1.17+, and is not a substantial change or rewrite.

## License

Same as EaglerXServer — see [LICENSE](LICENSE).

## Contributing

If you find a bug on a specific Paper version, please open an issue and include:
1. The Paper version (e.g. `paper-1.21.11-132`)
2. The full latest server log from `logs/latest.log`
3. The output of `java -version`

