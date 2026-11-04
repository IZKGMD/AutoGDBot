"""Original pixel artwork and PCM audio. Rebuild with Python 3 + Pillow.
No runtime generation and no borrowed game assets. Coordinates are top-down.
"""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import json, math, wave, struct, random
ROOT=Path(__file__).resolve().parents[1]/'core/src/main/resources'
ART=ROOT/'art'; ART.mkdir(exist_ok=True)
C={'ink':'#202936','dark':'#293443','line':'#394650','road':'#354650','stone':'#65757b','cream':'#ead6ac','gold':'#e8b56e','mint':'#98b9a5','wood':'#845d51','warm':'#b48167','blue':'#708c9d','leaf':'#506c64'}
def canvas(w,h,color=(0,0,0,0)): return Image.new('RGBA',(w,h),color)
def rect(d,box,c): d.rectangle(box,fill=C.get(c,c))
def save(im,name): im.save(ART/(name+'.png'))
def sprite(name,w,h,fn):
 im=canvas(w,h); d=ImageDraw.Draw(im); fn(d); save(im,name)
# All props have intentionally drawn silhouettes, outlines, highlights and hardware.
def desk(d):
 rect(d,(3,26,7,39),'ink');rect(d,(54,26,58,39),'ink');rect(d,(0,13,63,28),'ink');rect(d,(1,13,62,24),'wood');rect(d,(2,13,61,15),'warm')
 rect(d,(16,1,43,18),'ink');rect(d,(19,3,40,14),'#7c9b91');rect(d,(21,5,38,12),'#bac6a2');rect(d,(23,7,34,7),'#526b68');rect(d,(23,9,30,9),'#526b68');rect(d,(27,19,33,21),'dark')
 rect(d,(15,22,43,25),'dark')
 for x in range(17,42,4): rect(d,(x,22,x+1,23),'stone')
 rect(d,(48,7,60,22),'dark');rect(d,(50,9,58,10),'stone');rect(d,(57,17,58,18),'gold');rect(d,(4,18,9,23),'cream')
sprite('desk',64,40,desk)
def bed(d):
 rect(d,(1,4,46,68),'ink');rect(d,(3,1,44,13),'wood');rect(d,(4,12,43,62),'cream');rect(d,(7,14,39,26),'#f6e5be');rect(d,(8,16,38,17),'#cbbd9f');rect(d,(4,29,43,62),'#648d8b');rect(d,(4,31,43,33),'#95b4a4');rect(d,(6,34,8,60),'#4a7278');rect(d,(39,35,42,61),'#4a7278');rect(d,(2,62,45,67),'wood');rect(d,(3,68,7,71),'ink');rect(d,(40,68,44,71),'ink')
sprite('bed',48,72,bed)
def wardrobe(d):
 rect(d,(0,0,31,45),'ink');rect(d,(2,2,29,41),'wood');rect(d,(3,3,28,5),'warm');rect(d,(15,5,16,40),'ink');rect(d,(12,20,12,25),'gold');rect(d,(19,20,19,25),'gold');rect(d,(3,43,6,47),'ink');rect(d,(25,43,28,47),'ink')
sprite('wardrobe',32,48,wardrobe)
def kitchen(d):
 rect(d,(1,5,62,30),'ink');rect(d,(2,12,61,28),'#95a294');rect(d,(2,6,61,12),'cream');rect(d,(4,7,25,11),'stone');rect(d,(6,8,23,10),'dark');rect(d,(15,1,18,7),'stone');rect(d,(18,1,22,2),'stone');rect(d,(32,15,33,28),'dark');rect(d,(46,15,47,28),'dark');rect(d,(49,7,59,10),'dark');rect(d,(38,7,46,10),'dark');rect(d,(6,16,26,17),'#c9c9ac')
sprite('kitchen',64,32,kitchen)
def plant(d):
 rect(d,(6,21,20,31),'ink');rect(d,(7,22,19,28),'wood');rect(d,(10,29,17,31),'wood');rect(d,(12,6,14,21),'leaf');
 for b in [(3,8,12,13),(12,2,18,10),(14,12,24,17),(5,16,14,20)]: rect(d,b,'leaf')
 for b in [(3,7,10,9),(14,2,17,5),(17,12,23,14)]:rect(d,b,'mint')
