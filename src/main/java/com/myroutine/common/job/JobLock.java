package com.myroutine.common.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class JobLock {
    private static final String LOCK_SQL = "SELECT pg_try_advisory_lock(hashtext(?))";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(hashtext(?))";
    private final DataSource dataSource;

    public boolean runExclusively(String jobName, Runnable task) {
        return callExclusively(jobName, () -> {
            task.run();
            return true;
        }).isPresent();
    }

    public <T> Optional<T> callExclusively(String name, Supplier<T> task) {
        try (Connection c = dataSource.getConnection()) {
            if (!executeLockSql(c, LOCK_SQL, name)) return Optional.empty();
            try {
                return Optional.of(task.get());
            } finally {
                executeLockSql(c, UNLOCK_SQL, name);
            }
        } catch (SQLException e) {
            throw new RuntimeException("job lock failed: " + name, e);
        }
    }

    private boolean executeLockSql(Connection c, String sql, String jobName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "job:" + jobName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

}
