"""Original 32-second stereo miniature, continuous rain and short interaction cues."""
from pathlib import Path
from array import array
import math,random,wave
R=Path(__file__).resolve().parents[1]/'core/src/main/resources/audio'; SR=22050
N=32*SR;left=array('f',[0])*N;right=array('f',[0])*N
# Original eight-bar phrase. Equal temperament; no recorded or quoted material.
chords=[(52,55,59),(48,52,55),(55,59,62),(50,57,62),(52,55,59),(48,55,60),(55,59,64),(50,57,62)]
melody=[[76,74,71,None,67,71,74,None],[72,None,71,67,64,None,67,71],[74,71,None,67,69,71,74,None],[69,None,66,69,74,None,71,69],[76,None,79,76,74,71,None,67],[72,71,67,None,64,67,72,None],[74,None,76,71,67,69,71,None],[69,66,None,62,66,69,71,None]]
def note(start,key,length,amp,pan,kind):
 f=440*2**((key-69)/12);begin=int(start*SR);count=int(length*SR)
 for j in range(count):
  t=j/SR;attack=min(1,t/.012);decay=math.exp(-t*(2.3 if kind=='key' else 1.3));release=min(1,max(0,(length-t)/.12))
  v=math.sin(2*math.pi*f*t)+(.24 if kind=='key' else .09)*math.sin(2*math.pi*f*2*t)+.08*math.sin(2*math.pi*f*3*t)
  v*=amp*attack*decay*release
  idx=(begin+j)%N;left[idx]+=v*(1-pan);right[idx]+=v*pan
  # Very quiet, fixed stereo reflections. The wrap makes the loop seamless.
  if kind=='key':
   echo=(idx+int(.19*SR))%N;left[echo]+=v*.07*pan;right[echo]+=v*.07*(1-pan)
for bar,chord in enumerate(chords):
 note(bar*4,chord[0]-12,3.4,.09,.5,'bass')
 for i,key in enumerate([chord[0],chord[1],chord[2],chord[1]]):note(bar*4+i+.25,key,2,.055,.38 if i%2 else .62,'key')
 for i,key in enumerate(melody[bar]):
  if key is not None:note(bar*4+i*.5,key,1.8,.10,.56,'key')
def write(name,l,r=None):
 data=array('h')
 if r is None:
  data.extend(int(max(-1,min(1,x))*32767) for x in l)
 else:
  for a,b in zip(l,r):data.append(int(max(-1,min(1,a))*32767));data.append(int(max(-1,min(1,b))*32767))
 with wave.open(str(R/(name+'.wav')),'wb') as w:w.setnchannels(2 if r is not None else 1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(data.tobytes())
write('evening',left,right)
# Stable rain bed: crossfade the seam without fading the whole ambience to silence.
def rain(seed):
 rng=random.Random(seed);n=8*SR;cross=2048;raw=array('f');low=0;deep=0
 for i in range(n+cross):
  low=.77*low+.23*rng.uniform(-1,1);deep=.98*deep+.02*rng.uniform(-1,1);raw.append(low*.16+deep*.14)
 result=array('f',raw[:n])
 for i in range(cross):t=i/cross;result[i]=raw[n+i]*(1-t)+raw[i]*t
 return result
write('rain',rain(51),rain(103))
for name,duration in [('door',.28),('success',.65)]:
 samples=[]
 for i in range(int(duration*SR)):
  t=i/SR
  if name=='door':
   v=math.sin(2*math.pi*(160+90*t)*t)*.11*math.exp(-t*18)*min(1,t*200)
   if t>.1:v+=math.sin(2*math.pi*540*(t-.1))*.025*math.exp(-(t-.1)*30)
  else:
   v=0
   for start,f in [(0,523.25),(.12,659.25),(.24,783.99)]:
    if t>=start:v+=math.sin(2*math.pi*f*(t-start))*.065*math.exp(-(t-start)*9)*min(1,(t-start)*200)
  samples.append(v*min(1,(duration-t)*50))
 write(name,samples)
print('Sound pass: stereo melody, seamless rain, door and completion cues.')
