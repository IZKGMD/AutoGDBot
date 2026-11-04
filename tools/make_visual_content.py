"""Physical foreground decorations, kept in sync with make_visuals.py sprites."""
from pathlib import Path
import json
p=Path(__file__).resolve().parents[1]/'core/src/main/resources/data/world.json'
d=json.loads(p.read_text())
def prop(id,art,x,y,foot):return dict(id='v4_'+id,label='',action='decoration',x=x,y=y+foot[1]+foot[3],art=art,artX=x,artY=y,solid=[x+foot[0],y+foot[1],foot[2],foot[3]])
for m in d['maps']:
 m['objects']=[o for o in m['objects'] if not o['id'].startswith('v4_')]
 if m['id']=='cafe':m['objects'].append(prop('books','bookcase',342,104,[0,39,30,14]))
 if m['id']=='diner':m['objects'].append(prop('fridge','fridge',342,110,[0,29,28,14]))
 if m['id']=='workshop':
  for i,(x,y) in enumerate([(113,193),(305,191),(335,207)]):m['objects'].append(prop('crt_'+str(i),'crt_spare',x,y,[0,14,24,10]))
p.write_text(json.dumps(d,ensure_ascii=False,indent=2))
print('Visual content: five collidable, depth-sorted furnishing props.')
