/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.util;

import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterBasic
extends AtomicInteger {
    private long timer = System.nanoTime();

    public RateLimiterBasic() {
        super(0);
    }

    public boolean rateLimit(int limitVal) {
        return this.rateLimit(60000000000L, limitVal);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean rateLimit(long periodNanos, int limitVal) {
        if (this.incrementAndGet() >= limitVal) {
            RateLimiterBasic rateLimiterBasic = this;
            synchronized (rateLimiterBasic) {
                int v = this.get();
                if (v < limitVal) {
                    return true;
                }
                long period = 60000000000L / (long)limitVal;
                long delta = (System.nanoTime() - this.timer) / period;
                if (delta > 0L) {
                    this.timer += delta * period;
                    int correction = v - (limitVal << 1);
                    if (correction > 0) {
                        delta += (long)correction;
                    }
                    return this.addAndGet(-Math.min((int)delta, v)) < limitVal;
                }
                return false;
            }
        }
        return true;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean checkState(int limitVal) {
        if (this.get() >= limitVal) {
            RateLimiterBasic rateLimiterBasic = this;
            synchronized (rateLimiterBasic) {
                int v = this.get();
                if (v < limitVal) {
                    return true;
                }
                long period = 60000000000L / (long)limitVal;
                long delta = (System.nanoTime() - this.timer) / period;
                if (delta > 0L) {
                    this.timer += delta * period;
                    int correction = v - (limitVal << 1);
                    if (correction > 0) {
                        delta += (long)correction;
                    }
                    return this.addAndGet(-Math.min((int)delta, v)) < limitVal;
                }
                return false;
            }
        }
        return true;
    }
}

