-- Supabase PostgreSQL Migration for JJCET Attendance ERP
-- Enforcing IF NOT EXISTS to prevent production failures

-- 1. Ensure user_id does not contain nulls before constraints are applied (if it does not exist, this handles safely assuming column exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'user_id') THEN
        UPDATE users SET user_id = 'SYSTEM_' || id WHERE user_id IS NULL;
    END IF;
END $$;

-- 2. Create index on users for user_id safely
CREATE INDEX IF NOT EXISTS idx_user_id ON users (user_id);

-- 3. Drop legacy fields if they exist
DO $$
BEGIN
    -- users table legacy fields
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'instance_id') THEN
        ALTER TABLE users DROP COLUMN instance_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'aud') THEN
        ALTER TABLE users DROP COLUMN aud;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'encrypted_password') THEN
        ALTER TABLE users DROP COLUMN encrypted_password;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'email_confirmed_at') THEN
        ALTER TABLE users DROP COLUMN email_confirmed_at;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'email') THEN
        ALTER TABLE users DROP COLUMN email;
    END IF;

    -- teachers table legacy fields
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'teachers' AND column_name = 'email') THEN
        ALTER TABLE teachers DROP COLUMN email;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'teachers' AND column_name = 'department_id') THEN
        ALTER TABLE teachers DROP COLUMN department_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'teachers' AND column_name = 'department') THEN
        ALTER TABLE teachers DROP COLUMN department;
    END IF;

    -- students table legacy fields
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'students' AND column_name = 'phone') THEN
        ALTER TABLE students DROP COLUMN phone;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'students' AND column_name = 'email') THEN
        ALTER TABLE students DROP COLUMN email;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'students' AND column_name = 'password') THEN
        ALTER TABLE students DROP COLUMN password;
    END IF;
END $$;

-- 4. Ensure departments and batches have the 'archived' column
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'departments' AND column_name = 'archived') THEN
        ALTER TABLE departments ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'batches' AND column_name = 'archived') THEN
        ALTER TABLE batches ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- 5. Create performance indexes for querying
CREATE INDEX IF NOT EXISTS idx_register_number ON students (register_number);
CREATE INDEX IF NOT EXISTS idx_student_dept ON students (department_id);
CREATE INDEX IF NOT EXISTS idx_student_batch ON students (batch_id);

CREATE INDEX IF NOT EXISTS idx_teacher_employee ON teachers (employee_id);

CREATE INDEX IF NOT EXISTS idx_student_date ON attendance (student_id, date);
CREATE INDEX IF NOT EXISTS idx_teacher ON attendance (teacher_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance (date);
CREATE INDEX IF NOT EXISTS idx_attendance_session ON attendance (session);
