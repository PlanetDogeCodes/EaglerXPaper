/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerNettyPipelineInitializer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformNettyPipelineInitializer;
import net.lax1dude.eaglercraft.backend.server.base.CompoundRateLimiterMap;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;

class EaglerXServerNettyPipelineInitializer<PlayerObject>
implements IEaglerXServerNettyPipelineInitializer<NettyPipelineData> {
    private final EaglerXServer<PlayerObject> server;

    EaglerXServerNettyPipelineInitializer(EaglerXServer<PlayerObject> server) {
        this.server = server;
    }

    @Override
    public void initialize(IPlatformNettyPipelineInitializer<NettyPipelineData> initializer) {
        EaglerListener eagListener = (EaglerListener)initializer.getListener();
        if (this.server.getConfig().getSettings().isDebugLogNewChannels()) {
            this.server.logger().info("[" + eagListener.getName() + "]: New channel opened: " + initializer.getChannel());
        }
        Consumer<SocketAddress> realAddressHandle = null;
        CompoundRateLimiterMap.ICompoundRatelimits rateLimits = null;
        if (eagListener.isForwardIP()) {
            if (eagListener.getConfigData().isSpoofPlayerAddressForwarded()) {
                realAddressHandle = initializer.realAddressHandle();
            }
        } else {
            CompoundRateLimiterMap map = eagListener.getRateLimiter();
            if (map != null) {
                SocketAddress addr = initializer.getChannel().remoteAddress();
                if (addr instanceof InetSocketAddress) {
                    rateLimits = map.rateLimit(((InetSocketAddress)addr).getAddress());
                    if (rateLimits == null) {
                        initializer.getChannel().close();
                        return;
                    }
                } else {
                    this.server.logger().warn("Unable to ratelimit unknown address type: " + addr.getClass().getName() + " - \"" + addr + "\"");
                }
            }
        }
        NettyPipelineData attachment = new NettyPipelineData(initializer.getChannel(), this.server, eagListener, this.server.getEaglerAttribManager().createEaglerHolder(), realAddressHandle, rateLimits);
        initializer.setAttachment(attachment);
        if (eagListener.isDualStack()) {
            this.server.getPipelineTransformer().injectDualStack(initializer.getPipeline(), initializer.getChannel(), attachment);
        } else {
            this.server.getPipelineTransformer().injectSingleStack(initializer.getPipeline(), initializer.getChannel(), attachment);
        }
    }
}

