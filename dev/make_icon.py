#!/usr/bin/env python3
"""Compose the pack icon from Blockbench renders in dev/icon/frames/ (transparent PNGs, one per animation frame).

Outputs: dist/icon-animated.gif (Modrinth, <=256 KiB), dist/icon-512.png,
pack/pack.png (128px still), dev/icon/contact.png (frame sheet for review).
Source scene + animation: dev/icon/build_scene.js -> dev/icon/full_ghast_ahead_icon_anim.bbmodel (Aseprite textures from
dev/icon/draw_sprites.lua). The crop is fixed around frame 0 (the ghast only bobs).
Background: flat sky (sprites/bg_plain, 64px, x8) plus passing clouds that scroll left on that 64px grid: two layers behind
the ghast and one in front. Each layer moves a whole number of grid pixels per frame and wraps after exactly FRAMES frames,
so the loop is seamless."""
import os
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter, ImageMath

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "dev" / "icon"
SPRITES = ICON / "sprites"
BACKGROUND = SPRITES / (os.environ.get("BG", "bg_plain") + ".png")
FRAMES = Path(os.environ.get("FRAMES", ICON / "frames"))
DIST = Path(os.environ.get("DIST", ROOT / "dist"))
S = 512
GRID = 64
FPS = 25
STILL_FRAME = 0
GIF_SIZE = 256
GIF_LIMIT = 256 * 1024
CROP_MARGIN = 1.4
PACK_MARGIN = 1.12
OUTLINE = 19
# (sprite, x at frame 0, y, grid px per frame, wrap width, in front of the ghast); speed * frames must be a multiple of wrap
CLOUDS = [
    ("cloud_far", 4, 4, 1, 64, False), ("cloud_far", 36, 27, 1, 64, False), ("cloud_far", 18, 57, 1, 64, False),
    ("cloud_mid", 8, 12, 2, 128, False), ("cloud_mid", 74, 44, 2, 128, False),
    ("cloud_front", 120, 53, 3, 192, True),
]


def write_gif(frames: list, out: Path, fps: int, key_mask: Image.Image | None = None, delta: bool = True) -> int:
    """Write RGB frames as a GIF with an exact palette (Pillow's quantize(palette=...) snaps close colours together).
    Slot 255 is transparent: the key_mask pixels in every frame, plus (delta) every pixel unchanged since the previous frame
    (disposal 1 keeps what is underneath), so clouds moving across a wide frame don't force full-frame redraws. Without
    delta, Pillow crops each frame to the box that changed, which wins on a small square frame. Returns the colour count."""
    used = sorted({c for f in frames for _, c in f.getcolors(f.width * f.height)})
    if len(used) > 255:
        sys.exit(f"{len(used)} colours - more than a GIF palette holds next to the transparent slot")
    # a collision-free 8-bit hash of (r, g, b) turns each frame into palette indices with two C-level passes
    for mul in ((a, b, c) for a in range(1, 64, 2) for b in range(1, 64, 2) for c in range(1, 64, 2)):
        hashes = [(r * mul[0] + g * mul[1] + b * mul[2]) & 255 for r, g, b in used]
        if len(set(hashes)) == len(used):
            break
    else:
        sys.exit("no collision-free colour hash found")
    lut = [0] * 256
    for i, h in enumerate(hashes):
        lut[h] = i
    pal = [v for c in used for v in c] + [0, 0, 0] * (255 - len(used)) + [255, 0, 255]
    gif, prev = [], None
    for f in frames:
        r, g, b = f.split()
        h = ImageMath.lambda_eval(lambda a: (a["r"] * mul[0] + a["g"] * mul[1] + a["b"] * mul[2]) & 255, r=r, g=g, b=b)
        idx = h.convert("L").point(lut)
        q = idx.copy()
        if key_mask is not None:
            q.paste(255, mask=key_mask)
        if delta and prev is not None:
            q.paste(255, mask=ImageChops.difference(idx, prev).point(lambda v: 255 if v == 0 else 0))
        prev = idx
        p = Image.frombytes("P", q.size, q.tobytes())
        p.putpalette(pal)
        gif.append(p)
    gif[0].save(out, save_all=True, append_images=gif[1:], duration=1000 // fps, loop=0, optimize=True, disposal=1,
                transparency=255)
    return len(used)


def sprite(name: str) -> Image.Image:
    return Image.open(SPRITES / f"{name}.png").convert("RGBA")


