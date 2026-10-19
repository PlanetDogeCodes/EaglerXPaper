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

package net.lax1dude.eaglercraft.backend.server.base.config;

/**
 * EaglerXPaper-specific feature toggles, loaded from settings.yml by
 * EaglerConfigLoader. These sit on top of the upstream EaglercraftXServer
 * configuration and control the Stable-2 additions (adaptive batching,
 * outbound packet journal, skin cache pre-warm).
 */
public class EaglerXPaperConfig {
    public static volatile boolean enableSkinPrewarm = true;
    public static volatile int prewarmMaxPlayers = 50;

    public static volatile boolean enableAdaptiveBatching = true;

    // Outbound packet journal (v1.1.1 Stable 2): per-connection ring journal
    // of the outbound WIRE frame stream (vanilla MC packets + Eagler-protocol
    // plugin frames, in exact wire order), dumped to a log file when the
    // connection closes. Field instrument for diagnosing client-side
    // packet decode kicks ("kicked whenever they load a chunk where a
    // wither's hitbox is inside water" class of reports).
    public static volatile boolean enablePacketJournal = false;
    public static volatile int journalMaxPackets = 512;
    public static volatile int journalSnapshotBytes = 48;
}
