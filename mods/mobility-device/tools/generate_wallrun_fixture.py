"""Independent NumPy evaluation of the artist's files for the Java runtime integration test."""
import json
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]

def rotation(values):
    x,y,z=np.radians(values)
    cx,cy,cz=np.cos([x,y,z]);sx,sy,sz=np.sin([x,y,z])
    r=np.eye(4)
    r[:3,:3]=[[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],
              [sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]]
    return r

def channel(frames,kind,t):
    keys=sorted([k for k in frames if k['channel']==kind],key=lambda k:k['time'])
    if not keys:return np.zeros(3)
    b=max([k for k in keys if k['time']<=t],key=lambda k:k['time'],default=keys[0])
    a=min([k for k in keys if k['time']>=t],key=lambda k:k['time'],default=keys[-1])
    bv=np.array([float(b['data_points'][0][s]) for s in 'xyz'])
    av=np.array([float(a['data_points'][0][s]) for s in 'xyz'])
    f=(t-b['time'])/(a['time']-b['time']) if a['time']>b['time'] else 0
    return bv+(av-bv)*f

result={}
for side in ('left','right'):
    m=json.loads((ROOT/f'src/main/resources/assets/mobility_device/models/player/wall_{side}.bbmodel').read_text('utf-8'))
    groups={g['uuid']:g for g in m['groups']};elements={e['uuid']:e for e in m['elements']}
    animators=m['animations'][0]['animators']
    samples=[]
    for t in (0,.1,.25,.4,.65,.79):
        cubes={}
        def visit(node,parent,origin):
            g=groups[node['uuid']];o=np.array(g['origin']);keys=animators[g['uuid']]['keyframes']
            tr=np.eye(4);tr[:3,3]=o-origin+channel(keys,'position',t)
            mat=parent@tr@rotation(np.array(g['rotation'])+channel(keys,'rotation',t))
            for child in node['children']:
                if isinstance(child,dict):visit(child,mat,o)
                else:
                    e=elements[child];pivot=np.array(e['origin']);r=rotation(e.get('rotation',[0,0,0]))[:3,:3]
                    vertices=[]
                    for x in (e['from'][0],e['to'][0]):
                        for y in (e['from'][1],e['to'][1]):
                            for z in (e['from'][2],e['to'][2]):
                                p=r@(np.array([x,y,z])-pivot)+pivot-o
                                p=mat@np.r_[p,1]
                                vertices.append([float(p[0]),float(24-p[1]),float(p[2])])
                    cubes[child]=vertices
        for n in m['outliner']:visit(n,np.eye(4),np.zeros(3))
        samples.append({'seconds':t,'cubes':cubes})
    result[side]=samples
out=ROOT/'src/gametest/resources/wallrun_expected_vertices.json'
out.write_text(json.dumps(result,separators=(',',':')),'utf-8')
print(out)
