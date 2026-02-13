package com.jt.ecs.api;

import java.util.List;
import java.util.Optional;

public record Entity<T>(Id id, List<Component<T>> componentList) {

    public <R extends T> Optional<Component<R>> getComponent(Class<R> type) {
        return componentList.stream()
                .filter(c -> c.data().getClass().equals(type))
                .map(c -> this.safeCast(c, type))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private <R extends T> Component<R> safeCast(Component<T> component, Class<R> type) {
        return (Component<R>) component;
    }
}
