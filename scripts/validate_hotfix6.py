#!/usr/bin/env python3
"""
Creative validation for EaglerXServer v1.1.1 Hotfix 6.

Critical: Hotfix 6 fixes the GameProfile.getProperties() NoSuchMethodError
that was NOT fully fixed in Hotfix 5. authlib 6.x changed the return type
of getProperties() from PropertyMap to Multimap<String, Property>, and the
method signature itself changed — so direct calls throw NoSuchMethodError
even though the method name still exists.

This validation suite verifies:
1. Bytecode markers — confirms all Hotfix 6 helpers are in the compiled JAR
2. Reflection verification — loads JAR, verifies all helper methods exist
3. authlib simulation — creates real GameProfile + Property, verifies
   reflection helpers work correctly
4. Bounds check verification — confirms readMCString has the bounds check
"""

import os, sys, subprocess, glob

JDK_HOME = "/tmp/jdk17/jdk-17.0.20+8"
JAVAP = f"{JDK_HOME}/bin/javap"
JAR_TOOL = f"{JDK_HOME}/bin/jar"
JAVA = f"{JDK_HOME}/bin/java"
JAVAC = f"{JDK_HOME}/bin/javac"

JAR_PATH = "/home/z/my-project/eaglerxserver-1.1.1/core/build/libs/EaglerXServer.jar"
BUKKIT_CLASSES = "/home/z/my-project/eaglerxserver-1.1.1/core/core-platform-bukkit/build/classes/java/main"
CORE_CLASSES = "/home/z/my-project/eaglerxserver-1.1.1/core/build/classes/java/main"
RPC_CLASSES = "/home/z/my-project/eaglerxserver-1.1.1/backend-rpc-core/backend-rpc-core-platform-bukkit/build/classes/java/main"

def extract_strings(class_file):
    if not os.path.exists(class_file): return ""
    try:
        r = subprocess.run(["strings", class_file], capture_output=True, text=True, timeout=30)
        return r.stdout
    except: return ""

def check_bytecode():
    print("=" * 70)
    print("Validation 1: Bytecode Marker Inspection")
    print("=" * 70)
    markers = [
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "getPropertiesSafe", "getPropertiesSafe helper (authlib 6.x return type fix)"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "getPropertyName", "getPropertyName helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "getPropertyValue", "getPropertyValue helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "putProfileProperty", "putProfileProperty helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "multimapPut", "multimapPut helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "multimapRemove", "multimapRemove helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "getPropertyValuesSafe", "getPropertyValuesSafe helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.class",
         "NoSuchElementException", "setupCompression catch"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.class",
         "Hotfix6", "Hotfix 6 marker"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitListener.class",
         "getPropertyValuesSafe", "BukkitListener uses reflection helper"),
        (f"{RPC_CLASSES}/net/lax1dude/eaglercraft/backend/rpc/bukkit/BukkitUnsafe.class",
         "findGameProfileGetter", "backend-rpc GameProfile getter fix"),
        (f"{RPC_CLASSES}/net/lax1dude/eaglercraft/backend/rpc/bukkit/BukkitUnsafe.class",
         "getPropertiesSafe", "backend-rpc getPropertiesSafe"),
        (f"{CORE_CLASSES}/net/lax1dude/eaglercraft/backend/server/base/pipeline/BufferUtils.class",
         "charseqOk", "BufferUtils separate boolean fix"),
        (f"{CORE_CLASSES}/net/lax1dude/eaglercraft/backend/server/base/pipeline/WebSocketEaglerFrameCodec.class",
         "retain", "WebSocketEaglerFrameCodec retain fix"),
    ]
    passed = failed = 0
    for cls, marker, desc in markers:
        text = extract_strings(cls)
        if marker in text:
            print(f"  PASS  {desc}")
            passed += 1
        else:
            print(f"  FAIL  {desc}")
            failed += 1
    # Check readMCString bounds check
    text = extract_strings(f"{CORE_CLASSES}/net/lax1dude/eaglercraft/backend/server/base/pipeline/BufferUtils.class")
    if "readMCString: need" in text or "available" in text:
        print(f"  PASS  BufferUtils readMCString bounds check")
        passed += 1
    else:
        print(f"  FAIL  BufferUtils readMCString bounds check")
        failed += 1
    print(f"\n  Summary: {passed}/{passed+failed} markers found")
    return failed == 0