sprite('plant',28,32,plant)
def boxes(d):
 rect(d,(0,14,28,32),'ink');rect(d,(1,15,27,30),'#b18361');rect(d,(2,16,26,17),'gold');rect(d,(13,16,15,30),'cream');rect(d,(13,0,38,20),'ink');rect(d,(14,1,37,18),'#c69b70');rect(d,(24,2,27,18),'cream');rect(d,(30,10,35,13),'wood')
sprite('boxes',40,34,boxes)
def bench(d):
 rect(d,(4,25,7,31),'ink');rect(d,(56,25,59,31),'ink');rect(d,(0,1,63,18),'ink');
 for y in [3,9,15,22]:rect(d,(1,y,62,y+3),'wood');rect(d,(2,y,61,y),'warm')
 rect(d,(2,17,5,26),'ink');rect(d,(58,17,61,26),'ink')
sprite('bench',64,32,bench)
def tree(d):
 rect(d,(29,41,35,78),'ink');rect(d,(30,44,33,76),'wood');
 for b,c in [((12,14,53,48),'ink'),((4,22,61,41),'ink'),((20,3,45,48),'ink'),((8,21,57,39),'#405f59'),((15,13,51,44),'leaf'),((22,5,43,24),'leaf'),((16,14,40,18),'#72917a'),((9,23,25,27),'#72917a'),((42,28,55,36),'#36544e')]:rect(d,b,c)
 rect(d,(28,77,38,79),'ink')
sprite('tree',68,80,tree)
def lamp(d):
 rect(d,(10,11,13,69),'ink');rect(d,(11,11,11,67),'stone');rect(d,(5,67,18,71),'ink');rect(d,(2,2,21,14),'ink');rect(d,(4,4,19,11),'gold');rect(d,(6,5,17,9),'#f7e5b1');rect(d,(7,0,16,2),'ink')
sprite('lamp',24,72,lamp)
def mailbox(d):
 rect(d,(7,24,9,39),'ink');rect(d,(0,4,25,26),'ink');rect(d,(2,5,23,24),'#708d97');rect(d,(4,8,21,11),'dark');rect(d,(5,17,18,20),'cream');rect(d,(1,2,24,4),'blue')
sprite('mailbox',26,40,mailbox)
def notice(d):
 rect(d,(3,36,6,49),'ink');rect(d,(45,36,48,49),'ink');rect(d,(0,0,51,38),'ink');rect(d,(2,2,49,35),'wood');rect(d,(4,4,47,33),'warm')
 for x,y,w,h in [(7,7,14,18),(26,6,18,12),(28,22,14,9)]:
  rect(d,(x,y,x+w,y+h),'cream');rect(d,(x+3,y+4,x+w-3,y+4),'wood');rect(d,(x+3,y+7,x+w-5,y+7),'wood');rect(d,(x+6,y,x+7,y+1),'gold')
sprite('notice',52,50,notice)
def parcel(d):
 rect(d,(0,3,17,14),'ink');rect(d,(1,4,16,13),'cream');d.line((1,4,8,10,16,4),fill=C['warm']);rect(d,(12,5,14,7),'mint')
