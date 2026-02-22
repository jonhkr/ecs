package com.jt.ecs.mysql;

public record ComponentSystem<D>(
        SystemId id,
        DataMapper<D> mapper
) {
}
