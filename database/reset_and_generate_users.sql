-- ========================================
-- Reset User Data and Generate New Users
-- Preserve admin, huang, wen users
-- Generate large amount of new users and borrow records for recommendation system
-- ========================================

-- ========================================
-- WARNING: This script will delete all user data except admin, huang, wen!
-- Please backup database before execution
-- ========================================

BEGIN;

-- ========================================
-- Step 1: Delete users except admin, huang, wen and their borrow records
-- ========================================

-- Delete borrow records first (CASCADE will auto-delete, but we handle it manually to restore book inventory)
DO $$
DECLARE
    v_book_ids BIGINT[];
    v_users_to_delete INTEGER;
    v_records_to_delete INTEGER;
    v_deleted_records INTEGER;
    v_deleted_users INTEGER;
BEGIN
    -- Count users and records to delete
    SELECT COUNT(*) INTO v_users_to_delete
    FROM users 
    WHERE username NOT IN ('admin', 'huang', 'wen');
    
    SELECT COUNT(*) INTO v_records_to_delete
    FROM borrow_records
    WHERE user_id IN (
        SELECT id FROM users 
        WHERE username NOT IN ('admin', 'huang', 'wen')
    );
    
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Preparing to delete % users and % borrow records', v_users_to_delete, v_records_to_delete;
    RAISE NOTICE '========================================';
    
    -- Collect book IDs that need inventory restoration (for users to be deleted)
    SELECT ARRAY_AGG(DISTINCT book_id) INTO v_book_ids
    FROM borrow_records
    WHERE user_id IN (
        SELECT id FROM users 
        WHERE username NOT IN ('admin', 'huang', 'wen')
    )
    AND status = 'BORROWED';
    
    -- Restore available count for these books
    IF v_book_ids IS NOT NULL AND array_length(v_book_ids, 1) > 0 THEN
        UPDATE books 
        SET available_count = LEAST(available_count + (
            SELECT COUNT(*) 
            FROM borrow_records br 
            WHERE br.book_id = books.id 
            AND br.user_id IN (
                SELECT id FROM users 
                WHERE username NOT IN ('admin', 'huang', 'wen')
            )
            AND br.status = 'BORROWED'
        ), total_count)
        WHERE id = ANY(v_book_ids);
        RAISE NOTICE 'Restored inventory for % books', array_length(v_book_ids, 1);
    END IF;
    
    -- Delete borrow records for these users
    DELETE FROM borrow_records
    WHERE user_id IN (
        SELECT id FROM users 
        WHERE username NOT IN ('admin', 'huang', 'wen')
    );
    
    GET DIAGNOSTICS v_deleted_records = ROW_COUNT;
    RAISE NOTICE 'Deleted % borrow records', v_deleted_records;
    
    -- Delete these users
    DELETE FROM users
    WHERE username NOT IN ('admin', 'huang', 'wen');
    
    GET DIAGNOSTICS v_deleted_users = ROW_COUNT;
    RAISE NOTICE 'Deleted % users', v_deleted_users;
    RAISE NOTICE 'Delete operation completed!';
    RAISE NOTICE '========================================';
END $$;

-- ========================================
-- Step 2: Generate new users
-- Note: Password hash needs to be generated from Java program
-- Run: cd server && mvn compile exec:java -Dexec.mainClass="com.library.server.util.GenerateUserPasswords"
-- ========================================

-- Generate 150 new users
-- Password: 12345 (need to run Java program to generate hash)
-- Placeholder used here, replace with real hash before execution

-- Get password hash (need to run Java program first)
-- Assume password hash is generated and stored in variable, placeholder used here
DO $$
DECLARE
    v_password_hash VARCHAR(255) := '100000:a77QyD4158oGv3XdJZEFmA==:BnwtS0YjT4XvWmdsNJ7jPPVelMBChv8CvCbDrTse8oo=';
    -- Password hash generated (password: 12345)
    v_user_id BIGINT;
    v_book_count INTEGER;
    v_book_id BIGINT;
    v_username VARCHAR(50);
    v_created_months_ago INTEGER;
    v_borrow_count INTEGER;
    v_borrow_days_ago INTEGER;
    v_due_days INTEGER;
    v_return_days_ago INTEGER;
    v_overdue_days INTEGER;
    v_fine_amount DECIMAL(10, 2);
    v_total_fine DECIMAL(10, 2);
    v_record_status VARCHAR(10);
    v_borrow_time TIMESTAMP;
    v_due_time TIMESTAMP;
    v_return_time TIMESTAMP;
    v_i INTEGER;
    v_j INTEGER;
