-- ========================================
-- 演示数据导入脚本
-- 创建huang和wen两个演示账户及其借阅记录
-- 密码: 12345
-- ========================================

BEGIN;

-- 注意：密码哈希需要从Java程序生成
-- 运行以下命令生成密码哈希：
-- cd server
-- mvn compile exec:java -Dexec.mainClass="com.library.server.util.GenerateDemoData"

-- 临时方案：先创建用户，密码哈希稍后更新
-- 或者使用注册接口创建用户，然后手动插入借阅记录

-- 创建huang用户（如果不存在）
INSERT INTO users (username, password_hash, role, status, created_at)
SELECT 'huang', 
       '100000:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=', 
       'USER', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '6 months'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'huang');

-- 创建wen用户（如果不存在）
INSERT INTO users (username, password_hash, role, status, created_at)
SELECT 'wen', 
       '100000:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=', 
       'USER', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '5 months'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'wen');

-- 获取用户ID并插入借阅记录
DO $$
DECLARE
    v_huang_id BIGINT;
    v_wen_id BIGINT;
    v_book_count INTEGER;
BEGIN
    SELECT id INTO v_huang_id FROM users WHERE username = 'huang';
    SELECT id INTO v_wen_id FROM users WHERE username = 'wen';
    
    -- 检查是否有足够的图书
    SELECT COUNT(*) INTO v_book_count FROM books WHERE id <= 50;
    
    IF v_book_count < 10 THEN
        RAISE NOTICE '警告：图书数量不足，请先导入图书数据';
    END IF;
    
    -- 删除这两个用户的旧借阅记录（如果存在）
    DELETE FROM borrow_records WHERE user_id IN (v_huang_id, v_wen_id);
    
    -- 恢复这些图书的可用数量（如果之前被借出）
    UPDATE books SET available_count = LEAST(available_count + 1, total_count) 
    WHERE id IN (
        SELECT DISTINCT book_id FROM borrow_records 
        WHERE user_id IN (v_huang_id, v_wen_id) AND status = 'BORROWED'
    );
    
    -- huang的历史借阅记录（已归还）- 10条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES
        (v_huang_id, 1, CURRENT_TIMESTAMP - INTERVAL '90 days', CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '60 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '90 days'),
        (v_huang_id, 5, CURRENT_TIMESTAMP - INTERVAL '85 days', CURRENT_TIMESTAMP - INTERVAL '55 days', CURRENT_TIMESTAMP - INTERVAL '55 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '85 days'),
        (v_huang_id, 12, CURRENT_TIMESTAMP - INTERVAL '80 days', CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '50 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '80 days'),
        (v_huang_id, 18, CURRENT_TIMESTAMP - INTERVAL '75 days', CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '45 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '75 days'),
        (v_huang_id, 23, CURRENT_TIMESTAMP - INTERVAL '70 days', CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '40 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '70 days'),
        (v_huang_id, 28, CURRENT_TIMESTAMP - INTERVAL '65 days', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '35 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '65 days'),
        (v_huang_id, 35, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '60 days'),
        (v_huang_id, 42, CURRENT_TIMESTAMP - INTERVAL '55 days', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '55 days'),
        (v_huang_id, 8, CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '50 days'),
        (v_huang_id, 15, CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '15 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '45 days')
    ON CONFLICT DO NOTHING;
    
    -- huang的当前借阅记录（未归还）- 4条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES
        (v_huang_id, 3, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP + INTERVAL '10 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '20 days'),
        (v_huang_id, 7, CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP + INTERVAL '15 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '15 days'),
        (v_huang_id, 14, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '20 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '10 days'),
        (v_huang_id, 21, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '25 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '5 days')
    ON CONFLICT DO NOTHING;
    
    -- wen的历史借阅记录（已归还）- 10条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES
        (v_wen_id, 2, CURRENT_TIMESTAMP - INTERVAL '88 days', CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '58 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '88 days'),
        (v_wen_id, 6, CURRENT_TIMESTAMP - INTERVAL '83 days', CURRENT_TIMESTAMP - INTERVAL '53 days', CURRENT_TIMESTAMP - INTERVAL '53 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '83 days'),
        (v_wen_id, 11, CURRENT_TIMESTAMP - INTERVAL '78 days', CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '48 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '78 days'),
        (v_wen_id, 17, CURRENT_TIMESTAMP - INTERVAL '73 days', CURRENT_TIMESTAMP - INTERVAL '43 days', CURRENT_TIMESTAMP - INTERVAL '43 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '73 days'),
        (v_wen_id, 22, CURRENT_TIMESTAMP - INTERVAL '68 days', CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '38 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '68 days'),
        (v_wen_id, 27, CURRENT_TIMESTAMP - INTERVAL '63 days', CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '33 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '63 days'),
        (v_wen_id, 33, CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '58 days'),
        (v_wen_id, 40, CURRENT_TIMESTAMP - INTERVAL '53 days', CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '23 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '53 days'),
        (v_wen_id, 9, CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '48 days'),
        (v_wen_id, 16, CURRENT_TIMESTAMP - INTERVAL '43 days', CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '13 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '43 days')
    ON CONFLICT DO NOTHING;
    
    -- wen的当前借阅记录（未归还）- 4条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES
        (v_wen_id, 4, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP + INTERVAL '12 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '18 days'),
        (v_wen_id, 13, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP + INTERVAL '18 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '12 days'),
        (v_wen_id, 19, CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP + INTERVAL '22 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '8 days'),
        (v_wen_id, 25, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP + INTERVAL '27 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '3 days')
    ON CONFLICT DO NOTHING;
    
    -- 更新图书可用数量（减少已借出的数量）
    UPDATE books SET available_count = available_count - 1 
    WHERE id IN (3, 7, 14, 21, 4, 13, 19, 25) 
    AND available_count > 0;
    
    RAISE NOTICE '演示数据导入完成';
    RAISE NOTICE 'huang用户ID: %', v_huang_id;
    RAISE NOTICE 'wen用户ID: %', v_wen_id;
END $$;

COMMIT;

-- 验证数据
SELECT u.username, 
       COUNT(*) as total_records, 
       SUM(CASE WHEN br.status = 'BORROWED' THEN 1 ELSE 0 END) as current_borrowed,
       SUM(CASE WHEN br.status = 'RETURNED' THEN 1 ELSE 0 END) as returned
FROM users u
LEFT JOIN borrow_records br ON u.id = br.user_id
WHERE u.username IN ('huang', 'wen')
GROUP BY u.username;

-- 注意：密码哈希需要更新
-- 请运行以下Java程序生成正确的密码哈希，然后手动更新：
-- 或者使用客户端注册功能创建账户，然后运行此脚本插入借阅记录






