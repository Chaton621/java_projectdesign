-- ============================================
-- 图书馆管理系统数据库设计
-- PostgreSQL Schema
-- ============================================

-- 启用 pgvector 扩展（如果已安装）
-- 如果未安装，将自动使用 fallback 方案（float8[]数组）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        RAISE NOTICE 'pgvector extension enabled';
    ELSE
        RAISE NOTICE 'pgvector extension not available, will use float8[] fallback';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector extension not available, will use float8[] fallback';
END $$;

-- ============================================
-- 1. 用户表 (users)
-- ============================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户表索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);

-- ============================================
-- 2. 图书表 (books)
-- ============================================
CREATE TABLE books (
    id BIGSERIAL PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    publisher VARCHAR(100),
    description TEXT,
    cover_image_path VARCHAR(500),
    total_count INTEGER NOT NULL DEFAULT 0 CHECK (total_count >= 0),
    available_count INTEGER NOT NULL DEFAULT 0 CHECK (available_count >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束：可用数量不能超过总数量
    CONSTRAINT chk_available_le_total CHECK (available_count <= total_count)
);

-- 图书表索引
CREATE INDEX idx_books_isbn ON books(isbn);
CREATE INDEX idx_books_category ON books(category);
CREATE INDEX idx_books_author ON books(author);
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_available_count ON books(available_count);

-- ============================================
-- 3. 借阅记录表 (borrow_records)
-- ============================================
CREATE TABLE borrow_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    borrow_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_time TIMESTAMP NOT NULL,
    return_time TIMESTAMP,
    status VARCHAR(10) NOT NULL DEFAULT 'BORROWED' CHECK (status IN ('BORROWED', 'RETURNED', 'OVERDUE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 借阅记录表索引
CREATE INDEX idx_borrow_records_user_id ON borrow_records(user_id);
CREATE INDEX idx_borrow_records_book_id ON borrow_records(book_id);
CREATE INDEX idx_borrow_records_status ON borrow_records(status);
CREATE INDEX idx_borrow_records_user_status ON borrow_records(user_id, status);
CREATE INDEX idx_borrow_records_due_time ON borrow_records(due_time);
CREATE INDEX idx_borrow_records_borrow_time ON borrow_records(borrow_time);

-- ============================================
-- 4. 图书向量嵌入表 (book_embeddings)
-- ============================================
-- 自动检测pgvector，如果不存在则使用float8[]数组
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        -- 使用 pgvector 扩展
        EXECUTE 'CREATE TABLE IF NOT EXISTS book_embeddings (
            book_id BIGINT NOT NULL PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
            embedding vector(384),
            model_name VARCHAR(50) NOT NULL,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )';
    ELSE
        -- 使用 float8[] 数组（fallback方案）
        CREATE TABLE IF NOT EXISTS book_embeddings (
            book_id BIGINT NOT NULL PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
            embedding float8[],
            model_name VARCHAR(50) NOT NULL,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- 如果vector类型创建失败，使用fallback
        CREATE TABLE IF NOT EXISTS book_embeddings (
            book_id BIGINT NOT NULL PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
            embedding float8[],
            model_name VARCHAR(50) NOT NULL,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
END $$;

-- 向量表索引（pgvector 支持）
-- 如果使用 pgvector，可以创建向量相似度搜索索引
-- CREATE INDEX idx_book_embeddings_vector ON book_embeddings USING ivfflat (embedding vector_cosine_ops);

-- 如果使用 fallback 方案，可以创建 GIN 索引用于数组查询
-- CREATE INDEX idx_book_embeddings_embedding ON book_embeddings USING GIN (embedding);

-- ============================================
-- 5. 图书每日热门度统计表 (book_popularity_daily)
-- ============================================
CREATE TABLE book_popularity_daily (
    day DATE NOT NULL,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    borrow_count INTEGER NOT NULL DEFAULT 0 CHECK (borrow_count >= 0),
    PRIMARY KEY (day, book_id)
);

-- 热门度统计表索引
CREATE INDEX idx_book_popularity_daily_day ON book_popularity_daily(day);
CREATE INDEX idx_book_popularity_daily_book_id ON book_popularity_daily(book_id);
CREATE INDEX idx_book_popularity_daily_borrow_count ON book_popularity_daily(borrow_count DESC);

-- ============================================
-- 6. 触发器：自动更新逾期状态
-- ============================================
CREATE OR REPLACE FUNCTION update_overdue_status()
RETURNS TRIGGER AS $$
BEGIN
    -- 每天检查并更新逾期状态
    UPDATE borrow_records
    SET status = 'OVERDUE'
    WHERE status = 'BORROWED'
      AND due_time < CURRENT_TIMESTAMP
      AND return_time IS NULL;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 创建定时触发器（需要 pg_cron 扩展，或通过应用层定时任务实现）
-- 这里提供一个手动调用的函数，应用层可以定时调用
-- CREATE TRIGGER trigger_update_overdue
-- AFTER INSERT OR UPDATE ON borrow_records
-- FOR EACH ROW
-- EXECUTE FUNCTION update_overdue_status();

-- ============================================
-- 7. 借书事务示例函数（带库存锁定）
-- ============================================
CREATE OR REPLACE FUNCTION borrow_book(
    p_user_id BIGINT,
    p_book_id BIGINT,
    p_due_days INTEGER DEFAULT 30
)
RETURNS BIGINT AS $$
DECLARE
    v_record_id BIGINT;
    v_available_count INTEGER;
BEGIN
    -- 开始事务（函数内部自动事务）
    
    -- 1. 锁定图书行，检查可用数量
    SELECT available_count INTO v_available_count
    FROM books
    WHERE id = p_book_id
    FOR UPDATE;  -- 行级锁，防止并发
    
    -- 2. 检查库存
    IF v_available_count IS NULL THEN
        RAISE EXCEPTION '图书不存在: %', p_book_id;
    END IF;
    
    IF v_available_count <= 0 THEN
        RAISE EXCEPTION '图书库存不足，当前可用: %', v_available_count;
    END IF;
    
    -- 3. 减少可用数量
    UPDATE books
    SET available_count = available_count - 1
    WHERE id = p_book_id
      AND available_count > 0;  -- 双重检查，防止并发问题
    
    -- 4. 检查更新是否成功
    IF NOT FOUND THEN
        RAISE EXCEPTION '借书失败：库存已被其他事务占用';
    END IF;
    
    -- 5. 创建借阅记录
    INSERT INTO borrow_records (user_id, book_id, due_time, status)
    VALUES (p_user_id, p_book_id, CURRENT_TIMESTAMP + (p_due_days || ' days')::INTERVAL, 'BORROWED')
    RETURNING id INTO v_record_id;
    
    RETURN v_record_id;
    
EXCEPTION
    WHEN OTHERS THEN
        -- 发生异常时自动回滚
        RAISE;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 8. 还书事务示例函数
-- ============================================
CREATE OR REPLACE FUNCTION return_book(
    p_record_id BIGINT
)
RETURNS BOOLEAN AS $$
DECLARE
    v_book_id BIGINT;
    v_status VARCHAR(10);
BEGIN
    -- 1. 锁定借阅记录
    SELECT book_id, status INTO v_book_id, v_status
    FROM borrow_records
    WHERE id = p_record_id
    FOR UPDATE;
    
    -- 2. 检查记录是否存在
    IF v_book_id IS NULL THEN
        RAISE EXCEPTION '借阅记录不存在: %', p_record_id;
    END IF;
    
    -- 3. 检查是否已归还
    IF v_status = 'RETURNED' THEN
        RAISE EXCEPTION '该图书已经归还';
    END IF;
    
    -- 4. 更新借阅记录
    UPDATE borrow_records
    SET return_time = CURRENT_TIMESTAMP,
        status = 'RETURNED'
    WHERE id = p_record_id;
    
    -- 5. 恢复图书库存
    UPDATE books
    SET available_count = available_count + 1
    WHERE id = v_book_id;
    
    -- 6. 检查库存约束（可用数量不能超过总数量）
    IF (SELECT available_count FROM books WHERE id = v_book_id) > 
       (SELECT total_count FROM books WHERE id = v_book_id) THEN
        RAISE EXCEPTION '库存数据异常：可用数量超过总数量';
    END IF;
    
    RETURN TRUE;
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 9. 初始化数据（可选）
-- ============================================
-- 创建默认管理员账户（密码：admin123，实际使用时需要哈希）
-- INSERT INTO users (username, password_hash, role, status)
-- VALUES ('admin', '$2a$10$...', 'ADMIN', 'ACTIVE');

-- ============================================
-- 10. 查询示例
-- ============================================
-- 查询用户借阅记录（带图书信息）
-- SELECT br.*, b.title, b.author, u.username
-- FROM borrow_records br
-- JOIN books b ON br.book_id = b.id
-- JOIN users u ON br.user_id = u.id
-- WHERE br.user_id = ? AND br.status = 'BORROWED';

-- 查询逾期记录
-- SELECT br.*, b.title, u.username
-- FROM borrow_records br
-- JOIN books b ON br.book_id = b.id
-- JOIN users u ON br.user_id = u.id
-- WHERE br.status = 'OVERDUE';

-- 查询热门图书（最近30天）
-- SELECT b.*, SUM(bpd.borrow_count) as total_borrows
-- FROM books b
-- JOIN book_popularity_daily bpd ON b.id = bpd.book_id
-- WHERE bpd.day >= CURRENT_DATE - INTERVAL '30 days'
-- GROUP BY b.id
-- ORDER BY total_borrows DESC
-- LIMIT 10;




