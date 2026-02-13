package com.jt.ecs.api;

public record Component<T> (
        Id id,
        Id entityId,
        T data,
        boolean unique
) {

    public Component(Id id, Id entityId, T data) {
        this(id, entityId, data, false);
    }
}