sprite('letter',18,16,parcel)
# Characters: four orientations and four walking poses, hand-drawn 16 x 24 cells.
for name,coat,hair in [('hero','#cc9b68','#433b3c'),('hero_1','#78989e','#433b3c'),('hero_2','#b57b73','#433b3c'),('mira','#8aa89d','#734c47'),('arkady','#7e94a4','#b4b4a0'),('lev','#ae7e7a','#3e3738')]:
 sheet=canvas(64,96); d=ImageDraw.Draw(sheet)
 for direction in range(4):
  for frame in range(4):
   x=frame*16;y=direction*24; bob=1 if frame in [1,3] else 0; step=1 if frame==1 else -1 if frame==3 else 0
   def r(b,c): rect(d,tuple(v+(x if i%2==0 else y) for i,v in enumerate(b)),c)
   r((4,19,6,22+step),'ink');r((9,19,11,22-step),'ink');r((3,22+step,6,23+step if step<0 else 23),'#c6b7a0');r((9,22-step,12,23-step if step>0 else 23),'#c6b7a0')
   r((3,10+bob,12,19),'ink');r((4,11+bob,11,18),coat);r((6,10+bob,9,12+bob),'cream');r((2,12+bob,3,17+bob),coat);r((12,12+bob,13,17+bob),coat)
   r((4,1+bob,11,10+bob),'ink');r((3,3+bob,12,7+bob),'ink');r((5,3+bob,10,9+bob),'#e5b990');r((4,1+bob,11,4+bob),hair);r((3,4+bob,4,7+bob),hair)
   if direction==3:r((4,2+bob,11,9+bob),hair)
   elif direction==0:r((6,6+bob,6,6+bob),'ink');r((10,6+bob,10,6+bob),'ink')
   elif direction==1:r((5,6+bob,5,6+bob),'ink');r((9,3+bob,11,9+bob),hair)
   else:r((10,6+bob,10,6+bob),'ink');r((4,3+bob,6,9+bob),hair)
 save(sheet,name)
