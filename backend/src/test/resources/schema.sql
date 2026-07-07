DROP TABLE IF EXISTS ads_batch_metric_stat;
DROP TABLE IF EXISTS dim_process_metric;
DROP TABLE IF EXISTS fact_process_metric_value;

CREATE TABLE dim_process_metric (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    metric_code VARCHAR(32) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    source_column_name VARCHAR(255),
    metric_order INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

CREATE INDEX idx_fact_batch_time ON fact_process_metric_value (batch_no, process_time);
CREATE INDEX idx_fact_batch_metric ON fact_process_metric_value (batch_no, metric_code);
CREATE INDEX idx_ads_batch_metric ON ads_batch_metric_stat (batch_no, metric_code);
