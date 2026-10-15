package com.myroutine.common.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class JobLock {
    private static final String LOCK_SQL = "SELECT pg_try_advisory_lock(hashtext(?))";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(hashtext(?))";
    private final DataSource dataSource;

    public boolean runExclusively(String jobName, Runnable task) {
        try (Connection c = dataSource.getConnection()) {
            if (!executeLockSql(c, LOCK_SQL, jobName)) return false;
            try {
                task.run();
            } finally {
                executeLockSql(c, UNLOCK_SQL, jobName);
            }
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("job lock failed: " + jobName, e);
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
