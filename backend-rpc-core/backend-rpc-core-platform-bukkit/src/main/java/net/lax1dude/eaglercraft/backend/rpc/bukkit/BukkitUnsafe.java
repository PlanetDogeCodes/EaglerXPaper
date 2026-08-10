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
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() != null) { return; }
                Class<?> clz = playerObject.getClass();
                try {
                        method_CraftPlayer_getHandle = clz.getMethod("getHandle");
                        Object entityPlayer = method_CraftPlayer_getHandle.invoke(playerObject);
                        Class<?> clz2 = entityPlayer.getClass();
                        method_EntityPlayer_getProfile = findGameProfileGetter(clz2);
                        class_EntityPlayer = clz2;
                        CLASS_CRAFTPLAYER_HANDLE.setRelease(clz);
                } catch (Exception ex) { throw new RuntimeException("Reflection failed!", ex); }
        }

        private static Method findGameProfileGetter(Class<?> entityPlayerClass) {
                try { Method m = entityPlayerClass.getMethod("getGameProfile"); if (GameProfile.class.isAssignableFrom(m.getReturnType())) return m; } catch (NoSuchMethodException ex) {}
                try { Method m = entityPlayerClass.getMethod("getProfile"); if (GameProfile.class.isAssignableFrom(m.getReturnType())) return m; } catch (NoSuchMethodException ex) {}
                for (Method m : entityPlayerClass.getMethods()) { if (m.getParameterCount() == 0 && GameProfile.class.isAssignableFrom(m.getReturnType())) return m; }
                throw new RuntimeException("Could not locate GameProfile getter on " + entityPlayerClass.getName());
        }

        private static final boolean paperProfileAPISupport;

        static {
                boolean paperProfileAPISupport_ = false;
                try { Class.forName("com.destroystokyo.paper.profile.PlayerProfile"); paperProfileAPISupport_ = true; }
                catch (ClassNotFoundException e) { try { Class.forName("io.papermc.paper.profile.PlayerProfile"); paperProfileAPISupport_ = true; } catch (ClassNotFoundException e2) {} }
                paperProfileAPISupport = paperProfileAPISupport_;
        }

        private static volatile Method propertyGetValueMethod = null;
        private static volatile Method getPropertiesMethod = null;
        private static volatile boolean propertyMethodsInit = false;

        private static synchronized void initPropertyMethods() {
                if (propertyMethodsInit) return;
                try { propertyGetValueMethod = Property.class.getMethod("getValue"); }
                catch (NoSuchMethodException e) { try { propertyGetValueMethod = Property.class.getMethod("value"); } catch (NoSuchMethodException e2) { propertyGetValueMethod = null; } }
                try { getPropertiesMethod = GameProfile.class.getMethod("getProperties"); }
                catch (NoSuchMethodException e) { getPropertiesMethod = null; }
                propertyMethodsInit = true;
        }

        private static String getPropertyValue(Property prop) {
                if (prop == null) return null;
                if (!propertyMethodsInit) initPropertyMethods();
                if (propertyGetValueMethod == null) return null;
                try { return (String) propertyGetValueMethod.invoke(prop); } catch (Exception e) { return null; }
        }

        private static Object getPropertiesSafe(GameProfile profile) {
                if (profile == null) return null;
                if (!propertyMethodsInit) initPropertyMethods();
                if (getPropertiesMethod == null) return null;
                try { return getPropertiesMethod.invoke(profile); } catch (Exception e) { return null; }
        }

        public static boolean isEaglerPlayerProperty(Player player) {
                if (paperProfileAPISupport) { return isEaglerPlayerPropertyPaper(player); }
                else {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) { bindCraftPlayer(player); }
                        try {
                                GameProfile profile = (GameProfile) method_EntityPlayer_getProfile.invoke(method_CraftPlayer_getHandle.invoke(player));
                                Object props = getPropertiesSafe(profile);
                                if (props == null) return false;
                                Method getMethod = props.getClass().getMethod("get", Object.class);
                                Object texCollection = getMethod.invoke(props, "isEaglerPlayer");
                                if (texCollection instanceof Collection<?> tex && !tex.isEmpty()) {
                                        Object first = tex.iterator().next();
                                        if (first instanceof Property p) { return Boolean.parseBoolean(getPropertyValue(p)); }
                                }
                        } catch (Exception e) { throw new RuntimeException("Reflection failed!", e); }
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
