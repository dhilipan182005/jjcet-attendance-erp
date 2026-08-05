-- =========================================================================================
-- DATABASE MIGRATION SCRIPT - UPPERCASE STANDARDIZATION
-- Execute this script manually in Supabase SQL Editor.
-- =========================================================================================

-- 1. Update STUDENTS table
UPDATE students
SET 
    student_name = UPPER(TRIM(student_name)),
    register_number = UPPER(TRIM(register_number));

-- 2. Update TEACHERS table
UPDATE teachers
SET 
    full_name = UPPER(TRIM(full_name)),
    employee_id = UPPER(TRIM(employee_id)),
    access_type = UPPER(TRIM(access_type));

-- 3. Update USERS table
UPDATE users
SET 
    full_name = UPPER(TRIM(full_name)),
    user_id = UPPER(TRIM(user_id));

-- 4. Update ATTENDANCE logs (if any uppercase dependent fields exist like batch or register number)
-- Note: Assuming attendance references are by foreign key IDs (student_id), no string update needed here 
-- unless there is denormalization.

-- 5. Update DEPARTMENTS table
UPDATE departments
SET 
    name = UPPER(TRIM(name));

-- 6. Update BATCHES table
UPDATE batches
SET 
    name = UPPER(TRIM(name));

-- 7. Update SECTIONS table
UPDATE sections
SET 
    name = UPPER(TRIM(name));
