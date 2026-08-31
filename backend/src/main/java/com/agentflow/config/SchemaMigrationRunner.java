package com.agentflow.config;

import com.agentflow.common.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies small idempotent schema upgrades for local/dev databases that were created before new features.
 */
@Slf4j
@Component
@Order(0)
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final String LEGACY_OWNER_EMAIL = "765754281@qq.com";

    private final JdbcTemplate jdbcTemplate;

    @Value("${agentflow.default-username:}")
    private String defaultUsername;

    @Value("${agentflow.default-password:}")
    private String defaultPassword;

    @Value("${agentflow.default-email:}")
    private String defaultEmail;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTableIfMissing("user", """
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键 ID',
                    username VARCHAR(64) NOT NULL COMMENT '用户名(唯一,注册时自动生成)',
                    email VARCHAR(320) NOT NULL COMMENT '邮箱(唯一,小写存储)',
                    password_hash VARCHAR(100) NOT NULL COMMENT '密码哈希(BCrypt)',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
                    UNIQUE KEY uk_user_username (username),
                    UNIQUE KEY uk_user_email (email)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表'
                """);
        createDefaultUserIfMissing();
        createTableIfMissing("knowledge_base", """
                CREATE TABLE knowledge_base (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识库 ID',
                    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
                    description VARCHAR(500) DEFAULT NULL COMMENT '知识库描述',
                    config_id BIGINT DEFAULT NULL COMMENT 'Agent Plan 全局配置 ID',
                    embedding_model VARCHAR(100) DEFAULT NULL COMMENT '向量模型',
                    chunk_size INT DEFAULT 800 COMMENT '分片长度',
                    chunk_overlap INT DEFAULT 100 COMMENT '分片重叠长度',
                    status VARCHAR(30) DEFAULT 'DRAFT' COMMENT '状态',
                    document_count INT DEFAULT 0 COMMENT '文档数',
                    chunk_count INT DEFAULT 0 COMMENT '分片数',
                    char_count BIGINT DEFAULT 0 COMMENT '字符数',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
                    INDEX idx_knowledge_base_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表'
                """);
        createTableIfMissing("knowledge_document", """
                CREATE TABLE knowledge_document (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识文档 ID',
                    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
                    title VARCHAR(255) DEFAULT NULL COMMENT '文档标题',
                    source_type VARCHAR(30) DEFAULT 'TEXT' COMMENT '来源类型',
                    source_url VARCHAR(1024) DEFAULT NULL COMMENT '来源 URL',
                    file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名',
                    raw_text MEDIUMTEXT COMMENT '原始文本',
                    tags VARCHAR(500) DEFAULT NULL COMMENT '标签',
                    status VARCHAR(30) DEFAULT 'IMPORTED' COMMENT '状态',
                    char_count BIGINT DEFAULT 0 COMMENT '字符数',
                    error_message TEXT COMMENT '错误信息',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
                    INDEX idx_knowledge_document_base (knowledge_base_id),
                    INDEX idx_knowledge_document_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表'
                """);
        createTableIfMissing("knowledge_index_task", """
                CREATE TABLE knowledge_index_task (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '索引任务 ID',
                    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
                    document_id BIGINT NOT NULL COMMENT '文档 ID',
                    status VARCHAR(30) DEFAULT 'RUNNING' COMMENT '状态',
                    progress INT DEFAULT 0 COMMENT '进度',
                    total_chunks INT DEFAULT 0 COMMENT '总分片数',
                    finished_chunks INT DEFAULT 0 COMMENT '完成分片数',
                    error_message TEXT COMMENT '错误信息',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
                    INDEX idx_knowledge_index_task_base (knowledge_base_id),
                    INDEX idx_knowledge_index_task_document (document_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库索引任务表'
                """);
        createTableIfMissing("knowledge_chunk", """
                CREATE TABLE knowledge_chunk (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识片段主键 ID',
                    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
                    document_id BIGINT DEFAULT NULL COMMENT '知识文档 ID',
                    chunk_index INT DEFAULT 0 COMMENT '分片序号',
                    title VARCHAR(255) DEFAULT NULL COMMENT '标题',
                    content TEXT NOT NULL COMMENT '片段内容',
                    source_url VARCHAR(1024) DEFAULT NULL COMMENT '来源 URL',
                    tags JSON COMMENT '标签',
                    embedding_model VARCHAR(100) DEFAULT NULL COMMENT '向量模型',
                    embedding JSON COMMENT '向量数据',
                    status VARCHAR(30) DEFAULT 'READY' COMMENT '状态',
                    char_count INT DEFAULT 0 COMMENT '字符数',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
                    INDEX idx_knowledge_base_id (knowledge_base_id),
                    INDEX idx_knowledge_document_id (document_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库片段表'
                """);
        createTableIfMissing("mcp_tool_config", """
                CREATE TABLE mcp_tool_config (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MCP 工具配置 ID',
                    name VARCHAR(100) NOT NULL COMMENT '名称',
                    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
                    tool_type VARCHAR(50) DEFAULT 'custom' COMMENT '工具类型',
                    tool_name VARCHAR(100) NOT NULL COMMENT '暴露给 Agent 的工具名',
                    transport VARCHAR(30) DEFAULT 'stdio' COMMENT '传输方式',
                    command VARCHAR(500) NOT NULL COMMENT '启动命令',
                    args JSON COMMENT '启动参数',
                    env JSON COMMENT '环境变量',
                    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
                    preset TINYINT DEFAULT 0 COMMENT '是否预设',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标识',
                    INDEX idx_mcp_tool_name (tool_name),
                    INDEX idx_mcp_tool_type (tool_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具配置表'
                """);
        createTableIfMissing("execution_snapshot", """
                CREATE TABLE execution_snapshot (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '快照主键 ID',
                    execution_id BIGINT NOT NULL COMMENT '执行记录 ID',
                    flow_id BIGINT NOT NULL COMMENT '工作流 ID',
                    node_id VARCHAR(100) NOT NULL COMMENT '节点 ID',
                    node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
                    node_name VARCHAR(255) DEFAULT NULL COMMENT '节点名称',
                    status VARCHAR(50) NOT NULL COMMENT '节点状态',
                    input_data JSON COMMENT '节点输入数据',
                    output_data JSON COMMENT '节点输出数据',
                    error_message TEXT COMMENT '错误信息',
                    started_at TIMESTAMP NULL COMMENT '开始执行时间',
                    completed_at TIMESTAMP NULL COMMENT '完成时间',
                    duration INT COMMENT '执行耗时(毫秒)',
                    retry_count INT DEFAULT 0 COMMENT '重试次数',
                    execution_order INT DEFAULT 0 COMMENT '执行顺序',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    INDEX idx_execution_id (execution_id),
                    INDEX idx_flow_id (flow_id),
                    INDEX idx_node_id (node_id),
                    INDEX idx_status (status),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行快照表'
                """);
        createTableIfMissing("execution_variable", """
                CREATE TABLE execution_variable (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '变量主键 ID',
                    execution_id BIGINT NOT NULL COMMENT '执行记录 ID',
                    variable_name VARCHAR(100) NOT NULL COMMENT '变量名',
                    variable_type VARCHAR(50) DEFAULT 'STRING' COMMENT '变量类型',
                    variable_value TEXT COMMENT '变量值',
                    is_modified TINYINT DEFAULT 0 COMMENT '是否被修改',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    UNIQUE KEY uk_execution_variable (execution_id, variable_name),
                    INDEX idx_execution_id (execution_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行变量表'
                """);
        addColumnIfMissing("llm_global_config", "embedding_model",
                "ALTER TABLE llm_global_config ADD COLUMN embedding_model VARCHAR(100) DEFAULT NULL COMMENT '默认向量模型' AFTER tts_model");
        addColumnIfMissing("llm_global_config", "image_model",
                "ALTER TABLE llm_global_config ADD COLUMN image_model VARCHAR(100) DEFAULT NULL COMMENT '默认图片生成模型' AFTER embedding_model");
        addColumnIfMissing("llm_global_config", "video_model",
                "ALTER TABLE llm_global_config ADD COLUMN video_model VARCHAR(100) DEFAULT NULL COMMENT '默认视频生成模型' AFTER image_model");
        addColumnIfMissing("llm_global_config", "memory_enabled",
                "ALTER TABLE llm_global_config ADD COLUMN memory_enabled TINYINT DEFAULT 0 COMMENT '是否启用Agent Plan记忆能力' AFTER video_model");
        makeColumnNullableIfNeeded("llm_global_config", "model",
                "ALTER TABLE llm_global_config MODIFY COLUMN model VARCHAR(100) NULL COMMENT '默认LLM模型'");
        addColumnIfMissing("knowledge_chunk", "document_id",
                "ALTER TABLE knowledge_chunk ADD COLUMN document_id BIGINT DEFAULT NULL COMMENT '知识文档 ID' AFTER knowledge_base_id");
        addColumnIfMissing("knowledge_chunk", "chunk_index",
                "ALTER TABLE knowledge_chunk ADD COLUMN chunk_index INT DEFAULT 0 COMMENT '分片序号' AFTER document_id");
        addColumnIfMissing("knowledge_chunk", "status",
                "ALTER TABLE knowledge_chunk ADD COLUMN status VARCHAR(30) DEFAULT 'READY' COMMENT '状态' AFTER embedding");
        addColumnIfMissing("knowledge_chunk", "char_count",
                "ALTER TABLE knowledge_chunk ADD COLUMN char_count INT DEFAULT 0 COMMENT '字符数' AFTER status");
        migrateOwnership();
    }

    private void createDefaultUserIfMissing() {
        String email = defaultEmail == null ? "" : defaultEmail.trim().toLowerCase();
        if (email.isEmpty() || defaultPassword == null || defaultPassword.isEmpty()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE email = ?", Integer.class, email);
        if (count != null && count > 0) {
            return;
        }
        String username = defaultUsername == null ? "" : defaultUsername.trim();
        if (username.isEmpty()) {
            throw new IllegalStateException("APP_AUTH_DEFAULT_USERNAME 不能为空");
        }
        Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE username = ?", Integer.class, username);
        if (usernameCount != null && usernameCount > 0) {
            throw new IllegalStateException("默认账户用户名已被其他邮箱占用: " + username);
        }
        jdbcTemplate.update(
                "INSERT INTO user (username, email, password_hash) VALUES (?, ?, ?)",
                username, email, PasswordUtil.encode(defaultPassword));
        log.info("Schema migrated: created configured default user {}", email);
    }

    private void migrateOwnership() {
        String[] tables = {"workflow", "llm_global_config", "mcp_tool_config", "knowledge_base", "agent_memory"};
        for (String table : tables) {
            if (!tableExists(table)) {
                continue;
            }
            addColumnIfMissing(table, "owner_id",
                    "ALTER TABLE " + table + " ADD COLUMN owner_id BIGINT NULL COMMENT '所属用户 ID' AFTER id");
        }

        Long legacyOwnerId = null;
        for (String table : tables) {
            if (!tableExists(table)) {
                continue;
            }
            Long unowned = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE owner_id IS NULL", Long.class);
            if (unowned != null && unowned > 0) {
                if (legacyOwnerId == null) {
                    legacyOwnerId = findLegacyOwnerId();
                }
                jdbcTemplate.update("UPDATE " + table + " SET owner_id = ? WHERE owner_id IS NULL", legacyOwnerId);
                log.info("Schema migrated: assigned {} legacy rows in {} to {}", unowned, table, LEGACY_OWNER_EMAIL);
            }
            makeOwnerNotNull(table);
            addIndexIfMissing(table, "idx_" + table + "_owner",
                    "CREATE INDEX idx_" + table + "_owner ON " + table + " (owner_id)");
        }

        if (tableExists("llm_global_config")) {
            dropIndexIfExists("llm_global_config", "uk_provider_config_name");
            addIndexIfMissing("llm_global_config", "uk_owner_provider_config_name",
                    "CREATE UNIQUE INDEX uk_owner_provider_config_name "
                            + "ON llm_global_config (owner_id, provider, config_name)");
        }
    }

    private Long findLegacyOwnerId() {
        var ids = jdbcTemplate.queryForList(
                "SELECT id FROM user WHERE email = ? AND deleted = 0", Long.class, LEGACY_OWNER_EMAIL);
        if (ids.size() != 1) {
            throw new IllegalStateException("历史数据归属用户不存在或不唯一: " + LEGACY_OWNER_EMAIL);
        }
        return ids.get(0);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private void makeOwnerNotNull(String tableName) {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'owner_id'
                """, String.class, tableName);
        if ("YES".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '所属用户 ID'");
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void dropIndexIfExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        if (count != null && count > 0) {
            jdbcTemplate.execute("DROP INDEX " + indexName + " ON " + tableName);
        }
    }

    private void createTableIfMissing(String tableName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Schema migrated: created {}", tableName);
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Schema migrated: added {}.{}", tableName, columnName);
    }

    private void makeColumnNullableIfNeeded(String tableName, String columnName, String ddl) {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, String.class, tableName, columnName);
        if ("YES".equalsIgnoreCase(nullable)) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Schema migrated: made {}.{} nullable", tableName, columnName);
    }
}