# Floor textures are deterministic designed repeats, not noise.
def floor(d,b,kind):
 x0,y0,x1,y1=b
 rect(d,b,'#8c6c5d' if kind=='wood' else '#667b7d')
 for y in range(y0,y1,16):
  for x in range(x0,x1,32):
   if kind=='wood':
    d.line((x,y,x+31,y),fill='#624d47');d.line((x+(16 if (y//16)%2 else 0),y,x+(16 if (y//16)%2 else 0),y+15),fill='#715449');d.line((x+3,y+4,x+14,y+4),fill='#a0816a');d.line((x+20,y+10,x+27,y+10),fill='#977761')
   else:
    rect(d,(x,y,x+30,y+14),'#73868a' if (x//32+y//16)%2 else '#697e83');d.line((x+1,y+1,x+28,y+1),fill='#84969a')
def window(d,x,y,w=48):
 rect(d,(x-3,y-3,x+w+3,y+33),'ink');rect(d,(x,y,x+w,y+30),'#a5aea0');rect(d,(x+2,y+2,x+w-2,y+27),'#4e697c');rect(d,(x+5,y+4,x+w//2-2,y+24),'#708995');rect(d,(x+w//2,y+1,x+w//2+2,y+28),'wood');rect(d,(x-4,y+30,x+w+4,y+33),'warm')
 for i in range(5):d.line((x+7+i*7,y+5,x+5+i*7,y+10),fill='#a4b8b5')
def door(d,x,y,w=32):
 rect(d,(x-2,y-3,x+w+2,y+42),'ink');rect(d,(x,y,x+w,y+40),'wood');rect(d,(x+3,y+3,x+w-3,y+35),'#695652');rect(d,(x+5,y+4,x+w-5,y+17),'#a88b6d');rect(d,(x+w-7,y+23,x+w-5,y+25),'gold');rect(d,(x-4,y+40,x+w+4,y+43),'cream')
room=canvas(480,320,'#1b2531');d=ImageDraw.Draw(room)
rect(d,(57,28,422,280),'ink');floor(d,(64,64,415,271),'wood');rect(d,(64,32,415,63),'#a08b75');rect(d,(64,58,415,63),'#685655');rect(d,(64,32,415,34),'#c4ac8b');rect(d,(61,64,64,274),'#ad8c70');rect(d,(415,64,419,274),'#665452')
window(d,221,31,52)
rect(d,(217,28,221,60),'#708b84');rect(d,(276,28,280,60),'#708b84')
rect(d,(170,142,287,207),'#5a7073');rect(d,(173,145,284,204),'#84978b');rect(d,(176,148,281,201),'#586f70')
for x in range(181,278,8): d.line((x,151,x,198),fill='#647d78')
rect(d,(65,272,225,279),'#554947');rect(d,(261,272,416,279),'#554947');rect(d,(226,268,260,277),'#c6a57e');rect(d,(227,274,259,277),'#806555')
rect(d,(132,37,153,56),'ink');rect(d,(134,39,151,54),'cream');rect(d,(137,44,148,48),'leaf');rect(d,(142,41,145,44),'gold')
save(room,'room')
stair=canvas(480,320,'#1b2531');d=ImageDraw.Draw(stair)
floor(d,(96,64,383,271),'tile');rect(d,(96,32,383,63),'#7f9290');rect(d,(96,54,383,63),'#4b666a');rect(d,(93,32,96,279),'ink');rect(d,(383,32,387,279),'ink');rect(d,(96,272,383,278),'ink')
door(d,144,29);door(d,285,29);rect(d,(154,38,166,46),'cream');rect(d,(295,38,307,46),'cream')
for y in range(145,240,12):rect(d,(262,y,369,y+7),'#92a0a0');rect(d,(262,y+8,369,y+11),'#4d676e')
rect(d,(256,139,260,248),'ink');rect(d,(372,139,376,248),'ink');rect(d,(230,264,263,279),'gold');rect(d,(230,271,263,279),'wood')
rect(d,(101,91,122,118),'ink');rect(d,(103,93,120,116),'cream');rect(d,(106,98,117,99),'wood');rect(d,(106,103,117,104),'wood')
save(stair,'stair')
street=canvas(960,512,'#364e51');d=ImageDraw.Draw(street)
# lawns and road, individually placed cobbles and drainage.
rect(d,(16,192,943,431),'#536c68');floor(d,(16,224,943,319),'tile');rect(d,(16,328,943,399),'road');rect(d,(16,322,943,327),'#a0aaa0');rect(d,(16,400,943,405),'#8e9a94');floor(d,(16,406,943,453),'tile')
for x in range(22,940,32):rect(d,(x,324,x+26,325),'#c1c3af');rect(d,(x,402,x+26,403),'#b0b6a5')
for x in range(50,940,80):rect(d,(x,363,x+31,365),'#92a4a0')
for x,y,w in [(68,344,33),(221,384,70),(473,335,36),(612,375,56),(820,346,77)]:
 rect(d,(x,y,x+w,y+3),'#546e79');rect(d,(x+7,y+4,x+w-6,y+5),'#6b858e');rect(d,(x+14,y,x+w-10,y),'#94a8a8')
for y in range(333,395,12):rect(d,(355,y,390,y+6),'#b1b8aa')
# Buildings have depth, chimneys, cornices, individual window light and shopfronts.
def building(x,y,w,h,col):
 rect(d,(x+6,y+10,x+w+7,y+h+7),'#293f45');rect(d,(x,y,x+w,y+h),col);rect(d,(x,y,x+w,y+13),'#263640');rect(d,(x+4,y+14,x+w-4,y+17),'#a69b87');rect(d,(x+4,y+h-7,x+w-4,y+h),'#384b54')
 for yy in range(y+25,y+h-40,14):
  for xx in range(x+7,x+w-9,24): d.line((xx,yy,xx+8,yy),fill='#657378')
 rect(d,(x-4,y+4,x+w+4,y+9),'#556775');rect(d,(x+20,y-13,x+34,y+4),'#394c58')
building(64,61,216,157,'#7f7f79')
for x in [83,142,218]:window(d,x,88,32)
window(d,86,155,40);window(d,219,155,36);door(d,157,175,32)
rect(d,(146,163,203,170),'#3f565b');rect(d,(147,163,202,164),'gold')
building(400,96,144,122,'#8d756b');window(d,415,130,32);window(d,491,130,32);door(d,455,175,29);rect(d,(411,173,445,195),'#c5b38f');rect(d,(419,181,437,183),'wood')
building(625,70,238,148,'#6c8580')
for x in [641,708,793]:window(d,x,97,42)
for x in [641,705]:
 rect(d,(x,163,x+50,207),'ink');rect(d,(x+3,166,x+47,204),'#91a99b');rect(d,(x+7,172,x+17,195),'#c2b88c');rect(d,(x+20,185,x+40,197),'#5e8180')
door(d,798,175,32);rect(d,(636,145,846,158),'#c3ab83');
# shop awning
for x in range(634,848,14):rect(d,(x,159,x+13,166),'#b47562' if x//14%2 else '#d9c3a0')
# garden beds and edges
for x in [38,300,555,879]:
 rect(d,(x,246,x+36,283),'ink');rect(d,(x+2,248,x+34,281),'leaf')
 for xx,yy in [(5,5),(19,16),(26,8),(9,25)]:rect(d,(x+xx,250+yy,x+xx+3,253+yy),'#98ad83')
for x in range(18,945,18):rect(d,(x,477,x+2,503),'#263b43');rect(d,(x,482,x+17,484),'#536b68');rect(d,(x,494,x+17,496),'#536b68')
# Side alleys visually bounded with low masonry.
rect(d,(16,205,56,216),'#55696d');rect(d,(289,205,392,216),'#55696d');rect(d,(553,205,615,216),'#55696d');rect(d,(871,205,943,216),'#55696d')
save(street,'street')
# BMFont, thresholded for crisp nearest-neighbour text. Includes all Cyrillic.
chars=''.join(dict.fromkeys(''.join(chr(i) for i in range(32,127))+''.join(chr(i) for i in range(0x400,0x460))+'—–…«»№•·×→←↑↓✓₽−'))
for name,size,path in [('ui',11,'/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'),('small',9,'/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'),('title',27,'/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf')]:
 font=ImageFont.truetype(path,size);cell=size+8;cols=16; im=Image.new('L',(cols*cell,math.ceil(len(chars)/cols)*cell));dd=ImageDraw.Draw(im);rows=[]
 for i,ch in enumerate(chars):
  x=i%cols*cell;y=i//cols*cell;dd.text((x,y),ch,font=font,fill=255,stroke_width=0)
  bx,by,bw,bh=font.getbbox(ch); bw-=bx; bh-=by
  rows.append(f'char id={ord(ch)} x={x+bx} y={y+by} width={bw} height={bh} xoffset={bx} yoffset={by} xadvance={round(font.getlength(ch))} page=0 chnl=15')
 alpha=im.point(lambda p:255 if p>=95 else 0);out=Image.new('RGBA',im.size,'white');out.putalpha(alpha);out.save(ROOT/'fonts'/f'{name}.png')
 (ROOT/'fonts'/f'{name}.fnt').write_text(f'info face="DejaVu" size={size} padding=0,0,0,0 spacing=0,0\ncommon lineHeight={size+3} base={size} scaleW={im.width} scaleH={im.height} pages=1 packed=0\npage id=0 file="{name}.png"\nchars count={len(rows)}\n'+'\n'.join(rows),encoding='utf-8')
# Gentle original notes: no samples, no external music. Rain is filtered noise.
random.seed(17); rate=22050
for name,duration in [('click',.09),('step',.055),('rain',4),('evening',16)]:
 out=[];smooth=0
 notes=[220,261.63,329.63,293.66,196,246.94,293.66,329.63]
 for n in range(int(rate*duration)):
  t=n/rate
  if name=='click':v=math.sin(2*math.pi*660*t)*.15*(1-t/duration)**2
  elif name=='step':v=random.uniform(-1,1)*.08*(1-t/duration)**3
  elif name=='rain':
   smooth=smooth*.83+random.uniform(-1,1)*.17;v=smooth*.19*(min(1,t*5,(duration-t)*5))
  else:
   v=0
   for k,f in enumerate(notes):
    age=t-k*2
    if age>=0:v+=(math.sin(2*math.pi*f*age)+.24*math.sin(2*math.pi*f*2*age))*math.exp(-age*1.3)*min(1,age*30)*.10
   v*=min(1,t,(duration-t))
  out.append(struct.pack('<h',int(max(-1,min(1,v))*32767)))
 with wave.open(str(ROOT/'audio'/f'{name}.wav'),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(b''.join(out))
print('Assets generated: original scene art, sprites, Cyrillic fonts and four PCM sounds.')
import runpy
runpy.run_path(str(Path(__file__).with_name('polish_assets.py')))
runpy.run_path(str(Path(__file__).with_name('make_soundscape.py')))
runpy.run_path(str(Path(__file__).with_name('make_chapter2_art.py')))
runpy.run_path(str(Path(__file__).with_name('make_chapter3_art.py')))
