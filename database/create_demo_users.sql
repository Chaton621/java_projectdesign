-- ========================================
-- 创建演示用户账户（huang和wen）
-- 密码: 12345
-- 包含借阅记录和欠费
-- ========================================

BEGIN;

-- 生成密码哈希（密码: 12345）
-- 使用Java程序生成：com.library.server.util.GenerateDemoData
-- 或者使用以下SQL（需要先运行Java程序获取哈希值）

-- 创建huang用户（如果不存在）
-- 密码哈希需要从Java程序生成，这里使用占位符
INSERT INTO users (username, password_hash, role, status, fine_amount, created_at)
SELECT 'huang', 
       '100000:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=', 
       'USER', 'ACTIVE', 0.0, CURRENT_TIMESTAMP - INTERVAL '6 months'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'huang')
ON CONFLICT (username) DO UPDATE SET
    role = 'USER',
    status = 'ACTIVE';

-- 创建wen用户（如果不存在）
INSERT INTO users (username, password_hash, role, status, fine_amount, created_at)
SELECT 'wen', 
       '100000:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=', 
       'USER', 'ACTIVE', 0.0, CURRENT_TIMESTAMP - INTERVAL '5 months'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'wen')
ON CONFLICT (username) DO UPDATE SET
    role = 'USER',
    status = 'ACTIVE';

-- 获取用户ID并创建借阅记录和欠费
DO $$
DECLARE
    v_huang_id BIGINT;
    v_wen_id BIGINT;
    v_book_id BIGINT;
    v_overdue_days INTEGER;
    v_fine_amount DECIMAL(10, 2);
    v_total_fine_huang DECIMAL(10, 2) := 0.0;
    v_total_fine_wen DECIMAL(10, 2) := 0.0;
