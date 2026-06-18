-- Saga定义表
CREATE TABLE IF NOT EXISTS saga_definition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    definition JSONB NOT NULL,
    global_timeout_seconds INTEGER NOT NULL DEFAULT 300,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, version)
);

ALTER TABLE saga_definition ADD COLUMN IF NOT EXISTS global_timeout_seconds INTEGER NOT NULL DEFAULT 300;

CREATE INDEX IF NOT EXISTS idx_saga_definition_name ON saga_definition(name);

-- Saga实例表
CREATE TABLE IF NOT EXISTS saga_instance (
    id BIGSERIAL PRIMARY KEY,
    saga_definition_id BIGINT NOT NULL,
    saga_definition_name VARCHAR(255) NOT NULL,
    saga_definition_version INTEGER NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saga_instance_correlation_id ON saga_instance(correlation_id);
CREATE INDEX IF NOT EXISTS idx_saga_instance_status ON saga_instance(status);
CREATE INDEX IF NOT EXISTS idx_saga_instance_definition_id ON saga_instance(saga_definition_id);

-- 步骤执行记录表
CREATE TABLE IF NOT EXISTS step_execution (
    id BIGSERIAL PRIMARY KEY,
    saga_instance_id BIGINT NOT NULL,
    step_id VARCHAR(100) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    execution_order INTEGER NOT NULL,
    request_url VARCHAR(1024),
    request_method VARCHAR(20),
    request_body TEXT,
    response_body TEXT,
    response_status INTEGER,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_step_execution_instance_id ON step_execution(saga_instance_id);
CREATE INDEX IF NOT EXISTS idx_step_execution_status ON step_execution(status);

-- 事件日志表
CREATE TABLE IF NOT EXISTS event_log (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    saga_definition_id BIGINT,
    saga_instance_id BIGINT,
    step_id VARCHAR(100),
    payload JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_event_log_event_type ON event_log(event_type);
CREATE INDEX IF NOT EXISTS idx_event_log_instance_id ON event_log(saga_instance_id);
CREATE INDEX IF NOT EXISTS idx_event_log_occurred_at ON event_log(occurred_at);

-- 死信队列表
CREATE TABLE IF NOT EXISTS dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    saga_instance_id BIGINT NOT NULL,
    step_id VARCHAR(100) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    failure_type VARCHAR(50) NOT NULL,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    handled_by VARCHAR(100),
    handled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_status ON dead_letter_queue(status);
CREATE INDEX IF NOT EXISTS idx_dead_letter_instance_id ON dead_letter_queue(saga_instance_id);

-- 用户表
CREATE TABLE IF NOT EXISTS saga_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认用户 (密码使用BCrypt加密, 强度10)
-- admin/admin123
INSERT INTO saga_user (username, password, role, email)
SELECT 'admin', '$2a$10$M1cYnuvppOrkoExvlAv53uajtegpCbY3y9gtBPc8HRwknQ6ijqFVO', 'ADMIN', 'admin@saga.com'
WHERE NOT EXISTS (SELECT 1 FROM saga_user WHERE username = 'admin');

-- operator/operator123
INSERT INTO saga_user (username, password, role, email)
SELECT 'operator', '$2a$10$UTd3que5gNrC/p0ui0MZFeHab4gYtgZHlcy1K.HXwA3999IiQE2XS', 'OPERATOR', 'operator@saga.com'
WHERE NOT EXISTS (SELECT 1 FROM saga_user WHERE username = 'operator');

-- Saga模板主表
CREATE TABLE IF NOT EXISTS saga_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_tags JSONB,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    publisher VARCHAR(100) NOT NULL,
    reviewer VARCHAR(100),
    step_definition JSONB NOT NULL,
    download_count INTEGER NOT NULL DEFAULT 0,
    scene_description TEXT,
    dependencies JSONB,
    review_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    UNIQUE(name, version)
);

CREATE INDEX IF NOT EXISTS idx_saga_template_name ON saga_template(name);
CREATE INDEX IF NOT EXISTS idx_saga_template_status ON saga_template(status);
CREATE INDEX IF NOT EXISTS idx_saga_template_category ON saga_template USING gin(category_tags);

-- 模板收藏表
CREATE TABLE IF NOT EXISTS template_favorite (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(template_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_template_favorite_template_id ON template_favorite(template_id);
CREATE INDEX IF NOT EXISTS idx_template_favorite_user_id ON template_favorite(user_id);

-- 模板评分表
CREATE TABLE IF NOT EXISTS template_rating (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INTEGER NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(template_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_template_rating_template_id ON template_rating(template_id);
