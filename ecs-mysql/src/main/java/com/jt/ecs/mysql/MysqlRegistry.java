package com.jt.ecs.mysql;

import com.jt.ecs.api.*;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.*;

public class MysqlRegistry<T> implements Registry<T> {
    private final static String TABLE_NAME = "component";

    private final DataSource dataSource;
    private final DataMapper<T> dataMapper;

    public MysqlRegistry(DataSource dataSource, DataMapper<T> dataMapper) {
        this.dataSource = dataSource;
        this.dataMapper = dataMapper;
    }

    private Map<String, ValueWithType> intoRow(Component<T> component) {
        var typeAndData = dataMapper.map(component.data());
        var uniqueFlag = component.unique()
                ? new ValueWithType(1, JDBCType.TINYINT)
                : ValueWithType.NULL;
        var typeAndDataHash = hash(typeAndData);

        return Map.of(
                "id", new ValueWithType(component.id().bytes(), JDBCType.BINARY),
                "entity_id", new ValueWithType(component.entityId().bytes(), JDBCType.BINARY),
                "type", new ValueWithType(typeAndData.type(), JDBCType.SMALLINT),
                "data", new ValueWithType(typeAndData.data(), JDBCType.LONGNVARCHAR),
                "created_at", new ValueWithType(ZonedDateTime.now(Clock.systemUTC()), JDBCType.TIMESTAMP),
                "type_and_data_hash", new ValueWithType(typeAndDataHash, JDBCType.BINARY),
                "unique_flag", uniqueFlag
        );
    }

    private byte[] hash(TypeAndData typeAndData) {
        var stringToHash = (typeAndData.type() + ":" + typeAndData.data()).getBytes(StandardCharsets.UTF_8);
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(stringToHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void execute(Transaction<T> transaction) {
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            var rows = transaction.execute()
                    .stream()
                    .map(this::intoRow)
                    .toList();

            var insert = Insert.of(TABLE_NAME)
                    .withRows(rows)
                    .build();

            try (var stmt = insert.prepare(conn)) {
                stmt.execute();
                conn.commit();
                if (stmt.getUpdateCount() != rows.size()) {
                    throw new RuntimeException("Failed to insert rows. Update count: " + stmt.getUpdateCount() + ", expected: " + rows.size());
                }
            }

        } catch (SQLIntegrityConstraintViolationException e) {
            var message = e.getMessage();
            if (message.contains("Duplicate entry")) {
                if (message.contains("component.unique_type_and_data")) {
                    throw new DuplicateUniqueDataException(e);
                }

                if (message.contains("component.PRIMARY")) {
                    throw new DuplicateEntryException(e);
                }

                if (message.contains("component.unique_entity_id_and_type")) {
                    throw new DuplicateComponentException(e);
                }
            }

            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Entity<T>> execute(Query.SingletonQuery query) {
        return switch (query) {
            case Query.SingletonQuery.ByEntityIdQuery q -> find(q);
        };
    }

    private Optional<Entity<T>> find(Query.SingletonQuery.ByEntityIdQuery query) {
        var sql = "select id, entity_id, type, data, created_at, unique_flag from component where entity_id = ?";

        var componentList = new ArrayList<Component<T>>();
        try (var stmt = dataSource.getConnection().prepareStatement(sql)) {
            stmt.setBytes(1, query.id().bytes());
            var result = stmt.executeQuery();
            if (result.next()) {
                var id = result.getBytes("id");
                var entityId = result.getBytes("entity_id");
                var type = result.getInt("type");
                var data = result.getString("data");
                var dataObj = dataMapper.map(new TypeAndData(type, data));
                var _createdAt = result.getTimestamp("created_at");
                var uniqueFlag = result.getByte("unique_flag");
                var component = new Component<T>(new IdImpl(id), new IdImpl(entityId), dataObj, uniqueFlag == 1);
                componentList.add(component);
            }
            if (!componentList.isEmpty()) {
                return Optional.of(new Entity<T>(query.id(), Collections.unmodifiableList(componentList)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }
}
