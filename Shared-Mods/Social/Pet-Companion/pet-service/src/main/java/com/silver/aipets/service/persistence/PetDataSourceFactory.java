package com.silver.aipets.service.persistence;

import com.silver.aipets.service.config.PetServiceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Objects;

/** Creates the deliberately small MariaDB pool used only by the standalone pet service. */
public final class PetDataSourceFactory {
    private PetDataSourceFactory() {
    }

    public static HikariDataSource create(PetServiceConfig service) {
        Objects.requireNonNull(service, "service");
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("pet-service-db");
        pool.setDriverClassName("org.mariadb.jdbc.Driver");
        pool.setJdbcUrl(service.jdbcUrl());
        pool.setUsername(service.databaseUser());
        pool.setPassword(service.databasePassword());
        pool.setMaximumPoolSize(service.poolMaximumSize());
        pool.setMinimumIdle(service.poolMinimumIdle());
        pool.setConnectionTimeout(service.connectionTimeoutMs());
        pool.setValidationTimeout(service.validationTimeoutMs());
        pool.setInitializationFailTimeout(-1L);
        pool.setAutoCommit(true);
        pool.setConnectionInitSql("SET time_zone = '+00:00'");
        pool.setMaxLifetime(30 * 60_000L);
        pool.setKeepaliveTime(2 * 60_000L);
        pool.setIdleTimeout(10 * 60_000L);
        pool.setRegisterMbeans(false);
        return new HikariDataSource(pool);
    }
}
