-- The message entity was renamed from the legacy `message` table to `messages`.
-- Hibernate ddl-auto does not retarget existing foreign keys, which left older
-- databases inserting the parent and child rows into different table graphs.

SET @legacy_fk = (
    SELECT kcu.CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE kcu
    WHERE kcu.CONSTRAINT_SCHEMA = DATABASE()
      AND kcu.TABLE_NAME = 'message_attachment'
      AND kcu.COLUMN_NAME = 'message_id'
      AND kcu.REFERENCED_TABLE_NAME = 'message'
    LIMIT 1
);

SET @drop_legacy_fk_sql = IF(
    @legacy_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `message_attachment` DROP FOREIGN KEY `', @legacy_fk, '`')
);
PREPARE drop_legacy_fk_statement FROM @drop_legacy_fk_sql;
EXECUTE drop_legacy_fk_statement;
DEALLOCATE PREPARE drop_legacy_fk_statement;

SET @attachment_table_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message_attachment'
);
SET @messages_table_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages'
);
SET @current_fk_exists = (
    SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'message_attachment'
      AND COLUMN_NAME = 'message_id'
      AND REFERENCED_TABLE_NAME = 'messages'
);

SET @add_current_fk_sql = IF(
    @attachment_table_exists > 0 AND @messages_table_exists > 0 AND @current_fk_exists = 0,
    'ALTER TABLE `message_attachment` ADD CONSTRAINT `fk_message_attachment_message` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`)',
    'SELECT 1'
);
PREPARE add_current_fk_statement FROM @add_current_fk_sql;
EXECUTE add_current_fk_statement;
DEALLOCATE PREPARE add_current_fk_statement;
