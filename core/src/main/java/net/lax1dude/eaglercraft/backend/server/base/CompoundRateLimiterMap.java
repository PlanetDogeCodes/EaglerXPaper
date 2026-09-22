/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.CacheBuilder
 *  com.google.common.cache.CacheLoader
 *  com.google.common.cache.LoadingCache
 */
package net.lax1dude.eaglercraft.backend.server.base;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataListener;
import net.lax1dude.eaglercraft.backend.server.util.EnumRateLimitState;
import net.lax1dude.eaglercraft.backend.server.util.RateLimiterExclusions;
import net.lax1dude.eaglercraft.backend.server.util.RateLimiterLocking;

public class CompoundRateLimiterMap {
    private static final ICompoundRatelimits ALWAYS_OK = new ICompoundRatelimits(){

        @Override
        public EnumRateLimitState rateLimitLogin() {
            return EnumRateLimitState.OK;
        }

        @Override
        public EnumRateLimitState rateLimitMOTD() {
            return EnumRateLimitState.OK;
        }

        @Override
        public EnumRateLimitState rateLimitQuery() {
            return EnumRateLimitState.OK;
        }

        @Override
        public EnumRateLimitState rateLimitHTTP() {
            return EnumRateLimitState.OK;
        }
    };
    private final LoadingCache<InetAddress, RateLimits> cache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).maximumSize(8192L).build((CacheLoader)new CacheLoader<InetAddress, RateLimits>(){

        public RateLimits load(InetAddress arg0) throws Exception {
            return new RateLimits();
        }
    });
    private final RateLimiterLocking.Config ratelimitIPConf;
    private final RateLimiterLocking.Config ratelimitLoginConf;
    private final RateLimiterLocking.Config ratelimitMOTDConf;
    private final RateLimiterLocking.Config ratelimitQueryConf;
    private final RateLimiterLocking.Config ratelimitHTTPConf;
    private final RateLimiterExclusions ratelimitExclusions;

    public static CompoundRateLimiterMap create(ConfigDataListener.ConfigRateLimit ratelimitIPConfIn, ConfigDataListener.ConfigRateLimit ratelimitLoginConfIn, ConfigDataListener.ConfigRateLimit ratelimitMOTDConfIn, ConfigDataListener.ConfigRateLimit ratelimitQueryConfIn, ConfigDataListener.ConfigRateLimit ratelimitHTTPConfIn, RateLimiterExclusions ratelimitExclusions) {
        if (!(ratelimitIPConfIn.isEnabled() || ratelimitLoginConfIn.isEnabled() || ratelimitMOTDConfIn.isEnabled() || ratelimitQueryConfIn.isEnabled() || ratelimitHTTPConfIn.isEnabled())) {
            return null;
        }
        RateLimiterLocking.Config ratelimitIPConf = CompoundRateLimiterMap.createConf(ratelimitIPConfIn);
        RateLimiterLocking.Config ratelimitLoginConf = CompoundRateLimiterMap.createConf(ratelimitLoginConfIn);
        RateLimiterLocking.Config ratelimitMOTDConf = CompoundRateLimiterMap.createConf(ratelimitMOTDConfIn);
        RateLimiterLocking.Config ratelimitQueryConf = CompoundRateLimiterMap.createConf(ratelimitQueryConfIn);
        RateLimiterLocking.Config ratelimitHTTPConf = CompoundRateLimiterMap.createConf(ratelimitHTTPConfIn);
        return new CompoundRateLimiterMap(ratelimitIPConf, ratelimitLoginConf, ratelimitMOTDConf, ratelimitQueryConf, ratelimitHTTPConf, ratelimitExclusions);
    }

    private static RateLimiterLocking.Config createConf(ConfigDataListener.ConfigRateLimit ratelimitIPConfIn) {
        if (!ratelimitIPConfIn.isEnabled()) {
            return null;
        }
        return new RateLimiterLocking.Config(ratelimitIPConfIn.getPeriod(), ratelimitIPConfIn.getLimit(), ratelimitIPConfIn.getLimitLockout(), ratelimitIPConfIn.getLockoutDuration());
    }

    private CompoundRateLimiterMap(RateLimiterLocking.Config ratelimitIPConf, RateLimiterLocking.Config ratelimitLoginConf, RateLimiterLocking.Config ratelimitMOTDConf, RateLimiterLocking.Config ratelimitQueryConf, RateLimiterLocking.Config ratelimitHTTPConf, RateLimiterExclusions ratelimitExclusions) {
        this.ratelimitIPConf = ratelimitIPConf;
        this.ratelimitLoginConf = ratelimitLoginConf;
        this.ratelimitMOTDConf = ratelimitMOTDConf;
        this.ratelimitQueryConf = ratelimitQueryConf;
        this.ratelimitHTTPConf = ratelimitHTTPConf;
        this.ratelimitExclusions = ratelimitExclusions;
    }

    private RateLimits load(InetAddress address) {
        try {
            return this.cache.get(address);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException(e);
        }
    }

    public ICompoundRatelimits rateLimit(InetAddress address) {
        if (this.ratelimitExclusions != null && this.ratelimitExclusions.testExclusion(address)) {
            return ALWAYS_OK;
        }
        RateLimits limits = this.load(address);
        return this.ratelimitIPConf == null || limits.rateLimit(this.ratelimitIPConf).isOk() ? limits : null;
    }

    public ICompoundRatelimits getRateLimit(InetAddress address) {
        if (this.ratelimitExclusions != null && this.ratelimitExclusions.testExclusion(address)) {
            return ALWAYS_OK;
        }
        return this.load(address);
    }

    private class RateLimits
    extends RateLimiterLocking
    implements ICompoundRatelimits {
        private RateLimiterLocking ratelimitLogin;
        private RateLimiterLocking ratelimitMOTD;
        private RateLimiterLocking ratelimitQuery;
        private RateLimiterLocking ratelimitHTTP;

        private RateLimits() {
        }

        @Override
        public EnumRateLimitState rateLimitLogin() {
            if (CompoundRateLimiterMap.this.ratelimitLoginConf == null) {
                return EnumRateLimitState.OK;
            }
            RateLimiterLocking limiter = this.ratelimitLogin;
            if (limiter == null) {
                limiter = this.ratelimitLogin = new RateLimiterLocking();
            }
            return limiter.rateLimit(CompoundRateLimiterMap.this.ratelimitLoginConf);
        }

        @Override
        public EnumRateLimitState rateLimitMOTD() {
            if (CompoundRateLimiterMap.this.ratelimitMOTDConf == null) {
                return EnumRateLimitState.OK;
            }
            RateLimiterLocking limiter = this.ratelimitMOTD;
            if (limiter == null) {
                limiter = this.ratelimitMOTD = new RateLimiterLocking();
            }
            return limiter.rateLimit(CompoundRateLimiterMap.this.ratelimitMOTDConf);
        }

        @Override
        public EnumRateLimitState rateLimitQuery() {
            if (CompoundRateLimiterMap.this.ratelimitQueryConf == null) {
                return EnumRateLimitState.OK;
            }
            RateLimiterLocking limiter = this.ratelimitQuery;
            if (limiter == null) {
                limiter = this.ratelimitQuery = new RateLimiterLocking();
            }
            return limiter.rateLimit(CompoundRateLimiterMap.this.ratelimitQueryConf);
        }

        @Override
        public EnumRateLimitState rateLimitHTTP() {
            if (CompoundRateLimiterMap.this.ratelimitHTTPConf == null) {
                return EnumRateLimitState.OK;
            }
            RateLimiterLocking limiter = this.ratelimitHTTP;
            if (limiter == null) {
                limiter = this.ratelimitHTTP = new RateLimiterLocking();
            }
            return limiter.rateLimit(CompoundRateLimiterMap.this.ratelimitHTTPConf);
        }
    }

    public static interface ICompoundRatelimits {
        public EnumRateLimitState rateLimitLogin();

        public EnumRateLimitState rateLimitMOTD();

        public EnumRateLimitState rateLimitQuery();

        public EnumRateLimitState rateLimitHTTP();
    }
}

