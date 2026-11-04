<!--
Sync Impact Report
Version change: unratified template -> 1.0.0 (initial adoption)
Modified principles:
- Principle 1 placeholder -> I. Minecraft Stability and World Safety
- Principle 2 placeholder -> II. Secure and Authoritative Boundaries
- Principle 3 placeholder -> III. Test Driven Development
- Principle 4 placeholder -> IV. Clear Java Design and Ownership
- Principle 5 placeholder -> V. Composable Modular Synthesis
Added principles:
- VI. Musical Timing and Audio Fidelity
- VII. Bounded Work and Complete Lifecycles
- VIII. Persistent Patches and Compatible Evolution
- IX. Reproducible Builds and Trusted Dependencies
- X. Player Control and Actionable Diagnostics
Added sections:
- Engineering Constraints and Evidence
- Development Workflow and Quality Gates
- Concrete Governance rules replacing the template placeholder
Removed sections: none; illustrative template comments replaced.
Synchronization:
- ✅ updated: .specify/templates/plan-template.md
- ✅ updated: .specify/templates/spec-template.md
- ✅ updated: .specify/templates/tasks-template.md
- ✅ updated: .specify/templates/checklist-template.md
- ✅ reviewed: .specify/templates/constitution-template.md (generic bootstrap retained)
- ✅ reviewed: AGENTS.md (existing plan guidance retained)
- ✅ reviewed: installed Spec Kit task/plan skills; their conditional TDD path is
  activated by the mandatory requirements in the synchronized spec/task templates.
