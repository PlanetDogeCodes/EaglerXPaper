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

package net.lax1dude.eaglercraft.backend.server.base;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import net.lax1dude.eaglercraft.backend.skin_cache.ISkinCacheService;

class DeferredStartSkinCache implements ISkinCacheService {

        private final List<Consumer<ISkinCacheService>> deferredTasks = new LinkedList<>();

        private volatile ISkinCacheService service;

        /**
         * Stable 2 (round 4 fix): delegate installed when the skin cache database
         * cannot be reached (or after disable). Answers every lookup immediately
         * with the ERROR sentinel (mapped to a missing skin by callers) instead of
         * deferring the callback forever on a queue nothing will ever drain — the
         * pre-fix behavior both hung skin/cape lookups silently and grew the
         * deferred-task list for the entire server uptime.
         */
        private static final ISkinCacheService DEGRADED = new DegradedDelegate();

        static ISkinCacheService degraded() {
                return DEGRADED;
        }

        private static final class DegradedDelegate implements ISkinCacheService {

                @Override
                public void resolveSkinByURL(String skinURL, Consumer<byte[]> callback) {
                        try {
                                callback.accept(ERROR);
                        } catch (Throwable ignored) {
                        }
                }

                @Override
                public void resolveCapeByURL(String capeURL, Consumer<byte[]> callback) {
                        try {
                                callback.accept(ERROR);
                        } catch (Throwable ignored) {
                        }
                }

                @Override
                public void tick() {
                }

                @Override
                public int getTotalMemorySkins() {
                        return 0;
                }

                @Override
                public int getTotalMemoryCapes() {
                        return 0;
                }

                @Override
                public int getTotalStoredSkins() {
                        return 0;
                }

                @Override
                public int getTotalStoredCapes() {
                        return 0;
                }
        }

        void setDelegate(ISkinCacheService service) {
                this.service = service;
                if (service != null) {
                        List<Consumer<ISkinCacheService>> lst;
                        synchronized (deferredTasks) {
                                lst = new ArrayList<>(deferredTasks);
                                deferredTasks.clear();
                        }
                        if (!lst.isEmpty()) {
                                for (Consumer<ISkinCacheService> consumer : lst) {
                                        consumer.accept(service);
                                }
                        }
                }
        }

        // used by disableHandler to dispose the service before closing JDBC
        ISkinCacheService getDelegate() {
                return service;
        }

        @Override
        public void resolveSkinByURL(String skinURL, Consumer<byte[]> callback) {
                ISkinCacheService svc = service;
                if (svc != null) {
                        svc.resolveSkinByURL(skinURL, callback);
                } else {
                        synchronized (deferredTasks) {
                                // Stable 2 fix: re-check inside the lock — a lookup that read null just
                                // before setDelegate() drained the queue would otherwise enqueue a task
                                // that nothing will ever execute (silently dropped skin lookup).
                                if (service != null) {
                                        service.resolveSkinByURL(skinURL, callback);
                                        return;
                                }
                                deferredTasks.add((svc2) -> {
                                        svc2.resolveSkinByURL(skinURL, callback);
                                });
                        }
                }
        }

        @Override
        public void resolveCapeByURL(String capeURL, Consumer<byte[]> callback) {
                ISkinCacheService svc = service;
                if (svc != null) {
                        svc.resolveCapeByURL(capeURL, callback);
                } else {
                        synchronized (deferredTasks) {
                                // Stable 2 fix: same re-check-inside-lock as resolveSkinByURL
                                if (service != null) {
                                        service.resolveCapeByURL(capeURL, callback);
                                        return;
                                }
                                deferredTasks.add((svc2) -> {
                                        svc2.resolveCapeByURL(capeURL, callback);
                                });
                        }
                }
        }

        @Override
        public void tick() {
                ISkinCacheService svc = service;
                if (svc != null) {
                        svc.tick();
                }
        }

        @Override
        public int getTotalMemorySkins() {
                ISkinCacheService svc = service;
                if (svc != null) {
                        return svc.getTotalMemorySkins();
                } else {
                        return 0;
                }
        }

        @Override
        public int getTotalMemoryCapes() {
                ISkinCacheService svc = service;
                if (svc != null) {
                        return svc.getTotalMemoryCapes();
                } else {
                        return 0;
                }
        }

        @Override
        public int getTotalStoredSkins() {
                ISkinCacheService svc = service;
                if (svc != null) {
                        return svc.getTotalStoredSkins();
                } else {
                        return 0;
                }
        }

        @Override
        public int getTotalStoredCapes() {
                ISkinCacheService svc = service;
                if (svc != null) {
                        return svc.getTotalStoredCapes();
                } else {
                        return 0;
                }
        }
}
