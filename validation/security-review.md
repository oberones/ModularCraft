# Dependency security review

Reviewed on 2026-11-04 UTC (2026-11-03 local), for this uncommitted development candidate.
No public release is authorized or security-qualified. The reviewed Minecraft 1.21.1 /
NeoForge 21.1.218 matrix was pinned; replacing host libraries inside a mod would
change that matrix and requires independent compatibility qualification.

## NeoForge 21.1.234 update (2026-11-05 local)

The requested loader upgrade pins NeoForge 21.1.234 and its MixinExtras 0.5.3
compile dependency; other resolved component versions are unchanged. Reviewed the
official NeoForge module metadata and POM plus the MixinExtras POM: their declared
licenses remain LGPL 2.1 and MIT respectively. These are upstream patch updates to
existing host dependencies, with no new library or bundled binary introduced.

The refreshed OSV scan still covers 143 coordinates and reports the same 35 unique
advisory IDs. Neither upgraded coordinate has an OSV advisory match. The prior
exposure assessment below remains provisional; this is not release security approval.
All 501 recorded verification entries now have passing upstream integrity evidence,
including seven new artifact hashes and retained historical version entries.

## Evidence and scope (original review)

`scripts/audit-dependencies.py` queried the public OSV API for 143 resolved Maven binary
coordinates; it transmitted only public package names and versions. The complete results,
advisory text, affected ranges, severities, fixed versions, and upstream references are
retained in [vulnerability-scan.json](vulnerability-scan.json). There were **35 unique
advisories**. This is a dependency match, not proof that each vulnerable path is reachable.
OSV is a time-dependent database; rerun before release. Build plugins and the JDK need
their own ongoing vendor review; this Maven scan does not cover all embedded binaries.

All 478 Gradle verification entries separately match official repository checksums or
independently fetched artifacts over HTTPS. [upstream-integrity.json](upstream-integrity.json)
records each origin and method. Integrity verification does not remove vulnerabilities.
The Gradle 8.13 wrapper JAR also matches its published SHA-256.

## Exposure assessment

| Dependency / affected pins | Reachability in ModularCraft | Assessment |
|---|---|---|
| Guava 32.1.2-jre (host), 32.1.3-jre (formatter), 33.4.0-jre (Checkstyle) | Advisory requires Java object deserialization; mod codecs use explicit bounded data streams, never ObjectInputStream | No mod-created deserialization exposure identified; host review remains open |
| BeanUtils 1.10.0 (Checkstyle) | Build-only reflective configuration; checked-in local configuration, no player-controlled bean expressions | No game runtime exposure; do not run untrusted build/configuration inputs |
| Netty 4.1.97.Final | Host transport. Mod uses NeoForge payloads, no TLS/SNI handlers, Bzip2 decoder, LZ4 decoder, or Unix descriptor passing | Mod does not add the named handlers. Full host pipeline and other-mod exposure remain unqualified, including critical SNI advisory |
| PMD 7.10.0 | Build-only static analyzer; configured Gradle XML/HTML reports, no PMD Designer or VBHTML/YAHTML renderer | Named vulnerable rendering paths not enabled |
| Commons Lang 3.14.0 (host), 3.17.0 (PMD), 3.8.1 (Checkstyle) | Mod does not call ClassUtils.getClass on untrusted recursive type strings | No direct mod exposure; inherited host/tool usage not exhaustively audited |
| HttpClient/Core 5.1.3 | Checkstyle/PMD dependencies; no added runtime HTTP client/server | No player network exposure; build-side remote responses remain a toolchain risk |
| Log4j API/Core 2.22.1 | Minecraft logging. Mod adds no socket/TLS/XML/RFC5424 appender or MapMessage serialization | Named configurations not introduced by mod; operator-selected logging configurations need review |
| Plexus Utils 3.3.0 | Host/tool dependency; mod does not invoke archive extraction | No mod-provided archive/import surface; loader/tool archive handling review remains open |
| JLine reader 3.20.0 | Host console; mod never sets HISTORY_IGNORE | Named configuration not introduced by mod |
| LZ4 Java 1.8.0 | Host dependency. Mod has no LZ4 import or compressed custom format | Native extraction, range-validation, and decompression advisories require host review; no blanket non-exposure claim |

This is a source/configuration self-review, not a penetration test or a whole-host
security audit. **Public-release security gate: BLOCKED pending host exposure review or
a separately approved and qualified patched matrix.** No high/critical advisory is
silently waived. The local candidate remains available for controlled development testing.

## License and maintenance review

[dependencies.json](dependencies.json) maps every binary to its Gradle configuration and
SHA-256. [licenses.json](licenses.json) records licenses declared by those pinned POMs,
including inherited parents. Thirteen coordinates had no resolvable POM license
declaration; those are explicitly marked for upstream review. A missing declaration is
not assigned an invented license. Minecraft proprietary components are host-supplied.
No dependency binaries are bundled in any ModularCraft deliverable; only the standard
Apache-2.0 Gradle wrapper is included in the full project archive.

NeoForge/Gradle/JUnit/Checkstyle/PMD/formatter are established upstream projects; their
selected versions are reproducible snapshots, not claims of latest security maintenance.
Retain the game/loader pins for this requested MVP and reevaluate this inventory whenever
the host or tools change. Authored Java, artwork, model definitions, and documentation
are MIT; asset provenance is in [art/PROVENANCE.md](../art/PROVENANCE.md).
