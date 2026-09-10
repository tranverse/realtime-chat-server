-- V1 can be recorded as a baseline on databases that already existed before
-- Flyway was introduced. Re-evaluate the live metadata in a normal versioned
-- migration so every legacy installation converges on the current table name.

SET @wrong_fk = (
    SELECT kcu.CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE kcu
    WHERE kcu.CONSTRAINT_SCHEMA = DATABASE()
      AND kcu.TABLE_NAME = 'message_attachment'
      AND kcu.COLUMN_NAME = 'message_id'
      AND kcu.REFERENCED_TABLE_NAME <> 'messages'
    LIMIT 1
);

SET @drop_wrong_fk_sql = IF(
    @wrong_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `message_attachment` DROP FOREIGN KEY `', @wrong_fk, '`')
);
PREPARE drop_wrong_fk_statement FROM @drop_wrong_fk_sql;
EXECUTE drop_wrong_fk_statement;
DEALLOCATE PREPARE drop_wrong_fk_statement;

SET @attachment_table_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message_attachment'
);
SET @messages_table_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages'
);
SET @correct_fk_exists = (
    SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'message_attachment'
      AND COLUMN_NAME = 'message_id'
      AND REFERENCED_TABLE_NAME = 'messages'
);

SET @add_correct_fk_sql = IF(
    @attachment_table_exists > 0 AND @messages_table_exists > 0 AND @correct_fk_exists = 0,
    'ALTER TABLE `message_attachment` ADD CONSTRAINT `fk_message_attachment_message` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`)',
    'SELECT 1'
);
PREPARE add_correct_fk_statement FROM @add_correct_fk_sql;
EXECUTE add_correct_fk_statement;
DEALLOCATE PREPARE add_correct_fk_statement;
