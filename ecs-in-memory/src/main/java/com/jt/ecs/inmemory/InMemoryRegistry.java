package com.jt.ecs.inmemory;

import com.jt.ecs.api.*;

import java.util.Optional;

public class InMemoryRegistry<D> implements Registry<D> {

    @Override
    public void execute(Transaction<D> transaction) {
        var components = transaction.execute();
        components.forEach(System.out::println);
    }

    @Override
    public Optional<Entity<D>> execute(Query.SingletonQuery<D> query) {
        return Optional.empty();
    }
}