BEGIN
    -- Get total book count
    SELECT COUNT(*) INTO v_book_count FROM books;
    
    IF v_book_count = 0 THEN
        RAISE EXCEPTION 'No book data found, please import books first';
    END IF;
    
    -- Generate 150 users (provide more data for recommendation system)
    FOR v_i IN 1..150 LOOP
        v_username := 'user' || LPAD(v_i::TEXT, 3, '0');
        v_created_months_ago := (v_i % 12) + 1; -- Registered 1-12 months ago
        
        -- Create user
        INSERT INTO users (username, password_hash, role, status, fine_amount, created_at)
        VALUES (
            v_username,
            v_password_hash, -- Need to replace with real hash
            'USER',
            'ACTIVE',
            0.0,
            CURRENT_TIMESTAMP - (v_created_months_ago || ' months')::INTERVAL
        )
        ON CONFLICT (username) DO UPDATE SET
            role = 'USER',
            status = 'ACTIVE',
            fine_amount = 0.0;
        
        SELECT id INTO v_user_id FROM users WHERE username = v_username;
        
        -- Generate borrow records for each user
        -- Each user borrows 20-50 books (provide more borrow data for recommendation system)
        v_borrow_count := 20 + (v_i % 31); -- 20-50 books
        
        v_total_fine := 0.0;
        
        FOR v_j IN 1..v_borrow_count LOOP
            -- Randomly select a book
            SELECT id INTO v_book_id 
            FROM books 
            ORDER BY RANDOM() 
            LIMIT 1;
            
            -- Borrow time distribution: 30% in last 30 days, 40% in 30-90 days, 30% in 90-365 days
            -- This ensures enough data in recent days
            IF (v_j % 10) <= 3 THEN
                -- 30%: Last 30 days
                v_borrow_days_ago := 1 + (v_j * 3 + v_i) % 30;
            ELSIF (v_j % 10) <= 7 THEN
                -- 40%: 30-90 days ago
                v_borrow_days_ago := 30 + (v_j * 2 + v_i * 3) % 60;
            ELSE
                -- 30%: 90-365 days ago
                v_borrow_days_ago := 90 + (v_j * 5 + v_i * 7) % 275;
            END IF;
            v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
            
            -- Determine record status: 75% returned (provide more historical data for recommendation), 15% current borrow, 10% overdue
            IF v_j <= (v_borrow_count * 0.75) THEN
                -- Returned
                v_record_status := 'RETURNED';
                v_due_days := 30; -- Borrow for 30 days
                v_due_time := v_borrow_time + (v_due_days || ' days')::INTERVAL;
                
                -- 25% of returned records have slight overdue (1-10 days), generating small fine
                IF (v_j % 4) = 0 THEN
                    v_overdue_days := 1 + (v_j % 10);
                    v_return_time := v_due_time + (v_overdue_days || ' days')::INTERVAL;
                    -- Calculate fine: first 7 days 1 yuan/day, then 2 yuan/day
                    IF v_overdue_days <= 7 THEN
                        v_fine_amount := v_overdue_days * 1.0;
                    ELSE
                        v_fine_amount := 7.0 + (v_overdue_days - 7) * 2.0;
                    END IF;
                    v_total_fine := v_total_fine + v_fine_amount;
                ELSE
                    -- Return early or on time
                    -- Return 0-6 days early
                    v_return_time := v_due_time - ((v_j % 7) || ' days')::INTERVAL;
                    -- Ensure return time is not earlier than borrow time
                    IF v_return_time < v_borrow_time THEN
                        v_return_time := v_borrow_time + (v_due_days || ' days')::INTERVAL; -- Return on time
                    END IF;
                    v_fine_amount := 0.0;
                END IF;
            ELSIF v_j <= (v_borrow_count * 0.9) THEN
                -- Current borrow (not overdue)
                -- Make more current borrow records in recent days
                v_record_status := 'BORROWED';
                v_due_days := 30;
                -- Current borrow time more concentrated in last 30 days
                IF v_borrow_days_ago > 30 THEN
                    v_borrow_days_ago := 1 + (v_j * 2 + v_i) % 30;
                    v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
                END IF;
                v_due_time := v_borrow_time + (v_due_days || ' days')::INTERVAL;
                -- Ensure not overdue
                IF v_due_time < CURRENT_TIMESTAMP THEN
                    v_due_time := CURRENT_TIMESTAMP + ((v_j % 10 + 1) || ' days')::INTERVAL;
                    v_borrow_time := v_due_time - (v_due_days || ' days')::INTERVAL;
                END IF;
                v_return_time := NULL;
                v_fine_amount := 0.0;
            ELSE
                -- Overdue (currently overdue, not returned)
                v_record_status := 'BORROWED'; -- Status is still BORROWED, but overdue
                v_due_days := 30;
                -- Overdue records borrow time also more in recent
                IF v_borrow_days_ago > 60 THEN
                    v_borrow_days_ago := 30 + (v_j * 3 + v_i) % 30;
                    v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
                END IF;
                v_overdue_days := 1 + (v_j % 15); -- 1-15 days overdue (control overdue days, avoid too high fine)
                v_due_time := CURRENT_TIMESTAMP - (v_overdue_days || ' days')::INTERVAL;
                v_borrow_time := v_due_time - (v_due_days || ' days')::INTERVAL;
                v_return_time := NULL;
                -- Calculate fine
                IF v_overdue_days <= 7 THEN
                    v_fine_amount := v_overdue_days * 1.0;
                ELSIF v_overdue_days <= 30 THEN
                    v_fine_amount := 7.0 + (v_overdue_days - 7) * 2.0;
                ELSE
                    v_fine_amount := 7.0 + 23.0 * 2.0 + (v_overdue_days - 30) * 5.0;
                END IF;
                -- Note: Current overdue records fine_amount will be updated by system auto-calculation, set to 0 here first
                v_fine_amount := 0.0;
            END IF;
            
            -- Insert borrow record
            INSERT INTO borrow_records (
                user_id, book_id, borrow_time, due_time, return_time, 
                status, fine_amount, created_at
            ) VALUES (
                v_user_id, v_book_id, v_borrow_time, v_due_time, v_return_time,
                v_record_status, v_fine_amount, v_borrow_time
            );
            
            -- If current borrow status, decrease book available count
            IF v_record_status = 'BORROWED' THEN
                UPDATE books 
                SET available_count = GREATEST(available_count - 1, 0)
                WHERE id = v_book_id;
            END IF;
        END LOOP;
        
        -- Update user fine amount (only calculate fines from returned records)
        UPDATE users 
        SET fine_amount = v_total_fine 
        WHERE id = v_user_id;
        
        -- Set higher fine for some users (over 50 yuan, but controlled in reasonable range)
        -- Select about 5% of users (1 in every 20 users) to set higher fine
        -- Only add high fine if current fine is low (to control total fine between 50-80 yuan)
        IF v_i % 20 = 0 AND v_total_fine < 30.0 THEN
            -- Add only 1 high fine overdue record to keep total fine reasonable
            -- One record with 32-34 days overdue will add 63-73 yuan
            -- Combined with existing fine (<30), total will be 50-100 yuan (acceptable)
            SELECT id INTO v_book_id 
            FROM books 
            ORDER BY RANDOM() 
            LIMIT 1;
            
            v_borrow_days_ago := 60;
            v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
            -- Control overdue days: 32-34 days (fine: 63-73 yuan)
            -- Fine formula for overdue > 30: 7*1 + 23*2 + (overdue-30)*5
            -- For 32 days: 7 + 46 + 10 = 63 yuan
            -- For 33 days: 7 + 46 + 15 = 68 yuan
            -- For 34 days: 7 + 46 + 20 = 73 yuan
            v_overdue_days := 32 + (v_i % 3); -- 32-34 days (fine: 63-73 yuan)
            
            v_due_time := CURRENT_TIMESTAMP - (v_overdue_days || ' days')::INTERVAL;
            v_return_time := CURRENT_TIMESTAMP - ((v_overdue_days - 5) || ' days')::INTERVAL; -- Returned
            
            -- Calculate high fine: 7*1 + 23*2 + (overdue-30)*5
            v_fine_amount := 7.0 + 23.0 * 2.0 + (v_overdue_days - 30) * 5.0;
            v_total_fine := v_total_fine + v_fine_amount;
            
            INSERT INTO borrow_records (
                user_id, book_id, borrow_time, due_time, return_time,
                status, fine_amount, created_at
            ) VALUES (
                v_user_id, v_book_id, v_borrow_time, v_due_time, v_return_time,
                'RETURNED', v_fine_amount, v_borrow_time
            );
            
            UPDATE users 
            SET fine_amount = v_total_fine 
            WHERE id = v_user_id;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Generated 150 new users and their borrow records';
