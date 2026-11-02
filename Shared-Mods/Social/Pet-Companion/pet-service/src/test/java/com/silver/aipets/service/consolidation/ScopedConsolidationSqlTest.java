package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.persistence.PetRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Check scope at the SQL claim boundary; filtering after claim would consume foreign jobs. */
final class ScopedConsolidationSqlTest {
    @Test
    void petPredicateIsBoundBeforeDueCriteriaLimitAndRowLock() {
        Capture capture = new Capture();
        UUID pet = new UUID(0, 270);
        assertTrue(capture.repository().claimDueForPet(pet, "test", Instant.EPOCH,
                Duration.ofMinutes(1), 1).isEmpty());
        String sql = capture.sql.replaceAll("\\s+", " ");
        assertTrue(sql.contains("WHERE job_type=? AND pet_id=? AND ("));
        assertTrue(sql.contains("ORDER BY not_before, job_id LIMIT ? FOR UPDATE"));
        assertEquals("SLEEP_CONSOLIDATION", capture.bindings.get(1));
        assertEquals(pet.toString(), capture.bindings.get(2));
        assertEquals(1, capture.bindings.get(5));
        assertTrue(capture.committed);
    }

    @Test
    void globalWorkerRetainsItsOriginalUnscopedQueryAndParameterOrder() {
        Capture capture = new Capture();
        assertTrue(capture.repository().claimDue("test", Instant.EPOCH,
                Duration.ofMinutes(1), 10).isEmpty());
        assertFalse(capture.sql.contains("pet_id=?"));
        assertEquals(4, capture.bindings.size());
        assertEquals(10, capture.bindings.get(4));
    }

    private static final class Capture {
        String sql;
        boolean committed;
        Map<Integer, Object> bindings = new HashMap<>();

        JdbcConsolidationRepository repository() {
            ResultSet rows = proxy(ResultSet.class, (method, arguments) -> switch (method) {
                case "next" -> false;
                case "close" -> null;
                default -> throw new AssertionError("Unexpected result read: " + method);
            });
            PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
                if (method.startsWith("set")) { bindings.put((Integer) arguments[0], arguments[1]); return null; }
                return switch (method) {
                    case "executeQuery" -> rows;
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected statement: " + method);
                };
            });
            Connection connection = proxy(Connection.class, (method, arguments) -> switch (method) {
                case "prepareStatement" -> { sql = (String) arguments[0]; yield statement; }
                case "getAutoCommit" -> true;
                case "commit" -> { committed = true; yield null; }
                case "setAutoCommit", "close" -> null;
                default -> throw new AssertionError("Unexpected connection: " + method);
            });
            DataSource source = proxy(DataSource.class, (method, arguments) -> {
                if (method.equals("getConnection")) return connection;
                throw new AssertionError("Unexpected data source: " + method);
            });
            PetRepository pets = proxy(PetRepository.class, (method, arguments) -> {
                throw new AssertionError("Claim must not access pet state");
            });
            return new JdbcConsolidationRepository(source, pets, UUID::randomUUID, UUID::randomUUID);
        }
    }

    private interface Call { Object invoke(String method, Object[] arguments); }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> call.invoke(method.getName(), arguments)));
    }
}
