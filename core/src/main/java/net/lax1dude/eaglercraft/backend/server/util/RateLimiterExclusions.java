/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.net.InetAddresses
 */
package net.lax1dude.eaglercraft.backend.server.util;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;

public class RateLimiterExclusions {
    private final ImmutableList<Exclusion4> lst4;
    private final ImmutableList<Exclusion6> lst6;

    public static RateLimiterExclusions create(List<String> list, IPlatformLogger logger) {
        ArrayList<Exclusion4> lst4 = new ArrayList<Exclusion4>();
        ArrayList<Exclusion6> lst6 = new ArrayList<Exclusion6>();
        for (String str : list) {
            byte[] addrBytes;
            int subnet;
            InetAddress addr;
            block6: {
                int slashIdx = str.lastIndexOf(47);
                if (slashIdx != -1) {
                    try {
                        addr = InetAddresses.forString((String)str.substring(0, slashIdx));
                        subnet = Integer.parseInt(str.substring(slashIdx + 1));
                        break block6;
                    }
                    catch (IllegalArgumentException ex) {
                        logger.warn("Skipping invalid ratelimit exclusion: \"" + str + "\"", ex);
                        continue;
                    }
                }
                addr = InetAddresses.forString((String)str);
                subnet = -1;
            }
            if (addr instanceof Inet6Address) {
                Inet6Address addr6 = (Inet6Address)addr;
                addrBytes = addr6.getAddress();
                long addrHi = (long)(addrBytes[0] & 0xFF) << 56 | (long)(addrBytes[1] & 0xFF) << 48 | (long)(addrBytes[2] & 0xFF) << 40 | (long)(addrBytes[3] & 0xFF) << 32 | (long)(addrBytes[4] & 0xFF) << 24 | (long)(addrBytes[5] & 0xFF) << 16 | (long)(addrBytes[6] & 0xFF) << 8 | (long)(addrBytes[7] & 0xFF);
                long addrLo = (long)(addrBytes[8] & 0xFF) << 56 | (long)(addrBytes[9] & 0xFF) << 48 | (long)(addrBytes[10] & 0xFF) << 40 | (long)(addrBytes[11] & 0xFF) << 32 | (long)(addrBytes[12] & 0xFF) << 24 | (long)(addrBytes[13] & 0xFF) << 16 | (long)(addrBytes[14] & 0xFF) << 8 | (long)(addrBytes[15] & 0xFF);
                lst6.add(new Exclusion6(addrHi, addrLo, subnet != -1 ? subnet : 128));
                continue;
            }
            if (addr instanceof Inet4Address) {
                Inet4Address addr4 = (Inet4Address)addr;
                addrBytes = addr4.getAddress();
                int addrInt = (addrBytes[0] & 0xFF) << 24 | (addrBytes[1] & 0xFF) << 16 | (addrBytes[2] & 0xFF) << 8 | addrBytes[3] & 0xFF;
                lst4.add(new Exclusion4(addrInt, subnet != -1 ? subnet : 32));
                continue;
            }
            logger.warn("Skipping unknown ratelimit address: \"" + addr + "\" (" + addr.getClass().getName() + ")");
        }
        Collections.sort(lst4, (a, b) -> a.subnet - b.subnet);
        Collections.sort(lst6, (a, b) -> a.subnet - b.subnet);
        return new RateLimiterExclusions((ImmutableList<Exclusion4>)ImmutableList.copyOf(lst4), (ImmutableList<Exclusion6>)ImmutableList.copyOf(lst6));
    }

    private RateLimiterExclusions(ImmutableList<Exclusion4> lst4, ImmutableList<Exclusion6> lst6) {
        this.lst4 = lst4;
        this.lst6 = lst6;
    }

    public boolean testExclusion(InetAddress addr) {
        if (addr instanceof Inet6Address) {
            return this.testExclusion6((Inet6Address)addr);
        }
        if (addr instanceof Inet4Address) {
            return this.testExclusion4((Inet4Address)addr);
        }
        return false;
    }

    public boolean testExclusion4(Inet4Address addr) {
        int l = this.lst4.size();
        if (l == 0) {
            return false;
        }
        byte[] addrBytes = addr.getAddress();
        int addrInt = (addrBytes[0] & 0xFF) << 24 | (addrBytes[1] & 0xFF) << 16 | (addrBytes[2] & 0xFF) << 8 | addrBytes[3] & 0xFF;
        for (int i = 0; i < l; ++i) {
            Exclusion4 ex = (Exclusion4)this.lst4.get(i);
            if ((addrInt & ex.mask) != ex.addr) continue;
            return true;
        }
        return false;
    }

    public boolean testExclusion6(Inet6Address addr) {
        int l = this.lst6.size();
        if (l == 0) {
            return false;
        }
        byte[] addrBytes = addr.getAddress();
        long addrHi = (long)(addrBytes[0] & 0xFF) << 56 | (long)(addrBytes[1] & 0xFF) << 48 | (long)(addrBytes[2] & 0xFF) << 40 | (long)(addrBytes[3] & 0xFF) << 32 | (long)(addrBytes[4] & 0xFF) << 24 | (long)(addrBytes[5] & 0xFF) << 16 | (long)(addrBytes[6] & 0xFF) << 8 | (long)(addrBytes[7] & 0xFF);
        long addrLo = (long)(addrBytes[8] & 0xFF) << 56 | (long)(addrBytes[9] & 0xFF) << 48 | (long)(addrBytes[10] & 0xFF) << 40 | (long)(addrBytes[11] & 0xFF) << 32 | (long)(addrBytes[12] & 0xFF) << 24 | (long)(addrBytes[13] & 0xFF) << 16 | (long)(addrBytes[14] & 0xFF) << 8 | (long)(addrBytes[15] & 0xFF);
        for (int i = 0; i < l; ++i) {
            Exclusion6 ex = (Exclusion6)this.lst6.get(i);
            if ((addrHi & ex.maskHi) != ex.addrHi || (addrLo & ex.maskLo) != ex.addrLo) continue;
            return true;
        }
        return false;
    }

    private static class Exclusion6 {
        protected final long addrHi;
        protected final long addrLo;
        protected final long maskHi;
        protected final long maskLo;
        protected final int subnet;

        protected Exclusion6(long addrHi, long addrLo, int subnet) {
            if (subnet > 64) {
                this.maskHi = -1L;
                this.maskLo = (1L << 128 - subnet) - 1L ^ 0xFFFFFFFFFFFFFFFFL;
            } else {
                this.maskHi = (1L << 64 - subnet) - 1L ^ 0xFFFFFFFFFFFFFFFFL;
                this.maskLo = 0L;
            }
            this.addrHi = addrHi & this.maskHi;
            this.addrLo = addrLo & this.maskLo;
            this.subnet = subnet;
        }
    }

    private static class Exclusion4 {
        protected final int addr;
        protected final int mask;
        protected final int subnet;

        protected Exclusion4(int addr, int subnet) {
            this.mask = ~((1 << 32 - subnet) - 1);
            this.addr = addr & this.mask;
            this.subnet = subnet;
        }
    }
}