BEGIN
    -- 获取用户ID
    SELECT id INTO v_huang_id FROM users WHERE username = 'huang';
    SELECT id INTO v_wen_id FROM users WHERE username = 'wen';
    
    IF v_huang_id IS NULL OR v_wen_id IS NULL THEN
        RAISE EXCEPTION '用户创建失败';
    END IF;
    
    -- 删除这两个用户的旧借阅记录（如果存在）
    DELETE FROM borrow_records WHERE user_id IN (v_huang_id, v_wen_id);
    
    -- 恢复这些图书的可用数量
    UPDATE books SET available_count = LEAST(available_count + 1, total_count) 
    WHERE id IN (
        SELECT DISTINCT book_id FROM borrow_records 
        WHERE user_id IN (v_huang_id, v_wen_id) AND status = 'BORROWED'
    );
    
    -- ========================================
    -- huang的借阅记录
    -- ========================================
    
    -- huang的历史借阅记录（已归还，无欠费）- 5条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_huang_id, 1, CURRENT_TIMESTAMP - INTERVAL '90 days', CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '60 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '90 days'),
        (v_huang_id, 5, CURRENT_TIMESTAMP - INTERVAL '85 days', CURRENT_TIMESTAMP - INTERVAL '55 days', CURRENT_TIMESTAMP - INTERVAL '55 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '85 days'),
        (v_huang_id, 12, CURRENT_TIMESTAMP - INTERVAL '80 days', CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '50 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '80 days'),
        (v_huang_id, 18, CURRENT_TIMESTAMP - INTERVAL '75 days', CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '45 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '75 days'),
        (v_huang_id, 23, CURRENT_TIMESTAMP - INTERVAL '70 days', CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '40 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '70 days');
    
    -- huang的逾期借阅记录（已归还，有欠费）- 2条
    -- 逾期5天：5 * 1.0 = 5.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_huang_id, 28, CURRENT_TIMESTAMP - INTERVAL '65 days', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '30 days', 'RETURNED', 5.0, CURRENT_TIMESTAMP - INTERVAL '65 days');
    v_total_fine_huang := v_total_fine_huang + 5.0;
    
    -- 逾期15天：7 * 1.0 + 8 * 2.0 = 7.0 + 16.0 = 23.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_huang_id, 35, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '15 days', 'RETURNED', 23.0, CURRENT_TIMESTAMP - INTERVAL '60 days');
    v_total_fine_huang := v_total_fine_huang + 23.0;
    
    -- huang的当前借阅记录（未归还，未逾期）- 2条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_huang_id, 3, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP + INTERVAL '10 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '20 days'),
        (v_huang_id, 7, CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP + INTERVAL '15 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '15 days');
    
    -- huang的当前逾期借阅记录（未归还，已逾期）- 2条
    -- 逾期8天：7 * 1.0 + 1 * 2.0 = 7.0 + 2.0 = 9.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_huang_id, 14, CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '8 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '38 days');
    -- 注意：当前逾期记录的fine_amount会在系统自动计算时更新
    
    -- 逾期35天：7 * 1.0 + 23 * 2.0 + 5 * 5.0 = 7.0 + 46.0 + 25.0 = 78.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_huang_id, 21, CURRENT_TIMESTAMP - INTERVAL '65 days', CURRENT_TIMESTAMP - INTERVAL '35 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '65 days');
    
    -- ========================================
    -- wen的借阅记录
    -- ========================================
    
    -- wen的历史借阅记录（已归还，无欠费）- 5条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_wen_id, 2, CURRENT_TIMESTAMP - INTERVAL '88 days', CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '58 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '88 days'),
        (v_wen_id, 6, CURRENT_TIMESTAMP - INTERVAL '83 days', CURRENT_TIMESTAMP - INTERVAL '53 days', CURRENT_TIMESTAMP - INTERVAL '53 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '83 days'),
        (v_wen_id, 11, CURRENT_TIMESTAMP - INTERVAL '78 days', CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '48 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '78 days'),
        (v_wen_id, 17, CURRENT_TIMESTAMP - INTERVAL '73 days', CURRENT_TIMESTAMP - INTERVAL '43 days', CURRENT_TIMESTAMP - INTERVAL '43 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '73 days'),
        (v_wen_id, 22, CURRENT_TIMESTAMP - INTERVAL '68 days', CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '38 days', 'RETURNED', 0.0, CURRENT_TIMESTAMP - INTERVAL '68 days');
    
    -- wen的逾期借阅记录（已归还，有欠费）- 2条
    -- 逾期3天：3 * 1.0 = 3.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_wen_id, 27, CURRENT_TIMESTAMP - INTERVAL '63 days', CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '30 days', 'RETURNED', 3.0, CURRENT_TIMESTAMP - INTERVAL '63 days');
    v_total_fine_wen := v_total_fine_wen + 3.0;
    
    -- 逾期25天：7 * 1.0 + 18 * 2.0 = 7.0 + 36.0 = 43.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_wen_id, 33, CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '3 days', 'RETURNED', 43.0, CURRENT_TIMESTAMP - INTERVAL '58 days');
    v_total_fine_wen := v_total_fine_wen + 43.0;
    
    -- wen的当前借阅记录（未归还，未逾期）- 2条
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_wen_id, 4, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP + INTERVAL '12 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '18 days'),
        (v_wen_id, 13, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP + INTERVAL '18 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '12 days');
    
    -- wen的当前逾期借阅记录（未归还，已逾期）- 2条
    -- 逾期12天：7 * 1.0 + 5 * 2.0 = 7.0 + 10.0 = 17.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_wen_id, 19, CURRENT_TIMESTAMP - INTERVAL '42 days', CURRENT_TIMESTAMP - INTERVAL '12 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '42 days');
    
    -- 逾期20天：7 * 1.0 + 13 * 2.0 = 7.0 + 26.0 = 33.0元
    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, fine_amount, created_at) VALUES
        (v_wen_id, 25, CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, 'BORROWED', 0.0, CURRENT_TIMESTAMP - INTERVAL '50 days');
    
    -- 更新图书可用数量（减少已借出的数量）
    UPDATE books SET available_count = GREATEST(available_count - 1, 0) 
    WHERE id IN (3, 7, 14, 21, 4, 13, 19, 25) 
    AND available_count > 0;
    
    -- 更新用户的欠费金额（已归还记录的欠费）
    UPDATE users SET fine_amount = v_total_fine_huang WHERE id = v_huang_id;
    UPDATE users SET fine_amount = v_total_fine_wen WHERE id = v_wen_id;
    
    RAISE NOTICE '演示用户创建完成';
    RAISE NOTICE 'huang用户ID: %, 欠费金额: %元', v_huang_id, v_total_fine_huang;
    RAISE NOTICE 'wen用户ID: %, 欠费金额: %元', v_wen_id, v_total_fine_wen;
    RAISE NOTICE '注意：当前逾期记录的欠费金额需要系统自动计算更新';
END $$;

COMMIT;

-- 验证数据
SELECT 
    u.username,
    u.fine_amount as user_fine,
    COUNT(*) as total_records,
    SUM(CASE WHEN br.status = 'BORROWED' THEN 1 ELSE 0 END) as current_borrowed,
    SUM(CASE WHEN br.status = 'RETURNED' THEN 1 ELSE 0 END) as returned,
    SUM(CASE WHEN br.status = 'BORROWED' AND br.due_time < CURRENT_TIMESTAMP THEN 1 ELSE 0 END) as overdue_count,
    SUM(br.fine_amount) as total_record_fine
FROM users u
LEFT JOIN borrow_records br ON u.id = br.user_id
WHERE u.username IN ('huang', 'wen')
GROUP BY u.username, u.fine_amount;

-- 显示逾期记录
SELECT 
    u.username,
    b.title as book_title,
    br.borrow_time,
    br.due_time,
    CURRENT_DATE - br.due_time::date as overdue_days,
    br.status,
    br.fine_amount
FROM borrow_records br
JOIN users u ON br.user_id = u.id
JOIN books b ON br.book_id = b.id
WHERE u.username IN ('huang', 'wen')
  AND br.status = 'BORROWED'
  AND br.due_time < CURRENT_TIMESTAMP
ORDER BY u.username, br.due_time;