END $$;

-- ========================================
-- Step 3: Generate borrow records for huang and wen
-- ========================================
DO $$
DECLARE
    v_user_id BIGINT;
    v_book_id BIGINT;
    v_book_count INTEGER;
    v_username VARCHAR(50);
    v_borrow_count INTEGER;
    v_borrow_days_ago INTEGER;
    v_due_days INTEGER;
    v_overdue_days INTEGER;
    v_fine_amount DECIMAL(10, 2);
    v_total_fine DECIMAL(10, 2);
    v_record_status VARCHAR(10);
    v_borrow_time TIMESTAMP;
    v_due_time TIMESTAMP;
    v_return_time TIMESTAMP;
    v_j INTEGER;
    v_target_users VARCHAR(50)[] := ARRAY['huang', 'wen'];
    v_user_idx INTEGER;
BEGIN
    -- Get total book count
    SELECT COUNT(*) INTO v_book_count FROM books;
    
    IF v_book_count = 0 THEN
        RAISE EXCEPTION 'No book data found, please import books first';
    END IF;
    
    -- Process huang and wen
    FOR v_user_idx IN 1..array_length(v_target_users, 1) LOOP
        v_username := v_target_users[v_user_idx];
        
        -- Get user ID
        SELECT id INTO v_user_id FROM users WHERE username = v_username;
        
        IF v_user_id IS NULL THEN
            RAISE NOTICE 'User % not found, skipping', v_username;
            CONTINUE;
        END IF;
        
        -- Delete existing borrow records for this user (restore inventory first)
        DECLARE
            v_book_ids_to_restore BIGINT[];
        BEGIN
            -- Collect book IDs that need inventory restoration
            SELECT ARRAY_AGG(DISTINCT book_id) INTO v_book_ids_to_restore
            FROM borrow_records
            WHERE user_id = v_user_id
            AND status = 'BORROWED';
            
            -- Restore available count
            IF v_book_ids_to_restore IS NOT NULL AND array_length(v_book_ids_to_restore, 1) > 0 THEN
                UPDATE books 
                SET available_count = LEAST(available_count + (
                    SELECT COUNT(*) 
                    FROM borrow_records br 
                    WHERE br.book_id = books.id 
                    AND br.user_id = v_user_id
                    AND br.status = 'BORROWED'
                ), total_count)
                WHERE id = ANY(v_book_ids_to_restore);
            END IF;
            
            -- Delete borrow records
            DELETE FROM borrow_records WHERE user_id = v_user_id;
        END;
        
        -- Generate borrow records: 15-30 books per user
        v_borrow_count := 15 + (v_user_idx * 5 + 10); -- 15-30 books
        v_total_fine := 0.0;
        
        FOR v_j IN 1..v_borrow_count LOOP
            -- Randomly select a book
            SELECT id INTO v_book_id 
            FROM books 
            ORDER BY RANDOM() 
            LIMIT 1;
            
            -- Borrow time distribution: 30% in last 30 days, 40% in 30-90 days, 30% in 90-365 days
            IF (v_j % 10) <= 3 THEN
                -- 30%: Last 30 days
                v_borrow_days_ago := 1 + (v_j * 3 + v_user_idx) % 30;
            ELSIF (v_j % 10) <= 7 THEN
                -- 40%: 30-90 days ago
                v_borrow_days_ago := 30 + (v_j * 2 + v_user_idx * 3) % 60;
            ELSE
                -- 30%: 90-365 days ago
                v_borrow_days_ago := 90 + (v_j * 5 + v_user_idx * 7) % 275;
            END IF;
            v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
            
            -- Determine record status: 75% returned, 15% current borrow, 10% overdue
            IF v_j <= (v_borrow_count * 0.75) THEN
                -- Returned
                v_record_status := 'RETURNED';
                v_due_days := 30;
                v_due_time := v_borrow_time + (v_due_days || ' days')::INTERVAL;
                
                -- 30% of returned records have slight overdue (1-8 days), generating small fine (5-20 yuan)
                IF (v_j % 3) = 0 THEN
                    v_overdue_days := 1 + (v_j % 8); -- 1-8 days overdue
                    v_return_time := v_due_time + (v_overdue_days || ' days')::INTERVAL;
                    -- Calculate fine: first 7 days 1 yuan/day, then 2 yuan/day
                    IF v_overdue_days <= 7 THEN
                        v_fine_amount := v_overdue_days * 1.0;
                    ELSE
                        v_fine_amount := 7.0 + (v_overdue_days - 7) * 2.0;
                    END IF;
                    v_total_fine := v_total_fine + v_fine_amount;
                ELSE
                    -- Return early or on time
                    v_return_time := v_due_time - ((v_j % 7) || ' days')::INTERVAL;
                    IF v_return_time < v_borrow_time THEN
                        v_return_time := v_borrow_time + (v_due_days || ' days')::INTERVAL;
                    END IF;
                    v_fine_amount := 0.0;
                END IF;
            ELSIF v_j <= (v_borrow_count * 0.9) THEN
                -- Current borrow (not overdue)
                v_record_status := 'BORROWED';
                v_due_days := 30;
                IF v_borrow_days_ago > 30 THEN
                    v_borrow_days_ago := 1 + (v_j * 2 + v_user_idx) % 30;
                    v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
                END IF;
                v_due_time := v_borrow_time + (v_due_days || ' days')::INTERVAL;
                IF v_due_time < CURRENT_TIMESTAMP THEN
                    v_due_time := CURRENT_TIMESTAMP + ((v_j % 10 + 1) || ' days')::INTERVAL;
                    v_borrow_time := v_due_time - (v_due_days || ' days')::INTERVAL;
                END IF;
                v_return_time := NULL;
                v_fine_amount := 0.0;
            ELSE
                -- Overdue (currently overdue, not returned)
                v_record_status := 'BORROWED';
                v_due_days := 30;
                IF v_borrow_days_ago > 60 THEN
                    v_borrow_days_ago := 30 + (v_j * 3 + v_user_idx) % 30;
                    v_borrow_time := CURRENT_TIMESTAMP - (v_borrow_days_ago || ' days')::INTERVAL;
                END IF;
                v_overdue_days := 1 + (v_j % 10); -- 1-10 days overdue (keep it low)
                v_due_time := CURRENT_TIMESTAMP - (v_overdue_days || ' days')::INTERVAL;
                v_borrow_time := v_due_time - (v_due_days || ' days')::INTERVAL;
                v_return_time := NULL;
                v_fine_amount := 0.0; -- Will be calculated by system
            END IF;
            
            -- Insert borrow record
            INSERT INTO borrow_records (
                user_id, book_id, borrow_time, due_time, return_time, 
                status, fine_amount, created_at
            ) VALUES (
                v_user_id, v_book_id, v_borrow_time, v_due_time, v_return_time,
                v_record_status, v_fine_amount, v_borrow_time
            );
            
            -- If current borrow status, decrease book available count
            IF v_record_status = 'BORROWED' THEN
                UPDATE books 
                SET available_count = GREATEST(available_count - 1, 0)
                WHERE id = v_book_id;
            END IF;
        END LOOP;
        
        -- Update user fine amount (only from returned records)
        UPDATE users 
        SET fine_amount = v_total_fine 
        WHERE id = v_user_id;
        
        RAISE NOTICE 'Generated % borrow records for user % with fine amount: %', v_borrow_count, v_username, v_total_fine;
    END LOOP;
    
    RAISE NOTICE 'Generated borrow records for huang and wen';
