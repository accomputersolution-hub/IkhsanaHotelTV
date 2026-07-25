from PIL import Image, ImageDraw
import os
import math

root = r"C:\Users\mdsal\IkhsanaHotelTV\app\src\main\res"
for d in ["drawable", "drawable-hdpi", "drawable-xhdpi", "drawable-nodpi"]:
    os.makedirs(os.path.join(root, d), exist_ok=True)


def save_all(name, img):
    img.save(os.path.join(root, "drawable-nodpi", f"{name}.png"))
    img.save(os.path.join(root, "drawable", f"{name}.png"))
    img.save(os.path.join(root, "drawable-hdpi", f"{name}.png"))
    img2 = img.resize((img.width * 2, img.height * 2), Image.Resampling.LANCZOS)
    img2.save(os.path.join(root, "drawable-xhdpi", f"{name}.png"))
    print("saved", name, img.size)


# Lotus logo
s = 128
logo = Image.new("RGBA", (s, s), (0, 0, 0, 0))
d = ImageDraw.Draw(logo)
cx, cy = s // 2, s // 2 - 8
gold = (201, 169, 98, 255)
gold_l = (232, 213, 163, 255)
for i in range(4):
    ang = math.radians(i * 90 + 45)
    pts = []
    for t in range(0, 360, 10):
        rad = math.radians(t)
        lx = 18 * math.cos(rad)
        ly = 36 * math.sin(rad)
        ly *= 0.55 if ly > 0 else 1.0
        rx = lx * math.cos(ang) - ly * math.sin(ang)
        ry = lx * math.sin(ang) + ly * math.cos(ang)
        pts.append((cx + rx, cy + ry - 6))
    d.polygon(pts, fill=gold)
for i in range(4):
    ang = math.radians(i * 90)
    pts = []
    for t in range(0, 360, 12):
        rad = math.radians(t)
        lx = 10 * math.cos(rad)
        ly = 22 * math.sin(rad)
        if ly > 0:
            ly *= 0.5
        rx = lx * math.cos(ang) - ly * math.sin(ang)
        ry = lx * math.sin(ang) + ly * math.cos(ang)
        pts.append((cx + rx, cy + ry - 4))
    d.polygon(pts, fill=gold_l)
d.rounded_rectangle([cx - 28, s - 36, cx + 28, s - 30], radius=2, fill=gold)
d.rounded_rectangle([cx - 20, s - 28, cx + 20, s - 23], radius=2, fill=gold)
d.rounded_rectangle([cx - 10, s - 20, cx + 10, s - 16], radius=1, fill=gold)
save_all("ic_logo", logo)


def icon_base():
    return Image.new("RGBA", (96, 96), (0, 0, 0, 0))


white = (241, 245, 249, 255)
cyan = (34, 211, 238, 255)
green = (52, 211, 153, 255)
blue = (56, 189, 248, 255)
purple = (99, 102, 241, 255)

img = icon_base()
d = ImageDraw.Draw(img)
d.rounded_rectangle([12, 18, 84, 68], radius=6, outline=white, width=4)
d.rectangle([20, 26, 76, 56], fill=(15, 30, 50, 255))
d.polygon([(22, 54), (40, 36), (52, 48), (64, 32), (76, 54)], fill=cyan)
d.ellipse([28, 30, 38, 40], fill=cyan)
d.rectangle([40, 68, 56, 76], fill=white)
d.rectangle([28, 76, 68, 82], fill=white)
save_all("ic_live_tv", img)

img = icon_base()
d = ImageDraw.Draw(img)
d.rectangle([28, 18, 34, 70], fill=green)
for x in [22, 28, 34]:
    d.rectangle([x, 18, x + 5, 40], fill=green)
d.rectangle([22, 38, 40, 44], fill=green)
d.polygon([(62, 18), (72, 18), (68, 55), (60, 55)], fill=green)
d.rectangle([62, 55, 68, 78], fill=green)
save_all("ic_dining", img)

img = icon_base()
d = ImageDraw.Draw(img)
d.ellipse([22, 28, 74, 70], outline=blue, width=5)
d.rectangle([18, 68, 78, 76], fill=blue)
d.ellipse([42, 18, 54, 28], fill=blue)
# dome
d.arc([22, 22, 74, 72], 180, 360, fill=blue, width=5)
save_all("ic_services", img)

img = icon_base()
d = ImageDraw.Draw(img)
d.rounded_rectangle([16, 16, 80, 64], radius=10, fill=purple)
d.polygon([(28, 64), (40, 64), (24, 82)], fill=purple)
d.rectangle([28, 32, 68, 38], fill=white)
d.rectangle([28, 44, 58, 50], fill=white)
save_all("ic_alerts", img)

print("done")
