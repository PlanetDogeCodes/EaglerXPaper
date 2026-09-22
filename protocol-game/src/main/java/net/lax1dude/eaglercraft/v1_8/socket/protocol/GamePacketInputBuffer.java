/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.v1_8.socket.protocol;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;

public interface GamePacketInputBuffer
extends DataInput {
    public void skipAllBytes(int var1) throws IOException;

    public int readVarInt() throws IOException;

    public long readVarLong() throws IOException;

    public String readStringMC(int var1) throws IOException;

    public String readStringEaglerASCII8() throws IOException;

    public String readStringEaglerASCII16() throws IOException;

    @Deprecated
    public byte[] readByteArrayMC() throws IOException;

    public byte[] readByteArrayMC(int var1) throws IOException;

    public int available() throws IOException;

    public InputStream stream();

    public byte[] toByteArray() throws IOException;
}

