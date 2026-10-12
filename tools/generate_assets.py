"""Original pixel art for the trading counter. Optional: Python 3 + Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw
import random

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/vmexchange'
rng = random.Random(41)
image = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(image)

def rect(box, color): d.rectangle(box, fill=color)
def bevel(x, y, w, h, face, light, dark):
    rect((x, y, x+w-1, y+h-1), dark)
    rect((x, y, x+w-2, y+h-2), light)
    rect((x+1, y+1, x+w-2, y+h-2), face)

def well(x, y):
    bevel(x-1, y-1, 18, 18, '#8b8375', '#534f48', '#e4dfd0')
    rect((x, y, x+15, y+15), '#8b8375')
    rect((x, y, x+15, y), '#716b61')

def rivet(x,y):
    rect((x,y,x+3,y+3), '#4f4836')
    rect((x,y,x+2,y+2), '#c7b174')
    rect((x,y,x+1,y), '#f0d696')

# Cut, blocky stone corners and a four-pixel vanilla-like outer bevel.
rect((2,0,221,233),'#302c28'); rect((0,2,223,231),'#302c28')
rect((2,1,220,231),'#ebe4d5'); rect((1,2,220,230),'#ebe4d5')
rect((3,3,219,230),'#81796c'); rect((4,4,218,229),'#c7bfaf')
for _ in range(330):
    x,y=rng.randrange(5,218),rng.randrange(5,229)
    rect((x,y,x+1,y),rng.choice(['#c4bcac','#c9c1b1','#c3bbaa']))
# A dark oak nameplate, edged in brass; no gradients or antialiasing.
bevel(7,7,210,18,'#483a29','#a68d59','#29261e')
rect((9,9,214,9),'#695039'); rect((9,22,214,23),'#352b21')
for x in range(13,213,13): rect((x,20,x+6,20),'#53422e')
rivet(10,13); rivet(210,13)
# Exchange tray; inset parchment/stone, distinct from the player's inventory.
bevel(12,39,200,38,'#b0a694','#8d806d','#ece3cf')
for x in [30,78,126,174]: well(x,44)
# Paired resource routing lines terminating in a golden arrow.
rect((38,78,183,78),'#96805a')
rect((38,75,38,78),'#96805a'); rect((182,75,182,78),'#96805a')
rect((110,78,113,81),'#c7a653'); rect((108,80,115,81),'#c7a653')
rect((110,82,113,83),'#c7a653')
bevel(101,81,22,22,'#9a9a9a','#d0d0d0','#565656'); well(104,84)
# Progress track and continuous lower inventory panel.
bevel(16,142,192,6,'#5b5548','#857b65','#e6dac1')
rect((22,150,201,150),'#9b907a'); rect((22,151,201,151),'#e4dac6')
for row in range(3):
    for col in range(9): well(32+col*18,154+row*18)
for col in range(9): well(32+col*18,212)
for x,y in [(8,8),(212,8),(8,220),(212,220)]: rivet(x,y)
(ROOT/'textures/gui').mkdir(parents=True,exist_ok=True)
image.save(ROOT/'textures/gui/exchange.png')

# Six-sided in-world block: a stone casing, oak counter, brass V emblem.
block_root=ROOT/'textures/block'; block_root.mkdir(parents=True,exist_ok=True)
for face in ['side','front','top','bottom']:
    tile=Image.new('RGBA',(16,16),'#766f60'); t=ImageDraw.Draw(tile)
    for y in range(16):
        for x in range(16):
            n=rng.choice([-7,-3,0,3,6]); t.point((x,y),fill=(118+n,111+n,96+n,255))
    t.rectangle((0,0,15,1),fill='#bbb09a'); t.rectangle((0,14,15,15),fill='#4c473d')
    if face=='top':
        t.rectangle((1,2,14,13),fill='#745437')
        for y in [3,7,11]:
            t.line((1,y,14,y),fill='#99754d'); t.line((1,y+1,14,y+1),fill='#4e3b28')
        t.rectangle((4,4,11,11),fill='#3d3324'); t.rectangle((5,5,10,10),fill='#c9a44f')
        t.rectangle((6,6,9,9),fill='#e1c772'); t.line((6,6,7,8,8,9,9,6),fill='#70552b',width=1)
    elif face=='front':
        t.rectangle((2,3,13,12),fill='#43362a'); t.line((2,3,13,3),fill='#9c7a4d')
        t.rectangle((6,4,9,4),fill='#caaa58'); t.rectangle((4,5,11,10),fill='#caaa58')
        t.rectangle((5,6,10,11),fill='#e2c777'); t.line((6,6,7,9,8,9,9,6),fill='#72512c')
        t.line((3,12,12,12),fill='#211e19')
    elif face=='side':
        t.rectangle((3,4,12,11),fill='#5e5545')
        for y in [5,7,9]: t.line((4,y,11,y),fill='#2f302a'); t.line((4,y+1,11,y+1),fill='#92816a')
    for x,y in [(1,2),(13,2),(1,12),(13,12)]:
        t.rectangle((x,y,x+1,y+1),fill='#c2a65e'); t.point((x,y),fill='#e4d096')
    tile.save(block_root/('exchange_'+face+'.png'))
# Fabric's mod-list icon uses the same original front face with nearest-neighbour pixels.
Image.open(block_root/'exchange_front.png').resize((128,128),Image.Resampling.NEAREST).save(ROOT/'icon.png')
print('Generated exchange GUI, four block faces and mod icon.')
