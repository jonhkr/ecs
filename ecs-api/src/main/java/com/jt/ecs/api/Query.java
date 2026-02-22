package com.jt.ecs.api;

import java.util.Collections;
import java.util.List;

public sealed interface Query {
    sealed interface SingletonQuery<T> extends Query {
        record ByEntityIdQuery<T>(Id id, List<Class<? extends T>> selectedTypes) implements SingletonQuery<T> {
        }
    }

    static <T> SingletonQuery.ByEntityIdQuery<T> byEntityId(Id id) {
        return new SingletonQuery.ByEntityIdQuery<>(id, Collections.emptyList());
    }

    static <T> SingletonQuery.ByEntityIdQuery<T> byEntityId(Id id, List<Class<? extends T>> selectedTypes) {
        return new SingletonQuery.ByEntityIdQuery<>(id, selectedTypes);
    }
}