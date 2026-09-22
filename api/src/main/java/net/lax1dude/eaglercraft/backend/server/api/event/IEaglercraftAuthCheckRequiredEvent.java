/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 */
package net.lax1dude.eaglercraft.backend.server.api.event;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.lax1dude.eaglercraft.backend.server.api.event.IBaseHandshakeEvent;

public interface IEaglercraftAuthCheckRequiredEvent<PlayerObject, ComponentObject>
extends IBaseHandshakeEvent<PlayerObject> {
    public boolean isClientSolicitingPassword();

    @Nonnull
    public byte[] getAuthUsername();

    public boolean isNicknameSelectionEnabled();

    public void setNicknameSelectionEnabled(boolean var1);

    @Nullable
    public byte[] getSaltingData();

    public void setSaltingData(@Nullable byte[] var1);

    @Nullable
    default public EnumAuthType getUseAuthType() {
        return EnumAuthType.getById(this.getUseAuthTypeRaw());
    }

    default public void setUseAuthType(@Nullable EnumAuthType authType) {
        this.setUseAuthTypeRaw(authType != null ? authType.id : (byte)0);
    }

    public byte getUseAuthTypeRaw();

    public void setUseAuthTypeRaw(byte var1);

    @Nullable
    public EnumAuthResponse getAuthRequired();

    public void setAuthRequired(@Nullable EnumAuthResponse var1);

    @Nonnull
    public String getAuthMessage();

    public void setAuthMessage(@Nonnull String var1);

    public boolean getEnableCookieAuth();

    public void setEnableCookieAuth(boolean var1);

    @Nullable
    public ComponentObject getKickMessage();

    public void setKickMessage(@Nullable ComponentObject var1);

    public void setKickMessage(@Nullable String var1);

    default public void kickUser(@Nullable ComponentObject kickMessage) {
        this.setKickMessage(kickMessage);
        this.setAuthRequired(EnumAuthResponse.DENY);
    }

    default public void kickUser(@Nullable String kickMessage) {
        this.setKickMessage(kickMessage);
        this.setAuthRequired(EnumAuthResponse.DENY);
    }

    public static enum EnumAuthType {
        PLAINTEXT((byte)-1),
        EAGLER_SHA256((byte)1),
        AUTHME_SHA256((byte)2);

        private byte id;

        private EnumAuthType(byte id) {
            this.id = id;
        }

        public byte getId() {
            return this.id;
        }

        public static EnumAuthType getById(byte id) {
            switch (id) {
                case -1: {
                    return PLAINTEXT;
                }
                case 1: {
                    return EAGLER_SHA256;
                }
                case 2: {
                    return AUTHME_SHA256;
                }
            }
            return null;
        }
    }

    public static enum EnumAuthResponse {
        SKIP,
        REQUIRE,
        DENY;

    }
}

