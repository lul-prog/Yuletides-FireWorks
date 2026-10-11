#!/usr/bin/env python3
"""
Generates every asset this mod ships: particle sprites, entity glow textures, GUI panels, block and
item textures, and the JSON that describes them (particle definitions, blockstates, models, loot
tables, recipes, language file).

Why generated rather than hand-made: the GUI panel has to line up with slot coordinates that live in
the Java menu class, and the only way to keep those two in sync is to derive one from the other.
The slot constants below mirror PyrotechnicWorkstationMenu / LaunchPadMenu exactly.

Run:  python3 tools/generate_assets.py
Requires Pillow:  pip install Pillow
"""

import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "fireworkshaders")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "fireworkshaders")
NS = "fireworkshaders"

random.seed(20261011)


def ensure(*parts):
    path = os.path.join(*parts)
    os.makedirs(path, exist_ok=True)
    return path


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    return path


# --------------------------------------------------------------------------- particle sprites

def radial(size, power=2.4, core=0.0, brightness=1.0):
    """Soft radial falloff, white with an alpha channel. Vertex colour tints it at runtime."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / c
            if r > 1.0:
                continue
            a = (1.0 - r) ** power
            if core > 0.0:
                a = min(1.0, a + max(0.0, 1.0 - r / core) ** 1.4)
            a *= brightness
            px[x, y] = (255, 255, 255, int(max(0.0, min(1.0, a)) * 255))
    return img


def star_sprite(size=16, arms=4, arm_length=1.0, core=0.32):
    img = radial(size, power=1.7, core=core)
    draw = ImageDraw.Draw(img)
    c = (size - 1) / 2.0
    arm = c * arm_length
    for i in range(arms):
        angle = math.pi * i / (arms / 2.0)
        dx = math.cos(angle) * arm
        dy = math.sin(angle) * arm
        draw.line([c - dx, c - dy, c + dx, c + dy], fill=(255, 255, 255, 150), width=1)
    return img


def noise_puff(size=16, octaves=4, alpha=0.85):
    """Fractal value noise multiplied by a soft falloff: reads as a puff of smoke."""
    def grid(n):
        return [[random.random() for _ in range(n + 1)] for _ in range(n + 1)]

    def sample(g, u, v, n):
        x = u * n
        y = v * n
        x0, y0 = int(x), int(y)
        fx, fy = x - x0, y - y0
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        a = g[y0][x0] * (1 - fx) + g[y0][min(x0 + 1, n)][x0 and 0 or 0] if False else 0
        v00 = g[y0][x0]
        v10 = g[y0][min(x0 + 1, n)]
        v01 = g[min(y0 + 1, n)][x0]
        v11 = g[min(y0 + 1, n)][min(x0 + 1, n)]
        return (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    grids = [(grid(2 ** (o + 1)), 2 ** (o + 1)) for o in range(octaves)]
    for y in range(size):
        for x in range(size):
            u, v = x / (size - 1), y / (size - 1)
            r = math.hypot(x - c, y - c) / c
            falloff = max(0.0, 1.0 - r) ** 1.5
            n = 0.0
            amp, total = 1.0, 0.0
            for g, res in grids:
                n += sample(g, u, v, res) * amp
                total += amp
                amp *= 0.55
            n /= total
            a = falloff * (0.45 + 0.75 * n) * alpha
            shade = int(200 + 55 * n)
            px[x, y] = (shade, shade, shade, int(max(0.0, min(1.0, a)) * 255))
    return img.filter(ImageFilter.GaussianBlur(0.4))


def ring_sprite(size=16, width_ratio=0.20):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    inner = 1.0 - width_ratio
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / c
            if r > 1.05:
                continue
            # A sharp annulus with soft edges: a real blast shell has a thin, hard front.
            band = abs(r - inner * 0.94) / max(1e-5, width_ratio * 0.5)
            a = max(0.0, 1.0 - band) ** 1.6
            px[x, y] = (255, 255, 255, int(a * 255))
    return img


def ember_sprite(size=16):
    img = radial(size, power=2.0, core=0.30)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            r0 = math.hypot(x - c, y - c) / c
            # Lumpy, uneven: an ember is a chunk of burning material, not a light bulb.
            lump = 0.75 + 0.45 * math.sin(math.atan2(y - c, x - c) * 3.0 + r0 * 5.0)
            px[x, y] = (r, g, b, int(min(255, a * lump)))
    return img


def generate_particles():
    out = ensure(ASSETS, "textures", "particle")
    save(star_sprite(16, arms=4, arm_length=0.95, core=0.34), out, "pyro_spark.png")
    save(star_sprite(16, arms=4, arm_length=0.55, core=0.5), out, "pyro_glitter.png")
    save(ember_sprite(16), out, "pyro_ember.png")
    save(radial(16, power=3.2, core=0.15, brightness=0.72), out, "pyro_dust.png")
    save(noise_puff(16), out, "pyro_smoke.png")
    save(radial(16, power=1.25, core=0.55), out, "pyro_flash.png")
    save(ring_sprite(16, 0.22), out, "pyro_shockwave.png")
    save(radial(16, power=2.8, core=0.22), out, "pyro_spectrum.png")
    return {}


# --------------------------------------------------------------------------- entity glows

def generate_entity():
    out = ensure(ASSETS, "textures", "entity")
    save(radial(32, power=2.2, core=0.34), out, "pyro_shell.png")

    trail = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = trail.load()
    for y in range(32):
        for x in range(32):
            along = 1.0 - x / 31.0          # bright at u=0 (the head), gone by u=1
            across = max(0.0, 1.0 - abs(y - 15.5) / 8.0) ** 1.6
            a = (along ** 2.1) * across
            px[x, y] = (255, 250, 235, int(max(0.0, min(1.0, a)) * 255))
    save(trail.filter(ImageFilter.GaussianBlur(0.6)), out, "pyro_trail.png")
    return {}


# --------------------------------------------------------------------------- blocks & items

def panel_texture(size, base, accent, seed=7):
    rng = random.Random(seed)
    img = Image.new("RGBA", size, base + (255,))
    draw = ImageDraw.Draw(img)
    for i in range(size[0] * size[1] // 6):
        x = rng.randrange(size[0])
        y = rng.randrange(size[1])
        shade = rng.randint(-12, 12)
        draw.point((x, y), fill=(max(0, min(255, base[0] + shade)),
                                max(0, min(255, base[1] + shade)),
                                max(0, min(255, base[2] + shade)), 255))
    draw.rectangle([0, 0, size[0] - 1, size[1] - 1], outline=accent, width=1)
    return img


def slot_recess(draw, x, y, tint=(24, 27, 34)):
    draw.rectangle([x - 1, y - 1, x + 16, y + 16], fill=tint + (255,))
    draw.rectangle([x - 1, y - 1, x + 16, y + 16], outline=(12, 14, 18, 255))
    draw.line([x - 1, y + 16, x + 16, y + 16], fill=(60, 66, 80, 255))


def bevel(draw, box, light=(120, 128, 146), dark=(20, 22, 28)):
    """The two-pixel inner bevel that makes a flat rectangle read as a raised metal panel."""
    x0, y0, x1, y1 = box
    draw.line([x0, y0, x1 - 1, y0], fill=light + (255,))
    draw.line([x0, y0, x0, y1 - 1], fill=light + (255,))
    draw.line([x0, y1, x1, y1], fill=dark + (255,))
    draw.line([x1, y0, x1, y1], fill=dark + (255,))
    draw.line([x0 + 1, y0 + 1, x1 - 2, y0 + 1], fill=(light[0] // 2, light[1] // 2, light[2] // 2, 255))
    draw.line([x0 + 1, y0 + 1, x0 + 1, y1 - 2], fill=(light[0] // 2, light[1] // 2, light[2] // 2, 255))


def rivet(draw, x, y, shade=(150, 158, 176)):
    draw.point((x, y), fill=shade + (255,))
    draw.point((x + 1, y), fill=(70, 74, 86, 255))
    draw.point((x, y + 1), fill=(70, 74, 86, 255))


def generate_gui():
    out = ensure(ASSETS, "textures", "gui")

    # ---- workstation: 340x236, matching PyrotechnicWorkstationMenu -------------------------
    img = panel_texture((340, 236), (44, 48, 58), (96, 104, 124), seed=11)
    draw = ImageDraw.Draw(img)
    # machine half
    draw.rectangle([2, 2, 176, 233], fill=(38, 42, 51, 255))
    bevel(draw, (2, 2, 176, 233))
    # controls half, recessed
    draw.rectangle([178, 2, 337, 233], fill=(33, 36, 45, 255))
    draw.rectangle([178, 2, 337, 233], outline=(70, 76, 92, 255))
    draw.line([178, 14, 337, 14], fill=(70, 76, 92, 255))

    # machine slots (mirrors the menu constants)
    for i in range(5):
        slot_recess(draw, 24, 26 + i * 22)
    for i in range(2):
        slot_recess(draw, 60, 26 + i * 22)
    slot_recess(draw, 60, 92)
    slot_recess(draw, 120, 59, tint=(20, 22, 28))

    # the press arrow, with a pressure gauge next to it
    draw.polygon([(84, 58), (106, 67), (84, 76)], fill=(196, 132, 48, 255))
    draw.rectangle([78, 64, 84, 70], fill=(196, 132, 48, 255))
    draw.polygon([(86, 60), (104, 67), (86, 74)], fill=(224, 164, 74, 255))
    draw.ellipse([44, 58, 62, 76], fill=(26, 29, 36, 255), outline=(120, 128, 146, 255))
    draw.line([53, 67, 58, 62], fill=(214, 90, 70, 255))
    draw.point((53, 67), fill=(220, 226, 240, 255))

    # label rails on the reagent column
    for i in range(5):
        draw.line([46, 32 + i * 22, 74, 32 + i * 22], fill=(58, 62, 74, 255))

    # hazard stripe across the bottom of the machine half
    for x in range(4, 174, 8):
        draw.polygon([(x, 226), (x + 4, 226), (x + 8, 217), (x + 4, 217)],
                     fill=(196, 152, 44, 255))

    # module board: 7 rows of bracket pairs, with a column divider
    for row in range(7):
        y = 26 + row * 16
        draw.line([186, y, 250, y], fill=(48, 52, 63, 255))
        draw.line([250, y, 250, y + 12], fill=(48, 52, 63, 255))
    # preview frame
    draw.rectangle([251, 16, 332, 97], fill=(16, 18, 24, 255))
    draw.rectangle([251, 16, 332, 97], outline=(88, 96, 116, 255))
    for corner in ((251, 16), (330, 16), (251, 95), (330, 95)):
        rivet(draw, corner[0], corner[1])
    # ruler marks down the control column
    for y in range(104, 232, 8):
        draw.line([178, y, 181, y], fill=(88, 96, 116, 255))
    for y in (92, 108, 124, 140):
        draw.line([250, y, 250, y + 22], fill=(70, 76, 92, 255))
    for corner in ((6, 6), (170, 6), (6, 228), (170, 228)):
        rivet(draw, corner[0], corner[1])
    save(img, out, "pyrotechnic_workstation.png")

    # ---- launch pad: 256x220, matching LaunchPadMenu ---------------------------------------
    img = panel_texture((256, 220), (44, 48, 58), (96, 104, 124), seed=13)
    draw = ImageDraw.Draw(img)
    draw.rectangle([2, 2, 174, 217], fill=(38, 42, 51, 255))
    bevel(draw, (2, 2, 174, 217))
    draw.rectangle([176, 2, 253, 217], fill=(33, 36, 45, 255))
    draw.rectangle([176, 2, 253, 217], outline=(70, 76, 92, 255))
    for row in range(3):
        for col in range(3):
            slot_recess(draw, 26 + col * 18, 22 + row * 18)
    # launch rail: a fan of guide lines from the tube grid up to the sky
    draw.rectangle([79, 20, 172, 152], fill=(26, 29, 36, 255))
    draw.rectangle([79, 20, 172, 152], outline=(70, 76, 92, 255))
    for i in range(5):
        x = 92 + i * 16
        draw.line([x, 148, x + 8, 32], fill=(44, 48, 58, 255))
    draw.polygon([(120, 132), (132, 132), (126, 118)], fill=(196, 132, 48, 255))
    for corner in ((6, 6), (168, 6), (6, 212), (168, 212)):
        rivet(draw, corner[0], corner[1])
    save(img, out, "launch_pad.png")
    return {}


def generate_blocks():
    out = ensure(ASSETS, "textures", "block")
    side = panel_texture((16, 16), (74, 78, 90), (52, 56, 66), seed=3)
    draw = ImageDraw.Draw(side)
    draw.line([0, 4, 15, 4], fill=(52, 56, 66, 255))
    draw.line([0, 11, 15, 11], fill=(52, 56, 66, 255))
    for x in (3, 8, 13):
        draw.point((x, 7), fill=(126, 132, 148, 255))
    save(side, out, "pyrotechnic_workstation_side.png")

    top = panel_texture((16, 16), (62, 66, 78), (44, 48, 58), seed=5)
    draw = ImageDraw.Draw(top)
    draw.ellipse([3, 3, 12, 12], fill=(38, 41, 50, 255), outline=(96, 104, 124, 255))
    save(top, out, "pyrotechnic_workstation_top.png")

    front = panel_texture((16, 16), (74, 78, 90), (52, 56, 66), seed=6)
    draw = ImageDraw.Draw(front)
    draw.rectangle([3, 4, 12, 11], fill=(30, 33, 40, 255), outline=(104, 112, 132, 255))
    draw.rectangle([5, 6, 10, 9], fill=(96, 70, 40, 255))
    draw.rectangle([9, 2, 12, 5], fill=(64, 68, 80, 255))
    save(front, out, "pyrotechnic_workstation_front.png")

    lit = front.copy()
    draw = ImageDraw.Draw(lit)
    draw.rectangle([5, 6, 10, 9], fill=(255, 196, 112, 255))
    draw.rectangle([4, 5, 11, 10], outline=(255, 168, 64, 255))
    save(lit, out, "pyrotechnic_workstation_front_lit.png")

    pad_side = panel_texture((16, 16), (88, 60, 44), (58, 40, 30), seed=9)
    draw = ImageDraw.Draw(pad_side)
    draw.line([0, 8, 15, 8], fill=(58, 40, 30, 255))
    save(pad_side, out, "launch_pad_side.png")

    pad_top = panel_texture((16, 16), (72, 52, 40), (52, 36, 28), seed=10)
    draw = ImageDraw.Draw(pad_top)
    for row in range(3):
        for col in range(3):
            draw.ellipse([2 + col * 4, 2 + row * 4, 4 + col * 4, 4 + row * 4],
                         fill=(22, 20, 20, 255), outline=(120, 96, 72, 255))
    save(pad_top, out, "launch_pad_top.png")

    # Armed: the tube rims glow, so a lit pad is readable from the air without shaders.
    pad_top_armed = pad_top.copy()
    draw = ImageDraw.Draw(pad_top_armed)
    for row in range(3):
        for col in range(3):
            box = [2 + col * 4, 2 + row * 4, 4 + col * 4, 4 + row * 4]
            draw.ellipse(box, fill=(60, 20, 14, 255), outline=(255, 168, 64, 255))
            draw.point((box[0] + 1, box[1] + 1), fill=(255, 240, 200, 255))
    save(pad_top_armed, out, "launch_pad_top_armed.png")
    return {}


def generate_items():
    out = ensure(ASSETS, "textures", "item")

    # the designed shell: a paper-cased star shell with a burning tip
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([5, 5, 10, 14], radius=2, fill=(214, 204, 178, 255),
                           outline=(150, 138, 112, 255))
    draw.rectangle([5, 9, 10, 10], fill=(178, 60, 44, 255))
    draw.rectangle([6, 12, 9, 13], fill=(70, 64, 52, 255))
    draw.polygon([(7, 5), (8, 1), (9, 5)], fill=(255, 214, 140, 255))
    draw.point((8, 4), fill=(255, 255, 220, 255))
    save(img, out, "custom_firework.png")

    # remote igniter: a red transmitter with an antenna
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([4, 6, 11, 14], radius=1, fill=(58, 60, 70, 255),
                           outline=(30, 32, 38, 255))
    draw.rectangle([5, 8, 10, 9], fill=(178, 46, 40, 255))
    draw.rectangle([6, 11, 7, 12], fill=(80, 84, 96, 255))
    draw.rectangle([9, 11, 10, 12], fill=(80, 84, 96, 255))
    draw.line([8, 6, 8, 2], fill=(180, 184, 196, 255))
    draw.ellipse([7, 1, 9, 3], fill=(255, 196, 96, 255))
    save(img, out, "remote_igniter.png")

    # chemical powders: 15 little heaps, tinted, with a grain texture
    chemicals = {
        "strontium_nitrate": (255, 53, 36), "barium_nitrate": (63, 255, 99),
        "copper_chloride": (53, 166, 255), "sodium_nitrate": (255, 179, 36),
        "calcium_chloride": (255, 122, 30), "potassium_nitrate": (176, 92, 255),
        "magnesium_powder": (255, 251, 232), "aluminium_powder": (255, 240, 194),
        "titanium_sponge": (242, 246, 255), "iron_filings": (255, 192, 97),
        "potassium_perchlorate": (255, 232, 176), "sulfur": (138, 124, 255),
        "charcoal_dust": (255, 138, 51), "dextrin_binder": (240, 216, 168),
    }
    for name, colour in chemicals.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        draw.polygon([(3, 12), (5, 7), (11, 7), (13, 12)], fill=colour + (255,))
        draw.ellipse([4, 10, 12, 14], fill=colour + (255,))
        for _ in range(26):
            x = random.randint(3, 12)
            y = random.randint(7, 13)
            shade = random.randint(-45, 45)
            draw.point((x, y), fill=(max(0, min(255, colour[0] + shade)),
                                    max(0, min(255, colour[1] + shade)),
                                    max(0, min(255, colour[2] + shade)), 255))
        draw.line([3, 13, 12, 13], fill=(max(0, colour[0] - 70), max(0, colour[1] - 70),
                                         max(0, colour[2] - 70), 200))
        save(img, out, name + ".png")
    return {}


# --------------------------------------------------------------------------- JSON

PARTICLES = ["pyro_spark", "pyro_glitter", "pyro_ember", "pyro_dust", "pyro_smoke", "pyro_flash",
             "pyro_shockwave", "pyro_spectrum"]

# The generated set, kept in step with the enums in src/main/java/.../pyro.
SHAPE_IDS = ["peony", "chrysanthemum", "sphere", "willow", "ring", "double_ring", "cascade",
             "palm", "crossette", "saturn", "heart", "star", "comet"]

MODULE_IDS = ["trail", "crackle", "strobe", "smoke", "whistle", "shriek", "flash", "shockwave",
              "shell_of_shells", "drape", "spectrum", "ground_glow", "linger", "dust"]

PRESET_IDS = ["stardust_willow", "meteor_shower", "sonic_boom", "dragons_breath",
              "prismatic_cascade", "red_peony", "golden_chrysanthemum", "emerald_ring"]

# Matches Chemical.values() in order; the item names are the enum's own itemName() strings.
CHEMICALS = ["strontium_nitrate", "barium_nitrate", "copper_chloride", "sodium_nitrate",
             "calcium_chloride", "potassium_nitrate", "magnesium_powder", "aluminium_powder",
             "titanium_sponge", "iron_filings", "potassium_perchlorate", "sulfur",
             "charcoal_dust", "dextrin_binder"]


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def generate_json():
    # ---- particle definitions ---------------------------------------------------------------
    for name in PARTICLES:
        write_json(os.path.join(ASSETS, "particles", name + ".json"),
                   {"textures": [f"{NS}:{name}"]})

    # ---- blockstates & models ---------------------------------------------------------------
    # The workstation faces the player who placed it, so the front texture rides on the model's
    # north face and the blockstate rotates the whole model. LIT swaps in a texture with a glowing
    # specimen window; the block also emits light 8 in that state.
    workstation_variants = {}
    for facing, yaw in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        for lit in ["false", "true"]:
            workstation_variants[f"facing={facing},lit={lit}"] = {
                "model": f"{NS}:block/pyrotechnic_workstation" + ("_lit" if lit == "true" else ""),
                "y": yaw,
                "uvlock": True
            }
    write_json(os.path.join(ASSETS, "blockstates", "pyrotechnic_workstation.json"),
               {"variants": workstation_variants})

    def cube_model(top, side, front, particle):
        return {
            "parent": "block/block",
            "textures": {"top": top, "side": side, "front": front, "particle": particle},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": {
                     "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
                     "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                     "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
                     "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
                     "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                     "down": {"uv": [0, 0, 16, 16], "texture": "#side"}
                 }}
            ]
        }

    write_json(os.path.join(ASSETS, "models", "block", "pyrotechnic_workstation.json"),
               cube_model(f"{NS}:block/pyrotechnic_workstation_top",
                          f"{NS}:block/pyrotechnic_workstation_side",
                          f"{NS}:block/pyrotechnic_workstation_front",
                          f"{NS}:block/pyrotechnic_workstation_side"))
    write_json(os.path.join(ASSETS, "models", "block", "pyrotechnic_workstation_lit.json"),
               cube_model(f"{NS}:block/pyrotechnic_workstation_top",
                          f"{NS}:block/pyrotechnic_workstation_side",
                          f"{NS}:block/pyrotechnic_workstation_front_lit",
                          f"{NS}:block/pyrotechnic_workstation_side"))

    write_json(os.path.join(ASSETS, "blockstates", "launch_pad.json"),
               {"variants": {
                   "armed=false": {"model": f"{NS}:block/launch_pad"},
                   "armed=true": {"model": f"{NS}:block/launch_pad_armed"}
               }})

    def pad_model(top):
        return {
            "parent": "block/block",
            "textures": {"top": top, "side": f"{NS}:block/launch_pad_side",
                         "particle": f"{NS}:block/launch_pad_side"},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 12, 16],
                 "faces": {
                     "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                     "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
                     "north": {"uv": [0, 4, 16, 16], "texture": "#side"},
                     "south": {"uv": [0, 4, 16, 16], "texture": "#side"},
                     "east": {"uv": [0, 4, 16, 16], "texture": "#side"},
                     "west": {"uv": [0, 4, 16, 16], "texture": "#side"}
                 }}
            ]
        }

    write_json(os.path.join(ASSETS, "models", "block", "launch_pad.json"),
               pad_model(f"{NS}:block/launch_pad_top"))
    write_json(os.path.join(ASSETS, "models", "block", "launch_pad_armed.json"),
               pad_model(f"{NS}:block/launch_pad_top_armed"))

    # ---- item models ------------------------------------------------------------------------
    for item in ["custom_firework", "remote_igniter"] + CHEMICALS:
        write_json(os.path.join(ASSETS, "models", "item", item + ".json"),
                   {"parent": "item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
    for block in ["pyrotechnic_workstation", "launch_pad"]:
        write_json(os.path.join(ASSETS, "models", "item", block + ".json"),
                   {"parent": f"{NS}:block/{block}"})

    # ---- loot tables ------------------------------------------------------------------------
    for block in ["pyrotechnic_workstation", "launch_pad"]:
        write_json(os.path.join(DATA, "loot_tables", "blocks", block + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]
            }]
        })

    # ---- recipes ----------------------------------------------------------------------------
    def shapeless(name, ingredients, result, count=1):
        write_json(os.path.join(DATA, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": ingredients,
            "result": {"item": result, "count": count}
        })

    write_json(os.path.join(DATA, "recipes", "pyrotechnic_workstation.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["IRI", "GCG", "IPI"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "R": {"item": "minecraft:redstone"},
            "P": {"item": "minecraft:paper"},
            "G": {"item": "minecraft:gunpowder"},
            "C": {"item": "minecraft:crafting_table"}
        },
        "result": {"item": f"{NS}:pyrotechnic_workstation"}
    })
    write_json(os.path.join(DATA, "recipes", "launch_pad.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["III", "RPR", "SSS"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "R": {"item": "minecraft:redstone"},
            "P": {"item": "minecraft:paper"},
            "S": {"item": "minecraft:smooth_stone"}
        },
        "result": {"item": f"{NS}:launch_pad"}
    })
    shapeless("remote_igniter",
              [{"item": "minecraft:gold_ingot"}, {"item": "minecraft:redstone"},
               {"item": "minecraft:redstone"}, {"item": "minecraft:iron_ingot"}],
              f"{NS}:remote_igniter")
    # The chemistry: a colour salt is a metal salt plus an oxidiser, which is close enough to the
    # real thing that a player who knows fireworks will nod at it.
    recipes = {
        "strontium_nitrate": "minecraft:red_dye", "barium_nitrate": "minecraft:green_dye",
        "copper_chloride": "minecraft:light_blue_dye", "sodium_nitrate": "minecraft:yellow_dye",
        "calcium_chloride": "minecraft:orange_dye", "potassium_nitrate": "minecraft:purple_dye",
        "magnesium_powder": "minecraft:white_dye", "aluminium_powder": "minecraft:gray_dye",
        "titanium_sponge": "minecraft:light_gray_dye", "iron_filings": "minecraft:iron_nugget",
        "sulfur": "minecraft:glowstone_dust", "charcoal_dust": "minecraft:charcoal",
        "dextrin_binder": "minecraft:wheat",
        "potassium_perchlorate": "minecraft:gunpowder"
    }
    for item, dye in recipes.items():
        shapeless(item, [{"item": dye}, {"item": "minecraft:gunpowder"}], f"{NS}:{item}", 2)


# --------------------------------------------------------------------------- language

def generate_lang():
    lang = {}

    lang["itemGroup.fireworkshaders.main"] = "Yuletide Fireworks"
    lang["block.fireworkshaders.pyrotechnic_workstation"] = "Pyrotechnic Workstation"
    lang["block.fireworkshaders.launch_pad"] = "Launch Pad"
    lang["item.fireworkshaders.custom_firework"] = "Designed Shell"
    lang["item.fireworkshaders.remote_igniter"] = "Remote Igniter"
    lang["item.fireworkshaders.pyrotechnic_workstation"] = "Pyrotechnic Workstation"
    lang["item.fireworkshaders.launch_pad"] = "Launch Pad"

    chemical_names = {
        "strontium_nitrate": "Strontium Nitrate", "barium_nitrate": "Barium Nitrate",
        "copper_chloride": "Copper(I) Chloride", "sodium_nitrate": "Sodium Nitrate",
        "calcium_chloride": "Calcium Chloride", "potassium_nitrate": "Potassium Nitrate",
        "magnesium_powder": "Magnesium Powder", "aluminium_powder": "Aluminium Powder",
        "titanium_sponge": "Titanium Sponge", "iron_filings": "Iron Filings",
        "potassium_perchlorate": "Potassium Perchlorate", "sulfur": "Sulfur",
        "charcoal_dust": "Charcoal Dust", "dextrin_binder": "Dextrin Binder"
    }
    chemical_descs = {
        "strontium_nitrate": "Sr(NO3)2 \u00b7 606 nm crimson line",
        "barium_nitrate": "Ba(NO3)2 \u00b7 524 nm green line",
        "copper_chloride": "CuCl \u00b7 455 nm azure band",
        "sodium_nitrate": "NaNO3 \u00b7 589 nm D-line \u2014 washes out other colours",
        "calcium_chloride": "CaCl2 \u00b7 622 nm orange band",
        "potassium_nitrate": "KNO3 \u00b7 404 nm violet line, and an oxidiser itself",
        "magnesium_powder": "Mg \u00b7 burns white-hot at 3100 \u00b0C",
        "aluminium_powder": "Al \u00b7 gold-white glitter with long trails",
        "titanium_sponge": "Ti \u00b7 silver-white spark fountain",
        "iron_filings": "Fe \u00b7 sparkler spray, heavy smoke",
        "potassium_perchlorate": "KClO4 \u00b7 clean hot oxidiser, the heart of every shell",
        "sulfur": "S \u00b7 lowers the ignition temperature of the grain",
        "charcoal_dust": "C \u00b7 orange embers and a great deal of smoke",
        "dextrin_binder": "(C6H10O5)n \u00b7 binds the grain so the shell survives the lift"
    }
    for item, name in chemical_names.items():
        lang[f"item.fireworkshaders.{item}"] = name
        lang[f"chemical.fireworkshaders.{item}.desc"] = chemical_descs[item]
    for role, name in [("colour", "Colour producer"), ("brightener", "Brightener"),
                       ("oxidiser", "Oxidiser"), ("fuel", "Fuel"), ("binder", "Binder")]:
        lang[f"chemical.fireworkshaders.role.{role}"] = name

    shape_names = {
        "peony": "Peony", "chrysanthemum": "Chrysanthemum", "sphere": "Sphere",
        "willow": "Willow", "ring": "Ring", "double_ring": "Double Ring",
        "cascade": "Cascade", "palm": "Palm", "crossette": "Crossette", "saturn": "Saturn",
        "heart": "Heart", "star": "Star", "comet": "Comet"
    }
    for shape, name in shape_names.items():
        lang[f"shape.fireworkshaders.{shape}"] = name

    module_names = {
        "trail": "Trails", "crackle": "Crackle", "strobe": "Strobe", "smoke": "Heavy Smoke",
        "whistle": "Whistler", "shriek": "Shriek", "flash": "Flash Core",
        "shockwave": "Shockwave", "shell_of_shells": "Multi-Break", "drape": "Willow Drape",
        "spectrum": "Spectrum", "ground_glow": "Ground Light", "linger": "Lingering Embers",
        "dust": "Metal Dust"
    }
    for module, name in module_names.items():
        lang[f"module.fireworkshaders.{module}"] = name

    preset_names = {
        "stardust_willow": "Stardust Willow", "meteor_shower": "Meteor Shower",
        "sonic_boom": "Sonic Boom", "dragons_breath": "Dragon's Breath",
        "prismatic_cascade": "Prismatic Cascade", "red_peony": "Red Peony",
        "golden_chrysanthemum": "Golden Chrysanthemum", "emerald_ring": "Emerald Ring"
    }
    preset_lore = {
        "stardust_willow": "Gold and silver stars that hang at the top of the break, then weep "
                           "down over eight full seconds.",
        "meteor_shower": "Breaks, then divides into eight burning children that arc over and burst "
                         "again, each a little lower than the last.",
        "sonic_boom": "Fast, loud and rude: a shrieking climb, a blinding white core and a "
                      "pressure ring that rattles the windows.",
        "dragons_breath": "A ring of fire standing on the ground, throwing embers that burn for "
                          "seconds and light the fields below.",
        "prismatic_cascade": "A waterfall of stars that sweeps the whole spectrum as it falls, "
                             "from violet at the top to crimson at the bottom.",
        "red_peony": "The honest old favourite: crimson, dense, symmetrical.",
        "golden_chrysanthemum": "A slow golden sphere with heavy glittering trails.",
        "emerald_ring": "A flat green ring, best watched from directly underneath."
    }
    preset_desc = {
        "stardust_willow": "Willow break, trails, drape, long burn",
        "meteor_shower": "Multi-break, eight timed sub-shells",
        "sonic_boom": "Shriek lift, flash core, shockwave, heavy shake",
        "dragons_breath": "Ring break, ground light, lingering embers",
        "prismatic_cascade": "Cascade break, spectrum sweep",
        "red_peony": "Starter shell, 4 inch",
        "golden_chrysanthemum": "Starter shell, 5 inch",
        "emerald_ring": "Starter shell, flat ring"
    }
    for preset, name in preset_names.items():
        lang[f"preset.fireworkshaders.{preset}"] = name
        lang[f"preset.fireworkshaders.{preset}.lore"] = preset_lore[preset]
        lang[f"preset.fireworkshaders.{preset}.desc"] = preset_desc[preset]

    lang["design.fireworkshaders.auto_name"] = "%s %s"
    lang["tooltip.fireworkshaders.caliber"] = "Caliber: %s"
    lang["tooltip.fireworkshaders.stars"] = "%s stars"
    lang["tooltip.fireworkshaders.fuse"] = "Lift: %ss"
    lang["tooltip.fireworkshaders.payload"] = "Multi-break payload: %s"
    lang["tooltip.fireworkshaders.grains"] = "%s grains of propellant"
    lang["tooltip.fireworkshaders.burst_radius"] = "Break radius: %s blocks"
    lang["tooltip.fireworkshaders.unbuilt"] = "Uncharged shell \u2014 build it at a Pyrotechnic Workstation"
    lang["tooltip.fireworkshaders.igniter_mode"] = "Mode: %s (sneak-use to cycle)"
    lang["tooltip.fireworkshaders.igniter_links"] = "Linked pads: %s/%s"
    lang["tooltip.fireworkshaders.igniter_help"] = "Use on a pad to link \u00b7 sneak-use on a pad to unlink"
    for mode, name in [("go", "FIRE"), ("arm", "ARM"), ("safe", "SAFE"), ("test", "TEST CUE")]:
        lang[f"mode.fireworkshaders.{mode}"] = name

    lang["gui.fireworkshaders.missing_count"] = "%s x %s"
    lang["gui.fireworkshaders.output_occupied"] = "Take the finished shell out of the press first"
    lang["gui.fireworkshaders.cost"] = "%s grains \u00b7 %s salts \u00b7 %s binder \u00b7 %s modules"
    lang["gui.fireworkshaders.assemble"] = "ASSEMBLE"
    lang["gui.fireworkshaders.assemble_tip"] = "Consume the ingredients and press a shell"
    lang["gui.fireworkshaders.clear_colours"] = "Clear"
    lang["gui.fireworkshaders.save"] = "SAVE"
    lang["gui.fireworkshaders.load"] = "LOAD"
    lang["gui.fireworkshaders.payload_saved"] = "Multi-break from memory"
    lang["gui.fireworkshaders.name"] = "Name"
    lang["gui.fireworkshaders.memory"] = "Slot %s"
    lang["gui.fireworkshaders.ready"] = "Ingredients ready"
    lang["gui.fireworkshaders.and_more"] = "(+%s more)"
    lang["gui.fireworkshaders.custom"] = "CUSTOM"
    lang["gui.fireworkshaders.shape_label"] = "SHAPE"
    lang["gui.fireworkshaders.preset_label"] = "PRESET"
    lang["gui.fireworkshaders.caliber_label"] = "CALIBER"
    lang["gui.fireworkshaders.fuse_label"] = "LIFT"
    lang["gui.fireworkshaders.timeline"] = "TIMELINE"
    lang["gui.fireworkshaders.syncing"] = "syncing\u2026"
    lang["gui.fireworkshaders.empty_tube"] = "\u2014 empty \u2014"
    lang["gui.fireworkshaders.cue_add_tip"] = "Add a cue for the selected bracket with the same design"
    lang["gui.fireworkshaders.cue_delete_tip"] = "Delete the selected cue"
    lang["gui.fireworkshaders.cue_toggle"] = "ENABLE / DISABLE"
    lang["gui.fireworkshaders.arm"] = "ARM"
    lang["gui.fireworkshaders.safe"] = "SAFE"
    lang["gui.fireworkshaders.go"] = "GO"
    lang["gui.fireworkshaders.pad_state"] = "State: %s"
    lang["gui.fireworkshaders.pad_loaded"] = "Loaded: %s / 9 tubes"
    lang["gui.fireworkshaders.pad_lead"] = "Lead-in: %ss"
    lang["gui.fireworkshaders.armed_warning"] = "PAD ARMED"
    for state, name in [("idle", "idle"), ("armed", "armed"), ("running", "show running"),
                        ("safe", "safe (aborted)")]:
        lang[f"padstate.fireworkshaders.{state}"] = name

    lang["hud.fireworkshaders.show_running"] = "SHOW"
    lang["hud.fireworkshaders.t_minus"] = "T-"
    lang["hud.fireworkshaders.armed"] = "PAD ARMED"
    lang["hud.fireworkshaders.loaded"] = "%s cues \u00b7 %s tubes loaded"

    lang["message.fireworkshaders.pad_loaded"] = "Loaded %s into the pad"
    lang["message.fireworkshaders.pad_full"] = "The pad has no free tubes"
    lang["message.fireworkshaders.pad_armed"] = "Pad armed"
    lang["message.fireworkshaders.pad_safe"] = "Pad set safe"
    lang["message.fireworkshaders.pad_no_rockets"] = "Nothing loaded to fire"
    lang["message.fireworkshaders.pad_already_running"] = "That pad is already running"
    lang["message.fireworkshaders.pad_go"] = "Show starts in %ss \u2014 %s cues"
    lang["message.fireworkshaders.pad_program_demo"] = "Demo program loaded"
    lang["message.fireworkshaders.pad_program_cleared"] = "Program cleared"
    lang["message.fireworkshaders.igniter_linked"] = "Linked pad (%s linked, %s loaded)"
    lang["message.fireworkshaders.igniter_already_linked"] = "That pad is already linked"
    lang["message.fireworkshaders.igniter_link_limit"] = "Igniter is full (%s pads)"
    lang["message.fireworkshaders.igniter_unlinked"] = "Unlinked pad (%s remain)"
    lang["message.fireworkshaders.igniter_not_linked"] = "That pad is not linked"
    lang["message.fireworkshaders.igniter_mode"] = "Mode: %s"
    lang["message.fireworkshaders.igniter_no_links"] = "No pads linked \u2014 use the igniter on a pad first"
    lang["message.fireworkshaders.igniter_armed"] = "Armed %s pads"
    lang["message.fireworkshaders.igniter_go"] = "Fired %s pads"
    lang["message.fireworkshaders.igniter_safe"] = "Set %s pads safe"
    lang["message.fireworkshaders.igniter_test"] = "Test fired on %s pads"
    lang["message.fireworkshaders.preset_loaded"] = "Loaded the %s design"
    lang["message.fireworkshaders.design_saved"] = "Stored in slot %s"
    lang["message.fireworkshaders.missing_header"] = "Not enough ingredients:"
    lang["message.fireworkshaders.assembled"] = "Built %s"
    lang["message.fireworkshaders.command_test"] = "Firing %s"
    lang["message.fireworkshaders.command_give"] = "Gave %s"
    lang["message.fireworkshaders.command_no_pad"] = "No launch pad within 24 blocks"
    lang["message.fireworkshaders.command_show"] = "Show %s: %s cues, %s tubes"

    lang["subtitles.fireworkshaders.boom"] = "Firework booms"
    lang["subtitles.fireworkshaders.crackle"] = "Firework crackles"

    write_json(os.path.join(ASSETS, "lang", "en_us.json"), lang)


def main():
    generate_particles()
    generate_entity()
    generate_gui()
    generate_blocks()
    generate_items()
    generate_json()
    generate_lang()
    print("assets generated in", ASSETS)


if __name__ == "__main__":
    main()
