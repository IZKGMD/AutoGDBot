"""Evening material pass and authored light pools. No new invisible collision geometry."""
from pathlib import Path
from PIL import Image,ImageDraw
import math,random
A=Path(__file__).resolve().parents[1]/'core/src/main/resources/art'
def save(i,n):i.save(A/(n+'.png'))
def rect(d,b,c):d.rectangle(b,fill=c)
# Large soft pools are separate translucent assets; weather never hides navigation.
for name,color in [('light_amber',(255,184,105)),('light_monitor',(104,205,195)),('light_window',(255,218,156))]:
 im=Image.new('RGBA',(160,112));p=im.load()
 for y in range(112):
  for x in range(160):
   r=((x-80)/80)**2+((y-56)/56)**2
   a=int(max(0,1-r)**2*(36 if name=='light_amber' else 28))
   p[x,y]=(*color,a)
 save(im,name)
# Student room: taped receipts, an old curtain, books, loose cables, scuffed threshold.
im=Image.open(A/'room.png');d=ImageDraw.Draw(im)
for x in [225,268]:
 rect(d,(x,62,x+5,91),'#6e7875');d.line((x+1,64,x+1,88),fill='#a6a692');d.line((x+4,65,x+4,90),fill='#535f60')
rect(d,(276,67,289,85),'#c0b59b');rect(d,(278,69,287,82),'#e0d2ad')
for y in [74,77,80]:d.line((280,y,285,y),fill='#9b9784')
rect(d,(277,67,281,69),'#bebd9f');rect(d,(286,83,290,85),'#bebd9f')
d.line((167,99,153,99,153,144,166,144),fill='#434f53',width=1)
# Floor damage respects the room's small scale.
for x,y in [(187,185),(255,202),(282,214),(203,228)]:
 d.line((x,y,x+7,y-1),fill='#6b736b');d.line((x+2,y+1,x+9,y),fill='#8f9480')
save(im,'room')
# Two desk states: the upgrade stays in the same furniture footprint.
im=Image.open(A/'desk.png').convert('RGBA');d=ImageDraw.Draw(im)
rect(d,(5,10,11,17),'#d6c7a5');rect(d,(6,10,10,11),'#685346');d.line((11,12,13,12,13,15,11,15),fill='#c9b994')
rect(d,(48,10,58,17),'#c5b58f');d.line((49,12,56,12),fill='#7d7e6a');d.line((49,14,54,14),fill='#7d7e6a')
rect(d,(18,22,31,26),'#c0b795');d.line((19,23,29,23),fill='#6a817b');save(im,'desk')
up=im.copy();d=ImageDraw.Draw(up);rect(d,(57,3,67,11),'#364e58');rect(d,(59,5,65,8),'#607d79');rect(d,(64,9,65,9),'#c8de9c');save(up,'desk_ssd')
# Chair back lies behind the table, inside the table's existing collision footprint.
chair=Image.new('RGBA',(16,20));d=ImageDraw.Draw(chair);rect(d,(2,1,13,12),'#263b49');rect(d,(4,2,11,10),'#66817c');d.line((5,3,10,3),fill='#91a39a');rect(d,(3,13,12,15),'#54716c');rect(d,(6,16,9,19),'#304b54');save(chair,'chair_upgrade')
# Exterior: individual paving wear, copper guttering and the inhabited windows above shops.
im=Image.open(A/'street.png').convert('RGBA');d=ImageDraw.Draw(im);rng=random.Random(3003)
for x0,x1,y in [(85,281,141),(408,588,136),(641,841,131)]:
 d.line((x0,y,x1,y),fill='#a2937a');d.line((x0,y+2,x1,y+2),fill='#45545a')
 for x in range(x0+4,x1,27):d.line((x,y,x,y+3),fill='#68766f')
for x,y in [(117,107),(228,107),(432,99),(543,99),(672,99),(783,99)]:
 rect(d,(x,y,x+26,y+22),'#354c59');rect(d,(x+2,y+2,x+24,y+20),'#b7a17d');rect(d,(x+4,y+4,x+22,y+18),'#cfb58b');rect(d,(x+12,y+2,x+14,y+20),'#607275');d.line((x+3,y+21,x+25,y+21),fill='#a69980')
 # A curtain, pot and tiny silhouettes make windows belong to someone.
 rect(d,(x+3,y+3,x+6,y+17),'#9b977e');rect(d,(x+18,y+15,x+22,y+18),'#8c6e58');d.line((x+20,y+15,x+20,y+11),fill='#617561');d.line((x+17,y+12,x+21,y+13),fill='#617561')
for _ in range(110):
 x=rng.randrange(40,930);y=rng.randrange(244,434)
 # Fine surface marks are walkable, unlike actual planters and benches.
 if rng.random()<.6:d.line((x,y,x+2,y),fill="#81938f")
for x,y in [(208,300),(485,332),(736,392)]:
 d.line((x,y,x+20,y),fill='#839d9b');d.line((x+4,y+2,x+25,y+2),fill='#6f8d93');d.line((x+7,y+4,x+17,y+4),fill='#a5b4a6')
save(im,'street')
# Cafe: a contact sheet, radio and scratched counter. Diner: a thermos and folded towel.
for name in ['cafe','diner','workshop']:
 im=Image.open(A/(name+'.png')).convert('RGBA');d=ImageDraw.Draw(im)
 if name=='cafe':
  rect(d,(264,56,284,70),'#3b535b');rect(d,(266,58,282,68),'#bdb395')
  for x in range(268,281,4):d.line((x,60,x,66),fill='#6c817a')
  rect(d,(326,151,350,164),'#536d6a');rect(d,(328,153,348,161),'#9a9578');d.line((331,155,345,155),fill='#5a6c65')
 elif name=='diner':
  rect(d,(284,57,291,74),'#9d8c76');rect(d,(285,60,290,70),'#d1c7aa');rect(d,(283,57,292,60),'#4d676b')
 else:
  rect(d,(108,57,165,73),'#536e72')
  for x in range(111,163,8):rect(d,(x,59,x+4,63),'#bba581');d.line((x+2,64,x+2,70),fill='#8fa49a')
 save(im,name)
print('0.3 art: warm windows, inhabited materials, desk upgrades and separate light pools.')
