package com.jt.ecs.api;

import java.util.List;

public interface Transaction<T> {
    List<Component<T>> execute();
}
