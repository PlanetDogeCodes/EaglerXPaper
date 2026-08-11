/*
 * Hotfix 7 Feature 4: Adaptive Connection Throttling.
 *
 * Limits concurrent Eaglercraft logins to prevent overwhelming the login
 * flow on busy servers. If more than maxConcurrentLogins players try to
 * log in at once, additional connections get a "Server is busy" message
 * instead of timing out.
 *
 * This only throttles NEW logins, not existing connections. The limit is
 * configurable and defaults to a high enough number that normal servers
 * never hit it.
 */

package net.lax1dude.eaglercraft.backend.server.base;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ConnectionThrottler {

    private final Semaphore loginSlots;
    private final int maxConcurrentLogins;
    private final long acquireTimeoutMs;

    public ConnectionThrottler(int maxConcurrentLogins, long acquireTimeoutMs) {
        this.maxConcurrentLogins = maxConcurrentLogins;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.loginSlots = new Semaphore(maxConcurrentLogins, true);
    }

    /**
     * Tries to acquire a login slot. Returns true if successful, false if
     * the server is too busy (all slots are in use).
     */
    public boolean tryAcquireLoginSlot() {
        try {
            return loginSlots.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Releases a login slot after the login is complete (or failed).
     */
    public void releaseLoginSlot() {
        loginSlots.release();
    }

    /**
     * Returns the number of available login slots.
     */
    public int getAvailableSlots() {
        return loginSlots.availablePermits();
    }

    /**
     * Returns the maximum number of concurrent logins.
     */
    public int getMaxConcurrentLogins() {
        return maxConcurrentLogins;
    }
}
