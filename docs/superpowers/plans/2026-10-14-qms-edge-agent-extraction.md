# QMS Edge Agent Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the QMS device listener into `qms-edge-agent` as its sole source repository without changing its deployed behavior or QMS Telemetry v1 compatibility.

**Architecture:** Clone the existing `main` branch of `qms-edge-agent`, then migrate the current working-tree version of the listener into a small source/config/scripts/docs layout. Keep protocol handling and QMS interaction byte-for-byte stable where possible; only the build script and documentation receive reviewed path/reference changes. Publish and tag the standalone repository before replacing QMS's old source directory with a pointer README.

**Tech Stack:** Windows batch scripts, .NET Framework 4.x `csc.exe`, WinForms, C#, PowerShell SHA-256 verification, Git/GitHub.

**Spec:** `docs/superpowers/specs/2026-10-14-qms-edge-agent-extraction-design.md`

## Global Constraints

- The new local repository is exactly `D:\project\qms-edge-agent`; its remote is `https://github.com/CCQ-coder/qms-edge-agent.git` and default branch is `main`.
- Migrate the listener's current QMS working-tree files, never overwrite them from `HEAD`, an old commit, or `outputs/`.
- Do not modify QMS backend, frontend, database schema, telemetry endpoint, ERP Spring Boot gateway, or live field deployments.
- Keep the .NET Framework/WinForms runtime, INI keys, ports, request routes, normal device responses, `--headless`, `--self-test`, `--config`, local queue semantics, and `X-Gateway-Token` contract unchanged.
- `src/JingdeEapForwarder.cs` and `config/JingdeEapForwarder.ini` must match their current QMS source counterparts by SHA-256.
- Changes to build/start/install scripts are limited to required relative-path updates. Documentation changes are limited to repository, project-path, and build-output-path references.
- Do not remove QMS listener source until the standalone repository builds, passes self-test, commits, tags `v1.0.0-extracted`, and the commit plus tag are successfully pushed.
- Stage and commit only paths created or changed by this migration; the QMS worktree contains unrelated user changes.

## Review Focus

- A migration accidentally copies the stale EXE under `outputs/` instead of the modified source file; Task 1 compares source SHA-256 values before and after the migration.
- The relocated `build.cmd` resolves paths relative to `scripts/` incorrectly; Task 2 builds a release folder and checks all five expected release files.
- A valid `--self-test` build exits nonzero or cannot resolve its co-located INI after relocation; Task 2 runs the release EXE with `--self-test` and requires exit code `0`.
- A real token, queue, log, or binary enters the new repository; Task 3 checks tracked files against `.gitignore` and scans tracked config/document files for placeholder-only credentials.
- Remote authentication or push fails after local migration; Task 4 stops before replacing QMS source and reports the failure, leaving the QMS directory intact.

---

## File Structure

### New `D:\project\qms-edge-agent` repository

- `src/JingdeEapForwarder.cs` — unchanged current listener implementation.
- `config/JingdeEapForwarder.ini` — unchanged, placeholder-only default configuration.
- `scripts/build.cmd` — builds the source and assembles a portable release directory.
- `scripts/start-gateway.cmd` — copied launch helper, shipped beside the EXE.
- `scripts/install-autostart.cmd` — copied Windows startup-task installer, shipped beside the EXE.
- `scripts/verify-qms-telemetry-contract.ps1` — static regression check for the required Telemetry v1 URL, request wrapper, and authentication header constants.
- `docs/README.md` — field installation and operating guide migrated from QMS, with only repository/build-path references updated.
- `docs/qms-telemetry-contract.md` — Edge Agent consumer view of QMS Telemetry v1.
- `README.md` — repository overview, supported adapters, quick build command, and link to detailed docs.
- `CHANGELOG.md` — records the initial extraction release.
- `.gitignore` — excludes outputs, binaries, field configuration overrides, local queues, and logs.

### Existing `D:\project\qms` repository

- `gateway/jingde-eap-forwarder/README.md` — replaces the former listener source directory with a non-buildable migration pointer.
- `docs/superpowers/specs/2026-10-14-qms-edge-agent-extraction-design.md` — update only to include the already identified verification wording correction, then commit that document alone before beginning the migration.
- `docs/superpowers/plans/2026-10-14-qms-edge-agent-extraction.md` — this plan; commit it independently after review if repository practice requires design artifacts to be committed.

## Task 1: Snapshot the current listener and prepare the standalone checkout

