package com.example.attendancesystem.entity;

import org.springframework.security.core.GrantedAuthority;

/**
 * Final role model: only ADMIN and TEACHER may be assigned to active users.
 * SUPER_ADMIN and ATTENDANCE_MANAGER were removed - see V2__two_role_model.sql for the
 * data migration (SUPER_ADMIN -> ADMIN, ATTENDANCE_MANAGER -> TEACHER) and GAPS_REPORT.md
 * for why ATTENDANCE_MANAGER maps to TEACHER (it only ever held teacher-level access in
 * SecurityConfig and was already grouped with TEACHER in the frontend's post-login redirect).
 * Historical audit_logs rows may still contain the old role names - that is intentional,
 * for historical record integrity, and is never re-migrated.
 */
public enum Role implements GrantedAuthority {

    ADMIN,
    TEACHER;

    @Override
    public String getAuthority() {
        return "ROLE_" + this.name();
    }
}
