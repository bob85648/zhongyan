/*
 * 文件名称：hbzy_schema.sql
 * 文件说明：hbzy 正式 PostgreSQL 数据结构脚本，整理自项目正式库核心业务表定义，供环境初始化与结构校验使用。
 * 主要职责：
 * 1. 创建历史分析核心业务表。
 * 2. 创建正式查询依赖的核心索引。
 * 开发者：czd
 */

DROP TABLE IF EXISTS ads_batch_metric_stat;
DROP TABLE IF EXISTS dim_process_metric;
DROP TABLE IF EXISTS fact_process_metric_value;
DROP TABLE IF EXISTS stg_import_sensor_record;
DROP TABLE IF EXISTS import_task;
DROP TABLE IF EXISTS uploaded_file;
DROP TABLE IF EXISTS process_info;

CREATE TABLE process_info (
    id BIGINT PRIMARY KEY,
    process_code VARCHAR(64) NOT NULL UNIQUE,
    process_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dim_process_metric (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    metric_code VARCHAR(32) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    source_column_name VARCHAR(255),
    metric_order INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (process_id, metric_code)
);

CREATE TABLE ads_batch_metric_stat (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    import_run_id VARCHAR(64) NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    batch_no VARCHAR(128),
    metric_code VARCHAR(32) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    sample_count INT NOT NULL,
    avg_value DECIMAL(18, 6),
    variance_value DECIMAL(18, 6),
    stddev_value DECIMAL(18, 6),
    min_value DECIMAL(18, 6),
    max_value DECIMAL(18, 6),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fact_process_metric_value (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    import_run_id VARCHAR(64) NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    source_sheet VARCHAR(128),
    process_time TIMESTAMP NOT NULL,
    batch_no VARCHAR(128),
    metric_code VARCHAR(32) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(18, 6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE uploaded_file (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE import_task (
    id BIGINT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    process_id BIGINT NOT NULL,
    process_name VARCHAR(128) NOT NULL,
    generated_batch_code VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL,
    success_rows INT NOT NULL,
    failed_rows INT NOT NULL,
    message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE stg_import_sensor_record (
    id BIGINT PRIMARY KEY,
    import_task_id BIGINT NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_sheet VARCHAR(128),
    source_row_no INT NOT NULL,
    process_time TIMESTAMP,
    batch_no VARCHAR(128),
    metric_code VARCHAR(32) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(18, 6),
    validity_label VARCHAR(64),
    raw_text_value VARCHAR(255),
    import_status VARCHAR(32) NOT NULL,
    error_message VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ads_batch_metric_stat_batch_no ON ads_batch_metric_stat (batch_no);
CREATE INDEX idx_ads_batch_metric_stat_process_id ON ads_batch_metric_stat (process_id);
CREATE INDEX idx_ads_batch_metric_stat_metric_code ON ads_batch_metric_stat (metric_code);
CREATE INDEX idx_ads_batch_metric_stat_batch_metric ON ads_batch_metric_stat (process_id, batch_no, metric_code);
CREATE INDEX idx_fact_process_metric_value_process_id ON fact_process_metric_value (process_id);
CREATE INDEX idx_fact_process_metric_value_batch_time ON fact_process_metric_value (process_id, batch_no, process_time);
CREATE INDEX idx_fact_process_metric_value_metric_time ON fact_process_metric_value (process_id, metric_code, process_time);
CREATE INDEX idx_fact_process_metric_value_batch_metric ON fact_process_metric_value (process_id, batch_no, metric_code);
CREATE INDEX idx_import_task_process_created ON import_task (process_id, created_at DESC);
CREATE INDEX idx_stg_import_sensor_record_task_row ON stg_import_sensor_record (import_task_id, source_row_no);
