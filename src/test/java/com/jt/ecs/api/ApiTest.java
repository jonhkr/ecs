package com.jt.ecs.api;

import com.jt.ecs.id.UniqueId;
import com.jt.ecs.mysql.ComponentSystem;
import com.jt.ecs.mysql.DataMapper;
import com.jt.ecs.mysql.MysqlRegistry;
import com.jt.ecs.mysql.TypeAndData;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ApiTest {

    @Test
    public void testApi() {
        var dataSource = new MysqlDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setUser("root");
        dataSource.setPassword("root");
        dataSource.setDatabaseName("ecs");

        var registry = new MysqlRegistry<>(SystemData.SYSTEM, dataSource);
        var entityId = UniqueId.generate();
        var idempotencyKey = UUID.randomUUID().toString();
        registry.execute(new TestTransaction(entityId, idempotencyKey, "test", 111));

        var result = registry.execute(Query.byEntityId(entityId));
        assertTrue(result.isPresent());
        var entity = result.get();
        assertEquals(2, entity.componentList().size());
        var component = entity.getComponent(SystemData.TestData.class);
        assertTrue(component.isPresent());
        assertEquals("test", component.get().data().stringer);
        assertEquals(111, component.get().data().inter);

        var result2 = registry.execute(Query.byEntityId(entityId, List.of(SystemData.IdempotencyKey.class)));
        assertTrue(result2.isPresent());
        var entity2 = result2.get();
        assertEquals(1, entity2.componentList().size());
        assertTrue(entity2.getComponent(SystemData.TestData.class).isEmpty());
        var keyComponent = entity.getComponent(SystemData.IdempotencyKey.class);
        assertTrue(keyComponent.isPresent());
        assertEquals(idempotencyKey, keyComponent.get().data().key);

        var result3 = registry.execute(Query.byUniqueKey(new SystemData.IdempotencyKey(idempotencyKey)));
        assertTrue(result3.isPresent());
        var entity3 = result3.get();
        assertEquals(2, entity3.componentList().size());
        component = entity3.getComponent(SystemData.TestData.class);
        assertTrue(component.isPresent());
        assertEquals("test", component.get().data().stringer);
        assertEquals(111, component.get().data().inter);

        keyComponent = entity3.getComponent(SystemData.IdempotencyKey.class);
        assertTrue(keyComponent.isPresent());
        assertEquals(idempotencyKey, keyComponent.get().data().key);
    }

    sealed interface SystemData {
        ComponentSystem<SystemData> SYSTEM = new ComponentSystem<>(new com.jt.ecs.mysql.SystemId((byte) 1), new Mapper());

        record TestData(String stringer, int inter)
                implements SystemData {
        }

        record IdempotencyKey(String key) implements SystemData {
        }
    }

    static class Mapper implements DataMapper<SystemData> {
        private final ObjectMapper mapper = new ObjectMapper();

        enum SystemDataType {
            TEST_DATA(1, SystemData.TestData.class),
            IDEMPOTENCY_KEY(2, SystemData.IdempotencyKey.class);

            private final int id;
            private final Class<? extends SystemData> clazz;

            SystemDataType(int id, Class<? extends SystemData> clazz) {
                this.id = id;
                this.clazz = clazz;
            }

            private static final Map<Integer, SystemDataType> BY_ID =
                    Arrays.stream(values()).collect(Collectors.toMap(e -> e.id, e -> e));

            private static final Map<Class<?>, SystemDataType> BY_CLASS =
                    Arrays.stream(values()).collect(Collectors.toMap(e -> e.clazz, e -> e));

            public static SystemDataType fromId(int id) {
                return Optional.ofNullable(BY_ID.get(id))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown type ID: " + id));
            }

            public static SystemDataType fromClass(Class<?> clazz) {
                return Optional.ofNullable(BY_CLASS.get(clazz))
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported class: " + clazz));
            }
        }

        @Override
        public TypeAndData map(SystemData data) {
            var type = SystemDataType.fromClass(data.getClass());
            return new TypeAndData(type.id, mapper.writeValueAsString(data));
        }

        @Override
        public SystemData map(TypeAndData data) {
            var type = SystemDataType.fromId(data.type());
            return mapper.readValue(data.data(), type.clazz);
        }

        @Override
        public List<Integer> resolveTypes(List<Class<? extends SystemData>> classes) {
            return classes.stream()
                    .map(SystemDataType::fromClass)
                    .map(t -> t.id)
                    .toList();
        }
    }

    record TestTransaction(
            Id entityId,
            String idempotencyKey,
            String stringer,
            int inter
    ) implements Transaction<SystemData> {
        @Override
        public List<Component<SystemData>> execute() {
            return List.of(
                    new Component<>(UniqueId.generate(), entityId, new SystemData.IdempotencyKey(idempotencyKey), true),
                    new Component<>(UniqueId.generate(), entityId, new SystemData.TestData(stringer, inter), false));
        }
    }
}
