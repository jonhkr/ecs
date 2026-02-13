package com.jt.ecs.api;

import java.util.Optional;

public interface Registry<T> {
    void execute(Transaction<T> transaction);
    Optional<Entity<T>> execute(Query.SingletonQuery query);
}
