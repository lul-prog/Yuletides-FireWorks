#!/usr/bin/env python3
"""Verify every @Override in the project against the real 1.20.1 superclass/interface chain.

A wrong @Override signature is a hard compile error, and it is the most likely mistake when code is
written against an API from memory. For each project type the script resolves its parents, fetches
their sources over the GitHub API (cached in /tmp/mcref2), and checks that each @Override exists
with a matching parameter count. Parents it cannot fetch (loader-only classes) are reported so the
results are read honestly rather than as a pass.
"""
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_vanilla import fetch, BY_SIMPLE, FORGE_BY_SIMPLE  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")

WITH_BODY = re.compile(
    r"(?:^|\n)\s*(?:(?:public|protected|private|static|final|abstract|synchronized|default|native)\s+)*"
    r"(?:<[^>{};]*>\s+)?([A-Za-z_][\w.<>\[\], ?]*?)\s+([a-zA-Z_]\w*)\s*\(([^;{}]*)\)\s*"
    r"(?:throws [\w., ]+)?\{", re.S)
NO_BODY = re.compile(
    r"(?:^|\n)\s*(?:(?:public|protected|private|static|final|abstract|synchronized|default)\s+)*"
    r"(?:<[^>{};]*>\s+)?([A-Za-z_][\w.<>\[\], ?]*?)\s+([a-zA-Z_]\w*)\s*\(([^;{}]*)\)\s*"
    r"(?:throws [\w., ]+)?;")
# java.lang / java.util members every class already has; treated as known so that ordinary
# overrides (equals, toString, compareTo) do not read as errors.
JAVA_BASE = {("equals", 1), ("hashCode", 0), ("toString", 0), ("clone", 0), ("getClass", 0),
             ("notify", 0), ("notifyAll", 0), ("wait", 0), ("wait", 1), ("wait", 2),
             ("finalize", 0), ("compareTo", 1), ("iterator", 0), ("run", 0), ("apply", 1),
             ("accept", 1), ("test", 1), ("get", 0), ("length", 0), ("close", 0)}

KEYWORDS = {"if", "for", "while", "switch", "catch", "return", "new", "synchronized", "package",
            "import", "else", "case", "this", "super", "class", "interface", "enum", "record",
            "extends", "implements", "final", "abstract", "sealed", "permits", "non-sealed",
            "static", "default", "public", "protected", "private"}


def param_count(text):
    text = text.strip()
    if not text:
        return 0
    depth = 0
    count = 1
    for ch in text:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            count += 1
    return count


def methods_of(text):
    out = set()
    for regex in (WITH_BODY, NO_BODY):
        for m in regex.finditer(text):
            ret, name, params = m.group(1).strip(), m.group(2), m.group(3)
            if name in KEYWORDS or ret in KEYWORDS:
                continue
            out.add((name, param_count(params)))
    return out


def parents_of(text):
    """Every simple type name appearing after extends/implements in a declaration clause."""
    out = []
    for m in re.finditer(r"\b(?:extends|implements)\b([^{;]*)", text):
        for token in re.split(r"[,\s]+", m.group(1)):
            token = token.split("<")[0].strip()
            if re.fullmatch(r"[A-Za-z_][\w.]*", token or "") and token not in KEYWORDS:
                name = token.split(".")[-1]
                if name not in out:
                    out.append(name)
    return out


def project_index():
    """simple type name -> (file, imports, parent simple names) for every project type."""
    index = {}
    for base, _dirs, names in os.walk(SRC):
        for name in names:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            text = re.sub(r"/\*.*?\*/", "", open(path, encoding="utf-8").read(), flags=re.S)
            text = re.sub(r"//[^\n]*", "", text)
            imports = {m.group(1).split(".")[-1]: m.group(1)
                       for m in re.finditer(r"^import\s+([\w.]+)\s*;", text, re.M)}
            parents = []
            for m in re.finditer(r"(?:class|interface|enum|record)\s+(\w+)([^;{}]*?)\{", text):
                parents.extend(parents_of(m.group(2)))
            for m in re.finditer(r"^\s*(?:(?:public|protected|private|static|final|abstract)\s+)*"
                                 r"(?:class|interface|enum|record)\s+(\w+)", text, re.M):
                index[m.group(1)] = (path, imports, parents)
    return index


