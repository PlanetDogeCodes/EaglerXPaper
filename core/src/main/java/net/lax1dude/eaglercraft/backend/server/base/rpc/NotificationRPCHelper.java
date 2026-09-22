/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.rpc;

import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifBadgeShow;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketNotifBadgeShowV4EAG;

class NotificationRPCHelper {
    NotificationRPCHelper() {
    }

    static SPacketNotifBadgeShowV4EAG translateRPCPacket(CPacketRPCNotifBadgeShow packet) {
        SPacketNotifBadgeShowV4EAG.EnumBadgePriority priority;
        long titleIconUUIDLeast;
        long titleIconUUIDMost;
        long mainIconUUIDLeast;
        long mainIconUUIDMost;
        long badgeUUIDMost = packet.badgeUUID.getMostSignificantBits();
        long badgeUUIDLeast = packet.badgeUUID.getLeastSignificantBits();
        if (packet.mainIconUUID != null) {
            mainIconUUIDMost = packet.mainIconUUID.getMostSignificantBits();
            mainIconUUIDLeast = packet.mainIconUUID.getLeastSignificantBits();
        } else {
            mainIconUUIDMost = 0L;
            mainIconUUIDLeast = 0L;
        }
        if (packet.titleIconUUID != null) {
            titleIconUUIDMost = packet.titleIconUUID.getMostSignificantBits();
            titleIconUUIDLeast = packet.titleIconUUID.getLeastSignificantBits();
        } else {
            titleIconUUIDMost = 0L;
            titleIconUUIDLeast = 0L;
        }
        switch (packet.priority) {
            case HIGHEST: {
                priority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.HIGHEST;
                break;
            }
            case HIGHER: {
                priority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.HIGHER;
                break;
            }
            case LOW: {
                priority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.LOW;
                break;
            }
            default: {
                priority = SPacketNotifBadgeShowV4EAG.EnumBadgePriority.NORMAL;
            }
        }
        return new SPacketNotifBadgeShowV4EAG(badgeUUIDMost, badgeUUIDLeast, packet.bodyComponent, packet.titleComponent, packet.sourceComponent, packet.originalTimestampSec, packet.silent, priority, mainIconUUIDMost, mainIconUUIDLeast, titleIconUUIDMost, titleIconUUIDLeast, packet.hideAfterSec, packet.expireAfterSec, packet.backgroundColor, packet.bodyTxtColor, packet.titleTxtColor, packet.sourceTxtColor);
    }
}

