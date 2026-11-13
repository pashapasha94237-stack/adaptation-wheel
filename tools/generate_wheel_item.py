from pathlib import Path
from PIL import Image, ImageDraw
import math
root=Path(__file__).resolve().parent.parent
directory=root/"src/main/resources/assets/adaptationwheel/textures"
(directory/"item").mkdir(parents=True,exist_ok=True)
im=Image.new("RGBA",(32,32))
d=ImageDraw.Draw(im)
d.ellipse((7,7,25,25),outline=(235,191,64,255),width=2)
for i in range(8):
    a=i*math.pi/4
    x,y=round(16+13*math.sin(a)),round(16+13*math.cos(a))
    d.line((16,16,x,y),fill=(241,202,79,255),width=2)
    d.rectangle((x-2,y-2,x+2,y+2),fill=(171,119,28,255))
    d.rectangle((x-1,y-2,x+1,y+1),fill=(250,218,112,255))
    d.point((x,y-1),fill=(255,244,164,255))
d.rectangle((14,14,18,18),fill=(235,187,57,255))
d.rectangle((15,14,17,16),fill=(255,231,131,255))
im.save(directory/"item/wheel.png")
Image.new("RGBA",(64,32)).save(directory/"entity/blank.png")
print("Item icon and transparent armour texture generated.")
