package com.jt.ecs.api;

import com.jt.ecs.id.UniqueId;
import com.jt.ecs.mysql.DataMapper;
import com.jt.ecs.mysql.MysqlRegistry;
import com.jt.ecs.mysql.TypeAndData;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiTest {

    @Test
    public void testApi() {
        var dataSource = new MysqlDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setUser("root");
        dataSource.setPassword("root");
        dataSource.setDatabaseName("ecs");

        var registry = new MysqlRegistry<>(SystemData.SYSTEM, dataSource, new Mapper());
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

//        var result = registry.execute(Query.withData(new TestSystem.IdempotencyKey(idempotencyKey)));
    }

    sealed interface SystemData {
        ComponentSystem<SystemData> SYSTEM = new ComponentSystem<>(new SystemId((byte) 1));

        record TestData(String stringer, int inter)
                implements SystemData {
        }

        record IdempotencyKey(String key) implements SystemData {
        }
    }

    static class Mapper implements DataMapper<SystemData> {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public TypeAndData map(SystemData data) {
            return switch (data) {
                case SystemData.TestData c -> new TypeAndData(1, mapper.writeValueAsString(c));
                case SystemData.IdempotencyKey c -> new TypeAndData(2, mapper.writeValueAsString(c));
            };
        }

        @Override
        public SystemData map(TypeAndData data) {
            return switch (data.type()) {
                case 1 -> mapper.readValue(data.data(), SystemData.TestData.class);
                case 2 -> mapper.readValue(data.data(), SystemData.IdempotencyKey.class);
                default -> throw new IllegalArgumentException("Invalid data type: " + data.type());
            };
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
