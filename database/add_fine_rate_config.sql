-- ========================================
-- 罚款梯度价格配置表
-- ========================================
CREATE TABLE IF NOT EXISTS fine_rate_config (
    id BIGSERIAL PRIMARY KEY,
    day_range_start INTEGER NOT NULL,
    day_range_end INTEGER,
    rate_per_day DOUBLE PRECISION NOT NULL CHECK (rate_per_day >= 0),
    description VARCHAR(200),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(day_range_start, day_range_end)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_fine_rate_config_order ON fine_rate_config(display_order);

-- 插入默认配置
INSERT INTO fine_rate_config (day_range_start, day_range_end, rate_per_day, description, display_order) VALUES
    (1, 7, 0.1, '前7天每天0.1元', 1),
    (8, 30, 0.2, '第8-30天每天0.2元', 2),
    (31, NULL, 0.3, '31天以上每天0.3元', 3)
ON CONFLICT (day_range_start, day_range_end) DO NOTHING;

-- 更新触发器
CREATE OR REPLACE FUNCTION update_fine_rate_config_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_fine_rate_config_updated_at
    BEFORE UPDATE ON fine_rate_config
    FOR EACH ROW
    EXECUTE FUNCTION update_fine_rate_config_updated_at();










