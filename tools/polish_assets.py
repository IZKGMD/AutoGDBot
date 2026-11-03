"""Authored detail pass for the original CTRL+LIFE artwork.
Works only on our generated assets; no external imagery. Called by make_assets.py.
"""
from PIL import Image, ImageDraw
from pathlib import Path
import math
A=Path(__file__).resolve().parents[1]/'core/src/main/resources/art'
def load(n):
 im=Image.open(A/(n+'.png')).convert('RGBA');return im,ImageDraw.Draw(im)
def put(im,n):im.save(A/(n+'.png'))
def r(d,b,c):d.rectangle(b,fill=c)
def line(d,pts,c):d.line(pts,fill=c)
def polygon(d,pts,c):d.polygon(pts,fill=c)
# Joinery and tactile wear, not random high-frequency noise.
im,d=load('desk')
line(d,(2,25,61,25),'#c49a77');line(d,(4,28,59,28),'#513d3c')
for x,y in [(6,16),(35,17),(53,24)]:line(d,(x,y,x+5,y),'#966d58')
r(d,(18,2,41,2),'#d8c6a1');r(d,(20,3,39,3),'#91aa9c');r(d,(20,13,39,14),'#597670')
r(d,(27,16,30,16),'#b8bba4');r(d,(49,8,59,8),'#67717b')
for y in [13,15,17]:r(d,(50,y,53,y),'#182b36')
r(d,(47,23,50,25),'#d1b294');r(d,(48,22,49,22),'#ead8b3')
r(d,(6,17,8,17),'#e7cdb0');r(d,(5,19,8,20),'#674f49');r(d,(9,19,10,21),'#e7cdb0')
# Cable and under-desk shelf remain transparent around the legs.
line(d,(51,29,51,33,45,33),'#393742');r(d,(12,31,25,34),'#486c6c');r(d,(13,30,24,31),'#d5c29d')
put(im,'desk')
im,d=load('bed')
polygon(d,[(5,35),(13,34),(17,39),(30,38),(40,41),(43,40),(43,46),(28,43),(15,44),(5,41)],'#72998e')
for y in [48,55]:line(d,(6,y,41,y),'#7ea799');line(d,(7,y+1,40,y+1),'#547d7d')
line(d,(8,27,39,27),'#b8bda1');line(d,(8,15,38,15),'#fff0c8');r(d,(8,18,9,24),'#e2d3b3')
line(d,(7,65,40,65),'#b48b69');put(im,'bed')
im,d=load('wardrobe')
for x in [4,18]:
 r(d,(x,7,x+9,17),'#735449');r(d,(x+1,8,x+8,16),'#96715b');r(d,(x,28,x+9,37),'#735449');line(d,(x+1,30,x+8,30),'#a27c63')
r(d,(11,20,13,21),'#eccb8a');r(d,(18,20,20,21),'#eccb8a');line(d,(3,40,28,40),'#bc9470');put(im,'wardrobe')
im,d=load('kitchen')
r(d,(3,12,60,13),'#6c8078');r(d,(4,27,59,28),'#718178');r(d,(35,16,42,17),'#e7d7b1');r(d,(49,16,57,17),'#e7d7b1')
r(d,(38,6,45,10),'#293e48');r(d,(40,7,43,9),'#878e83');r(d,(50,6,58,10),'#293e48');r(d,(52,7,56,9),'#878e83')
r(d,(27,2,34,8),'#b17c62');r(d,(28,1,33,2),'#ddc6a1');r(d,(28,3,33,6),'#d7b18a');r(d,(32,7,33,9),'#8a6658')
put(im,'kitchen')
im,d=load('bench')
for x in [7,55]:
 for y in [4,10,16,23]:r(d,(x,y,x+1,y+1),'#d0b58d')
