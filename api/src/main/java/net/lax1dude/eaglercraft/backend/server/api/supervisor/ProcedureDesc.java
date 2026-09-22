/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 */
package net.lax1dude.eaglercraft.backend.server.api.supervisor;

import javax.annotation.Nonnull;
import net.lax1dude.eaglercraft.backend.server.api.supervisor.data.ISupervisorData;
import net.lax1dude.eaglercraft.backend.server.api.supervisor.data.SupervisorDataVoid;

public final class ProcedureDesc<In extends ISupervisorData, Out extends ISupervisorData> {
    private final String name;
    private final Class<In> inputType;
    private final Class<Out> outputType;

    @Nonnull
    public static <In extends ISupervisorData, Out extends ISupervisorData> ProcedureDesc<In, Out> create(@Nonnull String name, @Nonnull Class<In> inputType, @Nonnull Class<Out> outputType) {
        if (name.length() == 0) {
            throw new IllegalArgumentException("Procedure name cannot be empty!");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Procedure name is too long! Max is 255 chars");
        }
        if (inputType == null) {
            throw new NullPointerException("inputType");
        }
        if (outputType == null) {
            throw new NullPointerException("outputType");
        }
        return new ProcedureDesc<In, Out>(name.intern(), inputType, outputType);
    }

    @Nonnull
    public static <In extends ISupervisorData> ProcedureDesc<In, SupervisorDataVoid> create(@Nonnull String name, @Nonnull Class<In> inputType) {
        if (name.length() == 0) {
            throw new IllegalArgumentException("Procedure name cannot be empty!");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Procedure name is too long! Max is 255 chars");
        }
        if (inputType == null) {
            throw new NullPointerException("inputType");
        }
        return ProcedureDesc.create(name, inputType, SupervisorDataVoid.class);
    }

    @Nonnull
    public static ProcedureDesc<SupervisorDataVoid, SupervisorDataVoid> create(@Nonnull String name) {
        if (name.length() == 0) {
            throw new IllegalArgumentException("Procedure name cannot be empty!");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Procedure name is too long! Max is 255 chars");
        }
        return ProcedureDesc.create(name, SupervisorDataVoid.class, SupervisorDataVoid.class);
    }

    @Nonnull
    private ProcedureDesc(@Nonnull String name, @Nonnull Class<In> inputType, @Nonnull Class<Out> outputType) {
        this.name = name;
        this.inputType = inputType;
        this.outputType = outputType;
    }

    @Nonnull
    public String getName() {
        return this.name;
    }

    @Nonnull
    public Class<In> getInputType() {
        return this.inputType;
    }

    @Nonnull
    public Class<Out> getOutputType() {
        return this.outputType;
    }

    public int hashCode() {
        return (this.name.hashCode() * 31 + this.inputType.hashCode()) * 31 + this.outputType.hashCode();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcedureDesc)) {
            return false;
        }
        ProcedureDesc v = (ProcedureDesc)o;
        return v.name.equals(this.name) && v.inputType == this.inputType && v.outputType == this.outputType;
    }

    @Nonnull
    public String toString() {
        return this.name;
    }
}

