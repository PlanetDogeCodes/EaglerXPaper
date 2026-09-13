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

package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.lax1dude.eaglercraft.backend.server.api.EnumPipelineEvent;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.util.Util;

/**
 * Keeps vanilla MC-level compression off of Eaglercraft connections.
 *
 * The vanilla login listener (1.8 - 1.21.x) calls
 * {@code Connection.setupCompression(threshold, ...)} through a send listener
 * the moment the login compression packet has been flushed. The handshake
 * layer swallows the packet bytes, but the listener still fires and the
 * compression codecs still land in the pipeline, quietly rewriting every
 * outbound packet as {@code [uncompressedSize][data]} and desynchronizing the
 * raw per-frame WebSocket stream the Eaglercraft protocol uses.
 *
 * Paper fires {@code io.papermc.paper.network.ConnectionEvent
 * .COMPRESSION_THRESHOLD_SET} from inside {@code setupCompression} right after
 * the codecs are in place, so this runs within the same task on the event
 * loop - no packet can be written between the install and the swap.
 *
 * The codecs are replaced with {@link NOPDummyHandler} placeholders instead of
 * being removed. The handler names must survive: ViaVersion anchors its own
 * reordering on {@code addAfter("compress", ...)} /
 * {@code addAfter("decompress", ...)} when it sees the same event, and
 * {@code setupCompression(-1)} looks the names up too. Swapping in a no-op
 * keeps every one of those lookups valid while the framing itself is gone.
 */
public class EaglerCompressionGuardHandler extends ChannelInboundHandlerAdapter {

	public static final String HANDLER_NAME = "eagler-compression-guard";

	private static final Object COMPRESSION_ENABLED_EVENT;

	static {
		Object evt = null;
		if (Util.classExists("io.papermc.paper.network.ConnectionEvent")) {
			try {
				evt = Class.forName("io.papermc.paper.network.ConnectionEvent")
						.getDeclaredField("COMPRESSION_THRESHOLD_SET").get(null);
			} catch (ReflectiveOperationException ex) {
			}
		}
		COMPRESSION_ENABLED_EVENT = evt;
	}

	private final NettyPipelineData pipelineData;

	public EaglerCompressionGuardHandler(NettyPipelineData pipelineData) {
		this.pipelineData = pipelineData;
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt == EnumPipelineEvent.EAGLER_ENTERED_PLAY_STATE
				|| evt == EnumPipelineEvent.EAGLER_HANDSHAKE_COMPLETE
				|| (COMPRESSION_ENABLED_EVENT != null && evt == COMPRESSION_ENABLED_EVENT)) {
			if (ctx.channel().eventLoop().inEventLoop()) {
				swapCompressionHandlers(ctx);
			} else {
				ctx.channel().eventLoop().execute(() -> swapCompressionHandlers(ctx));
			}
		}
		super.userEventTriggered(ctx, evt);
	}

	/**
	 * Replaces the vanilla compression codec pair with no-op placeholders.
	 * Handlers are matched by pipeline name AND by class name so a third-party
	 * handler that happens to use one of the vanilla names is never touched.
	 */
	private void swapCompressionHandlers(ChannelHandlerContext ctx) {
		ChannelPipeline pipeline = ctx.pipeline();
		boolean stripped = false;
		stripped |= swap(pipeline, "decompress");
		stripped |= swap(pipeline, "compress");
		if (stripped && pipelineData != null) {
			pipelineData.connectionLogger
					.info("Vanilla compression codecs stripped from Eaglercraft connection");
		}
	}

	private boolean swap(ChannelPipeline pipeline, String name) {
		ChannelHandler handler = pipeline.get(name);
		if (handler != null) {
			String clz = handler.getClass().getSimpleName();
			if (clz.contains("ompress")) {
				try {
					pipeline.replace(name, name, NOPDummyHandler.INSTANCE);
					return true;
				} catch (Throwable ignored) {
				}
			}
		}
		return false;
	}

}
