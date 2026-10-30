-- =========================================================================
-- AcademiaX Microservices Migration: 02_enrollment_and_payment_uniqueness.sql
-- Purpose: Enforce strict uniqueness at database level:
--          1. Prevent duplicate active or pending enrollments for same student & course
--             while allowing re-enrollment after CANCELLED or PAYMENT_FAILED.
--          2. Prevent duplicate successful payments per enrollment.
--          3. Ensure unique payment idempotency keys.
-- =========================================================================

-- 1. Partial Unique Index on enrollments for active or pending states
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_enrollment 
ON enrollments (student_id, course_id) 
WHERE status IN ('ENROLLED', 'PENDING_PAYMENT');

-- 2. Partial Unique Index on payments for successful payments per enrollment
CREATE UNIQUE INDEX IF NOT EXISTS uq_successful_payment 
ON payments (enrollment_id) 
WHERE payment_status = 'SUCCESS';

-- 3. Unique Index on idempotency_key for non-null keys
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_idempotency_key 
ON payments (idempotency_key) 
WHERE idempotency_key IS NOT NULL;
