package com.jt.ecs.mysql;

import java.util.List;

public interface DataMapper<T> {
    TypeAndData map(T data);
    T map(TypeAndData data);

    List<Integer> resolveTypes(List<Class<? extends T>> classes);
}
