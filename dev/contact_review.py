#!/usr/bin/env python3
"""Dev-only motion review: contact sheet of rendered frames over a placeholder sky, crop = frame 0 bbox x MARGIN.
Usage: contact_review.py <frames_dir> <out.png> [margin] [first last] [cell]"""
import glob
import sys
from PIL import Image, ImageDraw

src, out = sys.argv[1], sys.argv[2]
margin = float(sys.argv[3]) if len(sys.argv) > 3 else 1.5
allf = sorted(glob.glob(src + '/frame_*.png'))
fs = allf[int(sys.argv[4]):int(sys.argv[5]) + 1] if len(sys.argv) > 5 else allf
cell = int(sys.argv[6]) if len(sys.argv) > 6 else 180
ims = [Image.open(f).convert('RGBA') for f in fs]
b0 = Image.open(allf[0]).getchannel('A').getbbox()
cx, cy = (b0[0] + b0[2]) // 2, (b0[1] + b0[3]) // 2
side = int(max(b0[2] - b0[0], b0[3] - b0[1]) * margin)
crop = (cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2)
cols = 10
sheet = Image.new('RGB', (cols * cell, ((len(ims) + cols - 1) // cols) * cell), (0, 0, 0))
d = ImageDraw.Draw(sheet)
for i, im in enumerate(ims):
    bg = Image.new('RGBA', (side, side), (75, 152, 216, 255))
    bg.alpha_composite(im.crop(crop))
    sheet.paste(bg.convert('RGB').resize((cell - 4, cell - 4), Image.NEAREST), ((i % cols) * cell + 2, (i // cols) * cell + 2))
    d.text(((i % cols) * cell + 6, (i // cols) * cell + 4), fs[i][-7:-4], fill=(255, 255, 255))
sheet.save(out)
print(out, 'crop', crop)