line(d,(16,6,24,6),'#684c46');line(d,(38,12,49,12),'#684c46');line(d,(14,25,46,25),'#a98264')
put(im,'bench')
im,d=load('mailbox');r(d,(3,7,22,7),'#a6b8ae');r(d,(3,23,22,24),'#4f6976');r(d,(18,14,21,17),'#344e5a');r(d,(19,15,20,16),'#ddbc80');put(im,'mailbox')
im,d=load('notice');line(d,(3,3,48,3),'#cca47c');line(d,(3,34,48,34),'#634b45');r(d,(1,0,50,2),'#6f7a77')
# Hand-drawn cat, a tiny illustration on the pinned advert.
polygon(d,[(29,24),(31,22),(33,24),(36,24),(38,22),(39,26),(37,29),(31,29)],'#746f65');r(d,(32,26,32,26),'#ded2ae');r(d,(36,26,36,26),'#ded2ae');put(im,'notice')
# Two concrete visible changes after interactions.
im,d=load('boxes');polygon(d,[(13,1),(24,5),(24,11),(14,7)],'#dfbd8d');polygon(d,[(25,5),(38,1),(38,7),(25,11)],'#aa7c5b');r(d,(18,8,32,14),'#4b5556');r(d,(18,9,30,10),'#b9c3a5');r(d,(20,12,33,14),'#7e9690');put(im,'boxes_open')
im,d=load('plant');r(d,(8,23,18,24),'#5d5248');r(d,(9,25,10,27),'#bb8c67');put(im,'plant_watered')
im,d=load('mailbox');r(d,(6,18,17,18),'#4b6775');r(d,(7,20,13,20),'#4b6775');put(im,'mailbox_signed')
# Organic tree silhouette with branch structure and grouped leaves.
im=Image.new('RGBA',(68,80));d=ImageDraw.Draw(im)
polygon(d,[(27,77),(29,69),(28,52),(20,42),(23,40),(32,49),(38,36),(41,36),(35,54),(36,70),(41,78)],'#293e42')
line(d,(31,74,31,52,24,44),'#a18062');line(d,(33,68,32,52,39,42),'#705d50');line(d,(28,78,38,78),'#809185')
clusters=[(24,5,21,17),(12,12,23,18),(35,14,20,19),(4,25,22,17),(20,23,25,24),(40,29,22,17),(12,37,24,15),(31,40,23,14)]
for x,y,w,h in clusters:
 polygon(d,[(x+5,y),(x+w-6,y),(x+w-2,y+4),(x+w,y+8),(x+w-3,y+h-3),(x+w-8,y+h),(x+5,y+h),(x,y+h-5),(x,y+5)],'#2d4848')
 polygon(d,[(x+5,y+1),(x+w-7,y+1),(x+w-3,y+5),(x+w-2,y+h-6),(x+w-8,y+h-2),(x+5,y+h-3),(x+2,y+7)],'#466b5e')
 polygon(d,[(x+6,y+2),(x+w-8,y+2),(x+w-5,y+5),(x+w-9,y+7),(x+7,y+6),(x+3,y+9),(x+3,y+6)],'#6d8c72')
 for dx,dy in [(8,10),(13,8),(6,13)]:r(d,(x+dx,y+dy,x+dx+2,y+dy+1),'#587d67')
put(im,'tree')
# Smaller shrub clusters, flowers and grass replace the flat planter squares.
im,d=load('street')
for x in [38,300,555,879]:
 r(d,(x,246,x+36,283),'#31454c');r(d,(x+2,248,x+34,280),'#6d7370');r(d,(x+4,250,x+32,277),'#3b5049');line(d,(x+2,248,x+34,248),'#a4a997');line(d,(x+2,280,x+34,280),'#4b5a5c')
 for xx,yy in [(9,256),(22,257),(15,267),(28,269)]:
  polygon(d,[(x+xx-5,yy),(x+xx-3,yy-3),(x+xx,yy-6),(x+xx+3,yy-2),(x+xx+5,yy),(x+xx+2,yy+3),(x+xx-3,yy+2)],'#617e65')
  line(d,(x+xx-3,yy-2,x+xx,yy-3,x+xx+2,yy-1),'#91a481');r(d,(x+xx,yy-1,x+xx,yy+1),'#c7b38b');r(d,(x+xx-1,yy,x+xx+1,yy),'#d9b38c')
