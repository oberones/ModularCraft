#!/usr/bin/env python3
"""Verify distributable contents and authored resource coverage; no game client required."""
from pathlib import Path
import hashlib,json,zipfile
from release import checked_artifacts
root=Path(__file__).resolve().parents[1]
artifacts=checked_artifacts(root)
jar,sources,project=(artifacts[key] for key in ('jar','sources','project'))
assert all(p.is_file() for p in (jar,sources,project)), 'Build the JARs and full project archive first'
with zipfile.ZipFile(jar) as archive:
 names=archive.namelist()
 assert not any('/gametest/' in name or name.startswith(('net/minecraft/','org/lwjgl/','org/junit/')) for name in names)
 assert not any(name.startswith('data/modularcraft/structure/') for name in names)
 assert 'META-INF/neoforge.mods.toml' in names and 'modularcraft.mixins.json' in names
 for module in ('mixer','lfo','square_oscillator','oscillator','filter','envelope','speaker','audio_speaker','keyboard','euclidean_sequencer'):
  for path in (f'blockstates/{module}.json',f'models/block/{module}.json',f'models/item/{module}.json',f'textures/block/{module}_front.png'):
   assert f'assets/modularcraft/{path}' in names, path
  assert (root/f'art/{module}_front.svg').is_file()
 language=json.loads(archive.read('assets/modularcraft/lang/en_us.json'))
 for parameter in (*(f'mix_{c}_{p}' for c in ('1','2','3','4','master') for p in ('level','mute','solo') if c!='master' or p!='solo'),'amp_level','drone','lfo_frequency','lfo_wave','lfo_polarity','lfo_division','resonance_depth','attack_depth','decay_depth','sustain_depth','release_depth','pulse_width','frequency','level','cutoff','resonance','amount','attack','decay','sustain','release','volume','octave','detune','tempo',*(f'{c}_{p}' for c in 'abcd' for p in ('steps','hits','rotation','mute'))):
  assert 'parameter.modularcraft.'+parameter in language
 for port in (*(f'audio_in_{i}' for i in range(1,5)),'control_out','resonance_in','attack_in','decay_in','sustain_in','release_in','audio_out','audio_in','envelope_out','cutoff_in','gain_in','cv_out','pitch_in','gate_out','gate_in','clock_in','reset_in','clock_out','trigger_a','trigger_b','trigger_c','trigger_d'):
  assert 'port.modularcraft.'+port in language
 for name in names:
  if name.endswith('.class'):
   data=archive.read(name)
   assert int.from_bytes(data[6:8],'big')==65, f'Not Java 21: {name}'
with zipfile.ZipFile(project) as archive:
 names=archive.namelist()
 for path in ('Makefile','scripts/run_validation.py','scripts/release.py','.cz.toml','build.gradle','gradle/wrapper/gradle-wrapper.jar','gradle/verification-metadata.xml',
              'src/gametest/java/com/malevolentgods/modularcraft/gametest/ModuleGameTests.java',
              'validation/ledger.md','validation/mixer/results.json','validation/mixer/full.png','validation/licenses.json','validation/lfo/results.json','README.md'):
  assert path in names, path
 assert not any(name.startswith(('run/','build/','.git/','.gradle/')) for name in names)
checksums=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in (jar,sources,project))
(root/'build/libs/SHA256SUMS').write_text(checksums)
print('PASS: Java 21 artifacts, ten complete asset sets, no test fixtures or bundled host/runtime libraries.')
print(checksums,end='')
