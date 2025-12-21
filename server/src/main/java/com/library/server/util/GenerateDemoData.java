package com.library.server.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 生成演示数据的SQL脚本
 * 用于创建huang和wen两个演示账户及其借阅记录
 */
public class GenerateDemoData {
    public static void main(String[] args) {
        String password = "12345";
        String passwordHash = PasswordUtil.hashPassword(password);
        
        System.out.println("-- ========================================");
        System.out.println("-- 演示数据导入脚本");
        System.out.println("-- 创建huang和wen两个演示账户");
        System.out.println("-- 密码: 12345");
        System.out.println("-- ========================================");
        System.out.println();
        
        System.out.println("BEGIN;");
        System.out.println();
        
        System.out.println("-- 创建huang用户");
        System.out.println("INSERT INTO users (username, password_hash, role, status, created_at)");
        System.out.printf("VALUES ('huang', '%s', 'USER', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '6 months')\n", passwordHash);
        System.out.println("ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, role = EXCLUDED.role, status = EXCLUDED.status;");
        System.out.println();
        
        System.out.println("-- 创建wen用户");
        System.out.println("INSERT INTO users (username, password_hash, role, status, created_at)");
        System.out.printf("VALUES ('wen', '%s', 'USER', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '5 months')\n", passwordHash);
        System.out.println("ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, role = EXCLUDED.role, status = EXCLUDED.status;");
        System.out.println();
        
        System.out.println("-- 获取用户ID（用于后续插入借阅记录）");
        System.out.println("DO $$");
        System.out.println("DECLARE");
        System.out.println("    v_huang_id BIGINT;");
        System.out.println("    v_wen_id BIGINT;");
        System.out.println("BEGIN");
        System.out.println("    SELECT id INTO v_huang_id FROM users WHERE username = 'huang';");
        System.out.println("    SELECT id INTO v_wen_id FROM users WHERE username = 'wen';");
        System.out.println();
        
        System.out.println("    -- 删除这两个用户的旧借阅记录（如果存在）");
        System.out.println("    DELETE FROM borrow_records WHERE user_id IN (v_huang_id, v_wen_id);");
        System.out.println();
        
        System.out.println("    -- huang的历史借阅记录（已归还）");
        System.out.println("    -- 假设有图书ID 1-50存在");
        System.out.println("    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES");
        System.out.println("        (v_huang_id, 1, CURRENT_TIMESTAMP - INTERVAL '90 days', CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '60 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '90 days'),");
        System.out.println("        (v_huang_id, 5, CURRENT_TIMESTAMP - INTERVAL '85 days', CURRENT_TIMESTAMP - INTERVAL '55 days', CURRENT_TIMESTAMP - INTERVAL '55 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '85 days'),");
        System.out.println("        (v_huang_id, 12, CURRENT_TIMESTAMP - INTERVAL '80 days', CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '50 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '80 days'),");
        System.out.println("        (v_huang_id, 18, CURRENT_TIMESTAMP - INTERVAL '75 days', CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '45 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '75 days'),");
        System.out.println("        (v_huang_id, 23, CURRENT_TIMESTAMP - INTERVAL '70 days', CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '40 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '70 days'),");
        System.out.println("        (v_huang_id, 28, CURRENT_TIMESTAMP - INTERVAL '65 days', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '35 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '65 days'),");
        System.out.println("        (v_huang_id, 35, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '60 days'),");
        System.out.println("        (v_huang_id, 42, CURRENT_TIMESTAMP - INTERVAL '55 days', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '55 days'),");
        System.out.println("        (v_huang_id, 8, CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '50 days'),");
        System.out.println("        (v_huang_id, 15, CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '15 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '45 days');");
        System.out.println();
        
        System.out.println("    -- huang的当前借阅记录（未归还）");
        System.out.println("    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES");
        System.out.println("        (v_huang_id, 3, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP + INTERVAL '10 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '20 days'),");
        System.out.println("        (v_huang_id, 7, CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP + INTERVAL '15 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '15 days'),");
        System.out.println("        (v_huang_id, 14, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '20 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '10 days'),");
        System.out.println("        (v_huang_id, 21, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '25 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '5 days');");
        System.out.println();
        
        System.out.println("    -- wen的历史借阅记录（已归还）");
        System.out.println("    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES");
        System.out.println("        (v_wen_id, 2, CURRENT_TIMESTAMP - INTERVAL '88 days', CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '58 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '88 days'),");
        System.out.println("        (v_wen_id, 6, CURRENT_TIMESTAMP - INTERVAL '83 days', CURRENT_TIMESTAMP - INTERVAL '53 days', CURRENT_TIMESTAMP - INTERVAL '53 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '83 days'),");
        System.out.println("        (v_wen_id, 11, CURRENT_TIMESTAMP - INTERVAL '78 days', CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '48 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '78 days'),");
        System.out.println("        (v_wen_id, 17, CURRENT_TIMESTAMP - INTERVAL '73 days', CURRENT_TIMESTAMP - INTERVAL '43 days', CURRENT_TIMESTAMP - INTERVAL '43 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '73 days'),");
        System.out.println("        (v_wen_id, 22, CURRENT_TIMESTAMP - INTERVAL '68 days', CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '38 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '68 days'),");
        System.out.println("        (v_wen_id, 27, CURRENT_TIMESTAMP - INTERVAL '63 days', CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '33 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '63 days'),");
        System.out.println("        (v_wen_id, 33, CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '58 days'),");
        System.out.println("        (v_wen_id, 40, CURRENT_TIMESTAMP - INTERVAL '53 days', CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '23 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '53 days'),");
        System.out.println("        (v_wen_id, 9, CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '48 days'),");
        System.out.println("        (v_wen_id, 16, CURRENT_TIMESTAMP - INTERVAL '43 days', CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '13 days', 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '43 days');");
        System.out.println();
        
        System.out.println("    -- wen的当前借阅记录（未归还）");
        System.out.println("    INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, return_time, status, created_at) VALUES");
        System.out.println("        (v_wen_id, 4, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP + INTERVAL '12 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '18 days'),");
        System.out.println("        (v_wen_id, 13, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP + INTERVAL '18 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '12 days'),");
        System.out.println("        (v_wen_id, 19, CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP + INTERVAL '22 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '8 days'),");
        System.out.println("        (v_wen_id, 25, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP + INTERVAL '27 days', NULL, 'BORROWED', CURRENT_TIMESTAMP - INTERVAL '3 days');");
        System.out.println();
        
        System.out.println("    -- 更新图书可用数量（减少已借出的数量）");
        System.out.println("    UPDATE books SET available_count = available_count - 1 WHERE id IN (3, 7, 14, 21, 4, 13, 19, 25);");
        System.out.println();
        
        System.out.println("END $$;");
        System.out.println();
        
        System.out.println("COMMIT;");
        System.out.println();
        System.out.println("-- 验证数据");
        System.out.println("SELECT u.username, COUNT(*) as total_records, ");
        System.out.println("       SUM(CASE WHEN br.status = 'BORROWED' THEN 1 ELSE 0 END) as current_borrowed,");
        System.out.println("       SUM(CASE WHEN br.status = 'RETURNED' THEN 1 ELSE 0 END) as returned");
        System.out.println("FROM users u");
        System.out.println("LEFT JOIN borrow_records br ON u.id = br.user_id");
        System.out.println("WHERE u.username IN ('huang', 'wen')");
        System.out.println("GROUP BY u.username;");
    }
}






