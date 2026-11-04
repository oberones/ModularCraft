#!/usr/bin/env python3
"""Query OSV for the public resolved Maven coordinates; no project source is transmitted."""
from pathlib import Path
import concurrent.futures,json,urllib.request
root=Path(__file__).resolve().parents[1]
rows=json.loads((root/'validation/dependencies.json').read_text())
packages=sorted({(r['group']+':'+r['name'],r['version']) for r in rows})
results=[]
for start in range(0,len(packages),100):
 batch=packages[start:start+100]
 body=json.dumps({'queries':[{'package':{'ecosystem':'Maven','name':name},'version':version} for name,version in batch]}).encode()
 request=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=body,headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(request,timeout=60) as response: answers=json.load(response)['results']
 for (name,version),answer in zip(batch,answers):results.append({'package':name,'version':version,'advisories':[v['id'] for v in answer.get('vulns',[])]})
ids=sorted({a for row in results for a in row['advisories']})
def detail(id):
 with urllib.request.urlopen('https://api.osv.dev/v1/vulns/'+id,timeout=30) as response:return json.load(response)
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: details=list(pool.map(detail,ids))
report={'source':'https://osv.dev/','packages':results,'advisories':details}
(root/'validation/vulnerability-scan.json').write_text(json.dumps(report,indent=2)+'\n')
print(len(packages),'packages queried;',len(ids),'unique advisories')
for row in results:
 if row['advisories']:print(row['package'],row['version'],','.join(row['advisories']))
