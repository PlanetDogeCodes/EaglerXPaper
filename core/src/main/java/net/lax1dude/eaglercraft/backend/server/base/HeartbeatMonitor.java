/*
 * Hotfix 7 Feature 5: Heartbeat Monitoring for Eaglercraft Connections.
 *
 * Sends periodic WebSocket ping frames to all connected Eaglercraft players.
 * If a player doesn't respond to 2 consecutive pings (60 seconds), cleanly
 * disconnects them. This prevents "ghost" players who appear online but
 * are actually disconnected (client closed tab, network dropped).
 *
 * WebSocket ping/pong is a standard part of the WebSocket protocol.
 * Eaglercraft clients already handle ping frames. This just ensures dead
 * connections are cleaned up promptly.
 */

package net.lax1dude.eaglercraft.backend.server.base;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.buffer.Unpooled;

public class HeartbeatMonitor {

    private final long pingIntervalMs;
    private final int maxMissedPongs;

    // Map of channel -> missed pong count
    private final Map<Channel, AtomicInteger> trackedConnections = new ConcurrentHashMap<>();

    public HeartbeatMonitor(long pingIntervalMs, int maxMissedPongs) {
        this.pingIntervalMs = pingIntervalMs;
        this.maxMissedPongs = maxMissedPongs;
    }

    /**
     * Starts tracking a channel for heartbeat monitoring.
     */
    public void track(Channel channel) {
        if (channel != null && channel.isActive()) {
            trackedConnections.put(channel, new AtomicInteger(0));
        }
    }

    /**
     * Stops tracking a channel (called on disconnect).
     */
    public void untrack(Channel channel) {
        trackedConnections.remove(channel);
    }

    /**
     * Called when a pong frame is received from a client.
     * Resets the missed-pong counter for that channel.
     */
    public void onPongReceived(Channel channel) {
        AtomicInteger missed = trackedConnections.get(channel);
        if (missed != null) {
            missed.set(0);
        }
    }

    /**
     * Sends ping frames to all tracked channels and disconnects
     * any that have missed too many pongs.
     * Should be called periodically by a scheduled task.
     */
    public void tick() {
        for (Map.Entry<Channel, AtomicInteger> entry : trackedConnections.entrySet()) {
            Channel channel = entry.getKey();
            AtomicInteger missed = entry.getValue();

            if (!channel.isActive()) {
                trackedConnections.remove(channel);
                continue;
            }

            try {
                // Send a ping frame
                channel.writeAndFlush(new PingWebSocketFrame(Unpooled.EMPTY_BUFFER));
            } catch (Throwable t) {
                // Channel may have closed between the isActive check and the write
                trackedConnections.remove(channel);
                continue;
            }

            // Increment missed pong count
            int missedCount = missed.incrementAndGet();
            if (missedCount > maxMissedPongs) {
                // Client hasn't responded to maxMissedPongs consecutive pings.
                // Disconnect them cleanly.
                try {
                    channel.close();
                } catch (Throwable t) {
                    // best effort
                }
                trackedConnections.remove(channel);
            }
        }
    }

    /**
     * Returns the number of currently tracked connections.
     */
    public int getTrackedCount() {
        return trackedConnections.size();
    }

    /**
     * Stops tracking all connections (called on plugin disable).
     */
    public void shutdown() {
        trackedConnections.clear();
    }
}
