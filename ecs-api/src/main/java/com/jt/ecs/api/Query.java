package com.jt.ecs.api;

public sealed interface Query {
    sealed interface SingletonQuery extends Query {
        record ByEntityIdQuery(Id id) implements SingletonQuery {}
    }

    static SingletonQuery byEntityId(Id id) {
        return new SingletonQuery.ByEntityIdQuery(id);
    }
}