END $$;

COMMIT;

-- ========================================
-- Verify data
-- ========================================
SELECT 
    COUNT(*) as total_users,
    COUNT(*) FILTER (WHERE username IN ('admin', 'huang', 'wen')) as preserved_users,
    COUNT(*) FILTER (WHERE username LIKE 'user%') as new_users
FROM users;

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
WHERE u.username LIKE 'user%'
GROUP BY u.username, u.fine_amount
ORDER BY u.fine_amount DESC
LIMIT 10;

-- Show huang and wen borrow records summary
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
GROUP BY u.username, u.fine_amount
ORDER BY u.username;

-- Show users with fine over 50
SELECT 
    u.username,
    u.fine_amount as total_fine,
    COUNT(*) as total_records
FROM users u
WHERE u.fine_amount > 50
GROUP BY u.username, u.fine_amount
ORDER BY u.fine_amount DESC;

-- ========================================
-- Step 4: Update password hash for new users
-- Note: Please run Java program to generate password hash first
-- cd server && mvn compile exec:java -Dexec.mainClass="com.library.server.util.GenerateUserPasswords"
-- Then replace the placeholder below with real password hash
-- ========================================

-- Update password hash for all new users (user001-user150)
-- Replace '100000:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' below
-- with real password hash generated from Java program
-- UPDATE users 
-- SET password_hash = 'Replace with real password hash'
-- WHERE username LIKE 'user%' AND password_hash = '100000:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';




