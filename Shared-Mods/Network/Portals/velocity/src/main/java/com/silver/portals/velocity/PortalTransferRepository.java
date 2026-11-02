package com.silver.portals.velocity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Transactional MariaDB persistence for portal transfers (table wakeup_portal_transfers, created by the routing
 * plugin's first migration; {@link #ensureTable()} also creates it so this plugin works on a fresh database).
 */
public final class PortalTransferRepository {
    private final DataSource dataSource;

    public PortalTransferRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Same DDL as the routing plugin's V001 migration; IF NOT EXISTS makes it a no-op on the live database. */
    public void ensureTable() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS wakeup_portal_transfers ("
                    + "transfer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY, "
                    + "player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
                    + "source_server VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
                    + "target_server VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
                    + "arrival_portal VARCHAR(128) NOT NULL DEFAULT '', "
                    + "status ENUM('PENDING','CLAIMED','COMPLETED','CANCELLED','EXPIRED') NOT NULL, "
                    + "issued_at TIMESTAMP(3) NOT NULL, "
                    + "expires_at TIMESTAMP(3) NOT NULL, "
                    + "claimed_at TIMESTAMP(3) NULL, "
                    + "completed_at TIMESTAMP(3) NULL, "
                    + "updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), "
                    + "INDEX ix_wakeup_transfer_player_status (player_uuid, status), "
                    + "INDEX ix_wakeup_transfer_expiry (status, expires_at)"
                    + ") ENGINE=InnoDB");
        }
    }

    /** Expires stale rows, makes interrupted CLAIMED ones resumable, and returns the pending transfer per player. */
    public Map<UUID, PortalTransfer> loadPending(Instant now) throws SQLException {
        Map<UUID, PortalTransfer> pending = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement expire = connection.prepareStatement(
                    "UPDATE wakeup_portal_transfers SET status='EXPIRED' "
                            + "WHERE status IN ('PENDING','CLAIMED') AND expires_at <= ?")) {
                expire.setTimestamp(1, Timestamp.from(now));
                expire.executeUpdate();
            }
            // CLAIMED is only an in-process connection attempt. If Velocity stopped
            // before completing it, no live attempt remains, so make it resumable.
            try (PreparedStatement recover = connection.prepareStatement(
                    "UPDATE wakeup_portal_transfers SET status='PENDING', claimed_at=NULL "
                            + "WHERE status='CLAIMED' AND expires_at > ?")) {
                recover.setTimestamp(1, Timestamp.from(now));
                recover.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT transfer_id, player_uuid, source_server, target_server, arrival_portal, "
                            + "status, issued_at, expires_at FROM wakeup_portal_transfers "
                            + "WHERE status='PENDING' AND expires_at > ? ORDER BY issued_at")) {
                statement.setTimestamp(1, Timestamp.from(now));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        PortalTransfer transfer = readTransfer(rows);
                        pending.put(transfer.playerId(), transfer);
                    }
                }
            }
        }
        return Map.copyOf(pending);
    }

    public PortalTransfer createTransfer(UUID playerId, String source, String target,
                                         String arrivalPortal, Instant issuedAt, Instant expiresAt) throws SQLException {
        PortalTransfer transfer = new PortalTransfer(UUID.randomUUID(), playerId, source, target,
                arrivalPortal, PortalTransfer.Status.PENDING, issuedAt, expiresAt);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement cancel = connection.prepareStatement(
                        "UPDATE wakeup_portal_transfers SET status='CANCELLED' "
                                + "WHERE player_uuid=? AND status IN ('PENDING','CLAIMED')")) {
                    cancel.setString(1, playerId.toString());
                    cancel.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO wakeup_portal_transfers(transfer_id, player_uuid, source_server, target_server, "
                                + "arrival_portal, status, issued_at, expires_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)")) {
                    insert.setString(1, transfer.transferId().toString());
                    insert.setString(2, playerId.toString());
                    insert.setString(3, source);
                    insert.setString(4, target);
                    insert.setString(5, transfer.arrivalPortal());
                    insert.setTimestamp(6, Timestamp.from(issuedAt));
                    insert.setTimestamp(7, Timestamp.from(expiresAt));
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return transfer;
    }

    public Optional<PortalTransfer> claimPending(UUID playerId, String target, Instant now) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement claim = connection.prepareStatement(
                        "UPDATE wakeup_portal_transfers SET status='CLAIMED', claimed_at=? "
                                + "WHERE player_uuid=? AND target_server=? AND status='PENDING' AND expires_at>?")) {
                    claim.setTimestamp(1, Timestamp.from(now));
                    claim.setString(2, playerId.toString());
                    claim.setString(3, target);
                    claim.setTimestamp(4, Timestamp.from(now));
                    changed = claim.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return Optional.empty();
                }
                PortalTransfer transfer;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT transfer_id, player_uuid, source_server, target_server, arrival_portal, "
                                + "status, issued_at, expires_at FROM wakeup_portal_transfers "
                                + "WHERE player_uuid=? AND target_server=? AND status='CLAIMED' ORDER BY claimed_at DESC LIMIT 1")) {
                    select.setString(1, playerId.toString());
                    select.setString(2, target);
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Claimed portal transfer disappeared");
                        transfer = readTransfer(rows);
                    }
                }
                connection.commit();
                return Optional.of(transfer);
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void restorePending(UUID transferId) throws SQLException {
        updateTransferStatus(transferId, "PENDING", "CLAIMED");
    }

    public void complete(UUID transferId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE wakeup_portal_transfers SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP(3) "
                             + "WHERE transfer_id=? AND status='CLAIMED'")) {
            statement.setString(1, transferId.toString());
            statement.executeUpdate();
        }
    }

    public void cancelActive(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE wakeup_portal_transfers SET status='CANCELLED' "
                             + "WHERE player_uuid=? AND status IN ('PENDING','CLAIMED')")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void updateTransferStatus(UUID transferId, String next, String expected) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE wakeup_portal_transfers SET status=? WHERE transfer_id=? AND status=?")) {
            statement.setString(1, next);
            statement.setString(2, transferId.toString());
            statement.setString(3, expected);
            statement.executeUpdate();
        }
    }

    private static PortalTransfer readTransfer(ResultSet rows) throws SQLException {
        return new PortalTransfer(
                UUID.fromString(rows.getString("transfer_id")),
                UUID.fromString(rows.getString("player_uuid")),
                rows.getString("source_server"),
                rows.getString("target_server"),
                rows.getString("arrival_portal"),
                PortalTransfer.Status.valueOf(rows.getString("status")),
                rows.getTimestamp("issued_at").toInstant(),
                rows.getTimestamp("expires_at").toInstant());
    }

}
