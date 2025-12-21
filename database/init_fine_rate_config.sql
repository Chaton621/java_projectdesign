-- ========================================
-- 初始化欠费价格梯度配置
-- ========================================
-- 此脚本用于设置或重置欠费价格梯度的初始配置
-- 如果配置已存在，将先删除再重新插入

-- 删除现有配置（可选，如果需要重置）
-- DELETE FROM fine_rate_config;

-- 确保表存在
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

-- 插入初始配置
-- 如果配置已存在（基于 day_range_start 和 day_range_end），则更新；否则插入
INSERT INTO fine_rate_config (day_range_start, day_range_end, rate_per_day, description, display_order) VALUES
    (1, 7, 0.1, '前7天每天0.1元', 1),
    (8, 30, 0.2, '第8-30天每天0.2元', 2),
    (31, NULL, 0.3, '31天以上每天0.3元', 3)
ON CONFLICT (day_range_start, day_range_end) 
DO UPDATE SET 
    rate_per_day = EXCLUDED.rate_per_day,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    updated_at = CURRENT_TIMESTAMP;

-- 显示插入的配置
SELECT 
    id,
    day_range_start,
    COALESCE(day_range_end::text, '无上限') as day_range_end,
    rate_per_day,
    description,
    display_order
FROM fine_rate_config
ORDER BY display_order ASC, day_range_start ASC;