def cloud_layer(frame: int, front: bool, width: int = GRID, clouds=None) -> Image.Image:
    """Clouds of one depth for this frame on the background grid (not scaled)."""
    layer = Image.new("RGBA", (width, GRID), (0, 0, 0, 0))
    for name, x0, y, speed, wrap, is_front in clouds or CLOUDS:
        if is_front != front:
            continue
        spr = sprite(name)
        x = (x0 - speed * frame) % wrap
        for k in (-1, 0, 1):
            xx = x + k * wrap
            if xx + spr.width > 0 and xx < width:
                layer.paste(spr, (xx, y), spr)
    return layer


def compose(art: Image.Image, frame: int) -> Image.Image:
    bg = Image.open(BACKGROUND).convert("RGBA")
    bg.alpha_composite(cloud_layer(frame, False))
    bg = bg.resize((S, S), Image.NEAREST)
    outline_layer = Image.new("RGBA", (S, S), (10, 12, 18, 0))
    outline_layer.putalpha(art.getchannel("A").filter(ImageFilter.MaxFilter(OUTLINE)))
    out = Image.alpha_composite(Image.alpha_composite(bg, outline_layer), art)
    return Image.alpha_composite(out, cloud_layer(frame, True).resize((S, S), Image.NEAREST))


def crop_box(rest: Image.Image) -> tuple:
    b = rest.getchannel("A").getbbox()
    side = int(max(b[2] - b[0], b[3] - b[1]) * CROP_MARGIN)
    cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    return (cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)


def build(write_pack: bool = True) -> tuple:
    files = sorted(FRAMES.glob("frame_*.png"))
    if not files:
        sys.exit(f"no frames in {FRAMES} - render them from Blockbench first")
    for name, _, _, speed, wrap, _ in CLOUDS:
        if speed * len(files) % wrap:
            sys.exit(f"{name}: {speed} px/frame x {len(files)} frames does not wrap {wrap}")
    raw = [Image.open(f).convert("RGBA") for f in files]
    crop = crop_box(raw[0])
    arts = [im.crop(crop).resize((S, S), Image.NEAREST) for im in raw]
    frames = [compose(a, i) for i, a in enumerate(arts)]

    DIST.mkdir(parents=True, exist_ok=True)
    if write_pack:
        # pack.png gets its own square crop around the still's art: the loop crop leaves room for the clouds
        sb = arts[STILL_FRAME].getchannel("A").getbbox()
        ps = int(max(sb[2] - sb[0], sb[3] - sb[1]) * PACK_MARGIN)
        px = min(max((sb[0] + sb[2] - ps) // 2, 0), S - ps)
        py = min(max((sb[1] + sb[3] - ps) // 2, 0), S - ps)
        frames[STILL_FRAME].crop((px, py, px + ps, py + ps)).convert("RGB").resize((128, 128), Image.LANCZOS).save(
            ROOT / "pack" / "pack.png", optimize=True)
        frames[STILL_FRAME].convert("RGB").save(DIST / "icon-512.png", optimize=True)

    small = [f.convert("RGB").resize((GIF_SIZE, GIF_SIZE), Image.NEAREST) for f in frames]
    out = DIST / "icon-animated.gif"
    write_gif(small, out, FPS, delta=False)

    if write_pack:
        cols = 8
        rows = (len(frames) + cols - 1) // cols
        contact = Image.new("RGB", (cols * 128, rows * 128))
        for i, f in enumerate(frames):
            contact.paste(f.convert("RGB").resize((128, 128), Image.LANCZOS), ((i % cols) * 128, (i // cols) * 128))
        contact.save(ICON / "contact.png")
    return out, frames, small


def main() -> None:
    out, frames, small = build()
    size = out.stat().st_size
    bgpx = Image.open(out).convert("RGB").getpixel((GIF_SIZE // 2, 2))
    want = Image.open(BACKGROUND).convert("RGB").getpixel((GRID // 2, 0))
    print(f"{len(frames)} frames @ {FPS} fps -> {out.relative_to(ROOT)} {size / 1024:.0f} KiB "
          f"({'OK' if size <= GIF_LIMIT else 'OVER'} Modrinth 256 KiB limit); still = frame {STILL_FRAME} -> pack/pack.png, "
          f"dist/icon-512.png; sky pixel {bgpx} (sprite {want})")
    if size > GIF_LIMIT:
        sys.exit(1)


if __name__ == "__main__":
    main()
