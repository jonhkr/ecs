package com.jt.ecs.id;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.jt.ecs.api.Id;

import java.nio.ByteBuffer;

public record UniqueId(byte[] bytes) implements Id {
    private final static NoArgGenerator GENERATOR = Generators.timeBasedEpochRandomGenerator();

    public static UniqueId generate() {
        var uuid = GENERATOR.generate();
        var bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return new UniqueId(bb.array());
    }
}
