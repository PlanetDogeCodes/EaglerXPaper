/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

package net.lax1dude.eaglercraft.backend.rpc.bukkit;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class BukkitUnsafe {

        private static final VarHandle CLASS_CRAFTPLAYER_HANDLE;

        static {
                try {
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        CLASS_CRAFTPLAYER_HANDLE = lookup.findStaticVarHandle(BukkitUnsafe.class, "class_CraftPlayer", Class.class);
                } catch(ReflectiveOperationException ex) {
                        throw new ExceptionInInitializerError(ex);
                }
        }

        private static volatile Class<?> class_CraftPlayer = null;
        private static Method method_CraftPlayer_getHandle = null;
        private static Class<?> class_EntityPlayer = null;
        private static Method method_EntityPlayer_getProfile = null;

        private static synchronized void bindCraftPlayer(Player playerObject) {
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() != null) {
                        return;
                }
                Class<?> clz = playerObject.getClass();
                try {
                        method_CraftPlayer_getHandle = clz.getMethod("getHandle");
                        Object entityPlayer = method_CraftPlayer_getHandle.invoke(playerObject);
                        Class<?> clz2 = entityPlayer.getClass();
                        // Hotfix 4: use findGameProfileGetter instead of blindly calling getProfile().
                        // On MC 1.21+, getProfile() returns ResolvableProfile (not GameProfile),
                        // which would cause a ClassCastException at the call site. We need to
                        // find a method that actually returns GameProfile.
                        method_EntityPlayer_getProfile = findGameProfileGetter(clz2);
                        class_EntityPlayer = clz2;
                        CLASS_CRAFTPLAYER_HANDLE.setRelease(clz);
                } catch (Exception ex) {
                        throw new RuntimeException("Reflection failed!", ex);
                }
        }

        /**
         * Hotfix 4: Finds a GameProfile-returning getter on the EntityPlayer class.
         * On 1.17+ the canonical method is getGameProfile(); on 1.21+ getProfile()
         * returns ResolvableProfile (which would ClassCastException), so we explicitly
         * check the return type. Falls back to walking all 0-arg methods returning
         * GameProfile.
         */
        private static Method findGameProfileGetter(Class<?> entityPlayerClass) {
                // Try getGameProfile() first (1.17+ Mojang mappings)
                try {
                        Method m = entityPlayerClass.getMethod("getGameProfile");
                        if (GameProfile.class.isAssignableFrom(m.getReturnType())) {
                                return m;
                        }
                } catch (NoSuchMethodException ex) {
                }
                // Try getProfile() but only if it returns GameProfile (not ResolvableProfile)
                try {
                        Method m = entityPlayerClass.getMethod("getProfile");
                        if (GameProfile.class.isAssignableFrom(m.getReturnType())) {
                                return m;
                        }
                } catch (NoSuchMethodException ex) {
                }
                // Fallback: walk all 0-arg methods returning GameProfile
                for (Method m : entityPlayerClass.getMethods()) {
                        if (m.getParameterCount() == 0 && GameProfile.class.isAssignableFrom(m.getReturnType())) {
                                return m;
                        }
                }
                throw new RuntimeException("Could not locate GameProfile getter on " + entityPlayerClass.getName());
        }

        private static final boolean paperProfileAPISupport;

        static {
                boolean paperProfileAPISupport_ = false;
                // Hotfix 4: check both the old and new Paper Profile API package locations.
                try {
                        Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
                        paperProfileAPISupport_ = true;
                } catch (ClassNotFoundException e) {
                        try {
                                Class.forName("io.papermc.paper.profile.PlayerProfile");
                                paperProfileAPISupport_ = true;
                        } catch (ClassNotFoundException e2) {
                                // Paper profile API is unsupported
                        }
                }
                paperProfileAPISupport = paperProfileAPISupport_;
        }

        // Hotfix 4: authlib 6.x compatibility — Property.getValue() was renamed to value().
        // We use reflection to call whichever method exists.
        private static volatile Method propertyGetValueMethod = null;
        private static volatile boolean propertyMethodsInit = false;

        private static synchronized void initPropertyMethods() {
                if (propertyMethodsInit) return;
                try {
                        propertyGetValueMethod = Property.class.getMethod("getValue");
                } catch (NoSuchMethodException e) {
                        try {
                                propertyGetValueMethod = Property.class.getMethod("value");
                        } catch (NoSuchMethodException e2) {
                                propertyGetValueMethod = null;
                        }
                }
                propertyMethodsInit = true;
        }

        private static String getPropertyValue(Property prop) {
                if (prop == null) return null;
                if (!propertyMethodsInit) initPropertyMethods();
                if (propertyGetValueMethod == null) return null;
                try {
                        return (String) propertyGetValueMethod.invoke(prop);
                } catch (Exception e) {
                        return null;
                }
        }

        public static boolean isEaglerPlayerProperty(Player player) {
                if (paperProfileAPISupport) {
                        return isEaglerPlayerPropertyPaper(player);
                } else {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                                bindCraftPlayer(player);
                        }
                        try {
                                Multimap<String, Property> props = ((GameProfile) method_EntityPlayer_getProfile
                                                .invoke(method_CraftPlayer_getHandle.invoke(player))).getProperties();
                                Collection<Property> tex = props.get("isEaglerPlayer");
                                if (!tex.isEmpty()) {
                                        // Hotfix 4: use reflection-based getter for authlib 6.x compatibility
                                        return Boolean.parseBoolean(getPropertyValue(tex.iterator().next()));
                                }
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                                throw new RuntimeException("Reflection failed!", e);
                        }
                        return false;
                }
        }

        private static boolean isEaglerPlayerPropertyPaper(Player player) {
                PlayerProfile profile = player.getPlayerProfile();
                if (profile != null) {
                        for (ProfileProperty o : profile.getProperties()) {
                                if ("isEaglerPlayer".equals(o.getName())) {
                                        return Boolean.parseBoolean(o.getValue());
                                }
                        }
                }
                return false;
        }

}
