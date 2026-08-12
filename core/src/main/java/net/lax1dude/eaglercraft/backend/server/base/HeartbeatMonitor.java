package net.lax1dude.eaglercraft.backend.server.base;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.buffer.Unpooled;
public class HeartbeatMonitor {
    private final int maxMissedPongs;
    private final Map<Channel, AtomicInteger> trackedConnections = new ConcurrentHashMap<>();
    public HeartbeatMonitor(long pingIntervalMs, int maxMissedPongs) { this.maxMissedPongs = maxMissedPongs; }
    public void track(Channel channel) { if (channel != null && channel.isActive()) trackedConnections.put(channel, new AtomicInteger(0)); }
    public void untrack(Channel channel) { trackedConnections.remove(channel); }
    public void onPongReceived(Channel channel) { AtomicInteger missed = trackedConnections.get(channel); if (missed != null) missed.set(0); }
    public void tick() {
        for (Map.Entry<Channel, AtomicInteger> entry : trackedConnections.entrySet()) {
            Channel channel = entry.getKey(); AtomicInteger missed = entry.getValue();
            if (!channel.isActive()) { trackedConnections.remove(channel); continue; }
            try { channel.writeAndFlush(new PingWebSocketFrame(Unpooled.EMPTY_BUFFER)); } catch (Throwable t) { trackedConnections.remove(channel); continue; }
            if (missed.incrementAndGet() > maxMissedPongs) { try { channel.close(); } catch (Throwable t) {} trackedConnections.remove(channel); }
        }
    }
    public int getTrackedCount() { return trackedConnections.size(); }
    public void shutdown() { trackedConnections.clear(); }
}
