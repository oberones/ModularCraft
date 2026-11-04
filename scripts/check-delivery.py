#!/usr/bin/env python3
"""Verify distributable contents and authored resource coverage; no game client required."""
from pathlib import Path
import hashlib,json,zipfile
root=Path(__file__).resolve().parents[1]
jar=root/'build/libs/modularcraft-0.1.0.jar'
sources=root/'build/libs/modularcraft-0.1.0-sources.jar'
project=root/'build/libs/modularcraft-0.1.0-project.zip'
assert all(p.is_file() for p in (jar,sources,project)), 'Build the JARs and full project archive first'
with zipfile.ZipFile(jar) as archive:
 names=archive.namelist()
 assert not any('/gametest/' in name or name.startswith(('net/minecraft/','org/lwjgl/','org/junit/')) for name in names)
 assert not any(name.startswith('data/modularcraft/structure/') for name in names)
 assert 'META-INF/neoforge.mods.toml' in names and 'modularcraft.mixins.json' in names
 for module in ('oscillator','filter','envelope','speaker','keyboard'):
  for path in (f'blockstates/{module}.json',f'models/block/{module}.json',f'models/item/{module}.json',f'textures/block/{module}_front.png'):
   assert f'assets/modularcraft/{path}' in names, path
  assert (root/f'art/{module}_front.svg').is_file()
 language=json.loads(archive.read('assets/modularcraft/lang/en_us.json'))
 for parameter in ('frequency','level','cutoff','resonance','amount','attack','decay','sustain','release','volume','octave'):
  assert 'parameter.modularcraft.'+parameter in language
 for port in ('audio_out','audio_in','envelope_out','cutoff_in','gain_in','cv_out','pitch_in','gate_out','gate_in'):
  assert 'port.modularcraft.'+port in language
 for name in names:
  if name.endswith('.class'):
   data=archive.read(name)
   assert int.from_bytes(data[6:8],'big')==65, f'Not Java 21: {name}'
with zipfile.ZipFile(project) as archive:
 names=archive.namelist()
 for path in ('Makefile','scripts/run_validation.py','build.gradle','gradle/wrapper/gradle-wrapper.jar','gradle/verification-metadata.xml',
              'src/gametest/java/com/malevolentgods/modularcraft/gametest/ModuleGameTests.java',
              'validation/ledger.md','validation/licenses.json','README.md'):
  assert path in names, path
 assert not any(name.startswith(('run/','build/','.git/','.gradle/')) for name in names)
checksums=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in (jar,sources,project))
(root/'build/libs/SHA256SUMS').write_text(checksums)
print('PASS: Java 21 artifacts, five complete asset sets, no test fixtures or bundled host/runtime libraries.')
print(checksums,end='')
