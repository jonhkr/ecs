package com.jt.ecs.mysql;

import java.sql.JDBCType;
import java.sql.SQLType;

public record ValueWithType(Object value, SQLType type) {
    public static final ValueWithType NULL = new ValueWithType(null, JDBCType.NULL);
}
