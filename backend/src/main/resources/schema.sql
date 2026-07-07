DROP TABLE IF EXISTS batch_statistic;
DROP TABLE IF EXISTS sensor_data;
DROP TABLE IF EXISTS import_task;
DROP TABLE IF EXISTS uploaded_file;
DROP TABLE IF EXISTS batch_info;
DROP TABLE IF EXISTS sensor_variable;
DROP TABLE IF EXISTS process_info;

CREATE TABLE process_info (
    id BIGINT PRIMARY KEY,
    process_code VARCHAR(64) NOT NULL,
    process_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sensor_variable (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    variable_code VARCHAR(64) NOT NULL,
    variable_name VARCHAR(128) NOT NULL,
    unit VARCHAR(32),
    variable_type VARCHAR(64),
    physical_min DECIMAL(12, 4),
    physical_max DECIMAL(12, 4),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE batch_info (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    batch_code VARCHAR(64) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    data_point_count INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    quality_level VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE uploaded_file (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL
);

CREATE TABLE import_task (
    id BIGINT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    process_id BIGINT NOT NULL,
    generated_batch_id BIGINT NOT NULL,
    generated_batch_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL,
    success_rows INT NOT NULL,
    failed_rows INT NOT NULL,
    message VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL
);

CREATE TABLE sensor_data (
    id BIGINT PRIMARY KEY,
    collect_time TIMESTAMP NOT NULL,
    process_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    variable_id BIGINT NOT NULL,
    raw_value DECIMAL(12, 4),
    clean_value DECIMAL(12, 4),
    standard_value DECIMAL(12, 4),
    is_missing BOOLEAN NOT NULL,
    is_stat_outlier BOOLEAN NOT NULL,
    is_physical_outlier BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE batch_statistic (
    id BIGINT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    variable_id BIGINT NOT NULL,
    mean_value DECIMAL(12, 4) NOT NULL,
    std_value DECIMAL(12, 4) NOT NULL,
    min_value DECIMAL(12, 4) NOT NULL,
    max_value DECIMAL(12, 4) NOT NULL,
    missing_rate DECIMAL(8, 4) NOT NULL,
    stat_outlier_rate DECIMAL(8, 4) NOT NULL,
    physical_outlier_rate DECIMAL(8, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sensor_data_batch_var_time ON sensor_data (batch_id, variable_id, collect_time DESC);
CREATE INDEX idx_sensor_data_process_time ON sensor_data (process_id, collect_time DESC);
CREATE INDEX idx_batch_statistic_batch_var ON batch_statistic (batch_id, variable_id);
CREATE INDEX idx_import_task_process_created ON import_task (process_id, created_at DESC);