PROJECT = None


def project_methods(simple, index, seen):
    entry = index.get(simple)
    if entry is None:
        return None
    path, imports, parents = entry
    text = open(path, encoding="utf-8").read()
    return methods_of(text), parents, imports


def main():
    global PROJECT
    PROJECT = project_index()
    problems = []
    partial = []
    checked = 0
    for base, _dirs, names in os.walk(SRC):
        for name in names:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            rel = os.path.relpath(path, ROOT)
            text = open(path, encoding="utf-8").read()
            clean = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
            clean = re.sub(r"//[^\n]*", "", clean)
            imports = {m.group(1).split(".")[-1]: m.group(1)
                       for m in re.finditer(r"^import\s+([\w.]+)\s*;", clean, re.M)}
            overrides = []
            for m in re.finditer(r"@Override\s+((?:(?:public|protected|private|static|final|"
                                 r"synchronized|default|abstract)\s+)*)(?:<[^>{};]*>\s+)?"
                                 r"([\w.<>\[\], ?]+?)\s+([a-zA-Z_]\w*)\s*\(([^;{}]*)\)", clean, re.S):
                overrides.append((m.group(2).strip(), m.group(3), param_count(m.group(4)),
                                  clean.count("\n", 0, m.start()) + 1))
            if not overrides:
                continue
            clause_parents = []
            for m in re.finditer(r"(?:class|interface|enum|record)\s+\w+([^;{}]*?)\{", clean):
                clause_parents.extend(parents_of(m.group(1)))
            # anonymous subclasses contribute their own supertype to the chain
            for m in re.finditer(r"new\s+([A-Z][\w.]*)\s*\([^()]*\)\s*\{", clean):
                simple = m.group(1).split(".")[-1]
                if simple not in clause_parents:
                    clause_parents.append(simple)
            queue = list(clause_parents)
            seen = set()
            known = set(JAVA_BASE)
            unresolved = set()
            while queue:
                simple = queue.pop(0)
                if simple in seen:
                    continue
                seen.add(simple)
                if simple in PROJECT:
                    entry = PROJECT[simple]
                    known |= methods_of(open(entry[0], encoding="utf-8").read())
                    for parent in entry[2]:
                        if parent not in seen:
                            queue.append(parent)
                    got = True
                candidates = []
                if simple in imports:
                    candidates.append(imports[simple])
                candidates += BY_SIMPLE.get(simple, [])
                candidates += FORGE_BY_SIMPLE.get(simple, [])
                if simple in PROJECT:
                    continue
                got = False
                for cand in candidates:
                    if not (cand.startswith("net.minecraft") or cand.startswith("com.mojang")
                            or cand.startswith("net.minecraftforge")):
                        continue
                    src = fetch(cand)
                    if src is None:
                        continue
                    known |= methods_of(src)
                    queue.extend(parents_of(src))
                    got = True
                    break
                if not got:
                    unresolved.add(simple)
            for ret, name, arity, line in overrides:
                checked += 1
                if (name, arity) not in known:
                    entry = (rel, line, f"{ret} {name}({arity} args)")
                    if unresolved:
                        partial.append((entry, sorted(unresolved)))
                    else:
                        problems.append(entry)
    print(f"checked {checked} @Override declarations")
    print(f"NOT FOUND, full chain resolved ({len(problems)}):")
    for rel, line, sig in problems:
        print(f"  {rel}:{line}: {sig}")
    print(f"UNCONFIRMED, chain incomplete ({len(partial)}):")
    for (rel, line, sig), missing in partial:
        print(f"  {rel}:{line}: {sig}   [could not fetch: {', '.join(missing)}]")


if __name__ == "__main__":
    main()
