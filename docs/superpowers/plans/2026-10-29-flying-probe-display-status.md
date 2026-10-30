# Flying Probe Display Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display “测试中” for online finished-test equipment with flying-probe data received in the last seven minutes, otherwise display “空闲”, while preserving “未接入” and “离线”.

**Architecture:** Add one pure frontend status-derivation helper so the time boundary can be tested without mounting the large Vue view. `InspectionWorkbenchView` will consume that helper using its existing runtime and probe snapshot data; backend APIs and other pages remain unchanged.

**Tech Stack:** Vue 3, TypeScript, Node test runner through `tsx`, Vite

**Spec:** `docs/superpowers/specs/2026-10-29-flying-probe-display-status-design.md`

## Global Constraints

- The recency window is exactly seven minutes.
- Data received exactly seven minutes ago still means “测试中”; older data means “空闲”.
- Preserve existing “未接入”, “离线”, and runtime-read-error behavior.
- Do not change backend status semantics or other monitoring pages.
- Do not commit the dirty working tree unless the user explicitly requests a commit.

## Review Focus

- Missing runtime record must remain “未接入”.
- An offline runtime must remain “离线” even if a recent probe record exists.
- An online runtime without a probe record must display “空闲”.
- Invalid probe timestamps must display “空闲”, not throw or display “测试中”.
- The exact seven-minute boundary must display “测试中”; one millisecond older must display “空闲”.

---

### Task 1: Derive and display the monitoring-card state

**Files:**
- Create: `frontend/src/views/inspectionMonitorStatus.ts`
- Create: `frontend/src/views/inspectionMonitorStatus.test.ts`
- Modify: `frontend/src/views/InspectionWorkbenchView.vue:1-15,406-415`

**Interfaces:**
- Consumes: `runtimeOnline: boolean | undefined`, `latestProbeReceivedAt: string | undefined`, and optional deterministic `nowMs: number`.
- Produces: `deriveInspectionMonitorStatus(runtimeOnline, latestProbeReceivedAt, nowMs): '未接入' | '离线' | '测试中' | '空闲'`.

- [ ] **Step 1: Write the failing status-derivation tests**

  Add tests named for missing runtime, offline runtime with recent data, online without data, online within seven minutes, exact seven-minute boundary, one millisecond beyond the boundary, and invalid timestamps. Assert the exact Chinese label from the interface above.

- [ ] **Step 2: Run the focused test and verify RED**

  Run: `npx --yes tsx --test src/views/inspectionMonitorStatus.test.ts`

  Expected: FAIL because `inspectionMonitorStatus.ts` or `deriveInspectionMonitorStatus` does not exist.

- [ ] **Step 3: Implement the pure helper**

  Implement `deriveInspectionMonitorStatus(runtimeOnline: boolean | undefined, latestProbeReceivedAt?: string, nowMs: number = Date.now())` with a `7 * 60 * 1000` inclusive recency threshold. Treat missing or invalid timestamps as “空闲” when online.

- [ ] **Step 4: Run the focused test and verify GREEN**

  Run: `npx --yes tsx --test src/views/inspectionMonitorStatus.test.ts`

  Expected: all status cases pass with zero failures.

- [ ] **Step 5: Integrate the helper into the card**

  Import the helper in `InspectionWorkbenchView.vue`. Keep “状态不可用” when runtime loading fails; otherwise derive the label from `device.runtime?.online` and `device.probe?.receivedAt`. Map “测试中” to the success tag, “空闲” to warning, and “离线”/“未接入”/read errors to info. Remove the now-unused `statusText` import.

- [ ] **Step 6: Verify the frontend**

  Run: `npx --yes tsx --test src/views/inspectionMonitorStatus.test.ts`

  Run: `npm run build`

  Expected: all focused tests pass and the production build exits successfully.

- [ ] **Step 7: Review the scoped diff**

  Run: `git diff --check`

  Run: `git diff -- frontend/src/views/inspectionMonitorStatus.ts frontend/src/views/inspectionMonitorStatus.test.ts frontend/src/views/InspectionWorkbenchView.vue`

  Expected: no whitespace errors; every product-code change traces to the confirmed display rule.