**Files:**
- Read: `gateway/jingde-eap-forwarder/JingdeEapForwarder.cs`
- Read: `gateway/jingde-eap-forwarder/JingdeEapForwarder.ini`
- Read: `gateway/jingde-eap-forwarder/build.cmd`
- Read: `gateway/jingde-eap-forwarder/start-gateway.cmd`
- Read: `gateway/jingde-eap-forwarder/install-autostart.cmd`
- Read: `gateway/jingde-eap-forwarder/README.md`
- Create: `D:\project\qms-edge-agent` (Git checkout only; do not alter QMS sources)

**Interfaces:**
- Consumes: the current QMS worktree files and remote `origin/main`.
- Produces: an untouched standalone checkout and a recorded SHA-256 baseline for `JingdeEapForwarder.cs` and `JingdeEapForwarder.ini` used by Task 2.

- [ ] **Step 1: Confirm the standalone destination is absent or is an untouched checkout of the specified remote**

Run: `Test-Path 'D:\project\qms-edge-agent'` and, if it exists, `git -C D:\project\qms-edge-agent status --short` plus `git -C D:\project\qms-edge-agent remote -v`.

Expected: either no destination exists, or it is clean and its `origin` is exactly `https://github.com/CCQ-coder/qms-edge-agent.git`. Stop for user direction if another checkout or uncommitted work is present.

- [ ] **Step 2: Record and display the source/config SHA-256 baseline from the QMS working tree**

Run PowerShell `Get-FileHash -Algorithm SHA256` on exactly `gateway/jingde-eap-forwarder/JingdeEapForwarder.cs` and `gateway/jingde-eap-forwarder/JingdeEapForwarder.ini`.

Expected: two nonempty SHA-256 values captured in the implementation log before any copy occurs.

- [ ] **Step 3: Clone the remote `main` branch when no local checkout exists**

Run: `git clone --branch main --single-branch https://github.com/CCQ-coder/qms-edge-agent.git D:\project\qms-edge-agent`.

Expected: `git -C D:\project\qms-edge-agent branch --show-current` prints `main`, and `git -C D:\project\qms-edge-agent status --short` has no output.

- [ ] **Step 4: Inspect the remote checkout before adding files**

Run: `git -C D:\project\qms-edge-agent log -1 --oneline`, `git -C D:\project\qms-edge-agent status --short`, and `Get-ChildItem -Force D:\project\qms-edge-agent`.

Expected: the initial remote content is visible and no existing file is overwritten without an explicit migration decision.

## Task 2: Migrate the listener source and preserve build behavior

**Files:**
- Create: `D:\project\qms-edge-agent/src/JingdeEapForwarder.cs`
- Create: `D:\project\qms-edge-agent/config/JingdeEapForwarder.ini`
- Create: `D:\project\qms-edge-agent/scripts/build.cmd`
- Create: `D:\project\qms-edge-agent/scripts/start-gateway.cmd`
- Create: `D:\project\qms-edge-agent/scripts/install-autostart.cmd`
- Create: `D:\project\qms-edge-agent/docs/README.md`
- Create at build time, ignored: `D:\project\qms-edge-agent/outputs/jingde-eap-forwarder/JingdeEapForwarder.exe`

**Interfaces:**
- Consumes: the Task 1 source/config baseline hashes and the six current QMS listener files.
- Produces: a portable release directory containing `JingdeEapForwarder.exe`, `JingdeEapForwarder.ini`, `README.md`, `start-gateway.cmd`, and `install-autostart.cmd`.

- [ ] **Step 1: Copy the current source and default configuration without content changes**

Copy only `JingdeEapForwarder.cs` to `src/` and `JingdeEapForwarder.ini` to `config/`; do not copy the compiled EXE, queue, logs, or any `outputs/` content.

- [ ] **Step 2: Verify the copied source and config against the Task 1 SHA-256 baseline**

Run `Get-FileHash -Algorithm SHA256` on the two new files and compare each value to the captured baseline.

Expected: both hashes are identical. Stop and correct the copy if either differs.

- [ ] **Step 3: Copy the launch and startup-task scripts unchanged**

Copy `start-gateway.cmd` and `install-autostart.cmd` to `scripts/`. Their `%~dp0` behavior must remain unchanged because `build.cmd` will copy them into the release directory beside the EXE.

- [ ] **Step 4: Adapt `scripts/build.cmd` only for the new layout**

Set `SCRIPT_DIR=%~dp0`, `REPO_DIR=%SCRIPT_DIR%..`, `SRC_DIR=%REPO_DIR%\src`, `CONFIG_DIR=%REPO_DIR%\config`, `DOCS_DIR=%REPO_DIR%\docs`, and `OUT_DIR=%REPO_DIR%\outputs\jingde-eap-forwarder`. Keep the .NET Framework compiler discovery, compiler references, target (`winexe`), platform (`anycpu`), optimization, output executable name, and error handling unchanged. Compile `%SRC_DIR%\JingdeEapForwarder.cs`, then copy the default config, `docs\README.md`, and the two release helpers into `%OUT_DIR%`.

