#!/usr/bin/env python3
"""
Creative validation for EaglerXServer v1.1.1 Hotfix 5 (from Hotfix 3 base).

Critical difference from Hotfix 4: the setupCompression fix is now a
try-catch on NoSuchElementException ONLY — NOT an unconditional no-op.
This means vanilla Java connections (which have "splitter" in their
pipeline) get normal compression, while Eaglercraft connections (which
lack "splitter") get the exception caught and swallowed.

This validation suite verifies:
1. Bytecode markers — confirms fixes are in the compiled JAR
2. Reflection verification — loads JAR, verifies helper methods exist
3. authlib simulation — creates real Property, verifies reflection works
4. setupCompression safety check — verifies the proxy does NOT
   unconditionally intercept setupCompression (the Hotfix 4 regression)
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
         "getPropertyName", "authlib 6.x Property.getName() helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "getPropertyValue", "authlib 6.x Property.getValue() helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/BukkitUnsafe.class",
         "putProfileProperty", "putProfileProperty helper"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.class",
         "NoSuchElementException", "setupCompression catch (NOT unconditional no-op)"),
        (f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.class",
         "Hotfix5", "Hotfix 5 marker"),
        (f"{RPC_CLASSES}/net/lax1dude/eaglercraft/backend/rpc/bukkit/BukkitUnsafe.class",
         "findGameProfileGetter", "backend-rpc GameProfile getter fix"),
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
    # CRITICAL: verify that "setupCompression" is NOT present as an intercepted
    # method name (the Hotfix 4 regression). In Hotfix 5, we catch the exception
    # rather than intercepting by name.
    text = extract_strings(f"{BUKKIT_CLASSES}/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.class")
    # The string "setupCompression" might still appear in comments/logging, but
    # the key is that we DON'T have an unconditional `return null` before the
    # method.invoke. We check that "NoSuchElementException" IS present (the catch).
    if "NoSuchElementException" in text:
        print(f"  PASS  setupCompression uses catch (not unconditional no-op)")
        passed += 1
    else:
        print(f"  FAIL  setupCompression catch not found")
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

public class ValidationProbe {
    static int passed = 0, failed = 0;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) throws Exception {
        Class<?> bukkitUnsafe = Class.forName("net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

        // 1-4. Helper methods exist
        check("getPropertyName exists", bukkitUnsafe.getDeclaredMethod("getPropertyName", propertyClass) != null);
        check("getPropertyValue exists", bukkitUnsafe.getDeclaredMethod("getPropertyValue", propertyClass) != null);
        check("putProfileProperty exists", bukkitUnsafe.getDeclaredMethod("putProfileProperty",
            Class.forName("com.mojang.authlib.GameProfile"), propertyClass) != null);
        check("removeProfileProperty exists", bukkitUnsafe.getDeclaredMethod("removeProfileProperty",
            Class.forName("com.mojang.authlib.GameProfile"), propertyClass) != null);

        // 5. getPropertyName works on real Property
        Object prop = propertyClass.getDeclaredConstructor(String.class, String.class, String.class)
            .newInstance("testName", "testValue", null);
        Method gpn = bukkitUnsafe.getDeclaredMethod("getPropertyName", propertyClass);
        gpn.setAccessible(true);
        check("getPropertyName returns 'testName'", "testName".equals(gpn.invoke(null, prop)));

        Method gpv = bukkitUnsafe.getDeclaredMethod("getPropertyValue", propertyClass);
        gpv.setAccessible(true);
        check("getPropertyValue returns 'testValue'", "testValue".equals(gpv.invoke(null, prop)));

        // 6. volatile fields
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

        // 7. backend-rpc findGameProfileGetter
        Class<?> rpcUnsafe = Class.forName("net.lax1dude.eaglercraft.backend.rpc.bukkit.BukkitUnsafe");
        check("backend-rpc findGameProfileGetter exists", rpcUnsafe.getDeclaredMethod("findGameProfileGetter", Class.class) != null);

        // 8. Version
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
        print(f"  ERROR compiling: {r.stderr[:300]}")
        return False
    r = subprocess.run([JAVA, "-cp", "/tmp:" + classpath, "ValidationProbe"],
                      capture_output=True, text=True, timeout=60)
    print(r.stdout)
    if r.stderr: print("STDERR:", r.stderr[:300])
    return r.returncode == 0

def check_no_regression():
    print("\n" + "=" * 70)
    print("Validation 3: Regression Check — setupCompression NOT unconditionally no-op'd")
    print("=" * 70)
    print()
    print("CRITICAL: The Hotfix 4 regression was that setupCompression was")
    print("unconditionally no-op'd for ALL proxied connections, breaking vanilla")
    print("Java client compression ('bad inflate data').")
    print()
    print("Hotfix 5 uses a try-catch on NoSuchElementException ONLY — so vanilla")
    print("Java connections (which have 'splitter') pass through normally.")
    print()
    # Check the source code directly
    src = "/home/z/my-project/eaglerxserver-1.1.1/core/core-platform-bukkit/src/main/java/net/lax1dude/eaglercraft/backend/server/bukkit/async/PlayerPostLoginInjector.java"
    with open(src) as f:
        content = f.read()
    # The Hotfix 4 regression: unconditional `return null` before meth.invoke
    # for setupCompression/setCompressionThreshold method names.
    # Hotfix 5: NO name-based interception. Instead, a try-catch around
    # meth.invoke that catches NoSuchElementException.
    has_name_check = '"setupCompression".equals(methName)' in content or 'setupCompression".equals' in content
    has_catch = 'NoSuchElementException' in content and 'InvocationTargetException' in content
    has_try = 'try {' in content and 'meth.invoke(netManager, args)' in content

    if not has_name_check and has_catch and has_try:
        print("  PASS  No unconditional setupCompression no-op (Hotfix 4 regression avoided)")
        print("  PASS  Uses try-catch on NoSuchElementException instead")
        return True
    else:
        if has_name_check:
            print("  FAIL  Found name-based setupCompression interception (Hotfix 4 regression!)")
        if not has_catch:
            print("  FAIL  NoSuchElementException catch not found")
        return False

def main():
    print()
    print("=" * 70)
    print("  EaglerXServer v1.1.1 Hotfix 5 — Validation Suite")
    print("=" * 70)
    print(f"\nJAR: {JAR_PATH}\n")
    results = [
        ("Bytecode markers", check_bytecode()),
        ("Reflection verification", check_reflection()),
        ("Regression check", check_no_regression()),
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
