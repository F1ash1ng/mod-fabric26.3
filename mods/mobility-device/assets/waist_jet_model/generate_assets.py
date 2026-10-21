"""Generate the editable waist-jet models and review images.

This is an art source generator. It does not register or activate a game item.
Run with the bundled Python/Pillow runtime or any Python with Pillow installed.
"""

from __future__ import annotations

import base64
import io
import json
import math
import uuid
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
TEX_SIZE = (64, 16)
COLORS = {
    "belt": "#303d40",
    "strap": "#394b4d",
    "shell": "#293d45",
    "shell_lit": "#3c5660",
    "panel": "#5a7074",
    "edge": "#82918e",
    "buckle": "#a4aaa0",
    "recess": "#18272d",
    "nozzle": "#415961",
    "idle_core": "#528b92",
    "hot_core": "#8df3ed",
    "jet_blue": "#41aeca",
    "jet_cyan": "#5ad5df",
    "jet_pale": "#b5f2ee",
    "jet_white": "#eefbf2",
    "shadow": "#1d3037",
}
COLOR_NAMES = list(COLORS)


def rgb(color: str) -> tuple[int, int, int]:
    value = COLORS.get(color, color).lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def make_texture() -> Image.Image:
    image = Image.new("RGBA", TEX_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # Four-pixel swatches keep every cube face visibly pixelated at Minecraft scale.
    for index, name in enumerate(COLOR_NAMES):
        x, y = (index % 16) * 4, (index // 16) * 4
        base = rgb(name)
        draw.rectangle((x, y, x + 3, y + 3), fill=(*base, 255))
        if name not in {"jet_white", "jet_pale", "recess"}:
            lighter = tuple(min(255, c + 6) for c in base)
            darker = tuple(max(0, c - 7) for c in base)
            draw.point((x + 1, y), fill=(*lighter, 255))
            draw.point((x + 3, y + 2), fill=(*darker, 255))
    return image


@dataclass(frozen=True)
class Box:
    name: str
    group: str
    xyz0: tuple[float, float, float]
    xyz1: tuple[float, float, float]
    material: str


def add(boxes, name, group, xyz0, xyz1, material):
    assert all(a < b for a, b in zip(xyz0, xyz1)), name
    boxes.append(Box(name, group, xyz0, xyz1, material))


DIRECTION_PORTS = {
    "forward": ("rear",),
    "forward_right": ("rear", "left_side"),
    "right": ("left_side",),
    "backward_right": ("front", "left_side"),
    "backward": ("front",),
    "backward_left": ("front", "right_side"),
    "left": ("right_side",),
    "forward_left": ("rear", "right_side"),
}

# These eight boxes are the V1 tail, with the same dimensions, layering and
# palette. New directions only translate or rotate the complete tail.
PLUME_SEGMENTS = (
    ("ignition blue", 5.10, 6.27, 10.73, 12.03, 4.52, 5.39, "jet_blue"),
    ("ignition core", 5.32, 6.06, 10.90, 11.84, 4.52, 5.82, "jet_white"),
    ("middle cyan", 5.19, 6.21, 10.60, 11.89, 5.39, 6.54, "jet_cyan"),
    ("middle pale", 5.37, 6.04, 10.76, 11.60, 5.82, 7.16, "jet_pale"),
    ("staggered tail", 5.30, 6.12, 10.36, 11.43, 6.54, 7.57, "jet_blue"),
    ("tail core", 5.46, 5.96, 10.49, 11.17, 7.16, 8.20, "jet_cyan"),
    ("tail tip", 5.55, 5.89, 10.13, 10.80, 8.20, 9.31, "jet_blue"),
    ("side spark", 6.27, 6.55, 10.47, 10.79, 6.95, 7.53, "jet_pale"),
)


def emit_plume(boxes: list[Box], name: str, anchor_x: float, anchor_z: float,
               outward: tuple[int, int], mirror: int) -> None:
    dx, dz = outward
    lateral_x, lateral_z = dz, -dx
    for part_name, x0, x1, y0, y1, z0, z1, material in PLUME_SEGMENTS:
        corners = []
        for x in (x0, x1):
            for z in (z0, z1):
                u = (x - 5.70) * mirror
                v = z - 4.52
                corners.append((anchor_x + u * lateral_x + v * dx,
                                anchor_z + u * lateral_z + v * dz))
        world_x = [p[0] for p in corners]
        world_z = [p[1] for p in corners]
        add(boxes, f"{name} {part_name}", f"{name} jet plume",
            (min(world_x), y0, min(world_z)),
            (max(world_x), y1, max(world_z)), material)


def build_boxes(direction: str | None) -> list[Box]:
    if direction is not None and direction not in DIRECTION_PORTS:
        raise ValueError(f"Unknown direction: {direction}")
    active = set(DIRECTION_PORTS.get(direction, ()))
    boxes: list[Box] = []
    add(boxes, "front belt", "waist belt", (-4.22, 12.45, -2.53), (4.22, 13.37, -2.36), "belt")
    add(boxes, "rear belt", "waist belt", (-4.22, 12.45, 2.36), (4.22, 13.37, 2.53), "belt")
    for side in (-1, 1):
        side_name = "left" if side < 0 else "right"
        x0, x1 = (-4.65, -4.37) if side < 0 else (4.37, 4.65)
        # Route the side straps just below the arms instead of through them.
        add(boxes, f"{side_name} belt", "waist belt", (x0, 11.50, -2.12), (x1, 11.85, 2.40), "belt")
        add(boxes, f"{side_name} hip strap", "waist belt", (x0, 11.10, 0.52), (x1, 11.82, 2.52), "strap")
    add(boxes, "front buckle frame", "waist belt", (-1.13, 12.30, -2.68), (1.13, 13.55, -2.38), "edge")
    add(boxes, "front buckle inset", "waist belt", (-0.78, 12.55, -2.74), (0.78, 13.30, -2.67), "recess")
    add(boxes, "front buckle latch", "waist belt", (-0.18, 12.48, -2.80), (0.18, 13.37, -2.74), "buckle")

    add(boxes, "rear power housing", "rear connector", (-1.86, 11.95, 2.37), (1.86, 14.48, 3.02), "shell")
    add(boxes, "rear power panel", "rear connector", (-1.44, 12.34, 3.02), (1.44, 14.10, 3.13), "shell_lit")
    add(boxes, "rear seam", "rear connector", (-0.18, 12.42, 3.13), (0.18, 14.01, 3.17), "edge")
    add(boxes, "rear lower latch", "rear connector", (-0.77, 11.80, 2.75), (0.77, 12.17, 3.07), "shadow")

    # A compact pair of forward-facing ports for backward movement.
    for side in (-1, 1):
        label = "left" if side < 0 else "right"
        center_x = side * 2.75
        group = "front ports"
        def front_box(name, half_width, y0, y1, z0, z1, material):
            add(boxes, f"{label} front {name}", group,
                (center_x - half_width, y0, z0),
                (center_x + half_width, y1, z1), material)
        front_box("mount", 0.85, 10.54, 12.61, -2.66, -2.36, "shell")
        front_box("rim", 0.90, 10.30, 12.54, -2.99, -2.59, "nozzle")
        front_box("aperture", 0.54, 10.69, 12.07, -3.06, -2.99,
                  "hot_core" if "front" in active else "idle_core")
        front_box("slit", 0.35, 10.93, 11.87, -3.09, -3.06,
                  "jet_white" if "front" in active else "recess")
        if "front" in active:
            emit_plume(boxes, f"{label} front", center_x, -3.10,
                       (0, -1), -side)

    # Pods sit a little farther outside and behind the player arms. Their
    # maximum inactive half-width remains below the vanilla arm span (8 px).
    pod_x_shift = 0.22
    pod_z_shift = 1.68
    for side in (-1, 1):
        label = "left" if side < 0 else "right"
        group = f"{label} thruster"
        def xs(a, b):
            return (-b - pod_x_shift, -a - pod_x_shift) if side < 0 else (a + pod_x_shift, b + pod_x_shift)
        def part(name, xa, xb, y0, y1, z0, z1, material, target=group):
            a, b = xs(xa, xb)
            add(boxes, f"{label} {name}", target,
                (a, y0, z0 + pod_z_shift), (b, y1, z1 + pod_z_shift), material)

        part("mount", 4.16, 4.94, 12.33, 14.70, 0.72, 2.92, "strap")
        part("body", 4.73, 6.48, 10.62, 15.10, 0.84, 3.83, "shell")
        part("front guard", 4.81, 6.41, 11.05, 14.52, 0.68, 0.88, "shadow")
        part("outer housing", 5.02, 6.57, 10.95, 14.55, 1.04, 3.56, "shell_lit")
        part("upper shoulder", 4.85, 6.56, 14.52, 15.41, 0.91, 3.32, "panel")
        part("upper lip", 5.03, 6.48, 15.40, 15.57, 1.09, 3.13, "edge")
        part("lower armor", 4.94, 6.50, 10.15, 10.79, 1.28, 3.50, "shadow")
        outer0, outer1 = xs(6.57, 6.68)
        add(boxes, f"{label} side inset", group,
            (outer0, 11.36, 1.44 + pod_z_shift),
            (outer1, 13.97, 3.18 + pod_z_shift), "panel")
        outer0, outer1 = xs(6.68, 6.74)
        add(boxes, f"{label} status strip", group,
            (outer0, 12.73, 1.78 + pod_z_shift),
            (outer1, 13.20, 2.94 + pod_z_shift),
            "hot_core" if ("rear" in active or f"{label}_side" in active) else "idle_core")
        part("rear port", 4.87, 6.48, 10.38, 12.46, 3.69, 4.14, "recess")
        part("nozzle rim", 4.81, 6.54, 10.27, 12.54, 4.06, 4.40, "nozzle")
        part("nozzle aperture", 5.14, 6.22, 10.69, 12.07, 4.40, 4.48,
             "hot_core" if "rear" in active else "idle_core")
        part("nozzle slit", 5.33, 6.03, 10.93, 11.87, 4.48, 4.51,
             "jet_white" if "rear" in active else "recess")
        if "rear" in active:
            emit_plume(boxes, f"{label} rear", side * (5.70 + pod_x_shift),
                       4.52 + pod_z_shift, (0, 1), side)

        # Outward-facing side port, placed on the same compact pod.
        port_x0, port_x1 = (-7.34, -6.96) if side < 0 else (6.96, 7.34)
        rim_x0, rim_x1 = (-7.42, -7.34) if side < 0 else (7.34, 7.42)
        lip_x0, lip_x1 = (-7.46, -7.42) if side < 0 else (7.42, 7.46)
        add(boxes, f"{label} side port mount", group,
            (port_x0, 10.43, 3.01), (port_x1, 12.50, 4.24), "shell")
        add(boxes, f"{label} side port rim", group,
            (rim_x0, 10.34, 2.94), (rim_x1, 12.55, 4.30), "nozzle")
        add(boxes, f"{label} side port aperture", group,
            (lip_x0, 10.69, 3.17), (lip_x1, 12.07, 4.07),
            "hot_core" if f"{label}_side" in active else "idle_core")
        if f"{label}_side" in active:
            emit_plume(boxes, f"{label} side", side * 7.47, 3.62,
                       (side, 0), side)
    return boxes


PLAYER_OUTER_LAYER = (
    Box("jacket", "player", (-4.25, 12, -2.25), (4.25, 24, 2.25), "#52696a"),
    Box("left sleeve", "player", (-8.25, 12, -2.25), (-3.75, 24, 2.25), "#496269"),
    Box("right sleeve", "player", (3.75, 12, -2.25), (8.25, 24, 2.25), "#496269"),
    Box("left pants", "player", (-4.25, 0, -2.25), (0.25, 12, 2.25), "#34465b"),
    Box("right pants", "player", (-0.25, 0, -2.25), (4.25, 12, 2.25), "#394a60"),
)


def player_clearance(boxes: list[Box]) -> float:
    """Reject volume intersections with vanilla player outer skin layers."""
    least_gap = float("inf")
    for box in boxes:
        for body in PLAYER_OUTER_LAYER:
            gaps = []
            for axis in range(3):
                gap = max(body.xyz0[axis] - box.xyz1[axis],
                          box.xyz0[axis] - body.xyz1[axis], 0)
                gaps.append(gap)
            distance = math.sqrt(sum(gap * gap for gap in gaps))
            if distance < 1e-7:
                raise ValueError(f"Player-model clipping: {box.name} / {body.name}")
            least_gap = min(least_gap, distance)
    return least_gap


def bbmodel(boxes: list[Box], texture: Image.Image, name: str) -> dict:
    texture_bytes = io.BytesIO()
    texture.save(texture_bytes, format="PNG")
    texture_url = "data:image/png;base64," + base64.b64encode(texture_bytes.getvalue()).decode("ascii")
    tex_uuid = str(uuid.uuid5(uuid.NAMESPACE_URL, "waist-jet-v1-texture"))
    elements = []
    group_ids: dict[str, str] = {}
    members: dict[str, list[str]] = {}
    for box in boxes:
        group_ids.setdefault(box.group, str(uuid.uuid5(uuid.NAMESPACE_URL, "waist-jet-v1-group-" + box.group)))
        members.setdefault(box.group, [])
        index = COLOR_NAMES.index(box.material)
        tx, ty = (index % 16) * 4, (index // 16) * 4
        face = {"uv": [tx, ty, tx + 4, ty + 4], "texture": 0}
        cube_uuid = str(uuid.uuid5(uuid.NAMESPACE_URL, "waist-jet-v1-cube-" + box.name))
        elements.append({
            "name": box.name,
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "from": list(box.xyz0),
            "to": list(box.xyz1),
            "autouv": 0,
            "color": 0,
            "origin": [0, 12, 0],
            "faces": {direction: face.copy() for direction in ("north", "east", "south", "west", "up", "down")},
            "type": "cube",
            "uuid": cube_uuid,
        })
        members[box.group].append(cube_uuid)
    groups = [
        {"uuid": group_ids[group], "export": True, "locked": False,
         "origin": [0, 12, 0], "rotation": [0, 0, 0], "color": 0,
         "name": group, "children": [], "reset": False, "shade": True,
         "mirror_uv": False, "selected": False, "visibility": True,
         "autouv": 0, "isOpen": True, "primary_selected": False}
        for group in members
    ]
    outliner = [
        {"uuid": group_ids[group], "isOpen": True, "children": ids}
        for group, ids in members.items()
    ]
    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": name,
        "model_identifier": "waist_jet_v2",
        "visible_box": [1, 1, 0],
        "resolution": {"width": TEX_SIZE[0], "height": TEX_SIZE[1]},
        "elements": elements,
        "groups": groups,
        "outliner": outliner,
        "textures": [{
            "path": "", "name": "waist_jet_palette.png", "folder": "", "namespace": "",
            "id": "0", "particle": False, "render_mode": "default", "visible": True,
            "mode": "bitmap", "saved": True, "uuid": tex_uuid,
            "uv_width": TEX_SIZE[0], "uv_height": TEX_SIZE[1], "source": texture_url,
        }],
    }


FACES = (
    ((0, 1, 2, 3), (0, 0, -1)),
    ((4, 5, 6, 7), (0, 0, 1)),
    ((0, 3, 7, 4), (-1, 0, 0)),
    ((1, 2, 6, 5), (1, 0, 0)),
    ((3, 2, 6, 7), (0, 1, 0)),
    ((0, 1, 5, 4), (0, -1, 0)),
)


def vertices(box: Box):
    x0, y0, z0 = box.xyz0
    x1, y1, z1 = box.xyz1
    return [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
            (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]


def unit(v):
    length = math.sqrt(sum(x * x for x in v))
    return tuple(x / length for x in v)


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def preview(boxes: list[Box], firing: bool, view: str) -> Image.Image:
    # A plain player-size mannequin shows fit; it is not included in the BBModel.
    mannequin = [
        Box("torso", "preview", (-4.25, 12, -2.25), (4.25, 18, 2.25), "#52696a"),
        Box("left arm", "preview", (-8.25, 12, -2.25), (-3.75, 18, 2.25), "#496269"),
        Box("right arm", "preview", (3.75, 12, -2.25), (8.25, 18, 2.25), "#496269"),
        Box("left leg", "preview", (-4.25, 7, -2.25), (0.25, 12, 2.25), "#34465b"),
        Box("right leg", "preview", (-0.25, 7, -2.25), (4.25, 12, 2.25), "#394a60"),
    ]
    camera = unit((0.62, 0.45, 1.55) if view == "rear" else (0.82, 0.50, -1.42))
    right = unit(cross((0, 1, 0), camera))
    up = unit(cross(camera, right))
    center = (0, 12.6, 2.0 if firing and view == "rear" else 0)
    canvas = Image.new("RGBA", (690, 570), (0, 0, 0, 0))
    polygons = []
    for box in mannequin + boxes:
        # In this simple orthographic illustration, hide hardware on the far
        # side of the body and paint the near-side device over the mannequin.
        if view == "rear" and (box.name.startswith("front ") or box.group == "front ports"
                               or box.group.endswith("front jet plume")):
            continue
        if view == "front" and (box.group == "rear connector" or box.name == "rear belt"
                                or box.group.endswith("rear jet plume")
                                or box.name.endswith(("rear port", "nozzle rim", "nozzle aperture", "nozzle slit"))):
            continue
        layer = 0 if box.group == "preview" else 1
        points = vertices(box)
        for indices, normal in FACES:
            if dot(normal, camera) <= 0.01:
                continue
            corners = [points[i] for i in indices]
            depth = dot(tuple(sum(p[i] for p in corners) / 4 for i in range(3)), camera)
            proj = []
            for point in corners:
                relative = tuple(point[i] - center[i] for i in range(3))
                proj.append((345 + dot(relative, right) * 31,
                             285 - dot(relative, up) * 31))
            base = rgb(box.material)
            light = 1.13 if normal == (0, 1, 0) else (0.94 if normal[0] else 0.78)
            if box.material in {"jet_white", "jet_pale", "jet_cyan", "jet_blue", "hot_core"}:
                light = max(light, 0.95)
            fill = tuple(min(255, int(c * light)) for c in base) + (255,)
            polygons.append((layer, depth, proj, fill))
    polygons.sort(key=lambda row: (row[0], row[1]))
    draw = ImageDraw.Draw(canvas)
    for _, _, points, fill in polygons:
        draw.polygon(points, fill=fill)
    return canvas


def top_view(boxes: list[Box]) -> Image.Image:
    """Actual model footprints from above, with a player outline for scale."""
    canvas = Image.new("RGBA", (470, 380), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    scale, cx, cy = 14.1, 235, 182

    def rectangle(x0, z0, x1, z1):
        return (cx + x0 * scale, cy + z0 * scale,
                cx + x1 * scale, cy + z1 * scale)

    for player in PLAYER_OUTER_LAYER[:3]:
        draw.rectangle(rectangle(player.xyz0[0], player.xyz0[2],
                                 player.xyz1[0], player.xyz1[2]),
                       fill="#4c6267", outline="#81979a", width=2)
    for box in sorted(boxes, key=lambda b: b.xyz1[1]):
        color = rgb(box.material)
        fill = tuple(min(255, int(c * 1.05)) for c in color) + (255,)
        draw.rectangle(rectangle(box.xyz0[0], box.xyz0[2],
                                 box.xyz1[0], box.xyz1[2]),
                       fill=fill, outline="#17282f", width=1)
    return canvas


def font(size, bold=False):
    filename = "msyhbd.ttc" if bold else "msyh.ttc"
    path = Path("C:/Windows/Fonts") / filename
    return ImageFont.truetype(str(path), size) if path.exists() else ImageFont.load_default()


def comparison(idle: Image.Image, firing: Image.Image) -> Image.Image:
    image = Image.new("RGB", (1420, 770), "#18242a")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((24, 92, 696, 735), radius=24, fill="#22323a")
    draw.rounded_rectangle((724, 92, 1396, 735), radius=24, fill="#22323a")
    image.paste(idle, (15, 107), idle)
    image.paste(firing, (715, 107), firing)
    draw.text((34, 25), "腰部喷气装置 · 模型方案 V2", fill="#e8f1ed", font=font(31, True))
    draw.text((915, 37), "灰蓝色人形仅用于展示佩戴位置", fill="#a8c1c0", font=font(18))
    draw.text((58, 628), "待机状态", fill="#d8e5df", font=font(27, True))
    draw.text((58, 672), "贴身双侧机体 · 前后左右都有喷口", fill="#9eb7b7", font=font(19))
    draw.text((758, 628), "向前突进", fill="#e4fbf7", font=font(27, True))
    draw.text((758, 672), "后方双喷口启动 · 保留原有尾流外观", fill="#9ecbd0", font=font(19))
    return image


def directions_overview(models: dict[str, list[Box]]) -> Image.Image:
    names = {
        "forward": "向前 ↑",
        "forward_right": "右前 ↗",
        "right": "向右 →",
        "backward_right": "右后 ↘",
        "backward": "向后 ↓",
        "backward_left": "左后 ↙",
        "left": "向左 ←",
        "forward_left": "左前 ↖",
    }
    image = Image.new("RGB", (2020, 1030), "#18242a")
    draw = ImageDraw.Draw(image)
    draw.text((30, 22), "八方向喷气 · 俯视模型", fill="#e8f1ed", font=font(32, True))
    draw.text((755, 36), "箭头是移动方向；灰色轮廓为玩家示意；喷气从反向喷口排出",
              fill="#a8c1c0", font=font(20))
    for index, (direction, boxes) in enumerate(models.items()):
        row, column = divmod(index, 4)
        left, top = 24 + column * 498, 82 + row * 468
        draw.rounded_rectangle((left, top, left + 476, top + 449),
                               radius=19, fill="#24353d")
        draw.text((left + 19, top + 10), names[direction],
                  fill="#dff4ef", font=font(26, True))
        top_image = top_view(boxes)
        image.paste(top_image, (left + 3, top + 52), top_image)
    return image


def main():
    texture = make_texture()
    texture.save(OUT / "waist_jet_palette.png")

    def write_model(filename: str, boxes: list[Box]):
        model = bbmodel(boxes, texture, filename.removesuffix(".bbmodel"))
        (OUT / filename).write_text(
            json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    idle_boxes = build_boxes(None)
    clearance = player_clearance(idle_boxes)
    write_model("waist_jet_idle.bbmodel", idle_boxes)
    idle_preview = preview(idle_boxes, False, "rear")
    idle_preview.save(OUT / "preview_idle.png")
    preview(idle_boxes, False, "front").save(OUT / "preview_front.png")
    top_view(idle_boxes).save(OUT / "preview_fit_top.png")

    directional_models = {}
    for direction in DIRECTION_PORTS:
        boxes = build_boxes(direction)
        clearance = min(clearance, player_clearance(boxes))
        directional_models[direction] = boxes
        write_model(f"waist_jet_{direction}.bbmodel", boxes)
        top_view(boxes).save(OUT / f"preview_{direction}.png")
    forward = directional_models["forward"]
    write_model("waist_jet_firing.bbmodel", forward)
    forward_preview = preview(forward, True, "rear")
    forward_preview.save(OUT / "preview_firing.png")
    comparison(idle_preview, forward_preview).save(OUT / "comparison.png")
    directions_overview(directional_models).save(OUT / "directions_overview.png")
    print(f"Minimum clearance from player outer layers: {clearance:.3f} model pixels")


if __name__ == "__main__":
    main()
