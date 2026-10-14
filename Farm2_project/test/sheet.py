import sys
from PIL import Image
# usage: sheet.py outdir name1 name2 ... -> outdir/sheet.png
d=sys.argv[1]; fs=sys.argv[2:]
ims=[Image.open(f'{d}/{f}.png') for f in fs]
w=max(i.width for i in ims); h=max(i.height for i in ims)
cols=min(len(ims),5); rows=(len(ims)+cols-1)//cols
o=Image.new('RGB',(cols*(w+6),rows*(h+6)),'gray')
for k,i in enumerate(ims): o.paste(i,((k%cols)*(w+6),(k//cols)*(h+6)))
o.save(f'{d}/sheet.png')
