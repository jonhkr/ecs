package com.jt.ecs.api;

import java.util.Optional;

public interface Registry<D, T extends ComponentSystem<D>> {
    void execute(Transaction<D> transaction);
    Optional<Entity<D>> execute(Query.SingletonQuery query);
}
