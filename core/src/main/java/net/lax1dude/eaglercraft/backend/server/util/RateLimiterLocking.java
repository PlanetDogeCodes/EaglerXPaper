/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.util;

import java.util.concurrent.atomic.AtomicInteger;
import net.lax1dude.eaglercraft.backend.server.util.EnumRateLimitState;

public class RateLimiterLocking
extends AtomicInteger {
    private long timer = System.nanoTime();
    private long lockedTimer = -1L;

    public RateLimiterLocking() {
        super(0);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public EnumRateLimitState rateLimit(Config conf) {
        int limitVal = conf.limit;
        if (this.incrementAndGet() >= limitVal) {
            RateLimiterLocking rateLimiterLocking = this;
            synchronized (rateLimiterLocking) {
                int v = this.get();
                if (v < limitVal) {
                    return EnumRateLimitState.OK;
                }
                long now = System.nanoTime();
                if (this.lockedTimer != -1L) {
                    if (now - this.lockedTimer > conf.lockoutDuration) {
                        this.lockedTimer = -1L;
                        this.set(0);
                        return EnumRateLimitState.OK;
                    }
                    return EnumRateLimitState.LOCKED;
                }
                if (v >= conf.limitLockout) {
                    this.lockedTimer = now;
                    return EnumRateLimitState.BLOCKED_LOCKED;
                }
                long period = conf.period / (long)limitVal;
                long delta = (now - this.timer) / period;
                if (delta > 0L) {
                    this.timer += delta * period;
                    return this.addAndGet(-Math.min((int)delta, v)) < limitVal ? EnumRateLimitState.OK : EnumRateLimitState.BLOCKED;
                }
                return EnumRateLimitState.BLOCKED;
            }
        }
        return EnumRateLimitState.OK;
    }

    public static class Config {
        public final long period;
        public final int limit;
        public final int limitLockout;
        public final long lockoutDuration;

        public Config(int period, int limit, int limitLockout, long lockoutDuration) {
            this.period = (long)period * 1000000000L;
            this.limit = limit;
            this.limitLockout = limitLockout;
            this.lockoutDuration = lockoutDuration * 1000000000L;
        }
    }
}