- [ ] **Step 5: Migrate the field guide with path-only edits**

Copy the old README to `docs/README.md`. Change its source/build reference from `D:\project\qms\outputs\jingde-eap-forwarder` to `D:\project\qms-edge-agent\outputs\jingde-eap-forwarder`; preserve protocol, port, configuration, command, queue, and troubleshooting guidance verbatim.

- [ ] **Step 6: Build the extracted agent**

Run from `D:\project\qms-edge-agent`: `cmd /c scripts\build.cmd`.

Expected: exit code `0` and all five release files exist under `outputs\jingde-eap-forwarder`.

- [ ] **Step 7: Run the extracted binary's built-in regression check**

Run: `& .\outputs\jingde-eap-forwarder\JingdeEapForwarder.exe --self-test`.

Expected: exit code `0`; any failure blocks the migration and leaves QMS source untouched.

- [ ] **Step 8: Commit the functional extraction files in the standalone repository**

```bash
git -C D:\project\qms-edge-agent add src config scripts/build.cmd scripts/start-gateway.cmd scripts/install-autostart.cmd docs/README.md
git -C D:\project\qms-edge-agent commit -m "feat: extract device edge listener"
```

Expected: the commit contains only extracted listener source, configuration, build/release scripts, and the migrated guide.

## Task 3: Add repository hygiene and freeze the QMS Telemetry v1 consumer contract

**Files:**
- Create: `D:\project\qms-edge-agent/.gitignore`
- Create: `D:\project\qms-edge-agent/scripts/verify-qms-telemetry-contract.ps1`
- Create or Modify: `D:\project\qms-edge-agent/README.md`
- Create: `D:\project\qms-edge-agent/docs/qms-telemetry-contract.md`
- Create: `D:\project\qms-edge-agent/CHANGELOG.md`

**Interfaces:**
- Consumes: `src/JingdeEapForwarder.cs` and `config/JingdeEapForwarder.ini` from Task 2.
- Produces: a local static contract check invoked as `powershell -ExecutionPolicy Bypass -File scripts\verify-qms-telemetry-contract.ps1`, plus documentation that identifies QMS as the contract authority.

- [ ] **Step 1: Write the Telemetry v1 regression script**

Implement `scripts/verify-qms-telemetry-contract.ps1` to read `src/JingdeEapForwarder.cs` and `config/JingdeEapForwarder.ini`, fail with a nonzero exit code if any of these required literals is absent: default `TelemetryPath=/api/v1/equipment/telemetry`, request wrapper key `events`, header `X-Gateway-Token`, and configuration key `GatewayToken`. Print a concise success message only when all checks pass.

