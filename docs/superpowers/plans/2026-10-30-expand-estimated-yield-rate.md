# Expand Estimated Yield Rate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the QMS finished-goods utilization rate from `DECIMAL(5,2)` to `DECIMAL(10,2)` and keep the ERP synchronizer's accepted range aligned.

**Architecture:** A forward-only Flyway migration changes the database column without touching API types. The ERP synchronizer retains its existing safety fallback but uses the new maximum, with behavior pinned by Python tests.

**Tech Stack:** MySQL 8, Flyway, Spring Boot, Python unittest

**Spec:** `docs/superpowers/specs/2026-10-30-expand-estimated-yield-rate-design.md`

## Global Constraints

- Deploy QMS V44 before the updated ERP synchronizer.
- Keep `estimated_yield_rate` nullable and preserve two decimal places.
- Do not edit historical migration V22.
- Preserve the raw ERP field when a value exceeds `DECIMAL(10,2)`.
- Do not commit either dirty working tree unless the user separately requests it.

## Review Focus

- `1500.9` and `5894.55` must populate the typed QMS field.
- `99999999.99` must remain valid.
- `100000000.00` must be rejected from the typed field but preserved in the raw ERP field.
- The warning must name `DECIMAL(10,2)`, not the obsolete type.
- The migration must modify the existing nullable column rather than adding or recreating it.

---

### Task 1: Align the ERP synchronizer range

**Files:**
- Modify: `D:/project/hr_sync/tests/test_estimated_yield_rate.py`
- Modify: `D:/project/hr_sync/qms_erp_sync.py`
- Modify: `D:/project/hr_sync/README.md`

**Interfaces:**
- Consumes: ERP `estimatedYieldRate` values parsed as decimal text.
- Produces: typed values within `DECIMAL(10,2)` or the existing raw-field fallback beyond it.

- [ ] Add failing tests for `1500.9`, `5894.55`, the exact maximum, and one value above it.
- [ ] Run `python -m unittest tests.test_estimated_yield_rate -v` and verify RED on the newly valid values.
- [ ] Change the maximum and warning type to `DECIMAL(10,2)`; update the README behavior note.
- [ ] Run the focused test and full Python suite; expect zero failures.

### Task 2: Add the forward database migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V44__expand_estimated_yield_rate.sql`

**Interfaces:**
- Consumes: existing nullable `qms_work_order.estimated_yield_rate NUMERIC(5,2)` from V22.
- Produces: nullable `DECIMAL(10,2)` storage compatible with the synchronizer maximum.

- [ ] Add V44 with `ALTER TABLE ... MODIFY COLUMN ... DECIMAL(10,2) NULL`.
- [ ] Run backend tests and package build; expect zero failures.
- [ ] Run `git diff --check` in both repositories and review only the scoped files.
