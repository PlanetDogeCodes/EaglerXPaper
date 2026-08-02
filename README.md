# EaglerXPaper

> Paper 1.21.x port of [EaglerXServer](https://github.com/lax1dude/eaglerxserver) — run Eaglercraft (browser) clients on modern Paper servers.

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-green)](LICENSE)

EaglerXPaper is a fork of lax1dude's EaglerXServer that extends Bukkit/Spigot/Paper support from 1.12.2–1.17 up to **1.21.x** (Paper 26.x). It lets Eaglercraft browser clients connect to a modern Paper server alongside vanilla Java Edition players, using the same dual-stack architecture as the original plugin.

## Compatibility

| Platform | Version Range | Status |
|----------|--------------|--------|
| **Paper** | 1.12.2 – 1.21.11+ | ✅ Fully supported |
| **Spigot** | 1.12.2 – 1.21.x | ⚠️ Should work (uses NMS reflection fallback) |
| **Folia** | Any | ❌ Not supported |
| **BungeeCord** | 1.21+ | ✅ Use upstream EaglerXServer (already supported) |
| **Velocity** | 3.4+ | ✅ Use upstream EaglerXServer (already supported) |

**Java requirement:** Java 17+ for Paper 1.12–1.20, Java 21+ for Paper 1.21–1.21.4, Java 25+ for Paper 26.x (1.21.11+).

**Runtime-verified on:** Paper 26.2-87 (Minecraft 1.21.11) with Java 25.

## How 1.17+ Compatibility Was Achieved

Paper 1.17 switched the runtime NMS mappings from CraftBukkit names (e.g. `EntityPlayer`, `PlayerConnection`, `NetworkManager`) to Mojang names (e.g. `ServerPlayer`, `ServerGamePacketListenerImpl`, `Connection`). EaglerXServer's Bukkit platform uses pure runtime reflection — zero compile-time NMS references — but anchored every reflection on the *simple class names* of NMS types. Those names changed in 1.17, breaking every reflection site.

EaglerXPaper fixes this with a **multi-version reflection name table** (`NmsNames.java`) that maps each NMS symbol to the set of simple names it has been known by across all supported versions. Reflection sites consult these sets rather than comparing against a single literal.

### The core technique

```java
// Before (broke on 1.17+):
if (f.getType().getSimpleName().equals("PlayerConnection")) { ... }

// After (works on all versions):
if (NmsNames.matches(f.getType(), NmsNames.PLAYER_CONNECTION)) { ... }
```

where `NmsNames.PLAYER_CONNECTION = Set.of("ServerGamePacketListenerImpl", "PlayerConnection")`.

### What changed between 1.12 and 1.21

| Change | MC Version | Impact | Fix |
|--------|-----------|--------|-----|
| Mojang mappings at runtime | 1.17 | All simple-name reflection anchors broke | `NmsNames` candidate-name table |
| Field visibility `public` → `private final` | 1.17 | `getFields()` missed private fields | Switched to `getDeclaredFields()` + `setAccessible(true)` + superclass walking |
| `CraftPlayer.addChannel(String)` removed | 1.20 | `addPlayerChannel` threw `NoSuchMethodException` | Made optional; global Messenger registration is sufficient on 1.13+ |
| `LazyInitVar` removed from `ServerConnection` | 1.17 | `getEventLoopGroup` couldn't find the event loop | Added modern direct-field lookup + own-EventLoopGroup fallback |
| `getServerConnection()` renamed to `getConnection()` | 1.17 | Legacy injection fallback broke | Try `getConnection()` first, fall back to `getServerConnection()` |
| `getDedicatedServerProperties()` removed | 1.21 | `isEnableNativeTransport` threw | Try both method names, default to `true` |
| `EnumProtocolDirection` renamed to `PacketFlow` | 1.21 | Login injector couldn't find direction field | Added `NmsNames.PROTOCOL_DIRECTION` candidate set |
| `ServerLoginPacketListenerImpl` constructor gained 3rd arg | 1.20.2 | 2-arg constructor match failed | Accept both 2-arg and 3-arg forms, pass `Boolean.FALSE` for transfer flag |
| `getProfile()` returns `ResolvableProfile` (not `GameProfile`) | 1.21 | `ClassCastException` on every login | `findGameProfileGetter()` tries `getGameProfile()` first, validates return type |
| CONFIGURATION protocol phase added | 1.20.2 | LOGIN → PLAY now goes through CONFIGURATION | Existing `awaitPlayState` architecture handles it transparently |
| `disconnect(String)` → `disconnect(Component)` | 1.17+ | `getMethod("disconnect", String.class)` threw | Try `Component` first, fall back to `String`, then any single-arg `disconnect` |
| Login state enum `EnumProtocolState` → `State` | 1.17 | State field lookup failed | `NmsNames.LOGIN_STATE_ENUM_SIMPLE` candidate set |
| `obj[length - 2]` state-skip trick unreliable | 1.19+ | New enum values added, position shifted | Explicit name-based lookup: `findEnumValueByName("READY_TO_ACCEPT")` |
| Packet class renames (`PacketLoginOutSuccess` → `ClientboundGameProfilePacket`, etc.) | 1.17 | Packet-name string comparisons failed | All packet names now go through `NmsNames` candidate sets |
| `send(Packet, ChannelFutureListener)` — subinterface | 1.21 | `params[1].equals(GenericFutureListener.class)` failed | Switched to `isAssignableFrom` |

### Defensive hardening

Beyond the version compatibility fixes, all six feature areas (skins, voice, WebView, MOTD, auth, IP forwarding) received a defensive hardening pass:

- **Null-safe image loading** — `ImageIO.read()` returns null for corrupt/invalid images; all callers now null-check before use
- **GameProfile property synchronization** — `PropertyInjector` and the login marker insert/remove now synchronize on the GameProfile to prevent `ConcurrentModificationException` when other plugins iterate properties concurrently
- **Null-guarded auth events** — Auth event handlers that return `ALLOW` without setting a UUID no longer NPE; the offline-mode UUID is preserved as a fallback
- **Null-guarded voice/WebView** — ICE servers, voice handlers, and WebView chunk schedulers all have null/bounds checks with graceful degradation
- **Error-tolerant HTTP inbound** — The HTTP initial handler now logs via the plugin logger, always closes the channel on error, and handles missing pipeline data gracefully
- **Reflection-failure-tolerant IP forwarding** — `updateRealAddress` is wrapped in try/catch so a reflection failure degrades gracefully instead of crashing the channel initializer

### What was NOT changed

- **Config structure** — identical to upstream EaglerXServer. Existing `plugins/EaglercraftXServer/` configs work without migration.
- **Plugin name** — still `"EaglercraftXServer"` internally, so EaglerXRewind and EaglerWeb dependency resolution works unchanged.
- **BungeeCord/Velocity modules** — untouched (upstream already supports 1.21 on those platforms).
- **Build target** — still compiles against `paper-api 1.12.2-R0.1-SNAPSHOT` as a stub. Runtime reflection handles everything else. This preserves backward compatibility with 1.12.2 servers.

## Installation

1. Download `EaglerXPaper.jar`
2. Place in your Paper 1.21.x server's `plugins/` folder
3. Start the server — config files generate in `plugins/EaglercraftXServer/`
4. Configure your reverse proxy / tunnel (see [SETUP-GUIDE](https://github.com/lax1dude/eaglerxserver/blob/main/CONFIG.md) for details)
5. Connect with an Eaglercraft client to `ws://yourserver:25565/` (or `wss://` if using a TLS-terminating reverse proxy)

**Dual-stack mode** is enabled by default — EaglerXPaper shares the main server port (25565) and auto-detects whether each connection is vanilla Minecraft TCP or an Eaglercraft WebSocket.

## Building from source

```bash
git clone https://github.com/YOUR_USERNAME/eaglerpaper.git
cd eaglerpaper
./gradlew :core:shadowJarBukkit
# Output: core/build/libs/EaglerXPaper.jar
```

Requires Java 17+ and Gradle 8.5+ (wrapper included). The build compiles against the Paper 1.12.2 API stub; runtime compatibility with 1.21.x is achieved via reflection, not compile-time dependencies.

## Architecture

```
Eaglercraft Client (ws:// or wss://)
        │
        ▼
  [Reverse Proxy / Tunnel]     ← TLS termination (Caddy, nginx, playit.gg, CloudFlare, etc.)
        │
        ▼ plaintext WebSocket
  Paper 1.21.x (port 25565)
        │
        ▼ ChannelInitializeListener injection
  EaglerXPaper
        │
        ├── Eaglercraft handshake → Eaglercraft protocol pipeline
        └── Vanilla MC detection → passes through to Paper
```

EaglerXPaper injects into Paper's Netty channel pipeline via Paper's `ChannelInitializeListener` API (the supported, stable injection method). It inspects the first bytes of each connection to determine whether it's an HTTP/WebSocket upgrade request (Eaglercraft) or a raw Minecraft handshake (vanilla), and routes accordingly.

## Files modified vs upstream

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

**Total:** 14 source files modified + 1 new file + 2 build files modified. No other modules (BungeeCord, Velocity, EaglerXRewind, EaglerWeb, etc.) were touched.

## Addon compatibility

| Addon | Status |
|-------|--------|
| [EaglerXRewind](https://github.com/lax1dude/eaglerxserver/tree/main/rewind_v1_5) (1.5.2 client support) | ⚠️ Source included; depends on EaglerXServer by name — should work but not runtime-tested on 1.21 |
| [EaglerWeb](https://github.com/lax1dude/eaglerxserver/tree/main/eaglerweb) (HTTP file hosting) | ⚠️ Source included; depends on EaglerXServer by name — should work but not runtime-tested on 1.21 |
| [EaglerMOTD](https://github.com/lax1dude/eaglerxserver/tree/main/eaglermotd) | ⚠️ Source included; not runtime-tested on 1.21 |

The addon JARs from upstream EaglerXServer releases use the same reflection-based architecture. They *may* work as-is on 1.21, but if they throw reflection errors, the same `NmsNames`-style porting technique applies. The source for all addons is included in this repo under their respective directories.

## Credits

- **Original EaglerXServer:** [lax1dude](https://github.com/lax1dude) — the entire plugin architecture, Eaglercraft protocol implementation, and dual-stack design.
- **1.21.x port:** This fork — cross-version reflection layer, defensive hardening, and Paper 26.x compatibility fixes.

EaglerXPaper is a derivative work of EaglerXServer. All credit for the plugin's core functionality goes to lax1dude. This fork only adds version compatibility for Paper 1.21.x.

## License

Same as upstream EaglerXServer — see [LICENSE](LICENSE). The upstream project places no restrictions on forks; the creators request that you don't take credit for portions without substantial modifications.

## Contributing

If you find a bug on a specific Paper version, please include:
1. The Paper version (e.g. `paper-1.21.11-132`)
2. The full stack trace from `logs/latest.log`
3. The output of `java -version`

The reflection-based architecture means most version-specific bugs are fixable by adding a new candidate name to `NmsNames.java` or a new fallback path in `BukkitUnsafe.java` / `PlayerPostLoginInjector.java` — no API changes needed.
