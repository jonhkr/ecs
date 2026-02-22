package com.jt.ecs.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

record Insert(String sql, List<ValueWithType> values) {

    public static Builder of(String tableName) {
        return new Builder(tableName);
    }

    PreparedStatement prepare(Connection conn) throws SQLException {
        var stmt = conn.prepareStatement(sql);
        for (int i = 1; i <= values.size(); i++) {
            var value = values.get(i - 1);
            stmt.setObject(i, value.value(), value.type());
        }
        return stmt;
    }

    static class Builder {
        private final String tableName;
        private List<Map<String, ValueWithType>> rows;

        public Builder(String tableName) {
            this.tableName = tableName;
        }

        public Builder withRows(List<Map<String, ValueWithType>> rows) {
            this.rows = rows;
            return this;
        }

        private String escaped(String string) {
            return "`" + string + "`";
        }

        Insert build() {
            var columns = new TreeSet<String>();
            var values = new ArrayList<ValueWithType>();
            for (var row : rows) {
                columns.addAll(row.keySet());
            }

            var outer = new StringJoiner(",");
            for (var row : rows) {
                var inner = new StringJoiner(",", "(", ")");
                for (var column : columns) {
                    inner.add("?");
                    values.add(row.getOrDefault(column, ValueWithType.NULL));
                }
                outer.add(inner.toString());
            }

            var sql = "insert into %s (%s) values %s".formatted(
                    escaped(tableName),
                    String.join(",", columns.stream().map(this::escaped).toList()),
                    outer.toString());

            return new Insert(sql, values);
        }
    }
}
