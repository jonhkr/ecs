package com.jt.ecs.mysql;

public interface DataMapper<T> {
    TypeAndData map(T data);
    T map(TypeAndData data);
}
