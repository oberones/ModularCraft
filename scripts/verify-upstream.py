#!/usr/bin/env python3
"""Compare Gradle integrity entries to official repositories, independently of Gradle's cache.
Only public Maven coordinates are sent; this does not transmit project source or secrets.
"""
from pathlib import Path
import concurrent.futures, hashlib, json, re, urllib.error, urllib.request, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
NS={'v':'https://schema.gradle.org/dependency-verification'}
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
CENTRAL='https://repo.maven.apache.org/maven2/'
NEOFORGE='https://maven.neoforged.net/releases/'
PLUGIN='https://plugins.gradle.org/m2/'
MOJANG='https://libraries.minecraft.net/'
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ModularCraft-integrity-review/1'}),timeout=12) as response:
  return response.read(128*1024*1024)
def check(job):
 group,name,version,filename,expected=job
 path=group.replace('.','/')+'/'+name+'/'+version+'/'+filename
 if name.endswith('.gradle.plugin') or group.startswith('gradle.plugin.'): roots=[PLUGIN,NEOFORGE,CENTRAL]
 elif name=='minecraft-dependencies': roots=['https://maven.neoforged.net/mojang-meta/',NEOFORGE]
 elif group.startswith(('net.neoforged','net.minecraftforge','cpw.mods','net.fabricmc','de.oceanlabs')): roots=[NEOFORGE,CENTRAL]
 elif group.startswith('com.mojang'): roots=[MOJANG,CENTRAL,NEOFORGE]
 elif group.endswith('.gradle.plugin') or group=='net.neoforged.moddev': roots=[PLUGIN,NEOFORGE,CENTRAL]
 else: roots=[CENTRAL,NEOFORGE,MOJANG]
 result={'coordinate':f'{group}:{name}:{version}','artifact':filename,'sha256':expected,'status':'UNVERIFIED'}
 for root in roots:
  url=root+path
  try:
   upstream=get(url+'.sha256').decode().strip().split()[0].lower()
   if re.fullmatch('[0-9a-f]{64}',upstream):
    result.update(status='PASS' if upstream==expected else 'FAIL',url=url+'.sha256',method='upstream SHA-256');return result
  except (urllib.error.URLError,UnicodeError,IndexError,TimeoutError): pass
  try:
   upstream=get(url+'.sha1').decode().strip().split()[0].lower()
   local=list((CACHE/group/name/version).glob('*/'+filename))
   if re.fullmatch('[0-9a-f]{40}',upstream) and local:
    content=local[0].read_bytes()
    result.update(status='PASS' if hashlib.sha1(content).hexdigest()==upstream and hashlib.sha256(content).hexdigest()==expected else 'FAIL',url=url+'.sha1',method='upstream SHA-1 plus local SHA-256');return result
  except (urllib.error.URLError,UnicodeError,IndexError,TimeoutError): pass
  try:
   content=get(url)
   result.update(status='PASS' if hashlib.sha256(content).hexdigest()==expected else 'FAIL',url=url,method='independent HTTPS artifact SHA-256');return result
  except (urllib.error.URLError,TimeoutError): pass
 return result
jobs=[]
for component in ET.parse(ROOT/'gradle/verification-metadata.xml').findall('.//v:component',NS):
 for artifact in component.findall('v:artifact',NS):
  checksum=artifact.find('v:sha256',NS)
  if checksum is not None:jobs.append((component.get('group'),component.get('name'),component.get('version'),artifact.get('name'),checksum.get('value')))
previous_path=ROOT/'validation/upstream-integrity.json'
previous=json.loads(previous_path.read_text())['artifacts'] if previous_path.exists() else []
verified={(r['coordinate'],r['artifact'],r['sha256']):r for r in previous if r['status']=='PASS'}
pending=[job for job in jobs if (':'.join(job[:3]),job[3],job[4]) not in verified]
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
 fresh=list(pool.map(check,pending))
results=[verified[(':'.join(job[:3]),job[3],job[4])] for job in jobs if (':'.join(job[:3]),job[3],job[4]) in verified]+fresh
report={'artifacts':results,'counts':{status:sum(row['status']==status for row in results) for status in ('PASS','FAIL','UNVERIFIED')}}
(ROOT/'validation/upstream-integrity.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report['counts']))
for row in results:
 if row['status']!='PASS':print(row['status'],row['coordinate'],row['artifact'])
raise SystemExit(0 if all(row['status']=='PASS' for row in results) else 1)
