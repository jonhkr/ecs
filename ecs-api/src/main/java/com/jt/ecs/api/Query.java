package com.jt.ecs.api;

public sealed interface Query {
    static SingletonQuery withData(com.jt.ecs.api.ApiTest.SealedData.IdempotencyKey idempotencyKey) {
    }

    sealed interface SingletonQuery extends Query {
        record ByEntityIdQuery(Id id) implements SingletonQuery {}
    }

    static SingletonQuery byEntityId(Id id) {
        return new SingletonQuery.ByEntityIdQuery(id);
    }
}