# Facade craft: drainpipes, cornices, bricks at edges, sill shadows and electrical boxes.
for x,y,w,h in [(64,61,216,157),(400,96,144,122),(625,70,238,148)]:
 for sy in range(y+20,y+h-12,12):
  r(d,(x+3,sy,x+9,sy+4),'#6b706e');line(d,(x+3,sy,x+9,sy),'#98928a');r(d,(x+w-9,sy+6,x+w-3,sy+10),'#5d706f')
 line(d,(x+w-17,y+17,x+w-17,y+h-8),'#354d56');line(d,(x+w-15,y+17,x+w-15,y+h-9),'#a1a59a');line(d,(x+w-17,y+h-8,x+w-12,y+h-8),'#344d56')
 for sy in range(y+24,y+h-15,28):r(d,(x+w-19,sy,x+w-14,sy+2),'#667779')
 line(d,(x+12,y+13,x+w-9,y+13),'#c5b69a');line(d,(x+2,y+h-2,x+w-2,y+h-2),'#82938b')
# A glowing home window and book silhouettes behind the upstairs glass.
for x,y,w in [(83,88,32),(491,130,32),(708,97,42)]:
 r(d,(x+3,y+3,x+w-3,y+26),'#c5ad80');r(d,(x+5,y+5,x+w-5,y+24),'#e3c294');r(d,(x+w//2,y+2,x+w//2+2,y+27),'#716d60')
 r(d,(x+6,y+4,x+9,y+24),'#a1866d');r(d,(x+w-10,y+4,x+w-6,y+24),'#b09574')
 line(d,(x+4,y+28,x+w-3,y+28),'#b3ba9e')
# Warm shop interior, glass diagonal glints and silhouettes of tea tins on shelves.
for x in [641,705]:
 r(d,(x+3,166,x+47,204),'#8d9b8d');r(d,(x+5,168,x+45,201),'#c3b88e')
 for sy in [180,194]:
  for sx in range(x+8,x+44,9):
   r(d,(sx,sy-8,sx+5,sy-1),'#6b8380' if sx%2 else '#b08269');r(d,(sx+1,sy-6,sx+4,sy-4),'#d8cca6')
  line(d,(x+6,sy,x+44,sy),'#716c5e')
 polygon(d,[(x+4,168),(x+9,168),(x+32,202),(x+27,202)],'#c8c5a3');line(d,(x+5,167,x+45,167),'#e3d8b1');r(d,(x+24,165,x+25,204),'#617c7b')
# Ornate but readable shop sign frame, not a flat banner.
line(d,(637,146,845,146),'#ead8af');line(d,(637,156,845,156),'#927d64');r(d,(640,149,642,151),'#6d7b71');r(d,(840,149,842,151),'#6d7b71')
# Repair tools, an electrical meter, doorbell plates, house number plate.
r(d,(411,173,445,195),'#cfbb93');line(d,(412,174,444,174),'#f0ddac');line(d,(413,190,443,190),'#9d896c')
line(d,(417,193,421,191,424,193),'#5b6262');line(d,(434,192,440,192),'#5b6262')
for x,y in [(194,191),(489,190),(836,190)]:
 r(d,(x,y,x+5,y+9),'#384e56');r(d,(x+1,y+1,x+4,y+7),'#a4a694');r(d,(x+2,y+5,x+3,y+6),'#485f68')
r(d,(146,159,203,172),'#3a555d');line(d,(147,159,202,159),'#bba579');line(d,(147,171,202,171),'#7a8e87')
# Worn road patches, chipped crossing, small drainage grates.
for x,y in [(106,336),(430,391),(773,332)]:
 r(d,(x,y,x+23,y+7),'#2b3e49')
 for sx in range(x+3,x+21,4):line(d,(sx,y+2,sx,y+5),'#778b8d')
for x,y in [(161,370),(535,351),(899,380)]:line(d,(x,y,x+9,y+2,x+14,y+1,x+23,y+4),'#293e49')
for y in [334,358,382]:r(d,(356,y,359,y+1),'#77898a');r(d,(385,y+3,389,y+4),'#586f77')
# Layered wet puddles with displaced reflections of lamp and windows.
for x,y,w in [(388,307,44),(848,438,43),(205,291,31),(627,310,25),(427,345,53)]:
 polygon(d,[(x,y+3),(x+7,y),(x+w-6,y+1),(x+w,y+4),(x+w-4,y+8),(x+5,y+9),(x,y+6)],'#496875');line(d,(x+7,y+1,x+w-8,y+1),'#819c9c');line(d,(x+8,y+7,x+w-5,y+7),'#617e87')
for y,w in [(299,9),(302,14),(306,8),(310,17)]:line(d,(406-w//2,y,406+w//2,y),'#bdac83')
# Curb weeds and a few fallen leaves: sparse authored placement.
for x,y in [(26,315),(279,319),(601,318),(911,319),(179,452),(694,452)]:
 line(d,(x,y,x-2,y-4),'#8a9e80');line(d,(x,y,x+1,y-5),'#728d76');line(d,(x,y,x+4,y-3),'#8a9e80')
for x,y in [(281,300),(552,309),(738,309),(752,318),(108,436)]:polygon(d,[(x,y),(x+3,y-2),(x+5,y),(x+2,y+1)],'#b4a47b')
put(im,'street')
# Room: patterned rug, subtle plank grain, skirting, real curtains and a radiator.
im,d=load('room')
for x,y in [(78,91),(182,118),(264,231),(305,176),(148,252),(376,250)]:
 line(d,(x,y,x+13,y),'#a18066');line(d,(x+2,y+2,x+7,y+2),'#775b50')
for y in range(40,56,6):
 for x in range(72,410,12):r(d,(x,y,x,y+1),'#b59e80')
line(d,(66,62,414,62),'#b99b79');line(d,(66,65,414,65),'#614c46')
# Curtains with folds and a scalloped hem.
for x in [211,272]:
 polygon(d,[(x,28),(x+14,28),(x+12,49),(x+10,55),(x+15,64),(x+5,64),(x+1,61)],'#557778')
 for dx in [3,8,12]:line(d,(x+dx,31,x+dx-2,48),'#86a39a')
 r(d,(x+4,51,x+11,53),'#c3ac80')
line(d,(208,26,290,26),'#664f48');r(d,(207,25,209,27),'#dab582');r(d,(290,25,292,27),'#dab582')
# Radiator belongs to the back wall, outside the walking area above the window light.
for x in range(225,270,5):r(d,(x,65,x+3,75),'#ccb695');line(d,(x,66,x,73),'#ebd4ad')
line(d,(224,75,272,75),'#705a4c');line(d,(224,68,219,68,219,74),'#b9a18b')
# Geometric woven border and faded central medallion.
r(d,(172,144,285,205),'#9eab91');r(d,(175,147,282,202),'#536e70');r(d,(178,150,279,199),'#66847c')
for x in range(180,280,8):
 polygon(d,[(x,151),(x+3,154),(x,157),(x-3,154)],'#a0b09a');polygon(d,[(x,193),(x+3,196),(x,199),(x-3,196)],'#a0b09a')
polygon(d,[(228,156),(263,175),(228,194),(193,175)],'#8b9e88');polygon(d,[(228,162),(252,175),(228,188),(204,175)],'#4e7273');polygon(d,[(228,168),(241,175),(228,182),(215,175)],'#baae89')
for y in range(148,204,4):line(d,(168,y,171,y),'#c5ba98');line(d,(286,y,289,y),'#c5ba98')
# Peg rail, hanging towel and a small landscape with its own perspective.
r(d,(299,39,319,42),'#745a4c');r(d,(303,41,304,44),'#ddc49c');r(d,(313,41,314,44),'#ddc49c');r(d,(306,45,316,60),'#78938a');line(d,(308,47,308,58),'#a8b5a0')
r(d,(134,39,151,54),'#d0bb90');r(d,(136,41,149,51),'#758b8b');polygon(d,[(136,50),(141,44),(146,49),(149,46),(149,52),(136,52)],'#446963');r(d,(145,42,147,44),'#f0d3a0')
# Contact shadows reinforce depth without changing the collision silhouettes.
r(d,(97,126,160,129),'#6b554d');r(d,(328,162,377,166),'#6b554d');r(d,(334,231,398,234),'#6b554d');r(d,(70,213,103,216),'#6b554d')
# Door mat and quiet contact shadows against the wall.
r(d,(222,253,265,266),'#5f655b');r(d,(224,255,263,264),'#a38a68')
for x in range(226,263,3):line(d,(x,256,x,263),'#897359')
put(im,'room')
im,d=load('stair')
# Separate stone landings, stair nosing, handrails and door number plaques.
for y in range(145,240,12):
 line(d,(264,y+1,367,y+1),'#bec1af');line(d,(264,y+7,367,y+7),'#71878a')
line(d,(257,143,257,244),'#a0b0a5');line(d,(374,143,374,244),'#8f9f99')
for y in range(150,240,25):line(d,(257,y,264,y+5),'#3c535e');line(d,(368,y+5,374,y),'#3c535e')
for x,y in [(137,81),(279,81)]:
 r(d,(x,y,x+48,y+17),'#445f64');r(d,(x+2,y+2,x+46,y+15),'#829587')
 for sx in range(x+5,x+45,5):line(d,(sx,y+4,sx,y+13),'#6b7f77')
line(d,(98,62,381,62),'#b2b5a1');r(d,(234,34,250,39),'#314a55');r(d,(237,35,247,38),'#ecd4a2');line(d,(242,40,242,51),'#a0a88f')
# A pair of muddy prints does not look like a solid obstacle.
for x,y in [(238,235),(247,222),(238,211)]:r(d,(x,y,x+3,y+5),'#647b7b');r(d,(x,y+7,x+3,y+8),'#647b7b')
put(im,'stair')
# More individual, readable silhouettes and walking highlights.
for name in ['hero','hero_1','hero_2','mira','arkady','lev']:
 im,d=load(name)
 for direction in range(4):
  for frame in range(4):
   x=frame*16;y=direction*24;b=int(frame in [1,3])
   def q(box,col):r(d,tuple(v+(x if i%2==0 else y) for i,v in enumerate(box)),col)
   q((5,20,5,21),'#526575');q((10,20,10,21),'#526575')
   q((2,16+b,3,18+b),'#d2a783');q((12,16+b,13,18+b),'#d2a783')
   q((4,12+b,4,16+b),'#e4bd88' if name.startswith('hero') else '#a5b7a5')
   if direction!=3:
    q((7,12+b,7,18),'#5e6460');q((9,14+b,10,14+b),'#dcc6a1')
   else:q((5,13+b,10,17),'#675c53');q((6,13+b,9,14+b),'#ae9471')
   q((5,2+b,8,2+b),'#66524b' if name.startswith('hero') else '#967060' if name=='mira' else '#c4c1ab' if name=='arkady' else '#615153')
   if name=='mira':
    q((12 if direction!=2 else 3,6+b,13 if direction!=2 else 4,11+b),'#76564d');q((12 if direction!=2 else 3,8+b,13 if direction!=2 else 4,8+b),'#d2b074')
   if name=='arkady':
    if direction!=3:q((6,8+b,10,8+b),'#b5ae95')
    q((4,17,11,18),'#746252');q((5,17,6,18),'#d3bd8c')
   if name=='lev' and direction==0:
    q((5,5+b,7,7+b),'#303b44');q((9,5+b,11,7+b),'#303b44');q((6,6+b,6,6+b),'#b4c2b5');q((10,6+b,10,6+b),'#b4c2b5')
 put(im,name)
# Portraits: original 32 px busts, matching the world sprites.
for name,coat,hair in [('hero','#c89d6b','#504342'),('mira','#91aba0','#815a50'),('arkady','#8499a0','#c4bda4'),('lev','#ad827a','#554649')]:
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
 polygon(d,[(3,31),(4,26),(10,22),(22,22),(28,26),(30,31)],'#263d48')
 polygon(d,[(6,31),(6,27),(12,24),(20,24),(26,27),(27,31)],coat)
 r(d,(12,20,20,25),'#b88970');r(d,(12,23,20,24),'#ead3a5')
 polygon(d,[(8,7),(11,4),(22,4),(25,8),(25,17),(22,22),(12,22),(8,17)],'#303e47')
 polygon(d,[(10,9),(14,7),(22,9),(23,17),(20,21),(13,20),(10,17)],'#dfb28c')
 r(d,(9,12,10,15),'#b58b72');r(d,(24,12,25,15),'#b58b72')
 polygon(d,[(8,10),(8,7),(11,3),(21,3),(25,7),(25,12),(22,9),(16,8),(12,11)],hair)
 line(d,(12,5,20,5),'#ab8165' if name=='mira' else '#d6cdb1' if name=='arkady' else '#7f6456')
 r(d,(13,13,14,14),'#2f4048');r(d,(20,13,21,14),'#2f4048');r(d,(17,16,18,17),'#c89576');line(d,(15,19,19,19),'#a57566')
 if name=='mira':polygon(d,[(25,9),(28,13),(26,22),(23,21),(24,16)],hair);r(d,(25,15,27,16),'#d3b57b')
 if name=='arkady':r(d,(13,18,21,19),'#b6ae96');r(d,(8,8,10,12),'#e0d5b9');r(d,(22,8,24,11),'#e0d5b9')
 if name=='lev':
  for x in [11,19]:d.rectangle((x,12,x+5,16),outline='#3c4a51');line(d,(16,13,19,13),'#3c4a51')
 put(im,'portrait_'+name)
# Notebook icon for environmental dialogue.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
r(d,(6,5,26,28),'#172e3b');r(d,(7,4,24,26),'#d7c49c');r(d,(8,5,23,24),'#eee0b7');r(d,(7,5,9,25),'#8e6b57')
for y in [10,14,18]:line(d,(12,y,21,y),'#8b9c90')
line(d,(20,21,27,12),'#e5b16e');line(d,(19,22,20,20),'#485b62');put(im,'portrait_note')
# Pixel lights: a few deliberately quantized levels, with no blur filtering.
for name,size,col in [('glow_warm',96,(242,191,116)),('glow_cool',80,(159,195,191))]:
 im=Image.new('RGBA',(size,size));pix=im.load()
 for y in range(size):
  for x in range(size):
   dist=math.hypot((x-size/2)/(size/2),(y-size/2)/(size/2))
   alpha=int(max(0,1-dist)**2*38)//3*3
   if alpha:pix[x,y]=(*col,alpha)
 put(im,name)
print('Art pass: crafted materials, wet reflections, foliage, character silhouettes, portraits and pixel lights.')
# An original desktop/window mark: a tiny working CRT and a living sprout.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
polygon(d,[(5,2),(27,2),(30,5),(30,23),(27,25),(5,25),(2,22),(2,5)],'#263c49')
r(d,(4,4,27,22),'#d7b88c');r(d,(5,4,26,5),'#f1d7a6');r(d,(6,7,25,19),'#304b56');r(d,(8,9,23,17),'#789e94')
line(d,(10,11,12,13,10,15),'#f0dfaf');line(d,(15,15,19,15),'#f0dfaf');r(d,(24,21,25,22),'#e6d9a5')
r(d,(13,25,18,27),'#806b59');r(d,(8,28,23,29),'#283f4c');r(d,(9,27,22,27),'#b8a17e')
line(d,(26,31,26,25,24,22),'#839b73');polygon(d,[(26,26),(27,22),(30,21),(30,24)],'#b0bd83');polygon(d,[(25,25),(22,24),(22,21),(25,22)],'#8eab78')
put(im,'icon32')
for size in [64,128]:im.resize((size,size),Image.Resampling.NEAREST).save(A/f'icon{size}.png')
