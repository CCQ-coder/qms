# Flying Probe Material and Time Match Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure tenant-specific middle-digit extraction and bind each flying-probe source file to the latest work order released before the file began.

**Architecture:** Keep existing exact matching when no flying-probe extraction rule is configured. For a configured rule, prefilter work orders by the device material substring, validate the captured numeric key in Java, then choose the unique latest eligible release date. Seed the standard flying-probe FQC template for existing and future tenants.

**Tech Stack:** Spring Boot 3, MyBatis Plus, MySQL 8, Vue 3, Element Plus.

**Spec:** `docs/superpowers/specs/2026-11-02-flying-probe-material-time-match-design.md`

## Global Constraints

- Preserve the existing product-code validation parameter's meaning.
- Never choose between work orders tied on the latest eligible release date.
- Keep all snapshots of a source file pinned to one work order.
- Preserve raw retention baseline behavior; no guessed historic FQC quantities.

## Review Focus

- Numeric keys such as `08169` retain leading zeroes; tested in Task 1.
- A newer work order released after source start cannot capture an old file; tested in Task 1.
- Same-day latest orders remain retryable; tested in Task 1.
- Updating a tenant parameter with an invalid regex is rejected; tested in Task 2.
- A newly created tenant has the three required flying-probe template items; tested in Task 3.

---

### Task 1: Projection matching

**Files:** Modify `backend/src/main/java/com/pcbqms/inspection/application/FlyingProbeInspectionProjectionService.java`; test `backend/src/test/java/com/pcbqms/inspection/application/FlyingProbeInspectionProjectionServiceTest.java`.

**Interfaces:** Read tenant parameter `flying-probe.material-extract-pattern`. A configured regex must match the full internal product number and capture one numeric group. `process` selects the unique latest `releasedAt <= sourceStartTime.toLocalDate()` and pins its `workOrderNo` in the run.

- [ ] Add failing tests for `42777` -> `02PT42777A01`, 8/26 start -> 8/14 order, later 10/21 order ignored, plus same-day tie, leading zero and existing run.
- [ ] Run `mvn -Dtest=FlyingProbeInspectionProjectionServiceTest test` and confirm the new tests fail for missing matching behavior.
- [ ] Implement extraction, candidate prefilter, date selection and pinned-order validation with existing mapper patterns.
- [ ] Re-run the focused test and full backend suite.

### Task 2: System parameter

**Files:** Modify `backend/src/main/java/com/pcbqms/platform/application/TenantParameterApplicationService.java`, `frontend/src/views/SystemParametersView.vue`; test backend tenant parameter tests or add a focused test.

**Interfaces:** Tenant admin saves `flying-probe.material-extract-pattern`; one numeric capture group is required. The UI previews `02PT42777A01` -> `42777` without changing product-code validation.

- [ ] Add a failing backend test for invalid/no-capture regex and a valid capture-group rule.
- [ ] Validate the dedicated key at save time; add a separate rule editor and preview to the system parameters page.
- [ ] Run the focused backend test and `npm run build`.

### Task 3: FQC template provisioning

**Files:** Create `backend/src/main/resources/db/migration/V46__backfill_flying_probe_template.sql`; modify `backend/src/main/java/com/pcbqms/platform/application/TenantManagementService.java`; test the tenant provisioning path.

**Interfaces:** Existing and newly created tenants have active `FLYING_PROBE_ELECTRICAL_V1` with `testedBoardCount`, `goodBoardCount`, `badBoardCount` items from V39, without replacing existing customized templates.

- [ ] Add a failing test for creating a tenant with the required template and three items.
- [ ] Implement tenant provisioning and the idempotent data migration for existing tenants.
- [ ] Run focused and full backend tests; inspect the migration on the production data snapshot before rollout.

### Task 4: Rollout and verification

**Files:** No source changes beyond Tasks 1–3.

- [ ] Simulate the configured rule against production read-only data; record the expected WTD-1 order and FQC delta.
- [ ] Back up production JAR, frontend dist, relevant DB tables and tenant parameter rows; deploy tested artifacts.
- [ ] Set the confirmed `customer-b` rule, confirm WTD-1 projections bind `26080450` and FQC count matches the incremental board counts.
- [ ] Commit only scoped files and push `gy`; verify public QMS and API health.
