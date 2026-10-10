"""Rebuild packaged sRGB 33-grid LUTs from pinned upstream cubes. Requires numpy and gh.
Sony JPEG is already tone-mapped: treating it as a linear-profile rendering is an
approximation, not recovery of sensor-linear light or exact Fujifilm rendering.
"""
import base64, concurrent.futures, hashlib, json, pathlib, struct, subprocess
import numpy as np
ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCES=[('abpy/FujifilmCameraProfiles','b5a1f16abfb2759b337c83d69d9dc49470e7e518','cube lut/','_sRGB.cube','abpy'),('pyrocat101/Fujifilm-LUTs','c687686384bbe6983c7d20d5ade21068d08dfbf8','output/AP0_display/','_33grid.cube','pyro')]
def api(path):return json.loads(subprocess.check_output(['gh','api',path]))
def read_cube(raw):
 n=None;lo=np.zeros(3);hi=np.ones(3);rows=[]
 for line in raw.decode().splitlines():
  t=line.split('#')[0].strip().split()
  if not t:continue
  if t[0]=='LUT_3D_SIZE':n=int(t[1])
  elif t[0]=='DOMAIN_MIN':lo=np.array(t[1:],float)
  elif t[0]=='DOMAIN_MAX':hi=np.array(t[1:],float)
  elif t[0][0] in '-+.0123456789':rows.append([float(v) for v in t])
 if n is None or len(rows)!=n**3:raise ValueError('Invalid cube')
 return np.array(rows).reshape(n,n,n,3),lo,hi

def sample(cube,rgb,lo,hi):
 p=np.clip((rgb-lo)/(hi-lo),0,1)*(len(cube)-1);a=np.floor(p).astype(int);b=np.minimum(a+1,len(cube)-1);f=p-a;out=np.zeros_like(rgb)
 for z in range(2):
  for y in range(2):
   for x in range(2):
    idx=np.where([x,y,z],b,a)
    w=np.prod(np.where([x,y,z],f,1-f),axis=1)
    out+=cube[idx[:,2],idx[:,1],idx[:,0]]*w[:,None]
 return out

def xyz(xy):x,y=xy;return np.array([x/y,1,(1-x-y)/y])
def rgb_matrix(primaries,white):
 m=np.column_stack([xyz(p) for p in primaries]);return m*np.linalg.solve(m,xyz(white))
# sRGB D65 -> linear ACES AP0 D60 with Bradford chromatic adaptation.
bradford=np.array([[.8951,.2664,-.1614],[-.7502,1.7135,.0367],[.0389,-.0685,1.0296]])
d65=(.3127,.3290);d60=(.32168,.33767)
cat=np.linalg.inv(bradford)@np.diag((bradford@xyz(d60))/(bradford@xyz(d65)))@bradford
matrix=np.linalg.inv(rgb_matrix([(.7347,.2653),(0,1),(.0001,-.077)],d60))@cat@rgb_matrix([(.64,.33),(.30,.60),(.15,.06)],d65)
n=33;z,y,x=np.meshgrid(np.arange(n),np.arange(n),np.arange(n),indexing='ij');grid=np.stack([x,y,z],-1).reshape(-1,3)/(n-1)
linear=np.where(grid<=.04045,grid/12.92,((grid+.055)/1.055)**2.4)
entries=[]
for repo,sha,prefix,suffix,group in SOURCES:
 tree=api(f'repos/{repo}/git/trees/{sha}?recursive=1')['tree']
 tasks=[t for t in tree if t['path'].startswith(prefix) and t['path'].endswith(suffix)]
 def convert(t):
  raw=base64.b64decode(api(f"repos/{repo}/git/blobs/{t['sha']}")['content']);cube,lo,hi=read_cube(raw)
  name=t['path'].split('/')[-1].removesuffix(suffix)
  if group=='pyro':name=name.removeprefix('AP0_to_')
  key=group+'_'+name.lower().replace(' ','_').replace('-','_').replace('.','')
  result=sample(cube,grid if group=='abpy' else linear@matrix.T,lo,hi)
  if group=='pyro':
   result=np.clip(result,0,1)**2.2
   result=np.where(result<=.0031308,12.92*result,1.055*result**(1/2.4)-.055)
  data=struct.pack('<I',n)+np.rint(np.clip(result,0,1)*255).astype('uint8').tobytes()
  out=ROOT/'app/src/main/assets/luts'/f'{key}.lut';out.write_bytes(data)
  print(key,flush=True)
  return dict(id=key,name=name,repo=repo,commit=sha,path=t['path'],source_sha256=hashlib.sha256(raw).hexdigest(),packed_sha256=hashlib.sha256(data).hexdigest())
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:entries.extend(pool.map(convert,tasks))
(ROOT/'app/src/main/assets/luts/manifest.json').write_text(json.dumps({'grid':n,'encoding':'uint8 sRGB output, R fastest, G middle, B slowest; little-endian grid header','input':'sRGB JPEG approximation; no Sony ISP tone-curve inversion','entries':entries},indent=2)+'\n')
print('Imported',len(entries),'LUTs')
