"""Readable cutaway locations, a poor starting room, and distinct character palettes."""
from pathlib import Path
from PIL import Image,ImageDraw
A=Path(__file__).resolve().parents[1]/'core/src/main/resources/art'
INK='#202c3b';CREAM='#e3d4b0';BRICK='#8e6254';WOOD='#88644e';GOLD='#dfb274'
def im(w=480,h=320,c='#172330'):return Image.new('RGBA',(w,h),c)
def r(d,b,c):d.rectangle(b,fill=c)
def ln(d,p,c):d.line(p,fill=c)
def poly(d,p,c):d.polygon(p,fill=c)
def save(i,n):i.save(A/(n+'.png'))
def floor(d,b,mode):
 x0,y0,x1,y1=b;r(d,b,'#7d8176' if mode=='cheap' else '#a18d71' if mode=='cafe' else '#8a9b98' if mode=='diner' else '#747f82')
 for y in range(y0,y1,16):
  for x in range(x0,x1,16):
   if mode=='cheap':
    r(d,(x,y,min(x+14,x1),min(y+14,y1)),'#85887a' if (x//16+y//16)%2 else '#777e74');ln(d,(x+2,y+2,x+11,y+2),'#949889')
   elif mode=='cafe':
    ln(d,(x,y,min(x+31,x1),y),'#7f725f');ln(d,(x+(8 if y%32 else 0),y,x+(8 if y%32 else 0),min(y+15,y1)),'#86785f')
   else:
    r(d,(x,y,min(x+14,x1),min(y+14,y1)),'#91a39d' if (x//16+y//16)%2 else '#82958f')
def doorway(d,x,y):
 r(d,(x-17,y-3,x+17,y+5),INK);r(d,(x-15,y-3,x+15,y+3),'#c3a378');ln(d,(x-15,y+1,x+15,y+1),'#ead5ac')
def pane(d,x,y,w=40,lit=False):
 r(d,(x-3,y-3,x+w+3,y+32),INK);r(d,(x,y,x+w,y+27),'#c2b491');r(d,(x+2,y+2,x+w-2,y+25),'#9daaa3' if not lit else '#d8b97e');r(d,(x+4,y+4,x+w//2-2,y+23),'#657e8e' if not lit else '#e7c894');r(d,(x+w//2,y+1,x+w//2+2,y+27),'#7c7766');r(d,(x-4,y+28,x+w+4,y+32),'#9c8c70')
 for dx in [8,19,30]:ln(d,(x+dx,y+5,x+dx-3,y+11),'#b4c6bb')
# The room is visibly less than half the former floor area.
i=im();d=ImageDraw.Draw(i)
r(d,(137,58,343,263),INK);floor(d,(144,96,335,255),'cheap');r(d,(144,64,335,95),'#9a9988');r(d,(144,89,335,95),'#616b69');ln(d,(144,65,335,65),'#beb7a0');r(d,(138,64,143,257),'#6f7a77');r(d,(336,64,341,257),'#485d61')
pane(d,228,62,35)
# Damp stain, hairline cracks, exposed cable and a taped socket.
poly(d,[(145,67),(170,67),(174,74),(168,80),(174,85),(146,86)],'#858c7e');poly(d,[(146,67),(161,67),(164,74),(158,79),(146,77)],'#737f75')
ln(d,(302,67,298,72,302,77,299,83),'#667975');ln(d,(303,67,300,72),'#b1ad98')
ln(d,(162,89,162,96,169,96,169,110),'#4a5b5c');r(d,(160,83,166,90),'#d1c6a6');r(d,(162,86,162,87),'#5c6b6a');r(d,(165,86,165,87),'#5c6b6a')
# Mattress no ornamental rug or kitchen. Floor patches and suitcase tell the move.
for x,y in [(154,170),(286,191),(236,217)]:r(d,(x,y,x+15,y+14),'#a5a48c');ln(d,(x,y,x+13,y),'#b4b29a');ln(d,(x,y+14,x+14,y+14),'#676f66')
ln(d,(182,180,193,178,198,182,205,179),'#656f6a');ln(d,(286,204,304,206,311,201),'#656f6a')
r(d,(196,69,212,87),INK);r(d,(198,71,210,85),'#b8b99c');ln(d,(200,74,208,74),'#788e83');ln(d,(200,77,205,77),'#788e83');r(d,(200,81,208,83),'#9c8c6d')
r(d,(145,256,213,262),'#4a575b');r(d,(247,256,335,262),'#4a575b');doorway(d,230,254)
r(d,(217,237,242,247),'#6f7569');ln(d,(220,240,239,240),'#98937b')
save(i,'room')
# Desk / blanket show age and repair, not polished luxury furniture.
i=Image.open(A/'desk.png').convert('RGBA');d=ImageDraw.Draw(i);r(d,(14,0,44,2),'#999f8b');ln(d,(7,26,11,23,17,24),'#4e514d');r(d,(54,14,59,15),'#d6c4a0');ln(d,(55,14,58,15),'#738684');save(i,'desk')
i=Image.open(A/'bed.png').convert('RGBA');d=ImageDraw.Draw(i);r(d,(4,29,43,62),'#72858a');r(d,(5,30,41,32),'#a2aba2');poly(d,[(5,40),(15,41),(20,45),(32,43),(41,47),(41,51),(30,48),(16,49),(5,46)],'#62777e');r(d,(31,53,39,59),'#aaa58b');ln(d,(32,54,38,58),'#7b8983');save(i,'bed')
# An original serving counter and two-chair date table.
i=Image.new('RGBA',(88,40));d=ImageDraw.Draw(i);r(d,(1,12,86,36),INK);r(d,(3,19,84,33),'#9e775e');r(d,(2,10,85,18),'#dfc99e');ln(d,(4,11,83,11),'#fff0c8');ln(d,(3,19,84,19),'#6f6555')
for x in [6,31,57]:r(d,(x,22,x+20,31),'#8a6655');ln(d,(x+3,24,x+17,24),'#c29c76')
r(d,(9,4,26,10),'#475d64');r(d,(12,1,24,8),'#a8b3a3');r(d,(14,3,22,5),'#334d57');r(d,(63,3,80,10),'#48656b');r(d,(65,5,78,8),'#dae0bb');r(d,(44,4,54,9),'#b7a278');r(d,(42,7,57,10),'#e1ca9e');save(i,'counter')
i=Image.new('RGBA',(32,40));d=ImageDraw.Draw(i);r(d,(1,15,4,31),INK);r(d,(27,15,30,31),INK);r(d,(2,18,7,24),'#96715b');r(d,(25,18,29,24),'#96715b');r(d,(7,5,24,27),INK);r(d,(8,4,23,23),'#b18c65');ln(d,(9,5,22,5),'#e0c197');r(d,(11,8,20,20),'#d7c49c');r(d,(14,11,18,15),'#f0e2b8');r(d,(15,12,17,14),'#6d7164');r(d,(9,28,11,33),INK);r(d,(21,28,23,33),INK);save(i,'table')
for name,wall,mode in [('cafe','#91a094','cafe'),('diner','#c5b18e','diner'),('workshop','#8a9996','workshop')]:
 i=im();d=ImageDraw.Draw(i);r(d,(90,48,389,270),INK);floor(d,(96,80,383,263),mode);r(d,(96,52,383,79),wall);r(d,(96,75,383,79),'#607774');ln(d,(96,52,383,52),'#d9d0ae');r(d,(91,52,95,264),'#627973');r(d,(384,52,388,264),'#465f65');r(d,(96,264,221,269),'#4f6264');r(d,(258,264,383,269),'#4f6264');doorway(d,240,262)
 pane(d,118,48,42,True);pane(d,300,48,48,False)
 if name=='cafe':
  # Film contact sheets, record shelf and a warm runner.
  for x in [189,205,221]:r(d,(x,54,x+11,69),INK);r(d,(x+2,56,x+9,67),'#d8c298');poly(d,[(x+2,66),(x+5,60),(x+9,64)],'#687f78')
  r(d,(107,198,180,228),'#5e7c76');ln(d,(110,201,177,201),'#bdba95');ln(d,(110,225,177,225),'#bdba95')
  r(d,(347,104,371,156),INK);r(d,(349,106,369,153),'#a98765')
  for y in [110,124,138]:
   for x in range(352,368,4):r(d,(x,y,x+1,y+9),'#d7c19a' if x%3 else '#668c88')
 if name=='diner':
  # Menu board, apron hooks and a tiled splashback.
  r(d,(179,54,257,76),INK);r(d,(181,56,255,74),'#42646a')
  for y in [59,64,69]:ln(d,(186,y,231,y),'#c4d1ad');ln(d,(239,y,249,y),'#e2bc82')
  for x in range(99,271,12):ln(d,(x,81,x,98),'#a9b5a5');ln(d,(x,88,x+11,88),'#c7c8b1')
  r(d,(340,111,367,151),INK);r(d,(343,114,364,147),'#d9d0af');ln(d,(343,129,364,129),'#819c92');r(d,(346,120,349,122),'#667e7d')
 if name=='workshop':
  r(d,(185,54,273,75),INK);r(d,(187,56,271,73),'#8e8f76')
  for x in range(193,267,12):ln(d,(x,59,x,68),'#c0baa0');r(d,(x-2,65,x+2,67),'#3c5962')
  for x,y in [(114,192),(304,188),(331,207)]:r(d,(x,y,x+25,y+22),INK);r(d,(x+2,y+2,x+23,y+18),'#71877f');r(d,(x+5,y+5,x+20,y+13),'#263e4a');r(d,(x+19,y+16,x+21,y+17),'#c4be85')
 save(i,name)
# Park is a different readable place, not another house floor.
i=im(c='#243e46');d=ImageDraw.Draw(i);r(d,(24,56,455,263),'#688a70')
for x,y in [(45,83),(200,67),(410,77),(58,224),(366,246),(204,220)]:
 for dx,dy in [(0,0),(5,2),(11,-1)]:ln(d,(x+dx,y+dy,x+dx-1,y+dy-3),'#88a37d');ln(d,(x+dx,y+dy,x+dx+2,y+dy-2),'#456e60')
r(d,(216,56,275,263),'#a0a48b');r(d,(24,174,455,204),'#a0a48b');ln(d,(216,58,216,261),'#c1bca0');ln(d,(274,58,274,261),'#617d73')
poly(d,[(90,91),(106,81),(166,82),(188,101),(189,143),(169,161),(106,163),(89,148)],'#b3b8a2');poly(d,[(97,96),(110,89),(163,89),(181,105),(180,139),(163,154),(109,155),(97,143)],'#476f7c');poly(d,[(103,102),(162,96),(175,107),(172,139),(158,149),(112,148)],'#5b8b92')
for x,y in [(110,110),(139,127),(115,140)]:ln(d,(x,y,x+22,y),'#a0b7ac');ln(d,(x+5,y+2,x+28,y+2),'#709d9b')
for x,y in [(42,61),(396,176)]:i.alpha_composite(Image.open(A/'tree.png'),(x,y))
d=ImageDraw.Draw(i);r(d,(218,256,273,263),'#c5b793');save(i,'park')
# Street: a cafe fits the gap; labels and the gate have visible architectural anchors.
i=Image.open(A/'street.png').convert('RGBA');d=ImageDraw.Draw(i)
r(d,(295,134,392,218),INK);r(d,(300,144,388,211),'#8c9f93');r(d,(295,132,392,144),'#435e64');ln(d,(298,145,390,145),'#cbb895');pane(d,306,151,26,True);r(d,(341,176,374,215),INK);r(d,(344,179,371,211),'#927b62');r(d,(347,183,367,198),'#bdb293');r(d,(367,202,369,204),'#dbc28e');r(d,(307,174,334,180),'#8c9f93')
# An actual park gate and direction marker.
for x in range(876,930,8):ln(d,(x,439,x,463),'#233e48');r(d,(x,438,x+2,440),'#a5b3a0')
r(d,(893,444,913,465),'#788f7b');ln(d,(889,439,889,460),'#c2c3a6')
# Remove the old closed-store visual and draw a little diner sign silhouette.
r(d,(636,145,846,158),'#c4aa7f');ln(d,(638,146,844,146),'#ead5a6');r(d,(680,150,687,152),'#7b735f');r(d,(801,150,808,152),'#7b735f')
save(i,'street')
# Distinct sprites/portraits: warm copper Asya; indigo Nika; work apron Tamara.
for new,base,old,newcoat,hair in [('asya','mira',(145,171,160),(190,126,82),(118,65,45)),('nika','lev',(173,130,122),(107,126,164),(48,48,65)),('tamara','mira',(145,171,160),(164,164,133),(111,104,91))]:
 for prefix in ['', 'portrait_']:
  src=Image.open(A/(prefix+base+'.png')).convert('RGBA');p=src.load()
  for y in range(src.height):
   for x in range(src.width):
    rr,gg,bb,aa=p[x,y]
    if not aa:continue
    # Recolour the exact clothing ranges while leaving skin and outlines legible.
    if (base=='mira' and abs(rr-145)<8 and abs(gg-171)<8 and abs(bb-160)<8) or (base=='lev' and abs(rr-173)<8 and abs(gg-130)<8 and abs(bb-122)<8):p[x,y]=(*newcoat,aa)
    if (base=='mira' and rr in range(110,139) and gg in range(75,100) and bb in range(65,90)) or (base=='lev' and rr in range(60,91) and gg in range(48,77) and bb in range(48,82)):p[x,y]=(*hair,aa)
  dd=ImageDraw.Draw(src)
  if prefix and new=='asya':r(dd,(23,17,24,19),'#e5be7d')
  if prefix and new=='nika':r(dd,(24,14,24,16),'#a5b3bb')
  save(src,prefix+new)
print('Chapter 0.2 art: cramped dorm room, cafe, diner, workshop, park and new characters.')
# Portrait silhouettes are authored separately: braid, asymmetric copper bob and undercut.
for name in ['mira','asya','nika']:
 i=Image.new('RGBA',(32,32));d=ImageDraw.Draw(i)
 skin='#ddb18d';light='#efc7a1';shadow='#b98870'
 coat={'mira':'#8eaaa0','asya':'#bc805b','nika':'#6d80a5'}[name]
 hair={'mira':'#78564a','asya':'#8c4c36','nika':'#303645'}[name]
 poly(d,[(3,31),(3,27),(9,23),(12,23),(12,20),(20,20),(20,23),(24,24),(29,28),(29,31)],coat)
 r(d,(12,21,19,25),shadow);r(d,(13,22,18,25),skin)
 poly(d,[(8,9),(10,5),(20,4),(24,8),(25,16),(21,22),(12,22),(8,18)],hair)
 poly(d,[(11,9),(20,8),(23,11),(22,18),(19,22),(13,21),(10,17),(10,12)],skin)
 r(d,(12,12,20,16),light);r(d,(10,15,11,17),skin);r(d,(22,14,23,16),skin)
 r(d,(12,13,13,14),'#354651');r(d,(19,13,20,14),'#354651');r(d,(16,16,17,17),shadow);r(d,(15,19,18,19),'#975e53')
 if name=='mira':
  poly(d,[(8,13),(7,8),(10,4),(20,3),(24,7),(24,11),(19,7),(14,7),(10,11)],hair)
  ln(d,(10,6,17,4,21,5),'#a77e61');r(d,(8,16,10,24),hair);r(d,(9,23,11,27),'#96725a');r(d,(9,26,11,27),'#d0b275')
  poly(d,[(10,25),(15,27),(20,24),(22,26),(16,29)],'#c0cec0');r(d,(23,28,24,29),'#d8c88f')
 elif name=='asya':
  poly(d,[(7,20),(7,9),(10,4),(19,3),(24,6),(25,11),(24,23),(21,21),(22,10),(18,8),(13,11),(10,13),(11,22)],hair)
  poly(d,[(10,7),(14,5),(21,5),(22,7),(17,7),(12,10),(9,12)],'#b3714c')
  r(d,(23,18,24,20),'#efcc83');r(d,(10,16,11,16),'#ac705a');r(d,(20,16,21,16),'#ac705a')
  ln(d,(10,24,19,31),'#34434c');r(d,(18,28,23,31),'#334751');r(d,(20,29,22,31),'#809995')
 else:
  poly(d,[(8,12),(8,7),(12,3),(22,3),(25,6),(24,10),(20,7),(11,10)],hair)
  poly(d,[(10,7),(13,4),(21,4),(22,5),(16,6)],'#54586a');r(d,(8,12,9,17),'#716c6e')
  d.rectangle((10,12,15,15),outline='#374555');d.rectangle((18,12,23,15),outline='#374555');ln(d,(16,13,17,13),'#374555')
  r(d,(8,18,9,19),'#cad1c6');poly(d,[(11,25),(16,28),(21,25),(20,28),(16,30)],'#283e50');r(d,(24,28,25,29),'#d8b76c')
 save(i,'portrait_'+name)
# Overworld hair silhouette and accessories match the portraits in every animation frame.
for name in ['asya','nika']:
 i=Image.open(A/(name+'.png')).convert('RGBA');d=ImageDraw.Draw(i)
 for row in range(4):
  for col in range(4):
   x=col*16;y=row*24
   if name=='asya':
    r(d,(x+3,y+7,x+4,y+11),'#8c4c36');r(d,(x+11,y+6,x+12,y+12),'#8c4c36');ln(d,(x+5,y+3,x+9,y+3),'#b3714c')
    if row==0:ln(d,(x+5,y+13,x+10,y+18),'#34434c');r(d,(x+8,y+17,x+11,y+19),'#34434c')
   else:
    r(d,(x+4,y+3,x+11,y+5),'#303645');ln(d,(x+5,y+3,x+10,y+3),'#54586a')
 save(i,name)
# Broken paving joints, small stones, reeds and flower borders keep the park readable.
i=Image.open(A/'park.png').convert('RGBA');d=ImageDraw.Draw(i)
import random
rng=random.Random(42)
for _ in range(180):
 x=rng.randrange(27,452);y=rng.randrange(59,260)
 if 90<x<190 and 80<y<164:continue
 if 216<=x<=275 or 174<=y<=204:
  if rng.random()<.5:ln(d,(x,y,x+2,y),'#b3b5a0')
 else:
  ln(d,(x,y,x+1,y-2),'#789679')
for y in range(70,256,18):
 ln(d,(218,y,239,y),'#919982');ln(d,(246,y+7,272,y+7),'#919982')
for x in range(31,452,24):ln(d,(x,177,x,186),'#8d9882');ln(d,(x+10,193,x+10,201),'#8d9882')
for x,y in [(35,152),(57,148),(316,70),(339,65),(426,229),(398,244)]:
 for dx,dy in [(0,0),(5,3),(10,-2)]:
  ln(d,(x+dx,y+dy,x+dx,y+dy-4),'#3f6b58');r(d,(x+dx-1,y+dy-5,x+dx+1,y+dy-4),'#b4aa82' if dx else '#cfad88')
for x,y in [(94,147),(109,158),(175,94)]:
 ln(d,(x,y,x-2,y-8),'#435e50');ln(d,(x+2,y,x+3,y-11),'#536e59');r(d,(x+2,y-12,x+3,y-8),'#a5966a')
save(i,'park')
