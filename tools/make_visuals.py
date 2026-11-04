"""Original two-density scene art. Coordinates are gameplay units; never upscale old PNGs.
All primitives clip to their surface. Static geometry matches world.json exactly.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math, random, json
ROOT=Path(__file__).resolve().parents[1]/'core/src/main/resources'
OUT=ROOT/'art/hd'; OUT.mkdir(parents=True,exist_ok=True)
S=2
class Canvas:
 def __init__(self,w,h,bg=None):
  self.w=w;self.h=h;self.im=Image.new('RGBA',(w*S,h*S),bg or (0,0,0,0));self.d=ImageDraw.Draw(self.im)
 def box(self,b,c):
  box=tuple(round(v*S) for v in b)
  if isinstance(c,str) and len(c)==9:
   overlay=Image.new('RGBA',self.im.size);ImageDraw.Draw(overlay).rectangle(box,fill=c);self.im.alpha_composite(overlay);self.d=ImageDraw.Draw(self.im)
  else:self.d.rectangle(box,fill=c)
 def line(self,p,c,w=.5):self.d.line(tuple(round(v*S) for v in p),fill=c,width=max(1,round(w*S)))
 def poly(self,p,c):self.d.polygon([(round(x*S),round(y*S)) for x,y in p],fill=c)
 def oval(self,b,c):self.d.ellipse(tuple(round(v*S) for v in b),fill=c)
 def save(self,n):self.im.save(OUT/(n+'.png'))
 def label(self,t,x,y,size=7,c='#ecd8b2'):
  f=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',round(size*S));self.d.text((x*S,y*S),t,font=f,fill=c)
 def grain(self,b,seed=17,n=400):
  rng=random.Random(seed);x0,y0,x1,y1=b
  for _ in range(n):
   x=rng.uniform(x0,x1-.5);y=rng.uniform(y0,y1-.5)
   self.box((x,y,x+.5,y+.5),rng.choice(['#879180','#717e78','#959986']))
INK='#142b35';VOID='#0e1a24'
def window(c,x,y,w,h,lit=True):
 c.box((x-3,y-3,x+w+3,y+h+4),'#253f47');c.box((x-1,y-1,x+w+1,y+h+2),'#b4aa90')
 c.box((x,y,x+w,y+h),'#294859');c.box((x+2,y+2,x+w-2,y+h-2),'#d3aa70' if lit else '#5e8792')
 c.box((x+4,y+3,x+w/2-2,y+h-3),'#efd09a' if lit else '#85a9ac')
 c.poly([(x+4,y+3),(x+w-3,y+3),(x+4,y+h-3)],'#e7c58c' if lit else '#a2b9b4')
 c.box((x+w/2-1,y,x+w/2+1,y+h),'#536568');c.line((x,y+h/2,x+w,y+h/2),'#667878')
 c.box((x-4,y+h+2,x+w+4,y+h+5),'#c6b99b');c.line((x-3,y+h+2,x+w+3,y+h+2),'#e8d7b2')
 c.box((x+3,y+1,x+6,y+h-2),'#8e947d');c.line((x+4,y+2,x+4,y+h-3),'#b9b49a')
def floor(c,b,kind):
 x0,y0,x1,y1=b
 c.box(b,'#817b68' if kind=='wood' else '#566c70' if kind=='tile' else '#656f69')
 rng=random.Random(14)
 step=8 if kind=='wood' else 16
 for y in range(y0,y1,step):
  for x in range(x0,x1,32 if kind=='wood' else 16):
   xx=min(x+(31 if kind=='wood' else 15),x1);yy=min(y+step-1,y1)
   col=rng.choice(['#967d60','#8b765c','#a08a69','#8e7e65']) if kind=='wood' else rng.choice(['#70837c','#6a7a75','#75867c','#687d79'])
   c.box((x,y,xx,yy),col);c.line((x+.5,y+.5,xx,y+.5),'#a69b7c' if kind=='wood' else '#87958a')
   if kind=='wood':
    for k in range(2):
     a=rng.uniform(x+2,max(x+2,xx-5)); c.line((a,y+2+k*2,min(a+8,xx),y+2+k*2),'#786b56')
 c.box((x0,y0,x1,y0+3),'#263e4090');c.box((x0,y0,x0+3,y1),'#263e4060')
def room_shell(c,b,wall,kind):
 x0,y0,x1,y1=b
 c.box((x0-7,y0-7,x1+7,y1+7),INK);c.box((x0-5,y0-5,x1+5,y1+5),'#394b4d')
 floor(c,(x0,y0+32,x1,y1),kind)
 c.box((x0,y0,x1,y0+31),wall);c.box((x0,y0+26,x1,y0+31),'#556a65')
 c.line((x0,y0,x1,y0),'#d4c8a6');c.line((x0,y0+25,x1,y0+25),'#b4ad90')
 c.box((x0-5,y0,x0-1,y1),'#6a7c72');c.line((x0-1,y0,x0-1,y1),'#a0aa91')
 c.box((x1+1,y0,x1+5,y1),'#263e43')
 c.box((x0,y1+1,x1,y1+5),'#283f43');c.line((x0,y1,x1,y1),'#a09a7f')
def threshold(c,x,y,w=32):
 c.box((x-w/2,y-2,x+w/2,y+5),'#233b43');c.box((x-w/2+1,y-1,x+w/2-1,y+2),'#b2a087');c.line((x-w/2+1,y-1,x+w/2-1,y-1),'#e6cf9f')
def shelf(c,x,y,w=30):
 c.box((x,y,x+w,y+13),'#34464a');c.box((x,y+12,x+w,y+14),'#b2966e')
 for k in range(5):
  a=x+2+k*5;c.box((a,y+2,a+3,y+11),['#a77959','#879e8c','#c6b48f','#597e84','#a1a78c'][k]);c.line((a+.5,y+3,a+.5,y+10),'#d7c19c')
# ROOM: a small but inhabited cutaway, with the same walkable 192 x 160 footprint.
c=Canvas(480,320,VOID);room_shell(c,(144,64,335,255),'#9b9f8b','cheap')
window(c,230,65,34,23,False)
c.box((225,64,229,93),'#55777c');c.line((226,65,226,92),'#9aaca2');c.box((267,64,272,93),'#55777c');c.line((269,65,269,92),'#9aaca2')
c.line((222,61,276,61),'#b5a58a',1)
shelf(c,176,71,32)
c.box((284,69,301,88),'#36474b');c.box((286,71,299,86),'#d6caa5');c.label('04',288,72,7,'#53696a')
c.line((153,90,153,145,171,145),'#354b4e');c.box((150,83,155,91),'#c8baa0');c.box((152,86,153,88),'#46595a')
# Local wear, not large mismatched squares.
for x,y in [(151,167),(294,197),(238,225)]:
 c.line((x,y,x+7,y+1,x+10,y),'#536a64');c.line((x+2,y+2,x+9,y+2),'#91a08a')
c.box((218,235,242,246),'#536c69')
for y in range(237,246,2):c.line((219,y,241,y),'#879684')
threshold(c,230,254);c.save('room')
# Public interiors: differentiated materials, wall decoration and properly bounded floors.
for name,wall,kind in [('cafe','#6f9188','wood'),('diner','#bcae8e','tile'),('workshop','#778c8a','cheap')]:
 c=Canvas(480,320,VOID);room_shell(c,(96,48,383,263),wall,kind)
 window(c,117,51,43,23,True);window(c,312,51,43,23,False);threshold(c,240,262)
 if name=='cafe':
  for x in [188,208,228]:
   c.box((x,53,x+14,72),'#253d44');c.box((x+1.5,54.5,x+12.5,70.5),'#d9c6a1');c.poly([(x+3,68),(x+8,59),(x+11,68)],'#64887e');c.oval((x+3,57,x+6,60),'#c69a70')
  c.box((103,194,181,231),'#526e66')
  for y in [197,228]:c.line((106,y,178,y),'#b8a886')
  for x in range(110,177,8):c.poly([(x,205),(x+3,211),(x,218),(x-3,211)],'#739181')
  # The standing bookcase is a depth-sorted, collidable prop.
  c.box((262,54,291,70),'#2b454c');c.label('COFFEE',266,57,5.5)
 elif name=='diner':
  c.box((177,51,268,75),'#223d46');c.line((179,52,266,52),'#aa977b');c.label('СУП ДНЯ  /  220 ₽',183,56,6)
  c.label('горячий обед · с собой',183,65,4.5,'#99b4a5')
  # The refrigerator is a depth-sorted, collidable prop.
  c.line((287,56,295,56),'#697e75');c.box((289,57,294,73),'#d7bf8d')
 else:
  c.box((180,52,282,75),'#3d5a5e')
  for x in range(185,279,4):
   for y in range(55,73,4):c.box((x,y,x+.5,y+.5),'#7f9c8c')
  for x in range(192,275,12):c.line((x,56,x,68),'#c1ba9d',1);c.box((x-2,63,x+2,67),'#79918c')
  # Keep the window unobstructed; tools occupy the central wall board.
  # Spare monitors are separate props with physical footprints.
 c.save(name)
# Staircase, rail and steps correspond to the blocked lower-right rectangle.
c=Canvas(480,320,VOID);room_shell(c,(97,32,383,271),'#80918a','tile')
for x in [144,285]:
 c.box((x-4,37,x+36,78),'#263b43');c.box((x,39,x+32,76),'#8f785e');c.box((x+2,41,x+30,58),'#a18a69');c.box((x+3,61,x+29,73),'#786850');c.box((x+26,60,x+28,62),'#d8b983')
 c.label('04' if x==144 else '05',x+11,45,6)
threshold(c,160,77)
c.box((256,139,377,248),'#203842')
for y in range(145,243,10):
 c.box((261,y,373,y+7),'#76867d');c.line((262,y,372,y),'#b2b29a');c.box((261,y+8,373,y+9),'#40595d')
c.line((257,135,377,135,377,248),'#b6b399',1.5)
for y in range(141,247,20):c.line((258,y,258,y+8),'#a3aba0',1)
c.box((106,109,133,143),'#2c4248');c.box((108,111,131,141),'#aa9b7a')
for x,y in [(110,114),(121,115),(112,129)]:c.box((x,y,x+8,y+9),'#dfd0a9');c.line((x+1,y+3,x+6,y+3),'#8b917f')
threshold(c,246,270);c.save('stair')
# Street facades, roof cornices, isolated signs and actual pavement. No text over windows.
c=Canvas(960,512,'#20343e')
c.box((16,221,943,464),'#647c7d');rng=random.Random(2604)
for y in range(223,465,14):
 for x in range(16,944,28):
  shift=14 if (y//14)%2 else 0; a=max(16,x-shift);b=min(943,x-shift+27)
  c.box((a,y,b,min(y+12,464)),rng.choice(['#677f7e','#60797b','#6c8280','#5c7679']));c.line((a,y,b,y),'#859891')
  if rng.random()<.17:c.line((a+3,y+4,min(a+10,b),y+4),'#83978e')
c.box((16,323,943,382),'#344e59')
for x in range(26,944,38):c.line((x,352,min(x+17,943),352),'#7e8c85',1)
for y in [322,382]:c.line((16,y,943,y),'#b0ac90',1)
for x0,x1,wall,door in [(65,288,'#8e9385',174),(295,392,'#547e7b',355),(399,608,'#8e7669',470),(615,858,'#a29277',814)]:
 c.box((x0-4,57,x1+4,220),'#243c45');c.box((x0,62,x1,212),wall)
 c.box((x0-3,59,x1+3,68),'#c1b395');c.line((x0-2,60,x1+2,60),'#ecdbb5');c.box((x0,69,x1,73),'#324e4f')
 c.box((x0,209,x1,219),'#405957');c.line((x0,210,x1,210),'#adb39b')
 for y in [80,119]:
  for x in range(x0+13,x1-31,52):window(c,x,y,29,24,(x//52)%2==0)
 # Ground-floor display is separate from signage and doorway.
 if x1-x0>120:
  window(c,x0+12,157,37,39,False)
  if door<x1-65:window(c,x1-52,157,37,39,True)
 c.box((door-18,170,door+18,219),'#233b44');c.box((door-15,173,door+15,216),'#806e58');c.box((door-12,176,door+12,198),'#b4b291' if door!=174 else '#6e6455')
 c.line((door-13,174,door+13,174),'#c6ac83');c.box((door+9,203,door+11,205),'#e4c891');threshold(c,door,218)
 # Pipes anchor building volumes without intersecting the path.
 c.line((x1-3,74,x1-3,207),'#475e60',1.5);c.line((x1-2,74,x1-2,206),'#a0a995',.5)
 for y in [110,151,192]:c.line((x1-5,y,x1,y),'#667b74')
for x in [38,300,555,879]:
 c.box((x,246,x+37,283),'#2a454c');c.box((x+2,248,x+35,281),'#a2a98f');c.box((x+4,250,x+33,279),'#446b5e')
 for k in range(10):
  a=x+6+rng.randrange(24);b=252+rng.randrange(24);c.oval((a-2,b-2,a+2,b+2),'#688b70');c.box((a,b,a+.5,b+.5),'#c2af82')
# Deliberate wet patches and broken reflected window strokes, confined to pavement.
for x,y,w in [(185,302,52),(480,304,61),(730,398,65),(324,417,40)]:
 c.poly([(x,y),(x+w*.7,y-2),(x+w,y+3),(x+w-8,y+9),(x+4,y+6)],'#456773')
 for k in range(4):c.line((x+8+k*3,y+k*2,x+w-13-k*4,y+k*2),'#799d9a' if k%2 else '#acaa87')
# Park gate: clear gap where the actual door is.
for x in list(range(865,890,6))+list(range(915,940,6)):
 c.line((x,426,x,464),'#1c3742',1);c.oval((x-1,425,x+1,427),'#d2bd92')
c.line((865,435,889,435),'#98a18c');c.line((915,435,940,435),'#98a18c')
for x in [889,915]:c.box((x,426,x+3,464),'#9ba58e')
c.box((895,444,912,464),'#9ca38a')
c.save('street')
# Park: layered planting, pond banks, stone paths and deliberately quieter walking areas.
c=Canvas(480,320,VOID);c.box((24,56,455,263),'#426c5a');rng=random.Random(2026)
for _ in range(1500):
 x=rng.uniform(25,454);y=rng.uniform(57,262)
 c.line((x,y,x+1,y-1),rng.choice(['#507b61','#5d8665','#375f52','#70946a']))
for b in [(216,56,275,263),(24,174,455,204)]:
 c.box(b,'#9a9e83');x0,y0,x1,y1=b;c.line((x0,y0,x1,y0),'#c9c4a2');c.line((x0,y1,x1,y1),'#677f6a')
 for y in range(y0+2,y1,9):
  for x in range(x0+2,x1,21):c.line((x,y,min(x+16,x1-1),y),'#aeb092');c.line((x,y,x,min(y+6,y1-1)),'#808f77')
c.poly([(89,91),(105,81),(167,81),(190,100),(191,144),(169,164),(105,164),(88,148)],'#294f4e')
c.poly([(93,93),(108,85),(165,85),(186,102),(187,141),(166,160),(108,160),(93,145)],'#b5b79a')
c.poly([(97,96),(111,90),(162,90),(181,105),(181,139),(162,155),(111,155),(97,142)],'#315c6b')
c.poly([(99,99),(114,94),(160,94),(178,107),(176,136),(160,150),(113,151),(100,140)],'#52838a')
for x,y in [(106,105),(135,116),(112,139),(152,136)]:c.line((x,y,x+18,y),'#a8c4b3');c.line((x+3,y+2,x+21,y+2),'#6e9d99')
for x,y in [(38,76),(398,74),(42,218),(393,230),(189,67)]:
 c.oval((x-8,y-7,x+20,y+12),'#284f44');c.oval((x-6,y-10,x+16,y+7),'#537e5d');c.oval((x-2,y-10,x+9,y-3),'#87a275')
 for k in range(4):c.oval((x+k*3,y-2,x+k*3+1.5,y-.5),'#d2b184')
threshold(c,246,262,50);c.save('park')
# Furniture drawn at native 2x detail, with all silhouettes inside the existing sprite frame.
sizes={p.stem:Image.open(p).size for p in (ROOT/'art').glob('*.png')}
for name in ['desk','desk_ssd','bed','wardrobe','boxes','boxes_open','bench','counter','table','mailbox','mailbox_signed','notice','lamp','chair_upgrade']:
 w,h=sizes[name];c=Canvas(w,h)
 if name in ['desk','desk_ssd']:
  c.box((4,25,7,39),'#354c4e');c.box((55,25,58,39),'#354c4e');c.line((5,27,5,38),'#9a9c81');c.box((1,14,62,27),'#433f37');c.box((1,13,62,23),'#a2845e');c.line((2,13,61,13),'#ddbc89');c.line((3,21,60,21),'#6c6149')
  for x in [5,24,44]:c.line((x,17,x+12,17),'#8e7654')
  c.box((16,1,43,19),'#233d47');c.box((17,2,42,17),'#7a9086');c.box((19,4,40,15),'#163b48')
  for y in [6,8.5,11,13]:c.line((21,y,35-(y%3)*2,y),'#8bc0a8',.5)
  c.box((27,19,33,21),'#8a9b87');c.box((17,23,40,26),'#344f55')
  for x in range(19,39,3):c.line((x,23.5,x,25),'#9aa791')
  c.box((48,5,60,22),'#334c52');c.box((49,6,59,20),'#6c837b')
  for y in [8,10,12]:c.line((51,y,57,y),'#2e4e55')
  c.oval((55,17,57,19),'#f0c678' if name=='desk' else '#a3e3b8');c.box((5,9,10,17),'#dcd0ac');c.oval((5,8,10,11),'#725443');c.line((11,11,13,11,13,15,11,15),'#e1d0a5')
  if name=='desk_ssd':c.box((44,19,48,24),'#345762');c.line((45,20,47,20),'#9ecab5')
 elif name=='bed':
  c.box((2,7,45,68),'#3b4140');c.box((3,1,44,13),'#87674d');c.line((4,2,43,2),'#bd9770');c.box((5,13,42,61),'#d3c7a4');c.box((8,15,39,27),'#ece0bf');c.line((10,17,37,17),'#fff1d1');c.line((9,26,37,26),'#b0ad93')
  c.box((5,29,42,62),'#527e83');c.box((5,29,42,32),'#8ba89a')
  for y in range(34,62,4):c.line((6,y,41,y),'#638c8d')
  c.poly([(7,34),(13,41),(11,55),(8,60)],'#3e666e');c.poly([(35,34),(31,45),(35,59),(39,61)],'#69918d');c.box((31,53,38,59),'#b2b399');c.line((32,54,37,58),'#728c83');c.box((2,64,45,68),'#927253');c.line((3,65,44,65),'#c3a17b');c.box((3,69,7,71),'#334b4e');c.box((40,69,44,71),'#334b4e')
 elif name=='wardrobe':
  c.box((1,2,w-2,h-1),'#253c41');c.box((3,3,w-4,h-3),'#896749');c.line((4,4,w-5,4),'#c19b6d');c.line((w/2,5,w/2,h-5),'#423f34')
  for x in [5,w/2+2]:c.box((x,7,x+9,h-12),'#a57d55');c.line((x+1,8,x+1,h-13),'#bc956a')
  c.box((w/2-3,h/2,w/2-2,h/2+3),'#ddbd80');c.box((w/2+2,h/2,w/2+3,h/2+3),'#ddbd80')
 elif name in ['boxes','boxes_open']:
  for x,y,ww,hh in [(1,17,24,24),(16,2,22,25)]:
   xx=min(w-1,x+ww);yy=min(h-1,y+hh);c.box((x,y,xx,yy),'#4c4e40');c.box((x+1,y+1,xx-1,yy-1),'#b68c5d');c.line((x+2,y+2,xx-2,y+2),'#dec092');c.box((x+ww/2-2,y+1,x+ww/2+2,yy-1),'#d5b681');c.box((x+3,y+hh/2,x+8,y+hh/2+4),'#8a6547')
  if name=='boxes_open':c.poly([(1,18),(8,12),(24,17),(17,22)],'#e0bd86')
 elif name=='bench':
  for x in [8,w-11]:c.box((x,19,x+3,h-2),'#29454c');c.line((x+.5,20,x+.5,h-3),'#68877b')
  for y in [4,10,16,22]:c.box((1,y,w-2,y+4),'#8e7455');c.line((2,y,w-3,y),'#d2af7c');c.line((3,y+3,w-4,y+3),'#695d48')
 elif name=='counter':
  c.box((2,12,85,37),'#314447');c.box((3,18,84,34),'#8a6a4e')
  for x in [6,32,58]:c.box((x,21,x+22,32),'#a57f59');c.line((x+1,22,x+21,22),'#c09d72')
  c.box((1,10,86,18),'#dac6a2');c.line((2,11,85,11),'#f5e6bf');c.line((2,18,85,18),'#665f4d')
  c.box((10,1,26,10),'#2e4a50');c.box((12,3,24,8),'#709c8e');c.line((14,5,21,5),'#c8d9b1')
  c.oval((42,7,57,11),'#efdfbb');c.oval((45,5,54,9),'#c29a65');c.box((65,3,79,10),'#4e726f');c.line((66,4,78,4),'#c4c8aa')
 elif name=='table':
  c.box((2,15,6,30),'#6b604a');c.box((26,15,30,30),'#6b604a');c.box((8,7,24,32),'#3a4845');c.box((7,4,25,26),'#a38a64');c.line((8,5,24,5),'#e2c696');c.box((10,8,22,23),'#d0c3a0');c.oval((13,12,20,18),'#eae0bb');c.oval((15,13,18,16),'#654d38');c.line((11,9,21,9),'#f1ddb7')
 elif name in ['mailbox','mailbox_signed']:
  c.box((w/2-1,24,w/2+2,h-1),'#38555a');c.box((3,3,w-4,29),'#223e4a');c.box((5,4,w-6,27),'#6a9694');c.line((5,5,w-6,5),'#b3b8a0');c.box((7,8,w-8,11),'#1c3945');c.box((7,17,w-8,24),'#d0c8a1');c.line((9,19,w-10,19),'#7b8e7b')
 elif name=='notice':
  c.box((5,8,8,h-1),'#3c4a40');c.box((w-9,8,w-6,h-1),'#3c4a40');c.box((2,3,w-3,h-8),'#536052');c.box((4,5,w-5,h-10),'#aa8a60');c.line((3,4,w-4,4),'#dbb983')
  for x,y in [(7,8),(25,7),(11,24)]:
   c.box((x,y,min(x+15,w-6),min(y+13,h-11)),'#e1cfa5')
   for yy in [y+3,y+6,y+9]:c.line((x+2,yy,min(x+11,w-7),yy),'#887c5f')
 elif name=='lamp':
  c.box((w/2-1,8,w/2+2,h-3),'#293f46');c.line((w/2,10,w/2,h-5),'#6f8780');c.box((w/2-5,h-4,w/2+6,h-1),'#273e44');c.box((2,3,w-3,17),'#243b43');c.box((4,5,w-5,14),'#e8bd7a');c.box((5,6,w-6,12),'#ffdda0');c.line((2,3,w-3,3),'#8b9c87')
 elif name=='chair_upgrade':
  c.box((2,1,13,12),'#203c47');c.box((3,2,12,10),'#56827e');c.line((4,3,11,3),'#a5b7a1');c.box((3,13,12,15),'#6b8f82');c.box((7,16,8,19),'#a2ada0')
 c.save(name)
# Tree silhouette has branches and clusters rather than a flat stamped canopy.
w,h=sizes['tree'];c=Canvas(w,h);rng=random.Random(64)
c.box((29,38,36,h-2),'#594e3a');c.line((31,40,31,h-3),'#a58c61',1);c.line((32,49,21,29),'#766345',2);c.line((34,43,45,23),'#766345',2)
for x,y,rr in [(29,18,15),(16,28,14),(44,30,15),(28,37,17),(43,16,11)]:
 c.oval((x-rr,y-rr,x+rr,y+rr),'#213f3c');c.oval((x-rr+2,y-rr+1,x+rr-2,y+rr-3),'#3e6d57');c.oval((x-rr+3,y-rr+2,x+rr-7,y+rr-10),'#648b65')
 for k in range(18):
  a=x+rng.uniform(-rr*.65,rr*.65);b=y+rng.uniform(-rr*.65,rr*.65);c.line((a,b,a+2,b),rng.choice(['#87a477','#527d5e','#315d4e']))
c.save('tree')
# Smooth light fields, intentionally transparent at every texture edge.
for name,rgb,power in [('light_amber',(255,182,92),74),('light_window',(255,220,156),52),('light_monitor',(104,214,212),60),('shadow',(8,26,31),115)]:
 w,h=(160,112) if name!='shadow' else (64,32);im=Image.new('RGBA',(w*2,h*2));p=im.load()
 for y in range(h*2):
  for x in range(w*2):
   radius=((x-w)/w)**2+((y-h)/h)**2;p[x,y]=(*rgb,round(max(0,1-radius)**2*power))
 im.save(OUT/(name+'.png'))
# Smooth high density UI fonts, with logical advances identical to the old layout.
fout=ROOT/'fonts/hd';fout.mkdir(parents=True,exist_ok=True)
chars=''.join(dict.fromkeys(''.join(chr(i) for i in range(32,127))+''.join(chr(i) for i in range(0x400,0x460))+'—–…«»№•·×→←↑↓✓₽−'))
for name,size,face in [('ui',11,'DejaVuSans.ttf'),('small',9,'DejaVuSans.ttf'),('title',27,'DejaVuSansMono-Bold.ttf')]:
 path='/usr/share/fonts/truetype/dejavu/'+face;font=ImageFont.truetype(path,size*4);metric=ImageFont.truetype(path,size);cell=(size+(5 if name=='title' else 8))*4;cols=16;im=Image.new('RGBA',(cols*cell,math.ceil(len(chars)/cols)*cell));d=ImageDraw.Draw(im);rows=[]
 for i,ch in enumerate(chars):
  x=i%cols*cell;y=i//cols*cell;d.text((x,y),ch,font=font,fill='white');bx,by,ex,ey=font.getbbox(ch)
  rows.append(f'char id={ord(ch)} x={x+bx} y={y+by} width={ex-bx} height={ey-by} xoffset={bx} yoffset={by} xadvance={round(metric.getlength(ch))*4} page=0 chnl=15')
 im.save(fout/(name+'.png'));(fout/(name+'.fnt')).write_text(f'info face="DejaVu HD" size={size*4} padding=0,0,0,0 spacing=0,0\ncommon lineHeight={(size+3)*4} base={size*4} scaleW={im.width} scaleH={im.height} pages=1 packed=0\npage id=0 file="{name}.png"\nchars count={len(rows)}\n'+'\n'.join(rows),encoding='utf-8')
print('Visual pass: 2x authored scenes/furniture, bounded light fields, 4x UI fonts.')
# Native 32×48 animation frames: clothing panels, half-pixel faces and directional silhouettes.
people={
 'hero':('#b58b52','#443c34','#d7b394'), 'hero_1':('#5e8591','#443c34','#d7b394'), 'hero_2':('#a36655','#443c34','#d7b394'),
 'mira':('#83a58e','#74503b','#dbb695'), 'asya':('#b47a52','#8c4a30','#e0b794'),
 'nika':('#657e9f','#2c3442','#cba68e'), 'tamara':('#a7ac8c','#706553','#d4b193'),
 'arkady':('#728a82','#76796c','#c7a58b'), 'lev':('#aa8274','#574b40','#d3af91')}
def lighten(hex,n):
 rgb=tuple(int(hex[i:i+2],16) for i in [1,3,5]);return tuple(max(0,min(255,v+n)) for v in rgb)+(255,)
for name,(coat,hair,skin) in people.items():
 sheet=Image.new('RGBA',(128,192))
 for direction in range(4):
  for frame in range(4):
   c=Canvas(16,24);look={0:0,1:2,2:3,3:1}[direction];swing=[0,1,0,-1][frame];dy=.5 if frame%2 else 0
   # Shoes remain inside their own cell at every frame. Footprint is unchanged.
   c.poly([(4.5,16),(7.5,16),(7,21+swing*.5),(3.5,22+swing*.5),(3.5,20+swing*.5)],'#273f4b')
   c.poly([(8.5,16),(11.5,16),(12,21-swing*.5),(8.5,22-swing*.5),(8,20-swing*.5)],'#344f59')
   c.line((4,22+swing*.5,7,22+swing*.5),'#aaada0');c.line((9,22-swing*.5,12,22-swing*.5),'#aaada0')
   c.poly([(4,10+dy),(6,9+dy),(10,9+dy),(12,11+dy),(12,17+dy),(4,17+dy)],'#233d47')
   c.poly([(4.5,11+dy),(7,10+dy),(10.5,10+dy),(11,16.5+dy),(5,16.5+dy)],coat)
   c.poly([(5,11+dy),(7,10+dy),(7,16+dy),(5,16+dy)],lighten(coat,18))
   c.line((8,11+dy,8,16+dy),lighten(coat,-25));c.line((5.5,15+dy,6.5,15+dy),lighten(coat,-30))
   c.poly([(3.5,11+dy),(5,11+dy),(4.5,16+swing*.5),(3,16+swing*.5)],lighten(coat,-10));c.box((3,16+swing*.5,4.5,17+swing*.5),skin)
   c.poly([(11,11+dy),(12.5,11+dy),(13,16-swing*.5),(11.5,16-swing*.5)],lighten(coat,-20));c.box((11.5,16-swing*.5,13,17-swing*.5),skin)
   c.box((6.5,8+dy,9.5,10+dy),lighten(skin,-20))
   c.poly([(4,3+dy),(6,1.5+dy),(10,1.5+dy),(12,4+dy),(11.5,8+dy),(9.5,10+dy),(6,9.5+dy),(4.5,7+dy)],hair)
   if look != 1:
    faceX=.5 if look==3 else -.5 if look==2 else 0
    c.poly([(5+faceX,4+dy),(10+faceX,3.5+dy),(11+faceX,6+dy),(10+faceX,9+dy),(7+faceX,9.5+dy),(5+faceX,7.5+dy)],skin)
    c.line((6+faceX,4+dy,9+faceX,4+dy),lighten(skin,22));c.box((6+faceX,5.5+dy,6.5+faceX,6+dy),'#263e47')
    if look==0:c.box((9.5,5.5+dy,10,6+dy),'#263e47')
    c.line((7+faceX,8+dy,9+faceX,8+dy),lighten(skin,-30))
   c.poly([(4,3+dy),(6,1.5+dy),(10,1.5+dy),(12,3.5+dy),(10,5+dy),(9,3.5+dy),(5,5+dy),(4,7+dy)],hair)
   c.line((5,3+dy,8,2+dy,10,2.5+dy),lighten(hair,30))
   if name=='mira':c.line((4.5,6+dy,4.5,10+dy,5,12+dy),hair,1.5);c.line((5,11+dy,6,11+dy),'#dbc38b')
   if name=='asya':
    c.box((3.5,5+dy,4.5,10.5+dy),hair);c.box((11,4+dy,12,10.5+dy),hair)
    c.line((5,11+dy,10,17+dy),'#29434b',1);c.box((9,16+dy,12,18+dy),'#2c4650');c.oval((10,16.5+dy,11.5,18+dy),'#829e99')
   if name=='nika' and look==0:
    c.line((5.5,5.5+dy,10.5,5.5+dy),'#354955');c.box((5.5,5.5+dy,7,6.5+dy),'#354955');c.box((9,5.5+dy,10.5,6.5+dy),'#354955')
   if name in ['tamara','arkady']:
    c.box((6,12+dy,10,17+dy),'#d0c5a2' if name=='tamara' else '#46676b');c.line((6,15+dy,10,15+dy),'#8b9c8a')
   sheet.alpha_composite(c.im,(frame*32,direction*48))
 sheet.save(OUT/(name+'.png'))
# Matching larger portraits keep distinct hair, accessories, clothes and face proportions.
for name,(coat,hair,skin) in people.items():
 if name.startswith('hero_'):continue
 c=Canvas(32,32)
 c.poly([(2,31),(3,27),(10,23),(12,22),(20,22),(25,24),(30,29),(30,31)],coat)
 c.poly([(4,29),(11,25),(14,28),(12,31),(3,31)],lighten(coat,16));c.poly([(19,27),(23,25),(29,29),(30,31),(20,31)],lighten(coat,-22))
 c.box((12,20,20,26),lighten(skin,-23));c.poly([(13,21),(19,21),(18.5,25),(15,26),(13,24)],skin)
 c.oval((7,2,25,25),hair)
 c.poly([(10,7),(16,5),(22,8),(23,16),(21,20),(18,23),(13,22),(10,18),(9,12)],skin)
 c.poly([(10,9),(14,7),(18,7),(21,10),(20,17),(17,20),(12,18)],lighten(skin,14))
 c.poly([(21,9),(23,12),(22,18),(19,22),(17,22),(20,18)],lighten(skin,-18))
 for x in [11.5,19]:
  c.line((x,12.5,x+2,12.5),'#6b5748');c.box((x+.5,13.5,x+1.5,14.5),'#2b4149');c.box((x+1,13.5,x+1.5,14),'#e2d7b8')
 c.line((16,14.5,15.5,17,17,17.5),lighten(skin,-27));c.line((14,19,18.5,19),'#9a6c58');c.line((15,19.5,18,19.5),lighten(skin,15))
 if name=='mira':
  c.poly([(7,12),(7,7),(11,2),(20,2),(25,7),(23,12),(21,7),(17,6),(12,8),(9,13)],hair)
  c.line((9,7,13,4,20,4,23,7),lighten(hair,25),1)
  c.line((8,14,8,21,10,26),hair,2.5);c.line((8,15,8.5,22,10,25),lighten(hair,22));c.box((9,25,11,26),'#cfb886')
  c.poly([(11,24),(16,27),(21,24),(23,26),(16,29)],'#b9c8ac')
 elif name=='asya':
  c.poly([(7,23),(6,10),(9,4),(15,1),(22,3),(26,8),(25,24),(22,21),(23,11),(19,7),(12,10),(9,14),(10,23)],hair)
  c.poly([(9,8),(13,4),(20,4),(23,7),(18,6),(12,9),(8,13)],lighten(hair,35));c.oval((23,17,24.5,20),'#efcb88');c.line((10,24,21,31),'#233f4c',1.5)
 elif name=='nika':
  c.poly([(7,12),(8,6),(13,2),(23,2),(26,6),(24,10),(19,7),(11,11)],hair);c.poly([(10,7),(14,4),(22,3.5),(24,5),(17,6)],lighten(hair,35));c.box((8,12,9,17),'#6d726a')
  for x in [10,18]:
   c.line((x,12,x+5,12,x+5,15.5,x,15.5,x,12),'#344a53',.5)
  c.line((15,13,18,13),'#344a53');c.oval((8,18,9,20),'#d6d1b2')
 else:
  c.poly([(8,11),(8,6),(12,3),(21,3),(25,8),(22,11),(19,7),(11,9)],hair);c.line((10,6,15,4,21,5),lighten(hair,25),1)
 c.save('portrait_'+name)
print('Character pass: nine 2x sprite sheets and seven matching portraits.')

# Physical furnishings have their own painter-order sprites and collision footprints.
for name,w,h in [('fridge',28,44),('crt_spare',24,24),('bookcase',30,54)]:
 c=Canvas(w,h)
 if name=='fridge':
  c.box((1,1,26,42),'#29444b');c.box((3,2,24,40),'#c2c4ac');c.box((4,3,23,15),'#e0d8b9');c.line((3,17,24,17),'#6c8b83');c.line((4,19,4,38),'#e5dec2');c.box((6,7,7,12),'#66817c');c.box((6,21,7,28),'#66817c');c.box((4,42,7,43),'#29444b');c.box((20,42,23,43),'#29444b')
 elif name=='crt_spare':
  c.box((1,1,22,22),'#263e47');c.box((2,2,21,19),'#82968b');c.box((4,4,19,15),'#213b48');c.line((6,7,16,7),'#7aa59c');c.line((6,9,13,9),'#608a87');c.box((18,18,20,18.5),'#dbb477');c.box((4,21,20,23),'#3c565b')
 else:
  c.box((1,1,28,52),'#283e43');c.box((3,2,26,49),'#947955');c.line((3,2,26,2),'#cdb186')
  for y in [5,20,35]:
   c.box((4,y,25,y+12),'#3c514b')
   for k in range(4):
    x=5+k*5;c.box((x,y+2,x+3,y+10),['#b49069','#8ba18c','#ceba8d','#648b88'][k]);c.line((x+.5,y+3,x+.5,y+9),'#d3c09b')
   c.line((3,y+13,26,y+13),'#c3a57a')
  c.box((3,51,6,53),'#30494a');c.box((23,51,26,53),'#30494a')
 c.save(name)
 c.im.resize((w,h),Image.Resampling.LANCZOS).save(ROOT/'art'/(name+'.png'))
