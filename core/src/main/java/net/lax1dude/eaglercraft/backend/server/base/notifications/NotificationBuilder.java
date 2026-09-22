/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.notifications;

import java.util.UUID;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentHelper;
import net.lax1dude.eaglercraft.backend.server.api.notifications.EnumBadgePriority;
import net.lax1dude.eaglercraft.backend.server.api.notifications.INotificationBuilder;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketNotifBadgeShowV4EAG;

public class NotificationBuilder<ComponentObject>
implements INotificationBuilder<ComponentObject> {
    private final IPlatformComponentHelper componentHelper;
    private UUID badgeUUID = null;
    private ComponentObject bodyComponent = null;
    private ComponentObject titleComponent = null;
    private ComponentObject sourceComponent = null;
    private long originalTimestampSec = 0L;
    private boolean silent = false;
    private EnumBadgePriority priority = EnumBadgePriority.NORMAL;
    private UUID mainIconUUID = null;
    private UUID titleIconUUID = null;
    private int hideAfterSec = 10;
    private int expireAfterSec = 3600;
    private int backgroundColor = 0xFFFFFF;
    private int bodyTxtColor = 0xFFFFFF;
    private int titleTxtColor = 0xFFFFFF;
    private int sourceTxtColor = 0xFFFFFF;
    private SPacketNotifBadgeShowV4EAG packetCache = null;
    private boolean packetDirty = true;

    public NotificationBuilder(IPlatformComponentHelper componentHelper) {
        this.componentHelper = componentHelper;
        this.originalTimestampSec = System.currentTimeMillis() / 1000L;
    }

    @Override
    public INotificationBuilder<ComponentObject> copyFrom(INotificationBuilder<ComponentObject> input) {
        NotificationBuilder<ComponentObject> inputCasted = (NotificationBuilder<ComponentObject>)input;
        this.badgeUUID = inputCasted.badgeUUID;
        this.bodyComponent = inputCasted.bodyComponent;
        this.titleComponent = inputCasted.titleComponent;
        this.sourceComponent = inputCasted.sourceComponent;
        this.originalTimestampSec = inputCasted.originalTimestampSec;
        this.silent = inputCasted.silent;
        this.priority = inputCasted.priority;
        this.mainIconUUID = inputCasted.mainIconUUID;
        this.titleIconUUID = inputCasted.titleIconUUID;
        this.hideAfterSec = inputCasted.hideAfterSec;
        this.backgroundColor = inputCasted.backgroundColor;
        this.bodyTxtColor = inputCasted.bodyTxtColor;
        this.titleTxtColor = inputCasted.titleTxtColor;
        this.sourceTxtColor = inputCasted.sourceTxtColor;
        this.packetCache = !inputCasted.packetDirty ? inputCasted.packetCache : null;
        this.packetDirty = inputCasted.packetDirty;
        return this;
    }

    @Override
    public INotificationBuilder<ComponentObject> copyFrom(SPacketNotifBadgeShowV4EAG input) {
        this.badgeUUID = new UUID(input.badgeUUIDMost, input.badgeUUIDLeast);
        try {
            this.bodyComponent = (ComponentObject)this.componentHelper.parseLegacyJSON(input.bodyComponent);
        }
        catch (Exception t) {
            this.bodyComponent = (ComponentObject)this.componentHelper.builder().buildTextComponent().text(input.bodyComponent).end();
        }
        try {
            this.titleComponent = (ComponentObject)this.componentHelper.parseLegacyJSON(input.titleComponent);
        }
        catch (Exception t) {
            this.titleComponent = (ComponentObject)this.componentHelper.builder().buildTextComponent().text(input.titleComponent).end();
        }
        try {
            this.sourceComponent = (ComponentObject)this.componentHelper.parseLegacyJSON(input.sourceComponent);
        }
        catch (Exception t) {
            this.sourceComponent = (ComponentObject)this.componentHelper.builder().buildTextComponent().text(input.sourceComponent).end();
        }
        this.originalTimestampSec = input.originalTimestampSec;
        this.silent = input.silent;
        switch (input.priority) {
            case LOW: {
                this.priority = EnumBadgePriority.LOW;
                break;
            }
            case HIGHER: {
                this.priority = EnumBadgePriority.HIGHER;
                break;
            }
            case HIGHEST: {
                this.priority = EnumBadgePriority.HIGHEST;
                break;
            }
            default: {
                this.priority = EnumBadgePriority.NORMAL;
            }
        }
        this.mainIconUUID = input.mainIconUUIDMost != 0L || input.mainIconUUIDLeast != 0L ? new UUID(input.mainIconUUIDMost, input.mainIconUUIDLeast) : null;
        this.titleIconUUID = input.titleIconUUIDMost != 0L || input.titleIconUUIDLeast != 0L ? new UUID(input.titleIconUUIDMost, input.titleIconUUIDLeast) : null;
        this.hideAfterSec = input.hideAfterSec;
        this.backgroundColor = input.backgroundColor;
        this.bodyTxtColor = input.bodyTxtColor;
        this.titleTxtColor = input.titleTxtColor;
        this.sourceTxtColor = input.sourceTxtColor;
        this.packetCache = input;
        this.packetDirty = false;
        return this;
    }

    @Override
    public UUID getBadgeUUID() {
        return this.badgeUUID;
    }

    @Override
    public INotificationBuilder<ComponentObject> setBadgeUUID(UUID uuid) {
        if (uuid == null) {
            throw new NullPointerException("icon");
        }
        this.badgeUUID = uuid;
        this.packetDirty = true;
        return this;
    }

    @Override
    public INotificationBuilder<ComponentObject> setBadgeUUIDRandom() {
        this.badgeUUID = UUID.randomUUID();
        this.packetDirty = true;
        return this;
    }

    @Override
    public ComponentObject getBodyComponent() {
        return this.bodyComponent;
    }

    @Override
    public INotificationBuilder<ComponentObject> setBodyComponent(ComponentObject component) {
        this.bodyComponent = component;
        this.packetDirty = true;
        return this;
    }

    @Override
    public INotificationBuilder<ComponentObject> setBodyComponent(String text) {
        this.bodyComponent = text != null ? (ComponentObject)this.componentHelper.builder().buildTextComponent().text(text).end() : null;
        this.packetDirty = true;
        return this;
    }

    @Override
    public ComponentObject getTitleComponent() {
        return this.titleComponent;
    }

    @Override
    public INotificationBuilder<ComponentObject> setTitleComponent(ComponentObject component) {
        this.titleComponent = component;
        this.packetDirty = true;
        return this;
    }

    @Override
    public INotificationBuilder<ComponentObject> setTitleComponent(String text) {
        this.titleComponent = text != null ? (ComponentObject)this.componentHelper.builder().buildTextComponent().text(text).end() : null;
        this.packetDirty = true;
        return this;
    }

    @Override
    public ComponentObject getSourceComponent() {
        return this.sourceComponent;
    }

    @Override
    public INotificationBuilder<ComponentObject> setSourceComponent(ComponentObject component) {
        this.sourceComponent = component;
        this.packetDirty = true;
        return this;
    }

    @Override
    public INotificationBuilder<ComponentObject> setSourceComponent(String text) {
        this.sourceComponent = text != null ? (ComponentObject)this.componentHelper.builder().buildTextComponent().text(text).end() : null;
        this.packetDirty = true;
        return this;
    }

    @Override
    public long getOriginalTimestampSec() {
        return this.originalTimestampSec;
    }

    @Override
    public INotificationBuilder<ComponentObject> setOriginalTimestampSec(long timestamp) {
        this.originalTimestampSec = timestamp;
        this.packetDirty = true;
        return this;
    }

    @Override
    public boolean getSilent() {
        return this.silent;
    }

    @Override
    public INotificationBuilder<ComponentObject> setSilent(boolean silent) {
        this.silent = silent;
        this.packetDirty = true;
        return this;
    }

    @Override
    public EnumBadgePriority getPriority() {
        return this.priority;
    }

    @Override
    public INotificationBuilder<ComponentObject> setPriority(EnumBadgePriority priority) {
        if (priority == null) {
            throw new NullPointerException("priority");
        }
        this.priority = priority;
        this.packetDirty = true;
        return this;
    }

    @Override
    public UUID getMainIconUUID() {
        return this.mainIconUUID;
    }

    @Override
    public INotificationBuilder<ComponentObject> setMainIconUUID(UUID uuid) {
        this.mainIconUUID = uuid;
        this.packetDirty = true;
        return this;
    }

    @Override
    public UUID getTitleIconUUID() {
        return this.titleIconUUID;
    }

    @Override
    public INotificationBuilder<ComponentObject> setTitleIconUUID(UUID uuid) {
        this.titleIconUUID = uuid;
        this.packetDirty = true;
        return this;
    }

    @Override
    public int getHideAfterSec() {
        return this.hideAfterSec;
    }

    @Override
    public INotificationBuilder<ComponentObject> setHideAfterSec(int seconds) {
        this.hideAfterSec = seconds;
        this.packetDirty = true;
        return this;
    }

    @Override
    public int getExpireAfterSec() {
        return this.expireAfterSec;
    }

    @Override
    public INotificationBuilder<ComponentObject> setExpireAfterSec(int seconds) {
        this.expireAfterSec = seconds;
        this.packetDirty = true;
        return this;
    }

    @Override
    public int getBackgroundColor() {
        return this.backgroundColor;
    }

    @Override
    public INotificationBuilder<ComponentObject> setBackgroundColor(int color) {
        this.backgroundColor = color;
        this.packetDirty = true;
        return this;
    }

    @Override
    public int getBodyTxtColorRGB() {
        return this.bodyTxtColor;
    }

    @Override
    public INotificationBuilder<ComponentObject> setBodyTxtColorRGB(int color) {
        this.bodyTxtColor = color;
        this.packetDirty = true;
        return this;
    }

    @Override
    public int getTitleTxtColorRGB() {
        return this.titleTxtColor;
    }

    @Override
    public INotificationBuilder<ComponentObject> setTitleTxtColorRGB(int color) {
        this.titleTxtColor = color;
        this.packetDirty = true;
        return this;
    }

    @Override
    public int getSourceTxtColorRGB() {
        return this.sourceTxtColor;
    }

    @Override
    public INotificationBuilder<ComponentObject> setSourceTxtColorRGB(int color) {
        this.sourceTxtColor = color;
        this.packetDirty = true;
        return this;
    }

    @Override
    public SPacketNotifBadgeShowV4EAG buildPacket() {
        if (this.packetDirty || this.packetCache == null) {
            String sourceComp;
            String titleComp;
            String bodyComp;
            SPacketNotifBadgeShowV4EAG.EnumBadgePriority internalPriority;
            if (this.badgeUUID == null) {
                this.badgeUUID = UUID.randomUUID();
            } else if (this.badgeUUID.getMostSignificantBits() == 0L && this.badgeUUID.getLeastSignificantBits() == 0L) {
                throw new IllegalStateException("Badge UUID cannot be 0!");
            }
            switch (this.priority) {
                case LOW: {
                    internalPriority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.LOW;
                    break;
                }
                case HIGHER: {
                    internalPriority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.HIGHER;
                    break;
                }
                case HIGHEST: {
                    internalPriority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.HIGHEST;
                    break;
                }
                default: {
                    internalPriority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.NORMAL;
                }
            }
            String string = bodyComp = this.bodyComponent != null ? this.componentHelper.serializeLegacyJSON(this.bodyComponent) : "";
            if (bodyComp.length() > Short.MAX_VALUE) {
                throw new IllegalStateException("Body component is longer than 32767 chars serialized!");
            }
            String string2 = titleComp = this.titleComponent != null ? this.componentHelper.serializeLegacyJSON(this.titleComponent) : "";
            if (titleComp.length() > 255) {
                throw new IllegalStateException("Title component is longer than 255 chars serialized!");
            }
            String string3 = sourceComp = this.sourceComponent != null ? this.componentHelper.serializeLegacyJSON(this.sourceComponent) : "";
            if (sourceComp.length() > 255) {
                throw new IllegalStateException("Body component is longer than 255 chars serialized!");
            }
            this.packetCache = new SPacketNotifBadgeShowV4EAG(this.badgeUUID.getMostSignificantBits(), this.badgeUUID.getLeastSignificantBits(), bodyComp, titleComp, sourceComp, this.originalTimestampSec, this.silent, internalPriority, this.mainIconUUID != null ? this.mainIconUUID.getMostSignificantBits() : 0L, this.mainIconUUID != null ? this.mainIconUUID.getLeastSignificantBits() : 0L, this.titleIconUUID != null ? this.titleIconUUID.getMostSignificantBits() : 0L, this.titleIconUUID != null ? this.titleIconUUID.getLeastSignificantBits() : 0L, this.hideAfterSec, this.expireAfterSec, this.backgroundColor, this.bodyTxtColor, this.titleTxtColor, this.sourceTxtColor);
            this.packetDirty = false;
        }
        return this.packetCache;
    }
}

