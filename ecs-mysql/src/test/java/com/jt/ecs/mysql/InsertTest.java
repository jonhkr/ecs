package com.jt.ecs.mysql;

import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InsertTest {

    @Test
    public void testGeneratedSql() {
        var row1 = Map.of(
                "id", new ValueWithType(123L, JDBCType.BIGINT),
                "name", new ValueWithType("123", JDBCType.VARCHAR)
        );
        var row2 = Map.of(
                "id", new ValueWithType(456L, JDBCType.BIGINT),
                "name", new ValueWithType("456", JDBCType.VARCHAR),
                "extra_field", new ValueWithType("extra", JDBCType.VARCHAR)
        );
        var insert = Insert.of("testTable")
                .withRows(List.of(row1, row2))
                .build();

        assertEquals("insert into `testTable` (`extra_field`,`id`,`name`) values (?,?,?),(?,?,?)", insert.sql());
        assertEquals(List.of(
                ValueWithType.NULL, row1.get("id"), row1.get("name"),
                row2.get("extra_field"), row2.get("id"), row2.get("name")), insert.values());
    }
}