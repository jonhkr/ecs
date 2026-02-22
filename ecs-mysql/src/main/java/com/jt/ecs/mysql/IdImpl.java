package com.jt.ecs.mysql;

import com.jt.ecs.api.Id;

record IdImpl(byte[] bytes) implements Id {
}
