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

public class MysqlRegistry<D> implements Registry<D> {
    private final static String FIND_BY_ENTITY_ID_QUERY = """
            WITH RankedComponents AS (
                SELECT
                    id,
                    entity_id,
                    type,
                    data,
                    created_at,
                    unique_flag,
                    ROW_NUMBER() OVER(PARTITION BY type ORDER BY version DESC) as rn
                FROM component
                WHERE system_id = ? AND entity_id = ? %s
            )
            SELECT
                id, entity_id, type, data, created_at, unique_flag
            FROM RankedComponents
            WHERE rn = 1""";

    private final static String FIND_BY_UNIQUE_KEY_QUERY = """
            WITH TargetEntity AS (
                SELECT entity_id
                FROM component
                WHERE system_id = ?
                  AND type_and_data_hash = ?
                  AND unique_flag = 1
                LIMIT 1
            ),
                RankedComponents AS (
                SELECT
                    id,
                    entity_id,
                    type,
                    data,
                    created_at,
                    unique_flag,
                    ROW_NUMBER() OVER(PARTITION BY type ORDER BY version DESC) as rn
                FROM component
                WHERE system_id = ?
                  AND entity_id = (SELECT entity_id from TargetEntity)
                  %s
            )
            SELECT
                id, entity_id, type, data, created_at, unique_flag
            FROM RankedComponents
            WHERE rn = 1""";

    private final static String TABLE_NAME = "component";

    private final ComponentSystem<D> system;
    private final DataSource dataSource;

    public MysqlRegistry(ComponentSystem<D> system, DataSource dataSource) {
        this.system = system;
        this.dataSource = dataSource;
    }

    private Map<String, ValueWithType> intoRow(Component<D> component) {
        var typeAndData = system.mapper().map(component.data());
        var uniqueFlag = component.unique()
                ? new ValueWithType(1, JDBCType.TINYINT)
                : ValueWithType.NULL;
        var typeAndDataHash = hash(typeAndData);

        return Map.of(
                "system_id", new ValueWithType(Byte.toUnsignedInt(system.id().value()), JDBCType.TINYINT),
                "id", new ValueWithType(component.id().bytes(), JDBCType.BINARY),
                "entity_id", new ValueWithType(component.entityId().bytes(), JDBCType.BINARY),
                "type", new ValueWithType(typeAndData.type(), JDBCType.SMALLINT),
                "data", new ValueWithType(typeAndData.data(), JDBCType.LONGNVARCHAR),
                "version", new ValueWithType(0, JDBCType.INTEGER),
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
    public void execute(Transaction<D> transaction) {
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
    public Optional<Entity<D>> execute(Query.SingletonQuery<D> query) {
        return switch (query) {
            case Query.SingletonQuery.ByEntityIdQuery<D> q -> find(q);
            case Query.SingletonQuery.ByUniqueKey<D> q -> find(q);
        };
    }

    private Optional<Entity<D>> find(Query.SingletonQuery.ByEntityIdQuery<D> query) {
        List<Integer> typeIds = system.mapper().resolveTypes(query.selectedTypes());
        boolean hasTypes = !typeIds.isEmpty();
        String typePlaceholder = hasTypes
                ? "AND type IN (" + String.join(",", Collections.nCopies(typeIds.size(), "?")) + ")"
                : "";
        var componentList = new ArrayList<Component<D>>();
        try (var stmt = dataSource.getConnection().prepareStatement(FIND_BY_ENTITY_ID_QUERY.formatted(typePlaceholder))) {
            int paramIdx = 1;
            stmt.setInt(paramIdx++, Byte.toUnsignedInt(system.id().value()));
            stmt.setBytes(paramIdx++, query.id().bytes());
            for (Integer typeId : typeIds) {
                stmt.setInt(paramIdx++, typeId);
            }
            var result = stmt.executeQuery();
            while (result.next()) {
                var id = result.getBytes("id");
                var entityId = result.getBytes("entity_id");
                var type = result.getInt("type");
                var data = result.getString("data");
                var dataObj = system.mapper().map(new TypeAndData(type, data));
                var _createdAt = result.getTimestamp("created_at");
                var uniqueFlag = result.getByte("unique_flag");
                var component = new Component<>(new IdImpl(id), new IdImpl(entityId), dataObj, uniqueFlag == 1);
                componentList.add(component);
            }
            if (!componentList.isEmpty()) {
                return Optional.of(new Entity<>(query.id(), Collections.unmodifiableList(componentList)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    private Optional<Entity<D>> find(Query.SingletonQuery.ByUniqueKey<D> query) {
        List<Integer> typeIds = system.mapper().resolveTypes(query.selectedTypes());
        boolean hasTypes = !typeIds.isEmpty();
        String typePlaceholder = hasTypes
                ? "AND type IN (" + String.join(",", Collections.nCopies(typeIds.size(), "?")) + ")"
                : "";
        var componentList = new ArrayList<Component<D>>();
        var systemId = Byte.toUnsignedInt(system.id().value());
        var typeAndData = system.mapper().map(query.keyData());
        var hash = hash(typeAndData);
        try (var stmt = dataSource.getConnection().prepareStatement(FIND_BY_UNIQUE_KEY_QUERY.formatted(typePlaceholder))) {
            int paramIdx = 1;
            stmt.setInt(paramIdx++, systemId);
            stmt.setBytes(paramIdx++, hash);
            stmt.setInt(paramIdx++, systemId);
            for (Integer typeId : typeIds) {
                stmt.setInt(paramIdx++, typeId);
            }
            var result = stmt.executeQuery();
            while (result.next()) {
                var id = result.getBytes("id");
                var entityId = result.getBytes("entity_id");
                var type = result.getInt("type");
                var data = result.getString("data");
                var dataObj = system.mapper().map(new TypeAndData(type, data));
                var _createdAt = result.getTimestamp("created_at");
                var uniqueFlag = result.getByte("unique_flag");
                var component = new Component<>(new IdImpl(id), new IdImpl(entityId), dataObj, uniqueFlag == 1);
                componentList.add(component);
            }
            if (!componentList.isEmpty()) {
                return Optional.of(new Entity<>(componentList.getFirst().entityId(), Collections.unmodifiableList(componentList)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }
}
