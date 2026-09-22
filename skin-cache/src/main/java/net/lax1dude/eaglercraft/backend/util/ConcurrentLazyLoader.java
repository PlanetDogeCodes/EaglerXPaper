/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.util.ILoggerAdapter;

public abstract class ConcurrentLazyLoader<T> {
    private List<Consumer<T>> waitingCallbacks = null;
    private volatile T result = null;

    protected abstract void loadImpl(Consumer<T> var1);

    protected abstract ILoggerAdapter getLogger();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void load(Consumer<T> callback) {
        T val = this.result;
        if (val != null) {
            callback.accept(val);
        } else {
            ConcurrentLazyLoader concurrentLazyLoader = this;
            synchronized (concurrentLazyLoader) {
                val = this.result;
                if (val != null) {
                } else {
                    if (this.waitingCallbacks != null) {
                        this.waitingCallbacks.add(callback);
                        return;
                    }
                    this.waitingCallbacks = new ArrayList<Consumer<T>>();
                    this.waitingCallbacks.add(callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            this.loadImpl(res -> {
                List<Consumer<T>> toCall;
                if (res == null) {
                    throw new NullPointerException("result must not be null");
                }
                synchronized (this) {
                    if (this.result != null) {
                        return;
                    }
                    this.result = res;
                    toCall = this.waitingCallbacks;
                    this.waitingCallbacks = null;
                }
                if (toCall != null) {
                    int l = toCall.size();
                    for (int i = 0; i < l; ++i) {
                        try {
                            toCall.get(i).accept(res);
                            continue;
                        }
                        catch (Exception ex) {
                            this.getLogger().error("Caught error from lazy load callback", ex);
                        }
                    }
                }
            });
        }
    }

    public T getIfLoaded() {
        return this.result;
    }

    public void clear() {
        this.result = null;
    }
}

