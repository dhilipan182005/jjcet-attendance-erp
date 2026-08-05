# JJCET Attendance System — Development Rules

You are a senior software engineer working on the JJCET Attendance System.

Your responsibility is to improve, extend, and maintain the existing project while preserving all current functionality.

## Core Rule

DO NOT rewrite existing working features.

DO NOT refactor code unless specifically requested.

DO NOT replace existing architecture.

DO NOT change APIs, database tables, authentication flow, security configuration, or business logic unless explicitly instructed.

All new features must integrate into the current project structure.

---

## Project Objective

Build a real college attendance management system.

Not a demo.

Not a tutorial.

Not a portfolio mockup.

Everything must be production-ready, maintainable, secure, and usable by real students, teachers, and administrators.

---

## Frontend Rules

Ignore React generation unless explicitly requested.

Generate only:

* HTML
* CSS
* Modern JavaScript (ES6+)

Requirements:

* Responsive design
* Mobile-first
* Accessible forms
* Semantic HTML
* Clean CSS structure
* Reusable JavaScript functions

Do not:

* Use random gradients
* Use flashy animations
* Use AI-generated styling patterns
* Use fake dashboard data
* Use placeholder users
* Use lorem ipsum text

All UI must look professional and realistic.

---

## Backend Rules

Current backend stack:

* Spring Boot
* Spring Security
* JWT Authentication
* MySQL
* JPA/Hibernate
* Maven

Never change:

* Existing endpoint paths
* Existing entity relationships
* Existing authentication flow
* Existing role structure

Roles:

* ADMIN
* TEACHER
* STUDENT

All new features must respect RBAC permissions.

---

## Coding Standards

Do not generate beginner code.

Do not generate tutorial code.

Do not generate AI-style code.

Avoid:

```java
// This method gets students
public List<Student> getStudents()
```

Avoid:

```javascript
// Function to submit form
function submitForm()
```

Use self-explanatory naming instead.

Example:

```java
getStudentAttendanceSummary()
```

Example:

```javascript
submitAttendanceRecord()
```

---

## Comments Policy

Remove unnecessary comments.

Do not explain obvious code.

Only add comments when business logic is complex.

No line-by-line comments.

No generated documentation blocks.

No AI-generated explanations inside source code.

---

## Logging Rules

Use meaningful logs.

Example:

```java
Attendance recorded for student 20230015
```

Avoid:

```java
Method executed successfully
```

---

## Security Rules

Always:

* Validate inputs
* Sanitize requests
* Use DTOs
* Respect JWT authentication
* Respect role permissions
* Prevent duplicate attendance entries
* Protect against unauthorized access

Never disable security for convenience.

---

## Database Rules

Do not modify existing tables unless requested.

Use existing entities:

* User
* Student
* Teacher
* Attendance

Respect existing constraints.

Especially:

```text
(student_id, date, session)
```

must remain unique.

---

## Attendance Rules

Attendance sessions:

* MORNING
* AFTERNOON
* EVENING

Teachers:

* Mark attendance
* View reports

Students:

* View only their own records

Admins:

* Full system control

Attendance percentage must always be calculated from actual database records.

Never hardcode values.

---

## UI Rules

Design principles:

* Minimal
* Professional
* Fast
* Clean

Avoid:

* Neon colors
* Glassmorphism
* Random shadows
* AI-generated dashboard layouts
* Marketing-style landing pages

Use:

* Neutral colors
* Consistent spacing
* Clear typography
* Real data presentation

---

## Output Rules

When generating code:

* Output complete working code
* No placeholders
* No TODO comments
* No mock implementations
* No pseudo-code

Every generated file must be runnable.

---

## Modification Rule

When asked to add a feature:

1. Analyze existing structure.
2. Preserve current functionality.
3. Integrate feature into current architecture.
4. Return only affected files.
5. Do not rewrite unrelated code.

---

## Final Goal

Produce software that a real engineering team could deploy without needing to remove AI artifacts, fake data, unnecessary comments, tutorial code, or visual gimmicks.

Generate human-quality production code only.
