# Dependency and license inventory

The installable JAR bundles only ModularCraft code and original assets (MIT).
Minecraft, NeoForge, LWJGL, OpenAL, JUnit, and build tools are **not** copied into it.
There are no runtime downloads, external services, codecs, FFmpeg, or additional
native libraries. Minecraft's installed LWJGL/OpenAL supplies playback.

| Component | Pin | Purpose | License / provenance |
|---|---|---|---|
| Minecraft | 1.21.1, official Mojang mappings | Host game | Mojang EULA; obtained by loader/build tooling, not redistributed |
| NeoForge | 21.1.234 | Host APIs and loader | LGPL-2.1; https://github.com/neoforged/NeoForge |
| Java | 21 | Runtime/toolchain | Installed separately; local Temurin 21.0.12.1+1, GPL-2.0 with Classpath Exception |
| ModDevGradle | 2.0.148 | NeoForge build integration | LGPL-2.1; https://github.com/neoforged/ModDevGradle |
| Gradle | 8.13 | Build wrapper | Apache-2.0; https://gradle.org/releases/ |
| JUnit Jupiter | 5.11.4 | Tests only | EPL-2.0; https://github.com/junit-team/junit5 |
| JUnit Platform launcher | 1.11.4 | Tests only | EPL-2.0; same upstream |
| Checkstyle | 10.21.2 | Formatting/basic correctness checks | LGPL-2.1; https://checkstyle.org/ |
| PMD | 7.10.0 | Static correctness/resource checks | BSD-style; https://pmd.github.io/ |
| google-java-format | 1.25.2 | Java formatting | Apache-2.0; https://github.com/google/google-java-format |

Resolved transitives and checksums are recorded in Gradle's dependency lock and
verification metadata. See `validation/ledger.md` for integrity review and
vulnerability-review status; pins alone are not a security assessment.

The full resolved binary-coordinate inventory is in
[`validation/dependencies.json`](validation/dependencies.json), with declared licenses
and inherited POM licenses in [`validation/licenses.json`](validation/licenses.json).
Missing upstream license declarations are explicitly marked rather than inferred.
No such dependency is redistributed in the mod or the project source archive.
The [security review](validation/security-review.md) records 35 OSV advisories across
143 resolved coordinates. Public-release security qualification remains open for the
pinned host matrix; artifact integrity is a separate, passing check.

Five client-only mixins expose the existing sound executor/library/channel handle,
close our resources before device destruction, and observe true singleplayer pause.
The 1.21.1 host stream implementation uses one-second buffers; using it unchanged
cannot meet the 32 ms PCM budget. The adapter instead reuses twelve native buffers on
one library channel. No world/server class references these mixins or OpenAL.
Compatibility with other audio mods has not been qualified.