- [ ] **Step 2: Execute the contract regression script**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-qms-telemetry-contract.ps1`.

Expected: exit code `0` and output confirming Telemetry v1 consumer compatibility. This is the automated check for the contract-sensitive values identified in the spec.

- [ ] **Step 3: Add safe repository ignore rules**

Create `.gitignore` entries for `outputs/`, `data/`, `logs/`, `*.log`, `JingdeEapForwarder-startup-errors.log`, `config/*.local.ini`, and editor/build metadata. Do not ignore `config/JingdeEapForwarder.ini` or any source, script, and documentation file required for a reproducible release.

- [ ] **Step 4: Write the repository overview, contract guide, and release note**

Set root `README.md` to explain that the project is the sole source for the Windows Edge Agent and link to `docs/README.md`. Create `docs/qms-telemetry-contract.md` with the exact URL, request wrapper, `X-Gateway-Token`, `2xx` acknowledgement, local queue retention, and QMS-owned contract-version policy. Set `CHANGELOG.md` entry `v1.0.0-extracted` to describe the repository extraction and explicitly state that no field protocol or telemetry behavior changed.

- [ ] **Step 5: Verify only intended files are tracked and no secret/runtime artifact is staged**

Run `git -C D:\project\qms-edge-agent status --short`, `git -C D:\project\qms-edge-agent check-ignore -v --no-index outputs\jingde-eap-forwarder\JingdeEapForwarder.exe data\pending-events.json logs\jingde-eap-forwarder.log`, and search tracked configuration/docs for `REPLACE_WITH_QMS_GATEWAY_TOKEN` as the only token value.

Expected: generated output, queues, and logs are ignored; no real token, binary, or local runtime data is staged.

- [ ] **Step 6: Commit documentation, hygiene, and compatibility verification**

```bash
git -C D:\project\qms-edge-agent add .gitignore README.md CHANGELOG.md docs/qms-telemetry-contract.md scripts/verify-qms-telemetry-contract.ps1
git -C D:\project\qms-edge-agent commit -m "docs: define edge agent telemetry contract"
```

Expected: this commit is isolated from the functional extraction commit and contains no generated artifacts.

## Task 4: Publish the standalone source, tag it, then replace the QMS copy with a pointer

**Files:**
- Modify: `D:\project\qms-edge-agent` Git history and remote `main`
- Create: `gateway/jingde-eap-forwarder/README.md`
- Delete after successful remote publish: `gateway/jingde-eap-forwarder/JingdeEapForwarder.cs`
- Delete after successful remote publish: `gateway/jingde-eap-forwarder/JingdeEapForwarder.ini`
- Delete after successful remote publish: `gateway/jingde-eap-forwarder/build.cmd`
- Delete after successful remote publish: `gateway/jingde-eap-forwarder/start-gateway.cmd`
- Delete after successful remote publish: `gateway/jingde-eap-forwarder/install-autostart.cmd`
- Modify: `docs/superpowers/specs/2026-10-14-qms-edge-agent-extraction-design.md` (commit the documented verification wording correction only)

**Interfaces:**
- Consumes: successful Task 2 build/self-test, Task 3 contract check, two standalone commits, and remote `origin/main`.
- Produces: remote `main` plus tag `v1.0.0-extracted`, and a QMS pointer README that names this release as the only listener source.

- [ ] **Step 1: Perform final standalone verification before any QMS deletion**

Run: `git -C D:\project\qms-edge-agent diff --check`, `git -C D:\project\qms-edge-agent status --short`, the Task 3 contract script, and `& D:\project\qms-edge-agent\outputs\jingde-eap-forwarder\JingdeEapForwarder.exe --self-test`.

Expected: no whitespace errors, no unexpected changes, contract check success, and self-test exit `0`.

- [ ] **Step 2: Create and push the extraction tag**

Run:

```bash
git -C D:\project\qms-edge-agent tag -a v1.0.0-extracted -m "Initial standalone Edge Agent extraction"
git -C D:\project\qms-edge-agent push origin main
git -C D:\project\qms-edge-agent push origin v1.0.0-extracted
```

Expected: both push commands exit `0`, and `git -C D:\project\qms-edge-agent ls-remote --heads --tags origin main v1.0.0-extracted` shows the remote branch and tag. If any push fails, stop here without deleting or altering QMS listener source.

- [ ] **Step 3: Replace the QMS listener directory with a non-buildable migration pointer**

After successful Step 2 only, delete the five migrated source/config/script files. Create `gateway/jingde-eap-forwarder/README.md` containing: the canonical GitHub repository URL, release tag `v1.0.0-extracted`, the statement that QMS no longer builds/releases this listener, the preserved QMS Telemetry v1 endpoint and `X-Gateway-Token` compatibility, and a warning against restoring a second editable source copy in QMS.

- [ ] **Step 4: Commit the design consistency correction separately from the source replacement**

```bash
git -C D:\project\qms add docs/superpowers/specs/2026-10-14-qms-edge-agent-extraction-design.md
git -C D:\project\qms commit -m "docs: align edge migration checks" -- docs/superpowers/specs/2026-10-14-qms-edge-agent-extraction-design.md
```

Expected: only the already-reviewed path/document-reference clarification is included.

- [ ] **Step 5: Verify and commit only the QMS migration pointer**

Run `git -C D:\project\qms diff -- gateway/jingde-eap-forwarder` and inspect that it affects no path outside this directory. Stage exactly `gateway/jingde-eap-forwarder`, then commit:

```bash
git -C D:\project\qms add -A -- gateway/jingde-eap-forwarder
git -C D:\project\qms commit -m "chore: point device listener to edge agent repo" -- gateway/jingde-eap-forwarder
```

Expected: the commit contains the five intended source/config/script removals plus the pointer README only; unrelated dirty worktree files remain unstaged.

- [ ] **Step 6: Record final evidence and report the handoff**

Run `git -C D:\project\qms-edge-agent log -2 --oneline`, `git -C D:\project\qms-edge-agent tag --points-at HEAD`, `git -C D:\project\qms status --short`, and re-run `git -C D:\project\qms-edge-agent ls-remote --heads --tags origin main v1.0.0-extracted`.

Expected: release source and tag are published, QMS contains only the migration pointer for this component, and any remaining QMS changes are clearly identified as pre-existing/unrelated.