- .specify/templates/commands/*.md: absent; no command templates to update.
- README.md, docs/quickstart.md, feature plans, and runtime sources: absent.
Deferred placeholders: none. The first implementation plan MUST select the supported
Minecraft/loader/Java matrix, tools, and numeric budgets under the gates below;
this constitution does not invent a version selection or claim runtime validation.
-->

# ModularCraft Constitution

## Core Principles

### I. Minecraft Stability and World Safety

- ModularCraft MUST preserve world integrity and Minecraft responsiveness. Feature richness
  and audio complexity MUST yield to bounded, recoverable operation.
- Gameplay and persistent patch mutations MUST be authoritative on the logical server and
  run on the owning game thread. Common code MUST load on a dedicated server without
  resolving client rendering, audio-device, or GUI classes. Singleplayer MUST use the same
  authority model as multiplayer.
- Work off the game thread MUST use immutable snapshots or explicit ownership transfer;
  workers MUST NOT read or mutate live world objects. World scans and patch traversal MUST
  NOT load chunks implicitly or create chunk tickets without a specified, bounded feature.
- Recoverable patch, decoding, or audio failures MUST isolate the affected operation and
  leave valid world state intact. Device loss MUST stop or safely recover local playback
  without stopping the server. Fatal JVM errors MUST NOT be swallowed as routine recovery.

Rationale: a musical instrument is useful only while its host game and saved work remain safe.

### II. Secure and Authoritative Boundaries

- Packets, saved data, configuration, imported patches, and optional media MUST be treated
  as untrusted. Validate types, sizes, nesting, counts, identifiers, finite numeric values,
  and ranges before allocation, graph evaluation, or state mutation. Enforce aggregate
  limits as well as per-message limits to prevent resource exhaustion.
- Server handlers MUST derive identity from the connection and verify permission,
  ownership or granted access, dimension, loaded target, allowed interaction range, and
  current revision before accepting edits. Clients MUST NOT dictate trusted world state.
  Protocols MUST define rejection, rate limiting, stale-message handling, and resynchronization.
- Serialization MUST use explicit, bounded schemas/codecs. Untrusted Java object
  deserialization, executable patch content, runtime dependency downloads, and arbitrary
  class or command execution MUST NOT be introduced. File import/export MUST stay within
  designated roots and resist traversal, symlink escapes, and decompression bombs.
- External services and telemetry MUST be absent by default; any such feature MUST be
  documented and explicitly opted into. Logs and release artifacts MUST exclude secrets
  and unnecessary personal data. Security tests MUST cover malformed and unauthorized input.

### III. Test Driven Development

- Every new or changed executable behavior MUST follow red–green–refactor: write a
  behavior-focused test, observe its expected failure, implement the minimum change, then
  refactor with the suite passing. Bug fixes MUST begin with a reproducing regression test;
  risky changes to untested code MUST first add characterization tests.
- Tests MUST assert public behavior, invariants, and failure handling rather than duplicate
  implementation logic. Time, randomness, scheduling, and I/O MUST be controllable. Tests
  MUST use fixed seeds, virtual clocks where appropriate, isolated worlds/files, and
  explicit numeric tolerances instead of arbitrary sleeps or brittle waveform equality.
- Pure Java tests MUST cover synthesis and graph logic without launching Minecraft.
  Loader-supported GameTests or equivalent integration tests MUST cover world interaction,
  persistence, networking, and lifecycle behavior when affected. Generative/property or
  fuzz tests MUST exercise bounded decoders and graph invariants; listening complements
  automated DSP tests and MUST NOT replace them.
- The first behavior implementation MUST establish repeatable test commands and CI gates.
  Documentation-only changes require link/consistency validation, not artificial runtime
  tests. Build/configuration changes MUST have an appropriate reproducible validation.
  A blocked or flaky required check MUST NOT be reported as passing or silently disabled.

### IV. Clear Java Design and Ownership

- Use the Java toolchain supported by the chosen Minecraft/loader matrix. Code MUST use
  clear names, cohesive types, encapsulated invariants, explicit nullability, and immutable
  value objects where practical. Prefer composition and small interfaces; abstraction and
  dependencies MUST address a demonstrated need. Avoid duplicated domain rules and mutable
  global state.
- DSP, musical transport, and patch graph rules MUST be independent of Minecraft classes.
  Loader, persistence, network, UI, and audio-backend adapters MUST cross explicit contracts.
  Thread affinity, mutable-state ownership, and transfer mechanisms MUST be documented.
- Public APIs and non-obvious algorithms MUST document units, ranges, side/thread rules,
  timing, ownership, and failure behavior. Comments MUST explain intent or constraints;
  changes MUST update affected documentation. Arithmetic MUST handle overflow, non-finite
  values, and precision limits explicitly.
- Resources MUST have explicit lifetimes and exception-safe cleanup, using try-with-resources
  for closeable scopes. Exceptions MUST retain context; empty catches and blanket
  `catch (Throwable)` recovery are prohibited. Cancellation MUST be cooperative and preserve
  interruption. Formatting, lint, compiler warnings, and static analysis MUST be enforced;
  suppressions MUST be narrow and justified.

### V. Composable Modular Synthesis

- Modules MUST expose documented input/output and parameter contracts: signal role
  (audio, control, pitch, gate, trigger, or clock), units/scaling, polarity, range, default,
  rate, channel count, and connection behavior. Port compatibility and any conversions MUST
  be explicit. Disconnected inputs, fan-in/fan-out, and supported polyphony MUST have tested
  semantics. An emulated voltage convention MUST define its reference pitch and scaling.
- Patches MUST express composable signal flow. Graph evaluation order MUST be deterministic
  for the same patch, events, seed, and supported runtime. Feedback MUST have explicit delay
  or a bounded, tested solver; unsupported algebraic loops MUST be rejected with a useful
  explanation. Recursive or unbounded evaluation is prohibited.
- Musical controls MUST use meaningful units such as Hz, BPM, cents, beats, and seconds.
  Pitch tracking, envelopes, gates, triggers, reset/clock ordering, modulation, and voice
  allocation MUST have acceptance cases when implemented. Quantization and scale constraints
  MUST be explicit creative choices; free tuning, noise, dissonance, and intentional
  distortion MUST NOT be silently corrected.
- Every musical feature MUST include a reproducible reference patch with documented intent
  and expected behavior. Quality is judged by predictable, expressive sound and interaction,
  with listening evidence for audible changes, not merely by producing nonzero samples.

### VI. Musical Timing and Audio Fidelity

- The musical timeline MUST use an explicit monotonic transport and audio sample timing;
  it MUST NOT derive elapsed musical time solely from rendered frames, game ticks, packet
  arrival, or a looping step index. Conversion between clock domains MUST be specified and
  tested for drift, tempo changes, and long sessions.
- Event scheduling MUST define timestamps, stable ordering, late-event policy, pause,
  reset, resume, and synchronization behavior. Shared playback MUST specify the authoritative
  transport and measurable inter-client skew limits; local rendering MUST remain insulated
  from ordinary network jitter. Sample-accurate claims require sample-domain evidence.
- The steady-state audio processing path MUST perform bounded work without blocking locks,
  file/network I/O, synchronous logging, world access, or application-controlled allocation.
  Prepare graphs and buffers outside that path; transfer changes at defined safe boundaries
  through bounded mechanisms. Plans MUST account for Java GC and backend limits and MUST
  NOT promise unmeasured hard real-time guarantees.
- Output MUST remain finite and within a documented ceiling, with conservative defaults,
  headroom, and a local master mute. Parameter changes and graph edits MUST avoid unintended
  clicks through suitable smoothing/transition policies. Aliasing, DC offset, clipping,
  denormals, and sample-rate changes MUST be addressed with measurable tests when relevant.
  Intentional synthesis distortion MUST remain separate from final output protection.

### VII. Bounded Work and Complete Lifecycles

- Every feature that consumes runtime resources MUST define and enforce budgets for its
  affected resources: modules, cables, voices, buffers, events, queued jobs, packet traffic,
  storage, and work per tick/audio block. Limits MUST cover each patch/player and aggregate
  world/client load. Exceeding a limit MUST have a deterministic, documented response.
- Game, render, and audio threads MUST NOT wait on slow I/O or unbounded computation.
  Background executors and queues MUST be bounded, with backpressure, cancellation,
  timeout, and shutdown policies. Overload MUST reduce or stop affected synthesis safely
  rather than stall Minecraft or create an unlimited catch-up backlog.
- Ownership and cleanup MUST cover block removal, cable changes, chunk/world unload,
  dimension changes, disconnect/reconnect, resource reload, and audio-device loss. No
  abandoned voices, listeners, tasks, native handles, or world references may survive their
  owner. Reload/unload cycles MUST be tested for leaks and stuck sound.
- Performance claims MUST include hardware, workload, duration, baseline, and measured
  tail latency, memory/allocation, and underruns as applicable. Numeric budgets and
  acceptance thresholds MUST be chosen before implementation and verified under load.

### VIII. Persistent Patches and Compatible Evolution

- Stable namespaced identifiers and explicitly versioned save, patch, module, and network
  schemas MUST preserve user compositions. Persist semantic musical state; do not persist
  device handles, thread identities, or transient object references as durable identities.
- Save/load and migrations MUST validate completely before replacing live state, preserve
  the original on failure, and use loader persistence or atomic replacement as appropriate.
  Migration tests MUST cover supported old versions, interruption/failure, and reapplication.
  Unknown modules or newer formats MUST be preserved safely or rejected without overwriting
  the source; silent data loss or silent retuning is prohibited.
- Breaking identifier, unit, default, timing, or DSP changes MUST include compatibility
  analysis, versioning, migration or explicit refusal, release notes, and a recovery/backup
  procedure. Bit-for-bit audio compatibility MUST NOT be claimed without evidence.
- Integrations MUST use supported extension APIs when available. Mixins, access changes,
  or internals MUST have a documented necessity, narrow scope, pinned compatibility, and
  regression tests. Optional integrations MUST tolerate absent dependencies; advertised
  modpack combinations and client/server protocol compatibility MUST be tested.

### IX. Reproducible Builds and Trusted Dependencies

- The repository MUST pin Minecraft, loader, mappings, Java toolchain, build wrapper,
  plugins, and dependencies. Resolved dependency versions and artifact integrity MUST be
  recorded and verified, including transitives. Dynamic versions and mutable snapshots
  MUST NOT be used in release inputs. Generated verification data MUST be reviewed against
  trusted upstream provenance; accepting new checksums blindly is prohibited.
- Dependencies MUST have a documented purpose, compatible license, provenance, maintenance
  assessment, and vulnerability review. Minimize runtime/native dependencies; native code
  MUST declare platform support, resource ownership, and failure handling. Do not redistribute
  Minecraft binaries or assets without permission.
- CI MUST run with minimum permissions and protect publishing credentials from untrusted
  contributions. Build steps and external actions MUST be pinned; release artifacts MUST
  come from a clean, identified revision using the documented build and checks.
- Releases MUST include dependency/license inventory, checksums, supported version matrix,
  changelog, and migration notes. Newly introduced high/critical vulnerabilities MUST block
  release unless a documented assessment demonstrates non-exposure. Reproducibility MUST
  be checked by rebuilding; byte-identical claims require matching artifact hashes.

### X. Player Control and Actionable Diagnostics

- Players MUST be able to stop local sound immediately and control local volume independently
  of server patch ownership. Local mute and Minecraft's documented sound-category/master
  controls MUST be respected. Remote patches MUST NOT override a player's audio preferences.
- Ports, parameter units/ranges, connections, and errors MUST be understandable through
  labels/tooltips and visible state, not sound or color alone. Controls MUST provide precise
  value entry or adjustment and keyboard access where the host UI permits it. Player-facing
  text MUST use localization resources.
- Failures and resource-limit rejections MUST identify the affected patch/module and offer
  a recovery action. Diagnostics MUST be bounded and rate limited; audio-path counters MUST
  be collected without blocking and reported elsewhere. Debugging MUST NOT require logging
  complete private patches or flooding game logs.
- Documentation MUST include a minimal playable patch, signal conventions, controls,
  resource limits, compatibility, and troubleshooting. Audible changes MUST be auditioned
  in Minecraft, including start/stop, edits, and overload recovery, before release acceptance.

## Engineering Constraints and Evidence

The project is a Java Minecraft mod whose primary purpose is musical modular synthesis.
This initial adoption does not select a loader, Minecraft release, Java release, build
plugin, or audio backend. The first implementation plan MUST record exact supported
versions and the selection rationale using version-matched official documentation.
Subsequent plans MUST preserve or explicitly revise that compatibility matrix.

Each plan MUST separate pure synthesis/transport/graph code from host adapters and state
which side/thread owns each mutable resource. It MUST specify affected trust boundaries,
schemas, compatibility behavior, resource budgets, and reproducible validation commands.
Numeric performance and musical tolerances MUST identify workload, hardware/backend,
sample rate/buffer size, measurement duration, and acceptable error; adjectives such as
"fast", "stable", and "musical" alone are not acceptance criteria.

Evidence MUST name the revision/artifact, environment, command or manual procedure, and
result. Unit tests, headless GameTests, dedicated-server tests, native client sessions,
multiplayer sessions, audio captures, human listening, performance runs, and hosted CI are
distinct evidence classes. One MUST NOT be used to claim another. Required checks use
PASS, FAIL, BLOCKED, or PENDING; BLOCKED/PENDING are not passes. N/A requires a written
scope rationale and MUST NOT waive an applicable invariant.

The following references inform these rules; version-specific APIs MUST be rechecked
against the selected target. These references do not mandate NeoForge, VCV compatibility,
or PortAudio as dependencies:

- [Oracle Java secure coding guidelines](https://www.oracle.com/java/technologies/javase/seccodeguide.html):
  input validation, bounded resource consumption, and defensive ownership.
- [NeoForge logical and physical sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/):
  host-side separation and dedicated-server class loading.
- [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
  and [dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html):
  resolved versions and independently reviewed artifact integrity.
- [VCV Rack signal conventions](https://vcvrack.com/manual/VoltageStandards):
  a reference for explicit pitch, gate, trigger, and signal contracts.
- [PortAudio callback guidance](https://portaudio.com/docs/v19-doxydocs/writing_a_callback.html):
  bounded processing without blocking operations in an audio callback.

## Development Workflow and Quality Gates

1. **Specify behavior.** Each feature MUST define player value, musical acceptance where
   applicable, security/negative cases, lifecycle and persistence expectations, and a
   mandatory TDD strategy. Documentation-only scope MUST be identified explicitly.
2. **Plan and review.** The Constitution Check MUST map all ten principles to concrete
   design decisions, planned evidence, or justified N/A. Check before research and again
   after design; research may resolve declared unknowns, but no behavior implementation
   may begin with unresolved compatibility, safety, timing, or budget decisions.
3. **Make tests fail for the right reason.** Tasks MUST place regression/contract tests and
   the observed red result before dependent production changes. Missing tooling, a syntax
   error, or an unrelated failure is not the required red evidence.
4. **Implement and refactor.** Make the tests pass, keep changes scoped, run relevant
   integration tests, and refactor without changing validated contracts. Security and
   lifecycle handling MUST be implemented with each feature, not deferred to final polish.
5. **Verify and review.** Before merge, formatting/static analysis, build/resource validation,
   unit tests, and affected integration suites MUST pass. Review MUST cover side/thread
   ownership, hostile input, bounds, save compatibility, licensing, and musical behavior.
   Record reviewer sign-off; a sole maintainer MUST record an explicit self-review.
6. **Qualify releases.** Test the packaged artifact in a dedicated server and native client
   on the advertised matrix. Shared musical behavior requires a server with at least two
   clients and join/leave/resynchronization evidence. Changes affecting audio require both
   objective measurements and human listening against reference patches; lifecycle/load
   changes require stress and repeated unload/reload checks. Document failures and recovery.
   A release MUST NOT claim support or acceptance for required evidence still pending.

Documentation changes MUST pass consistency, link, placeholder, and whitespace checks.
The first implementation MUST wire the executable gates into CI; this constitution itself
does not establish a working build, passing tests, or validated audio.

## Governance

This constitution is the project's authority for development practice. Specifications,
plans, tasks, reviews, and templates MUST comply with it. Generic scaffolding that calls
tests optional MUST NOT override Principle III; every behavior-changing specification
MUST explicitly request TDD so installed task-generation commands select that path.
Runtime commands, source layout, and selected technologies belong in the current plan,
as directed by the existing `AGENTS.md`.

Amendments MUST record the motivation, affected principles, compatibility/migration impact,
and maintainer approval. Update this document, its Sync Impact Report, dependent templates,
and affected active plans together. Constitution versions follow semantic versioning:
MAJOR for incompatible removals or redefinitions, MINOR for new or materially expanded
obligations, PATCH for non-semantic clarifications. Keep the original ratification date
and update the amendment date in ISO format for every amendment.

Exceptions MUST be explicit, narrowly scoped, and approved by a maintainer before affected
implementation proceeds, with rationale, compensating controls, evidence, owner, expiry,
and a tracked removal task. A justification alone is not approval. Exceptions MUST NOT
permit silent world/patch data loss, bypassed authorization/input bounds, unsafe unbounded
execution, or false validation claims. Such changes require a constitutional amendment
and a safe replacement design. Compliance MUST be reviewed for each feature and release;
outstanding deviations MUST be visible in the plan and release evidence.

**Version**: 1.0.0 | **Ratified**: 2026-11-03 | **Last Amended**: 2026-11-03