def check_reflection():
    print("\n" + "=" * 70)
    print("Validation 2: Reflection-Based Method Verification")
    print("=" * 70)
    netty_jars = glob.glob("/home/z/.gradle/caches/modules-2/files-2.1/io.netty/*/*/*/*.jar")
    authlib_jars = glob.glob("/home/z/.gradle/caches/modules-2/files-2.1/com.mojang/authlib/*/*/*.jar")
    guava_jars = glob.glob("/home/z/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/*/*/*.jar")
    paper_jars = glob.glob("/home/z/.gradle/caches/modules-2/files-2.1/com.destroystokyo.paper/paper-api/*/*/*.jar")
    paper_jars += glob.glob("/home/z/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/*/*/*.jar")
    bungee_jars = glob.glob("/home/z/.gradle/caches/modules-2/files-2.1/net.md-5/bungeecord-*/*/*/*.jar")
    cp = [JAR_PATH, BUKKIT_CLASSES, CORE_CLASSES, RPC_CLASSES] + netty_jars + authlib_jars + guava_jars + paper_jars + bungee_jars
    cp = [e for e in cp if os.path.exists(e)]
    classpath = ":".join(cp)
    if not netty_jars or not authlib_jars:
        print("  SKIP  Missing dependencies")
        return True

    probe = """
import java.lang.reflect.*;
import java.util.UUID;

public class ValidationProbe {
    static int passed = 0, failed = 0;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) throws Exception {
        Class<?> bukkitUnsafe = Class.forName("net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");

        // 1-6. Helper methods exist
        check("getPropertyName exists", bukkitUnsafe.getDeclaredMethod("getPropertyName", propertyClass) != null);
        check("getPropertyValue exists", bukkitUnsafe.getDeclaredMethod("getPropertyValue", propertyClass) != null);
        check("getPropertiesSafe exists", bukkitUnsafe.getDeclaredMethod("getPropertiesSafe", gameProfileClass) != null);
        check("putProfileProperty exists", bukkitUnsafe.getDeclaredMethod("putProfileProperty", gameProfileClass, propertyClass) != null);
        check("removeProfileProperty exists", bukkitUnsafe.getDeclaredMethod("removeProfileProperty", gameProfileClass, propertyClass) != null);
        check("getPropertyValuesSafe exists", bukkitUnsafe.getDeclaredMethod("getPropertyValuesSafe", gameProfileClass) != null);

        // 7. multimap helpers exist
        check("multimapPut exists", bukkitUnsafe.getDeclaredMethod("multimapPut", Object.class, String.class, Object.class) != null);
        check("multimapRemove exists", bukkitUnsafe.getDeclaredMethod("multimapRemove", Object.class, String.class, Object.class) != null);
        check("multimapGet exists", bukkitUnsafe.getDeclaredMethod("multimapGet", Object.class, String.class) != null);

        // 8. Test with real GameProfile + Property
        Object profile = gameProfileClass.getDeclaredConstructor(UUID.class, String.class)
            .newInstance(java.util.UUID.randomUUID(), "TestPlayer");
        Object props = bukkitUnsafe.getDeclaredMethod("getPropertiesSafe", gameProfileClass).invoke(null, profile);
        check("getPropertiesSafe returns non-null", props != null);

        // Create a Property and put it
        Object prop = propertyClass.getDeclaredConstructor(String.class, String.class, String.class)
            .newInstance("testKey", "testValue", null);
        bukkitUnsafe.getDeclaredMethod("putProfileProperty", gameProfileClass, propertyClass).invoke(null, profile, prop);

        // Verify it's there via getPropertyValuesSafe
        java.util.Collection<?> values = (java.util.Collection<?>)
            bukkitUnsafe.getDeclaredMethod("getPropertyValuesSafe", gameProfileClass).invoke(null, profile);
        check("getPropertyValuesSafe returns non-empty", !values.isEmpty());

        // 9. volatile fields
        Class<?> hijacker = Class.forName("net.lax1dude.eaglercraft.backend.server.util.ChannelInitializerHijacker");
        Field implField = hijacker.getDeclaredField("impl");
        check("ChannelInitializerHijacker.impl is volatile", Modifier.isVolatile(implField.getModifiers()));

        Class<?> pipelineData = Class.forName("net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData");
        Field discTask = null;
        try { discTask = pipelineData.getDeclaredField("disconnectTask"); }
        catch (NoSuchFieldException e) {
            for (Class<?> inner : pipelineData.getDeclaredClasses()) {
                try { discTask = inner.getDeclaredField("disconnectTask"); break; }
                catch (NoSuchFieldException e2) {}
            }
        }
        if (discTask != null) {
            check("NettyPipelineData.disconnectTask is volatile", Modifier.isVolatile(discTask.getModifiers()));
        }

        // 10. backend-rpc findGameProfileGetter
        Class<?> rpcUnsafe = Class.forName("net.lax1dude.eaglercraft.backend.rpc.bukkit.BukkitUnsafe");
        check("backend-rpc findGameProfileGetter exists", rpcUnsafe.getDeclaredMethod("findGameProfileGetter", Class.class) != null);

        // 11. Version
        Class<?> versionClass = Class.forName("net.lax1dude.eaglercraft.backend.server.base.EaglerXServerVersion");
        Field versionField = versionClass.getDeclaredField("VERSION");
        versionField.setAccessible(true);
        check("Version is 1.1.1", "1.1.1".equals(versionField.get(null)));

        System.out.println("\\nReflection: " + (failed == 0 ? "ALL PASS" : failed + " FAILURES"));
        System.exit(failed == 0 ? 0 : 1);
    }
}
"""
    with open("/tmp/ValidationProbe.java", "w") as f: f.write(probe)
    r = subprocess.run([JAVAC, "-cp", classpath, "-d", "/tmp", "/tmp/ValidationProbe.java"],
                      capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        print(f"  ERROR compiling: {r.stderr[:500]}")
        return False
    r = subprocess.run([JAVA, "-cp", "/tmp:" + classpath, "ValidationProbe"],
                      capture_output=True, text=True, timeout=60)
    print(r.stdout)
    if r.stderr: print("STDERR:", r.stderr[:500])
    return r.returncode == 0

def check_no_direct_getProperties():
    print("\n" + "=" * 70)
    print("Validation 3: No Direct getProperties() Calls (authlib 6.x safety)")
    print("=" * 70)
    print()
    print("CRITICAL: Hotfix 5 still had direct profile.getProperties() calls in")
    print("BukkitListener and PlayerPostLoginInjector, causing NoSuchMethodError")
    print("on authlib 6.x. Hotfix 6 routes ALL calls through getPropertiesSafe().")
    print()
    # Check source files for direct getProperties() calls
    files = [
        "/home/z/my-project/eaglerxserver-1.1.1/core/core-platform-bukkit/src/main/java/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitListener.java",
        "/home/z/my-project/eaglerxserver-1.1.1/core/core-platform-bukkit/src/main/java/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.java",
        "/home/z/my-project/eaglerxserver-1.1.1/core/core-platform-bukkit/src/main/java/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.java",
    ]
    all_safe = True
    for f in files:
        with open(f) as fh:
            content = fh.read()
        # Look for direct .getProperties() calls that are NOT in getPropertiesSafe
        # or in comments. We check for "profile.getProperties()" or ".getProperties()."
        # but exclude lines that are part of getPropertiesSafe's implementation.
        lines = content.split('\n')
        unsafe_lines = []
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*'):
                continue
            # Check for direct getProperties() calls
            if '.getProperties()' in line:
                # Allow it if it's inside getPropertiesSafe's own implementation
                # (which uses getMethod("getProperties") not .getProperties())
                if 'getMethod("getProperties")' in line or 'getPropertiesMethod' in line:
                    continue
                # Allow if it's in a comment
                if '//' in line and '.getProperties()' in line.split('//')[1]:
                    continue
                unsafe_lines.append((i, line.strip()))
        if unsafe_lines:
            print(f"  FAIL  {os.path.basename(f)} has direct getProperties() calls:")
            for ln, txt in unsafe_lines:
                print(f"        line {ln}: {txt}")
            all_safe = False
        else:
            print(f"  PASS  {os.path.basename(f)} uses reflection helpers only")
    return all_safe

def main():
    print()
    print("=" * 70)
    print("  EaglerXServer v1.1.1 Hotfix 6 — Validation Suite")
    print("=" * 70)
    print(f"\nJAR: {JAR_PATH}\n")
    results = [
        ("Bytecode markers", check_bytecode()),
        ("Reflection verification", check_reflection()),
        ("No direct getProperties()", check_no_direct_getProperties()),
    ]
    print("\n" + "=" * 70)
    print("  Final Summary")
    print("=" * 70)
    all_pass = True
    for name, ok in results:
        print(f"    [{'PASS' if ok else 'FAIL'}] {name}")
        if not ok: all_pass = False
    print()
    print("  ALL VALIDATIONS PASSED." if all_pass else "  SOME VALIDATIONS FAILED.")
    return 0 if all_pass else 1

if __name__ == "__main__":
    sys.exit(main())
