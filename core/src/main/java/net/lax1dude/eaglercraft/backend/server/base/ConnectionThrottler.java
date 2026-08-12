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
    public boolean tryAcquireLoginSlot() {
        try { return loginSlots.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
    public void releaseLoginSlot() { loginSlots.release(); }
    public int getAvailableSlots() { return loginSlots.availablePermits(); }
    public int getMaxConcurrentLogins() { return maxConcurrentLogins; }
}
