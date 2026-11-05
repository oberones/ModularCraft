#!/usr/bin/env python3
"""Author original instrument art as editable SVG and deterministic 64px PNG. No external assets."""
from pathlib import Path
import json, math, struct, zlib
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/modularcraft'
ART=ROOT/'art'
ART.mkdir(exist_ok=True)
FONT={
'Q':['010','101','101','111','011'],'A':['010','101','111','101','101'],'D':['110','101','101','101','110'],'S':['011','100','010','001','110'],
'R':['110','101','110','101','101'],'L':['100','100','100','100','111'],'P':['110','101','110','100','100'],
'F':['111','100','110','100','100'],'M':['101','111','111','101','101'],'W':['101','101','111','111','101'],
'O':['010','101','101','101','010'],'I':['111','010','010','010','111'],'C':['011','100','100','100','011'],
'G':['011','100','101','101','011'],'V':['101','101','101','101','010'],'E':['111','100','110','100','111'],
'N':['101','111','111','111','101'],'T':['111','010','010','010','010'],'U':['101','101','101','101','111'],
'K':['101','110','100','110','101'],'B':['110','101','110','101','110'],'Y':['101','101','010','010','010'],
'0':['111','101','101','101','111'],'1':['010','110','010','010','111'],'2':['110','001','010','100','111'],
'4':['101','101','111','001','001'],'8':['111','101','111','101','111'],' ':['000']*5,
}
class Drawing:
 def __init__(self):
  self.pixels=bytearray(64*64*4);self.svg=['<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" shape-rendering="crispEdges">']
 def rect(self,x,y,w,h,color):
  c=tuple(bytes.fromhex(color.lstrip('#')))
  self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{color}"/>')
  for py in range(max(0,y),min(64,y+h)):
   for px in range(max(0,x),min(64,x+w)):
    i=(py*64+px)*4;self.pixels[i:i+4]=bytes((*c,255))
 def circle(self,cx,cy,r,color):
  for y in range(cy-r,cy+r+1):
   for x in range(cx-r,cx+r+1):
    if (x-cx)**2+(y-cy)**2<=r*r:self.rect(x,y,1,1,color)
 def text(self,text,x,y,color,scale=1):
  for char in text:
   for yy,row in enumerate(FONT[char]):
    for xx,bit in enumerate(row):
     if bit=='1':self.rect(x+xx*scale,y+yy*scale,scale,scale,color)
   x+=4*scale
 def save(self,name,folder='block'):
  target=ASSETS/f'textures/{folder}/{name}.png';target.parent.mkdir(parents=True,exist_ok=True)
  def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
  data=b''.join(b'\0'+self.pixels[y*256:(y+1)*256] for y in range(64))
  target.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data,9))+chunk(b'IEND',b''))
  (ART/f'{name}.svg').write_text('\n'.join(self.svg+['</svg>'])+'\n')
def panel():
 d=Drawing();d.rect(0,0,64,64,'#151b23');d.rect(2,2,60,60,'#34434e');d.rect(4,4,56,56,'#202d37')
 for x,y in [(5,5),(58,5),(5,58),(58,58)]:d.circle(x,y,1,'#b1b9b8');d.rect(x,y,1,1,'#46515b')
 return d
