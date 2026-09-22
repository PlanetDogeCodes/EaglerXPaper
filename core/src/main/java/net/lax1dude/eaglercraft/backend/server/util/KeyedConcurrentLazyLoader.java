/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.api.collect.ObjectIntMap;
import net.lax1dude.eaglercraft.backend.server.base.collect.ObjectIntHashMap;
import net.lax1dude.eaglercraft.backend.server.api.collect.ObjectIntProcedure;

public abstract class KeyedConcurrentLazyLoader<K, T> {
    private KeyedConsumerList<K, T> waitingCallbacks = null;
    protected volatile T result = null;

    protected abstract void loadImpl(Consumer<T> var1);

    protected abstract IPlatformLogger getLogger();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void load(K key, Consumer<T> callback) {
        T val = this.result;
        if (val != null) {
            callback.accept(val);
        } else {
            KeyedConcurrentLazyLoader keyedConcurrentLazyLoader = this;
            synchronized (keyedConcurrentLazyLoader) {
                val = this.result;
                if (val != null) {
                } else {
                    if (this.waitingCallbacks != null) {
                        this.waitingCallbacks.add(key, callback);
                        return;
                    }
                    this.waitingCallbacks = new KeyedConsumerList();
                    this.waitingCallbacks.add(key, callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            this.loadImpl(res -> {
                KeyedConsumerList<K, T> toCall;
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
                    List<Consumer<T>> toCallList = toCall.getList();
                    int l = toCallList.size();
                    for (int i = 0; i < l; ++i) {
                        try {
                            toCallList.get(i).accept(res);
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected final void cmpXchgRelease(T expect, T set) {
        KeyedConcurrentLazyLoader keyedConcurrentLazyLoader = this;
        synchronized (keyedConcurrentLazyLoader) {
            if (this.result == expect) {
                this.result = set;
            }
        }
    }

    public static class KeyedConsumerList<K, T> {
        private final ObjectIntMap<K> map = new ObjectIntHashMap<K>(8);
        private final List<Consumer<T>> list = new ArrayList<Consumer<T>>(8);

        public void add(K key, Consumer<T> value) {
            if (key != null) {
                int idx = this.map.indexOf(key);
                if (idx >= 0) {
                    this.list.set(this.map.indexGet(idx), value);
                } else {
                    int i = this.list.size();
                    this.map.addTo(key, i);
                    this.list.add(value);
                }
            } else {
                this.list.add(value);
            }
        }

        public List<Consumer<T>> getList() {
            return this.list;
        }

        public void forEach(BiConsumer<K, Consumer<T>> cb) {
            this.map.forEach((ObjectIntProcedure<K>)(k, i) -> cb.accept(k, this.list.get(i)));
        }
    }
}

