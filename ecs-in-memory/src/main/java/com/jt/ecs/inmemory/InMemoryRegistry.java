package com.jt.ecs.inmemory;

import com.jt.ecs.api.Entity;
import com.jt.ecs.api.Query;
import com.jt.ecs.api.Registry;
import com.jt.ecs.api.Transaction;

import java.util.Optional;

public class InMemoryRegistry<T> implements Registry<T> {

    @Override
    public void execute(Transaction<T> transaction) {
        var components = transaction.execute();
        components.forEach(System.out::println);
    }

    @Override
    public Optional<Entity<T>> execute(Query.SingletonQuery query) {
        return Optional.empty();
    }
}
