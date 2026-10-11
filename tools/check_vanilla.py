#!/usr/bin/env python3
"""Verify that every member referenced on an imported vanilla/Forge class exists in 1.20.1.

Fetches the class sources from a decompiled 1.20.1 tree over the GitHub API (cached in /tmp/mcref2),
walks the superclass/interface chain, and reports members that cannot be found. Catches the classic
blind-writing mistake: calling a method that exists in 1.20.4 but not 1.20.1.
"""
import json
import os
import re
import subprocess
import sys
from collections import defaultdict

ROOT = "/home/user/Yuletides-FireWorks"
SRC = os.path.join(ROOT, "src", "main", "java")
CACHE = "/tmp/mcref2"
MC_REPO = "Blackjack200/minecraft_client_1_20_1"
FORGE_REPO = "MinecraftForge/MinecraftForge"
FORGE_REF = "1.20.1"
os.makedirs(CACHE, exist_ok=True)

TREE = [x.strip() for x in open("/tmp/mc_tree.txt") if x.strip()]
TREE_SET = set(TREE)
BY_SIMPLE = defaultdict(list)
for fqn in TREE:
    BY_SIMPLE[fqn.split(".")[-1]].append(fqn)
FORGE_TREE = [x.strip()[len("src/main/java/"):-5].replace("/", ".")
              for x in open("/tmp/forge_tree.txt") if x.strip().endswith(".java")]
FORGE_SET = set(FORGE_TREE)
FORGE_BY_SIMPLE = defaultdict(list)
for fqn in FORGE_TREE:
    FORGE_BY_SIMPLE[fqn.split(".")[-1]].append(fqn)


def gh_fetch(repo, path, ref):
    try:
        out = subprocess.run(["gh", "api", f"repos/{repo}/contents/{path}?ref={ref}",
                              "--jq", ".content"], capture_output=True, text=True, timeout=60)
        if out.returncode != 0:
            return None
        import base64
        return base64.b64decode(out.stdout.strip() + "===").decode("utf-8", "replace")
    except Exception:
        return None


def fetch(fqn):
    """Return source text for a class, or None."""
    cached = os.path.join(CACHE, fqn + ".java")
    if os.path.exists(cached):
        data = open(cached, encoding="utf-8").read()
        return data or None
    text = None
    if fqn in TREE_SET:
        text = gh_fetch(MC_REPO, fqn.replace(".", "/") + ".java", "HEAD")
    elif fqn in FORGE_SET:
        text = gh_fetch(FORGE_REPO, "src/main/java/" + fqn.replace(".", "/") + ".java", FORGE_REF)
    open(cached, "w", encoding="utf-8").write(text or "")
    return text


def fetch_nested(simple):
    """Nested types have no file of their own in the decompiled tree; find the declaration inside
    any class already downloaded and return just that block."""
    import glob
    pattern = re.compile(r"\b(?:interface|class|enum|record)\s+" + re.escape(simple) + r"\b")
    for path in glob.glob(os.path.join(CACHE, "*.java")):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            continue
        m = pattern.search(text)
        if not m:
            continue
        brace = text.find("{", m.end())
        if brace < 0:
            continue
        depth = 0
        i = brace
        while i < len(text):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        return text[m.start():i + 1]
    return None


MEMBER_RE = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*\.\s*([a-z_][A-Za-z0-9_]*)\b")


def collect_usages():
    usages = defaultdict(set)
    for base, _dirs, names in os.walk(SRC):
        for name in names:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            rel = os.path.relpath(path, ROOT)
            text = open(path, encoding="utf-8").read()
            imports = {}
            for m in re.finditer(r"^import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M):
                fqn = m.group(1)
                imports[fqn.split(".")[-1]] = fqn
            for m in MEMBER_RE.finditer(text):
                typ, member = m.group(1), m.group(2)
                if typ in ("String", "Math", "System", "Objects", "List", "Map", "Set", "EnumSet"):
                    continue
                if typ in imports and (imports[typ].startswith("net.minecraft")
                                       or imports[typ].startswith("com.mojang")):
                    usages[imports[typ]].add((member, rel))
    return usages


def declared_members(text):
    names = set()
    names.update(re.findall(r"\b(?:public|protected)\s+(?:static\s+)?(?:final\s+)?[\w.<>\[\], ?]+\s+(\w+)\s*[=(;,)]", text))
    names.update(re.findall(r"\b(\w+)\s*\(", text))
    names.update(re.findall(r"\b(?:public|protected)\s+(?:static\s+)?(?:final\s+)?[\w.<>\[\], ?]+\s+(\w+)\s*;", text))
    return names


def parents(text):
    out = []
    for m in re.finditer(r"(?:extends|implements)\s+([A-Za-z_][\w.<>]*)", text):
        raw = m.group(1).split("<")[0]
        out.append(raw.split(".")[-1])
    return out


def main():
    usages = collect_usages()
    unresolved = []
    checked = 0
    for fqn, members in sorted(usages.items()):
        text = fetch(fqn)
        if text is None:
            unresolved.append((fqn, sorted(m for m, _ in members), "SOURCE NOT AVAILABLE"))
            continue
        chain = [fqn]
        seen = {fqn}
        members_found = set(declared_members(text))
        queue = [fqn]
        while queue:
            cur = queue.pop()
            curtext = fetch(cur)
            if curtext is None:
                continue
            members_found |= declared_members(curtext)
            for parent in parents(curtext):
                cands = BY_SIMPLE.get(parent, []) + FORGE_BY_SIMPLE.get(parent, [])
                for cand in cands:
                    if cand not in seen and (cand.startswith("net.minecraft")
                                             or cand.startswith("com.mojang")
                                             or cand.startswith("net.minecraftforge")):
                        seen.add(cand)
                        queue.append(cand)
        for member, where in sorted(members):
            checked += 1
            if member not in members_found:
                unresolved.append((fqn, member, where))
    print(f"checked {checked} member references on {len(usages)} vanilla/forge classes")
    print(f"{len(unresolved)} not found:")
    for fqn, member, where in unresolved:
        if isinstance(member, list):
            print(f"  {fqn}: {where}: {', '.join(member)}")
        else:
            print(f"  {where}: {fqn.split('.')[-1]}.{member}")


if __name__ == "__main__":
    main()