MODULES=[('lfo','LFO','#e5a1e8',['CO','CI']),('square_oscillator','SQR','#65d7e5',['AO','PI']),('oscillator','SAW','#65d7e5',['AO','PI']),('filter','LPF','#91c79a',['AI','CI','AO','RC']),('envelope','ADSR','#ffc36a',['CO','GT','A','D','S','R']),('speaker','AMP','#d69bbd',['AI','GI']),('keyboard','KEYS','#b3a3ef',['CV','GO']),('euclidean_sequencer','EUCLID','#65d7e5',['CI','RI','CO','A','B','C','D'])]
for name,title,accent,ports in MODULES:
 d=panel();d.rect(9,8,46,2,accent);d.text(title,20 if name=='euclidean_sequencer' else 12,12 if name=='euclidean_sequencer' else 13,accent,1 if name=='euclidean_sequencer' else 2)
 if name in ('oscillator','square_oscillator'):
  d.rect(9,27,27,17,'#101820')
  for x in range(11,34):
   if name=='oscillator':d.rect(x,41-(x-11)%8,1,1,accent)
   else:
    d.rect(x,32 if (x-11)%12<6 else 40,1,1,accent)
    if (x-11)%6==0:d.rect(x,32,1,9,accent)
  d.circle(46,35,8,'#0e141b');d.circle(46,35,6,'#667b83');d.rect(45,29,2,6,'#e4edea')
 elif name=='lfo':
  d.rect(9,27,46,17,'#101820')
  for x in range(11,53):d.rect(x,35-round(6*math.sin((x-11)*math.pi/20)),1,2,accent)
 elif name=='filter':
  for x in range(11,53):d.rect(x,30+int(10/(1+math.exp(-(x-34)/3))),1,1,accent)
  for x in [17,32,47]:d.circle(x,42,4,'#0e141b');d.rect(x,38,1,4,'#d9e4dc')
 elif name=='envelope':
  points=[(10,43),(19,28),(31,36),(44,36),(54,43)]
  for (x1,y1),(x2,y2) in zip(points,points[1:]):
   for x in range(x1,x2+1):d.rect(x,round(y1+(y2-y1)*(x-x1)/(x2-x1)),1,2,accent)
 elif name=='euclidean_sequencer':
  for channel,(color,hits) in enumerate(zip(['#65d7e5','#ffc36a','#91c79a','#d69bbd'],[4,5,7,9])):
   cx=20 if channel%2==0 else 44;cy=24 if channel<2 else 39
   d.text('ABCD'[channel],cx-1,cy-2,color)
   for step in range(16):
    angle=-math.pi/2+step*2*math.pi/16
    x=round(cx+6*math.cos(angle));y=round(cy+6*math.sin(angle))
    d.rect(x,y,1,1,color if step*hits%16<hits else '#53636d')
 elif name=='keyboard':
  for i in range(7):d.rect(10+i*6,28,5,16,'#e4edea')
  for i in (1,2,4,5,6):d.rect(8+i*6,28,4,9,'#0e141b')
 else:
  d.circle(32,36,12,'#0c121a');d.circle(32,36,10,'#4d5b65');d.circle(32,36,7,'#1a252d');d.circle(32,36,3,'#566a76')
  for y in range(28,46,3):d.rect(22,y,21,1,'#0e161c')
 for i,port in enumerate(ports):
  x=round((i+.5)*64/len(ports));d.text(port,x-3,47,'#d8e4e6');d.circle(x,56,4,accent);d.circle(x,56,2,'#080c11')
 d.save(name+'_front')
 model={'parent':'minecraft:block/cube','textures':{'particle':'modularcraft:block/chassis','down':'modularcraft:block/chassis','up':'modularcraft:block/top','north':f'modularcraft:block/{name}_front','south':'modularcraft:block/chassis','east':'modularcraft:block/chassis','west':'modularcraft:block/chassis'}}
 for sub,obj in [(f'models/block/{name}.json',model),(f'models/item/{name}.json',{'parent':f'modularcraft:block/{name}'}),(f'blockstates/{name}.json',{'variants':{f'facing={face}':{'model':f'modularcraft:block/{name}','y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)]}})]:
  p=ASSETS/sub;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,indent=2)+'\n')
 loot=ROOT/f'src/main/resources/data/modularcraft/loot_table/blocks/{name}.json';loot.parent.mkdir(parents=True,exist_ok=True)
 loot.write_text(json.dumps({'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'modularcraft:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},indent=2)+'\n')
d=panel()
for y in range(12,54,6):d.rect(12,y,40,2,'#0f1720');d.rect(12,y+2,40,1,'#465963')
d.save('chassis')
d=panel();d.rect(10,15,44,3,'#657782');d.text('MODULAR',10,27,'#91a7ad');d.text('CRAFT',16,37,'#91a7ad');d.save('top')
d=Drawing()
for i in range(34):d.rect(12+i,48-i,7,7,'#111b25');d.rect(14+i,49-i,3,4,'#65d7e5')
d.rect(39,10,12,14,'#84999f');d.rect(43,4,4,11,'#e2c88c');d.rect(6,45,15,12,'#84999f');d.save('patching_tool','item')
p=ASSETS/'models/item/patching_tool.json';p.write_text(json.dumps({'parent':'minecraft:item/handheld','textures':{'layer0':'modularcraft:item/patching_tool'}},indent=2)+'\n')
lang={'block.modularcraft.square_oscillator':'Square Oscillator','tab.modularcraft':'ModularCraft Instruments','block.modularcraft.keyboard':'Keyboard','block.modularcraft.oscillator':'Saw Oscillator','block.modularcraft.filter':'Low-Pass Filter','block.modularcraft.envelope':'Envelope Generator','block.modularcraft.speaker':'Amplifier / Speaker','item.modularcraft.patching_tool':'Patching Tool','key.modularcraft.mute':'Mute ModularCraft','key.modularcraft.settings':'ModularCraft Audio Settings','key.categories.modularcraft':'ModularCraft','modularcraft.local_audio':'ModularCraft Audio','modularcraft.local_volume':'Local volume: %s%%','modularcraft.volume_help':'Minecraft Master + Records also apply','modularcraft.apply':'Apply','modularcraft.enable':'Enable speaker','modularcraft.disable':'Disable speaker','modularcraft.hold_gate':'Hold Gate (or Space)','modularcraft.mute':'Mute locally','modularcraft.unmute':'Unmute locally','modularcraft.recover':'Resynchronize audio','modularcraft.selected':'Selected %s at %s, %s, %s. Choose an input.','modularcraft.tool_help':'Ports are on the lower front face. Right-click air to cancel.','modularcraft.port_hint':'%s · Right-click to patch; Shift-right-click an input to remove.','modularcraft.gain_warning':'Removing Gain In makes connected audio a sustained drone.'}
for key,label in {'pulse_width':'PWM (pulse width)','frequency':'Frequency','detune':'Detune (unison spread)','level':'Level','cutoff':'Cutoff','resonance':'Resonance','amount':'Cutoff CV Amount','attack':'Attack','decay':'Decay','sustain':'Sustain','release':'Release','volume':'Volume','octave':'Octave'}.items():lang['parameter.modularcraft.'+key]=label
for key,label in {'audio_out':'AO → Audio Out','audio_in':'AI ← Audio In','envelope_out':'CO → Envelope Out','cutoff_in':'CI ← Cutoff Control In','gain_in':'GI ← Gain Control In','cv_out':'CV → Pitch CV Out','pitch_in':'PI ← Pitch CV In','gate_out':'GO → Gate Out','gate_in':'GT ← Gate In'}.items():lang['port.modularcraft.'+key]=label
lang.update({'modularcraft.note.letter': 'Note: %s ▾', 'modularcraft.note.octave': 'Octave: %s ▾', 'modularcraft.note.set': 'Set Note', 'modularcraft.note.preview': 'Selected: %s = %s Hz', 'modularcraft.note.range': 'Below 20 Hz. Choose E0 or higher.', 'modularcraft.note.help': 'Sets manual frequency (A4 = 440 Hz). Connected Pitch In controls the sounding pitch.', 'modularcraft.note.menu_help': 'Up/Down selects; Home/End jumps; Enter confirms; Escape cancels. Tab moves to the next control.'})
lang.update({'modularcraft.octave_down':'Octave −','modularcraft.octave_up':'Octave +',
'modularcraft.octave':'Octave %s','modularcraft.keyboard_state':'Note %s · Gate %s',
'modularcraft.keyboard_help':'Piano: A W S E D F T G Y H U J. Left/right changes octave. Tab selects controls.'})
lang.update({
'modularcraft.midi.off':'Off',
'modularcraft.midi.all':'All',
'modularcraft.midi.device':'MIDI: %s',
'modularcraft.midi.port':'MIDI: %s (%s/%s)',
'modularcraft.midi.channel':'Channel: %s',
'modularcraft.midi.refresh':'Refresh',
'modularcraft.midi.release':'Release Notes'})
lang['block.modularcraft.euclidean_sequencer']='Euclidean Sequencer'
lang['parameter.modularcraft.tempo']='Tempo'
for channel in 'abcd':
 for control,label in [('steps','Steps'),('hits','Hits'),('rotation','Rotation'),('mute','Mute')]:
  lang[f'parameter.modularcraft.{channel}_{control}']=f'{channel.upper()} {label}'
for port,label in {'clock_in':'CI ← Clock In (rising edge)','reset_in':'RI ← Reset In (rising edge)','clock_out':'CO → Clock Out','trigger_a':'A → Trigger A','trigger_b':'B → Trigger B','trigger_c':'C → Trigger C','trigger_d':'D → Trigger D'}.items():
 lang['port.modularcraft.'+port]=label
for key,label in {'start':'Start / Resume','stop':'Stop','reset':'Reset','mute':'Mute channel','unmute':'Unmute channel','set':'Set','up':'↑','down':'↓','bpm':'BPM','steps':'Steps','hits':'Hits','rotation':'Rotate','pending':'Next clock…','stopped':'Stopped','external':'External clock','internal':'Internal · 1/16','channel':'Channel %s','range':'%s: %s–%s (hits/rotation limited by Steps)','fast_clock':'Clock above 50 Hz ignored. Slow the source.','legend':'Filled: hit · Hollow: rest · White: playhead'}.items():
 lang['modularcraft.euclidean.'+key]=label
lang['modularcraft.scroll_help']='PgUp / PgDn'
lang['block.modularcraft.lfo']='Low-Frequency Oscillator'
for key,label in {'lfo_wave':'Waveform', 'lfo_frequency':'Frequency (free-running)', 'lfo_polarity':'Output polarity', 'lfo_division':'Clock cycle length', 'resonance_depth':'Resonance CV depth', 'attack_depth':'Attack CV depth', 'decay_depth':'Decay CV depth', 'sustain_depth':'Sustain CV depth', 'release_depth':'Release CV depth'}.items():
 lang['parameter.modularcraft.'+key]=label
for key,label in {'control_out':'CO → LFO Control Out (−1…1 / 0…1)', 'resonance_in':'RC ← Resonance CV In', 'attack_in':'A ← Attack CV In', 'decay_in':'D ← Decay CV In', 'sustain_in':'S ← Sustain CV In', 'release_in':'R ← Release CV In'}.items():
 lang['port.modularcraft.'+key]=label
for key,values in {'lfo_wave':['Sine','Triangle','Rising saw','Falling saw','Square'], 'lfo_polarity':['Bipolar −1…+1','Unipolar 0…1'], 'lfo_division':['1/16 note · 1 pulse','1/8 note · 2 pulses','Quarter note · 4 pulses','Half note · 8 pulses','Whole note · 16 pulses','Two bars (4/4) · 32 pulses','Four bars (4/4) · 64 pulses']}.items():
 for index,label in enumerate(values):lang[f'modularcraft.lfo.{key}.{index}']=label
errors={
'lfo_free':'Free-running · Hz controls the rate.',
'lfo_sync':'Clock synchronized · Hz is saved for disconnection.',
'lfo_waiting':'Waiting for clock · phase held; start the sequencer.',
'lfo_checkpoint':'Invalid LFO state. Resynchronize audio.',
'sequencer_pattern':'Hits must be 0–Steps; rotation must be 0–Steps−1.',
'sequencer_overload':'Control timeline overload: reduce modulation/clock load, then restart sequencers and enable speakers.',
'keyboard_unavailable':'Keyboard unavailable. Move within six blocks and reopen its panel.',
'midi_off':'MIDI off. Select an input device to play.',
'midi_ready':'MIDI connected. Exact pitch; sustain pedal supported.',
'midi_connecting':'Connecting MIDI input...',
'midi_paused':'MIDI paused. Return to the keyboard panel to play.',
'midi_missing':'MIDI device missing. Connect it, then Refresh or select an input.',
'midi_ambiguous':'Several MIDI inputs match. Select the intended device again.',
'midi_unavailable':'MIDI input unavailable. Check the device, then Refresh.',
'midi_timeout':'MIDI timed out. Refresh; if still unavailable, restart Minecraft.',
'keyboard_busy':'Keyboard in use. Wait until the other player releases it.',
'keyboard_overload':'Keyboard input overloaded. Close and reopen the panel.',
'pulse_pitch_connected':'CV sets pitch; PWM sets pulse width.',
'pitch_connected':'CV sets pitch; Detune adds unison spread.',
'permission':'Only the owner or an operator within six blocks may edit. Move closer or ask the owner.',
'range':'Use a finite value in the displayed range; stay within six blocks.',
'quarantined':'Stored data is quarantined. Back up the world and restore it with a compatible version.',
'world_capacity':'World limit: 256 modules / 512 cables. Remove an unused module or cable.',
'player_modules':'Owner limit: 64 modules. Remove an unused module.',
'player_cables':'Owner limit: 128 cables. Remove an unused cable.',
'patch_modules':'Patch limit: 16 modules. Split the patch first.',
'patch_cables':'Patch limit: 24 cables. Remove a cable first.',
'patch_speakers':'Patch limit: two speakers. Disconnect a speaker first.',
'active_patches':'World limit: four active patches. Disable another patch first.',
'active_speakers':'World limit: eight enabled speakers. Disable one first.',
'patch_unloaded':'Patch suspended: load all chunks, restart sequencers, and enable speakers.',
'unloaded':'Endpoint unavailable. Load both modules and try again.',
'disabled':'Speaker disabled. Enable it to play; restart and chunk unload disable speakers.',
'missing_audio':'No audio input. Connect Oscillator or Filter Audio Out to Audio In.',
'released':'Envelope released. Hold Gate or apply redstone power.',
'ready':'Ready. Master, Records, local volume and distance affect what you hear.',
'awaiting':'Waiting for the server to accept this value.',
'stale':'This module changed. Review refreshed values and retry.',
'occupied_input':'Input already connected. Shift-right-click that input to remove its cable first.',
'incompatible_ports':'Match audio, control, or pitch ports. Gate/trigger/clock outputs connect to Gate, Clock, or Reset inputs.',
'different_owners':'Both cable endpoints must have the same owner, including operator edits.',
'cycle':'Feedback is unsupported. Remove the connection that would form a loop.',
'self_connection':'Connect two different modules.',
'duplicate_cable':'That cable already exists. Choose a free input.',
'fanout':'Output limit: four destinations. Disconnect one first.',
'cable_distance':'Cable limit: 16 blocks between centers. Move the modules closer.',
'dimension':'Both endpoints must be in the same dimension.',
'missing_cable':'That input has no cable to remove.',
'remove_at_input':'Shift-right-click the destination input to remove one cable.',
'select_output':'Select an output on the source module first.',
'cancelled':'Cable selection cancelled. Select an output to start again.',
'connected':'Cable connected. Select another output to continue.',
'removed':'Cable removed. Other connections are unchanged.',
'gain_disconnected':'Gain cable removed: connected audio now plays continuously.',
'gate_eligibility':'Open the envelope panel nearby to hold its manual gate.',
'missing_module':'Module no longer exists. Select its replacement explicitly.',
'not_speaker':'Only Amplifier / Speaker modules can be enabled.',
'parameter':'This control does not belong to this module.',
'parameters':'Invalid control set. Refresh the module panel.',
'action':'Unknown action. Reconnect with a compatible mod version.',
'invalid':'Edit rejected. Refresh accepted state and try again.',
'identity_conflict':'Conflicting saved identity. Back up and restore the affected module.',
'duplicate_identity':'Duplicate saved identity. Restore the original module from backup.',
'occupied_position':'A module already owns this position. Inspect or remove it first.',
'encoded_size':'Storage limit reached. Back up and remove unused compositions.',
'record_count':'Storage record limit reached. Recover quarantined records from backup.',
'muted':'Local audio muted. Unmute here or use your configured mute key.',
'overload':'Local playback overloaded and was silenced. Reduce load, then resynchronize.',
'device_recovery':'Audio device unavailable. Select a working Minecraft device, then resynchronize.',
'resync':'Synchronizing current sound. Missed notes will not be replayed.',
'revision_gap':'Patch state incomplete. Requesting a current snapshot.',
'invalid_snapshot':'Invalid patch snapshot. Reconnect using compatible mod versions.',
'reloading':'Resources reloading. Playback will resume at the current musical position.',
'settings_read':'Could not read local settings. Check the config file permissions.',
'settings_write':'Could not save local settings. Check the config directory permissions.',
 'throttled':'Edit rate limited: 20/s per player, 200/s per world. Wait and retry refreshed values.'}
lang.update({'modularcraft.error.'+k:v for k,v in errors.items()})
p=ASSETS/'lang/en_us.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n')
print('Wrote eight complete block sets, tool, localization, and editable SVG sources.')
