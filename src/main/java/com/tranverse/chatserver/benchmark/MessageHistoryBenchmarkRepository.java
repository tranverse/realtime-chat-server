package com.tranverse.chatserver.benchmark;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("benchmark")
public class MessageHistoryBenchmarkRepository {

    private static final int BATCH_SIZE = 5_000;
    private static final long MESSAGE_ID_PREFIX = 0x4d534742454e4348L;

    private final JdbcTemplate jdbcTemplate;

    public MessageHistoryBenchmarkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BenchmarkMessageRow> findByKeyset(UUID conversationId,
                                                   long beforeSequence,
                                                   int size) {
        return jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT sequence, content
                      FROM messages
                     WHERE conversation_id = ?
                       AND sequence < ?
                     ORDER BY sequence DESC
                     LIMIT ?
                    """);
            statement.setBytes(1, uuidBytes(conversationId));
            statement.setLong(2, beforeSequence);
            statement.setInt(3, size);
            return statement;
        }, (resultSet, rowNumber) -> new BenchmarkMessageRow(
                resultSet.getLong("sequence"), resultSet.getString("content")));
    }

    public List<BenchmarkMessageRow> findByOffset(UUID conversationId,
                                                   long offset,
                                                   int size) {
        return jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT sequence, content
                      FROM messages
                     WHERE conversation_id = ?
                     ORDER BY sequence DESC
                     LIMIT ? OFFSET ?
                    """);
            statement.setBytes(1, uuidBytes(conversationId));
            statement.setInt(2, size);
            statement.setLong(3, offset);
            return statement;
        }, (resultSet, rowNumber) -> new BenchmarkMessageRow(
                resultSet.getLong("sequence"), resultSet.getString("content")));
    }

    public long ensureMessageCount(UUID conversationId, UUID senderId, int targetCount) {
        Long currentValue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ?",
                Long.class,
                uuidBytes(conversationId));
        long current = currentValue == null ? 0 : currentValue;
        if (current > targetCount) {
            jdbcTemplate.update(
                    "DELETE FROM messages WHERE conversation_id = ? AND sequence > ?",
                    uuidBytes(conversationId), targetCount);
            return targetCount;
        }
        if (current == targetCount) {
            return current;
        }

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO messages
                        (id, created_at, updated_at, deleted_at, content, type, sequence,
                         conversation_id, sender_user_id, reply_to_message_id, edited_at)
                    VALUES (?, ?, ?, NULL, ?, 'TEXT', ?, ?, ?, NULL, NULL)
                    """)) {
                byte[] conversationBytes = uuidBytes(conversationId);
                byte[] senderBytes = uuidBytes(senderId);
                Timestamp timestamp = Timestamp.from(Instant.now());
                int pending = 0;
                for (long sequence = current + 1; sequence <= targetCount; sequence++) {
                    statement.setBytes(1, messageIdBytes(sequence));
                    statement.setTimestamp(2, timestamp);
                    statement.setTimestamp(3, timestamp);
                    statement.setString(4, "Benchmark message " + sequence);
                    statement.setLong(5, sequence);
                    statement.setBytes(6, conversationBytes);
                    statement.setBytes(7, senderBytes);
                    statement.addBatch();
                    pending++;
                    if (pending == BATCH_SIZE) {
                        statement.executeBatch();
                        connection.commit();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    statement.executeBatch();
                    connection.commit();
                }
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            return null;
        });
        return targetCount;
    }

    private byte[] messageIdBytes(long sequence) {
        return ByteBuffer.allocate(16)
                .putLong(MESSAGE_ID_PREFIX)
                .putLong(sequence)
                .array();
    }

    static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
