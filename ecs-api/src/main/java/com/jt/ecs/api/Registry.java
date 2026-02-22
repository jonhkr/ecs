package com.jt.ecs.api;

import java.util.Optional;

public interface Registry<D> {
    void execute(Transaction<D> transaction);
    Optional<Entity<D>> execute(Query.SingletonQuery<D> query);
}
