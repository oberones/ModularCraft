# 0.1.0 development candidate

Initial NeoForge 1.21.1 implementation: four adjustable modules, visible typed cables,
owner/operator edits, persistent compositions, server-synchronized local synthesis,
manual/redstone linear ADSR, local mute/volume, and bounded existing-device OpenAL output.
Save and network schemas start at version 1. Speakers intentionally restart disabled;
manual gates and envelope progress are transient. Unknown data is preserved/quarantined.
No earlier public saves need migration. Back up worlds before installing development builds.

The initial PolyBLEP implementation failed the alias residual gate; the candidate adds
4x internal oscillator oversampling and a preallocated decimator while retaining a 48 kHz
public graph rate. The native queue was increased from 21.3 ms to the allowed 32 ms after
measured underruns. See the evidence ledger for outstanding acceptance and performance
findings. This candidate has not been publicly published or accepted as a release.

Added a comprehensive Makefile with built-JAR interactive/client/server launches,
packaged GameTests, isolated native smoke/load/recovery fixtures, checks, packaging,
and maintenance targets. Managed instances preserve manual worlds and replace only their
tracked mod JAR. CI now uses `make ci`; launcher isolation tests accompany the commands.
