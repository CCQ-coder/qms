# Flying Probe Clock Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show future flying-probe records using a stable, per-device clock correction derived from the Win7 status file and QMS server time.

**Architecture:** The gateway samples QMS time, then calculates each device's offset from `host-status.json` and its server-side file timestamp. It persists an adjusted timestamp in each event's attributes without changing the raw device time or record identity. QMS returns the adjusted timestamp separately, and the flying-probe UI prefers it for display.

**Tech Stack:** .NET Framework 4.x C# collector, Spring Boot Java backend, Vue/TypeScript frontend.

**Spec:** `docs/superpowers/specs/2026-11-19-flying-probe-clock-correction.md`

## Global Constraints

- The collector remains compatible with its current .NET Framework 4.x build.
- Existing telemetry `deviceTime`, `recordNo`, deduplication, and FQC calculations retain their meaning.
- Invalid or stale status never blocks TXT synchronization.

## Review Focus

- Device clock ahead and behind: both signed offsets must correct in the right direction.
- Status belongs to another directory or is malformed: no correction and no dropped TXT.
- Clock probe response is slow or unavailable: no guessed QMS offset.
- Collector runs on a PC with a different clock from SCBGX: calibration uses the share file timestamp unchanged.
- Event is queued then retried after a clock change: correction remains unchanged.
- Old events without corrected time: UI clearly shows their original time.

---

### Task 1: Authenticated QMS time reference

**Files:** QMS gateway API controller and its test.

**Interfaces:** `GET /api/v1/gateway/server-time` with `X-Gateway-Token`, JSON body `{ "serverTime": "<UTC ISO-8601 instant>" }`.

- [ ] Add a controller test for valid and missing gateway tokens; run it and observe failure from the missing route.
- [ ] Implement the endpoint using `Instant.now()` and existing `GatewayIntegrationAuthentication`.
- [ ] Run the focused and full backend test suites.

### Task 2: Per-device correction in SCBGX collector

**Files:** `tools/flying-probe-collector` clock calibration class, scanner/runtime/build/test scripts, collector tests.

**Interfaces:** Each newly queued flying-probe event may contain `attributes.correctedDeviceTime` (local ISO timestamp) and `attributes.clockOffsetSeconds` (signed integer). A missing valid status produces neither field.

- [ ] Test QMS time probe midpoint, status identity/freshness/offset validation, positive and negative offset, and unchanged record identity; observe failure.
- [ ] Implement the minimum calibration and attach it only when a stable TXT is queued. Keep queued payload immutable on retry.
- [ ] Run collector tests and build both EXEs.

### Task 3: QMS response and monitor display

**Files:** `EquipmentTelemetryRecordResponse`, query service, `InspectionWorkbenchView.vue`, API type and focused backend/frontend tests.

**Interfaces:** `correctedDeviceTime` is optional on `EquipmentTelemetryRecordResponse`, parsed only for flying-probe events from the persisted attribute. UI uses `correctedDeviceTime ?? deviceTime` for flying-probe detection times.

- [ ] Add tests for corrected and missing/invalid values plus UI display; observe failure.
- [ ] Implement response mapping and the flying-probe table/card/detail labels.
- [ ] Run focused and full QMS backend/frontend tests and builds.

### Task 4: Packaging and review

- [ ] Compare final diffs against the spec, check no secret or runtime output is staged.
- [ ] Package matching collector and QMS builds with commit IDs and hashes.
- [ ] Obtain one independent review, address any material findings, and document deployment order: QMS first, collector second.
