import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
W_, H_ = 1080, 1920
src, out_dir = sys.argv[1], sys.argv[2]
SHOTS = [
    (12, "La télécommande\nde toutes vos TV", "One remote\nfor all your TVs"),
    (6,  "Wi-Fi, infrarouge\net Bluetooth", "Wi-Fi, infrared\nand Bluetooth"),
    (17, "Pavé numérique,\ncouleurs et lecture", "Number pad,\ncolor keys, playback"),
    (19, "Photos, vidéos et écran\ndu téléphone sur la TV", "Phone photos, videos\nand screen on your TV"),
    (18, "Tapez vos recherches\ndepuis le téléphone", "Type your searches\nfrom your phone"),
    (4,  "Disponible\nen 15 langues", "Available\nin 15 languages"),
    (8,  "Sans compte,\nsans publicité", "No account,\nno ads"),
]
font = None
for path in ["/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
    try:
        font = ImageFont.truetype(path, 66); break
    except OSError: pass

def background():
    bg = Image.new("RGB", (W_, H_), (14, 18, 34))
    glow = Image.new("RGB", (W_, H_), (14, 18, 34))
    d = ImageDraw.Draw(glow)
    d.ellipse((-200, -500, W_ + 200, 700), fill=(36, 70, 170))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    return Image.blend(bg, glow, 0.85)

for lang_idx, lang in ((1, "fr-FR"), (2, "en-US")):
    for n, item in enumerate(SHOTS, 1):
        num, caption = item[0], item[lang_idx]
        shot = Image.open(f"{src}/{num}.jpg").convert("RGB").crop((0, 95, 1080, 2255))
        h = 1440; w = round(shot.width * h / shot.height)
        shot = shot.resize((w, h), Image.LANCZOS)
        canvas = background()
        x, y = (W_ - w) // 2, H_ - h - 70
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, w, h), 44, fill=255)
        shadow = Image.new("RGBA", (W_, H_), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((x - 6, y + 10, x + w + 6, y + h + 16), 50, fill=(0, 0, 0, 160))
        shadow = shadow.filter(ImageFilter.GaussianBlur(24))
        canvas.paste(shadow, (0, 0), shadow)
        frame = Image.new("RGB", (w + 12, h + 12), (60, 80, 140))
        fmask = Image.new("L", frame.size, 0)
        ImageDraw.Draw(fmask).rounded_rectangle((0, 0, w + 11, h + 11), 50, fill=255)
        canvas.paste(frame, (x - 6, y - 6), fmask)
        canvas.paste(shot, (x, y), mask)
        d = ImageDraw.Draw(canvas)
        d.multiline_text((W_ // 2, 215), caption, font=font, fill="white", anchor="mm", align="center", spacing=14)
        canvas.save(f"{out_dir}/{lang}/{n}.png", optimize=True)
print("ok")
