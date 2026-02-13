package com.jt.ecs.mysql;

import com.jt.ecs.api.Id;

public record IdImpl(byte[] bytes) implements Id {
}
