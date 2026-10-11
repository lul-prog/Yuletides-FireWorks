#!/usr/bin/env python3
"""Cross-check the Java registries against the shipped assets.

Checks performed:
  * every {@code register("name")} in the registry classes has a model, a blockstate (for blocks)
    and the textures those models reference,
  * every particle type has an {@code assets/<ns>/particles/<name>.json} whose texture exists,
  * every {@code Component.translatable("...")} key with a static namespace resolves in en_us.json,
  * every JSON file under assets/data parses.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java")
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "fireworkshaders")
DATA = os.path.join(RES, "data", "fireworkshaders")
NS = "fireworkshaders"

problems = []
notes = []


def read(path):
    return open(path, encoding="utf-8").read()


def java_sources():
    for base, _dirs, names in os.walk(JAVA):
        for name in names:
            if name.endswith(".java"):
                yield os.path.join(base, name)


def registry_names(file_name, pattern):
    path = os.path.join(JAVA, "com", "fireworkshaders", "registry", file_name)
    if not os.path.exists(path):
        problems.append(f"missing registry class {file_name}")
        return []
    return re.findall(pattern, read(path))


def texture_exists(path):
    return os.path.exists(os.path.join(ASSETS, "textures", path + ".png"))


def check_json_tree():
    for root, _dirs, files in os.walk(RES):
        for name in files:
            if not name.endswith(".json"):
                continue
            p = os.path.join(root, name)
            try:
                json.load(open(p, encoding="utf-8"))
            except Exception as exc:                     # noqa: BLE001
                problems.append(f"invalid JSON: {os.path.relpath(p, ROOT)}: {exc}")


def check_items():
    names = registry_names("FSItems.java", r'ITEMS\.register\(\s*"([a-z_]+)"')
    # block items come from the blocks registry
    names += ["pyrotechnic_workstation", "launch_pad"]
    if "chemical.itemName()" in read(os.path.join(JAVA, "com", "fireworkshaders", "registry",
                                                  "FSItems.java")):
        for m in re.finditer(r'^\s*[A-Z_]+\("([a-z_]+)"', read(
                os.path.join(JAVA, "com", "fireworkshaders", "pyro", "Chemical.java")), re.M):
            names.append(m.group(1))
    for name in sorted(set(names)):
        model = os.path.join(ASSETS, "models", "item", name + ".json")
        if not os.path.exists(model):
            problems.append(f"item '{name}': no models/item/{name}.json")
            continue
        entry = json.load(open(model, encoding="utf-8"))
        if "textures" in entry and "layer0" in entry["textures"]:
            tex = entry["textures"]["layer0"].split(":")[-1]
            if not texture_exists(tex):
                problems.append(f"item '{name}': missing texture {tex}.png")


def check_blocks():
    names = registry_names("FSBlocks.java", r'BLOCKS\.register\(\s*"([a-z_]+)"')
    for name in names:
        blockstate = os.path.join(ASSETS, "blockstates", name + ".json")
        if not os.path.exists(blockstate):
            problems.append(f"block '{name}': no blockstates/{name}.json")
            continue
        variants = json.load(open(blockstate, encoding="utf-8")).get("variants", {})
        for variant, entry in variants.items():
            parts = entry if isinstance(entry, list) else [entry]
            for part in parts:
                model = part["model"].split(":")[-1].split("/", 1)[1]
                mpath = os.path.join(ASSETS, "models", "block", model.split("/")[-1] + ".json")
                if not os.path.exists(mpath):
                    problems.append(f"block '{name}' variant '{variant}': missing model {model}")
                    continue
                for tex in json.load(open(mpath, encoding="utf-8"))["textures"].values():
                    if tex.startswith("#"):
                        continue
                    rel = tex.split(":")[-1]
                    if not texture_exists(rel):
                        problems.append(f"model {model}: missing texture {rel}.png")
        for extra in ("north", "east", "south", "west"):
            pass
    return names


def check_particles():
    path = os.path.join(JAVA, "com", "fireworkshaders", "registry", "FSParticles.java")
    if not os.path.exists(path):
        problems.append("missing FSParticles.java")
        return
    text = read(path)
    block = re.search(r"Kind\.[A-Z_]+\(\s*\"([a-z_]+)\"", text)
    kinds = re.findall(r'\(\s*Kind\.[A-Z_]+,\s*"([a-z_0-9]+)"', text)
    names = re.findall(r'new PyroParticleType\(|"(pyro_[a-z_]+)"', text)
    declared = sorted(set(re.findall(r'"(pyro_[a-z_]+)"', text)))
    if not declared:
        notes.append("could not read particle names from FSParticles.java")
    for name in declared:
        particle = os.path.join(ASSETS, "particles", name + ".json")
        if not os.path.exists(particle):
            problems.append(f"particle '{name}': no particles/{name}.json")
            continue
        for tex in json.load(open(particle, encoding="utf-8"))["textures"]:
            rel = tex.split(":")[-1]
            if not os.path.exists(os.path.join(ASSETS, "textures", "particle",
                                               rel.split("/")[-1] + ".png")):
                problems.append(f"particle '{name}': missing texture {rel}")
    # every provider registration must point at a registered particle
    notes.append(f"particle types declared in code: {', '.join(declared) or 'none found'}")


def check_lang():
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lang_path, encoding="utf-8"))
    used = set()
    for path in java_sources():
        text = read(path)
        for m in re.finditer(r'translatable\(\s*"([a-z_]+)\."\s*\+\s*([A-Za-z_.]+)\s*\+\s*"\.([a-z_0-9.]+)"',
                             text):
            used.add(f"{m.group(1)}.{NS}.{m.group(3)}")
        for m in re.finditer(r'translatable\(\s*"' + NS + r'\.([a-z_0-9.]+)"', text):
            used.add(m.group(1))
    missing = sorted(k for k in used if not k.endswith(".") and k not in lang)
    for key in missing:
        problems.append(f"lang: no entry for '{key}'")
    unused = sorted(k for k in lang if k not in used and not k.startswith(
        ("item.", "block.", "shape.", "module.", "preset.", "chemical.", "mode.", "padstate.")))
    notes.append(f"{len(lang)} lang keys, {len(missing)} missing")


def main():
    check_json_tree()
    check_items()
    check_blocks()
    check_particles()
    check_lang()
    # recipes / loot tables should cover the two blocks
    for block in ("pyrotechnic_workstation", "launch_pad"):
        for kind, sub in (("recipe", "recipes"), ("loot table", "loot_tables/blocks")):
            p = os.path.join(DATA, sub, block + ".json")
            if not os.path.exists(p):
                problems.append(f"no {kind} for {block}")
    print(f"{len(problems)} problems:")
    for p in problems:
        print("  -", p)
    print("notes:")
    for n in notes:
        print("  *", n)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
