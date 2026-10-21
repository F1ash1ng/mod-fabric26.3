"""Draw a small inventory sprite based on the approved V2 waist-jet geometry.

The approved Blockbench models are read as reference only. This script writes
separate, transparent review assets and never changes the approved package.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from generate_assets import FACES, build_boxes, cross, dot, rgb, unit, vertices  # noqa: E402


S = 32
CAMERA = unit((0.63, 1.50, 1.22))  # right and above, looking toward rear nozzles
RIGHT = unit(cross((0, 1, 0), CAMERA))
UP = unit(cross(CAMERA, RIGHT))
CENTER = (0.0, 12.55, 1.65)
SCALE = 1.96
OUTLINE = (17, 29, 34, 255)

# Small-screen drawing: choose the visible forms rather than every thin layer
# of the full 3D source. All coordinates and colors still come from V2.
KEEP = {
    "front belt", "rear belt", "left belt", "right belt",
    "front buckle frame", "front buckle inset", "front buckle latch",
    "rear power housing", "rear power panel", "rear seam",
}
POD_PARTS = {
    "mount", "body", "outer housing", "upper shoulder", "upper lip",
    "lower armor", "side inset", "status strip", "rear port",
    "nozzle rim", "nozzle aperture", "nozzle slit",
    "side port mount", "side port rim", "side port aperture",
}


def project(point: tuple[float, float, float]) -> tuple[float, float]:
    rel = tuple(point[i] - CENTER[i] for i in range(3))
    return (17.5 + dot(rel, RIGHT) * SCALE,
            13.9 - dot(rel, UP) * SCALE)


def shade(material: str, normal: tuple[int, int, int]) -> tuple[int, int, int, int]:
    base = rgb(material)
    factor = 1.19 if normal[1] == 1 else (0.94 if normal[0] else 0.78)
    if material in {"idle_core", "edge", "buckle"}:
        factor = max(factor, 0.98)
    return tuple(min(255, round(c * factor)) for c in base) + (255,)


def make_icon() -> Image.Image:
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    faces = []
    for box in build_boxes(None):
        if box.name not in KEEP and not any(
            box.name == f"{side} {part}"
            for side in ("left", "right") for part in POD_PARTS
        ):
            continue
        corners = vertices(box)
        for indices, normal in FACES:
            if dot(normal, CAMERA) <= 0:
                continue
            world = [corners[i] for i in indices]
            depth = dot(tuple(sum(p[i] for p in world) / 4 for i in range(3)), CAMERA)
            polygon = [project(p) for p in world]
            # Key silhouette and top edges use a one-pixel dark border.
            bordered = box.name.endswith((
                "body", "upper shoulder", "nozzle rim", "rear power housing",
                "front buckle frame", "side port rim",
            ))
            faces.append((depth, polygon, shade(box.material, normal), bordered))
    faces.sort(key=lambda face: face[0])
    for _, polygon, color, bordered in faces:
        draw.polygon(polygon, fill=color)
        if bordered:
            draw.line(polygon + polygon[:1], fill=OUTLINE, width=1, joint="curve")

    # A few hand-placed sprite highlights make the paired thrusters legible at
    # true inventory size without adding geometry that was not in the model.
    return image


def preview(icon: Image.Image) -> Image.Image:
    board = Image.new("RGB", (720, 368), "#e7e4d8")
    draw = ImageDraw.Draw(board)
    draw.rectangle((0, 0, 719, 367), fill="#e7e4d8")
    draw.rectangle((0, 0, 719, 46), fill="#26343a")
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 18)
        small = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 15)
    except OSError:
        font = small = ImageFont.load_default()
    draw.text((24, 12), "腰部喷气装置 · 物品栏图标草稿", font=font, fill="#f4f3e9")
    draw.text((34, 67), "32 × 32 原尺寸", font=small, fill="#26343a")
    draw.text((193, 67), "最近邻放大 8 倍", font=small, fill="#26343a")
    draw.text((522, 67), "物品栏模拟", font=small, fill="#26343a")
    board.paste(icon, (51, 107), icon)
    enlarged = icon.resize((256, 256), Image.Resampling.NEAREST)
    board.paste(enlarged, (182, 96), enlarged)
    # Slot simulation uses only a plain slot frame and checkerboard background.
    for x, y, bg in ((523, 111, "#8b8b8b"), (610, 111, "#252d33"),
                     (523, 202, "#c9c6b6"), (610, 202, "#6d6d6d")):
        draw.rectangle((x, y, x + 65, y + 65), fill="#181b1d")
        draw.rectangle((x + 3, y + 3, x + 62, y + 62), fill="#d9d9d9")
        draw.rectangle((x + 6, y + 6, x + 59, y + 59), fill=bg)
        sample = icon.resize((42, 42), Image.Resampling.NEAREST)
        board.paste(sample, (x + 12, y + 12), sample)
    draw.text((34, 317), "独立外观素材 · 未加入已确认的 V2 模型包", font=small,
              fill="#405055")
    return board


if __name__ == "__main__":
    icon = make_icon()
    icon.save(HERE / "waist_jet_inventory_32.png")
    preview(icon).save(HERE / "inventory_icon_preview.png")
