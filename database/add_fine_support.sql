-- 添加欠费支持
-- 在users表中添加fine_amount字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS fine_amount DECIMAL(10, 2) DEFAULT 0.0 CHECK (fine_amount >= 0);

-- 在borrow_records表中添加fine_amount字段
ALTER TABLE borrow_records ADD COLUMN IF NOT EXISTS fine_amount DECIMAL(10, 2) DEFAULT 0.0 CHECK (fine_amount >= 0);

-- 创建索引以便快速查询欠费用户
CREATE INDEX IF NOT EXISTS idx_users_fine_amount ON users(fine_amount) WHERE fine_amount > 0;











