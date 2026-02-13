package com.jt.ecs.api;

public class DuplicateEntryException extends RuntimeException {
    public DuplicateEntryException(Throwable cause) {
        super(cause);
    }
}
