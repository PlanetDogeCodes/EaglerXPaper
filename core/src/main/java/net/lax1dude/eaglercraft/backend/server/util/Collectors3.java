/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableSet
 */
package net.lax1dude.eaglercraft.backend.server.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Collectors3 {
    public static final Collector<Object, ?, List<Object>> IMMUTABLE_LIST;
    public static final Collector<Object, ?, Set<Object>> IMMUTABLE_SET;

    public static <E> Collector<E, ?, List<E>> toImmutableList() {
        return (Collector)IMMUTABLE_LIST;
    }

    public static <E> Collector<E, ?, Set<E>> toImmutableSet() {
        return (Collector)IMMUTABLE_SET;
    }

    static {
        Collector c2;
        Collector c1;
        try {
            c1 = (Collector)ImmutableList.class.getMethod("toImmutableList", new Class[0]).invoke(null, new Object[0]);
        }
        catch (ReflectiveOperationException ex) {
            c1 = Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
        }
        try {
            c2 = (Collector)ImmutableSet.class.getMethod("toImmutableSet", new Class[0]).invoke(null, new Object[0]);
        }
        catch (ReflectiveOperationException ex) {
            c2 = Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet);
        }
        IMMUTABLE_LIST = c1;
        IMMUTABLE_SET = c2;
    }
}

