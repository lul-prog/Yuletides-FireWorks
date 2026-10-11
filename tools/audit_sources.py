#!/usr/bin/env python3
"""
Static audit for the Yuletide Fireworks sources.

There is no JDK in this sandbox, so this script is the next best thing: it tokenises every .java
file (handling comments, strings, chars and text blocks), checks bracket balance, builds a table of
every type and member the project declares, and then resolves every `Type.member` reference against
that table. Anything it cannot resolve is either an external API (fine) or a blind-writing mistake
(not fine) — the report separates the two.

Usage:  python3 tools/audit_sources.py
"""

import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    from check_vanilla import fetch as fetch_vanilla, BY_SIMPLE as VANILLA_SIMPLE, \
        FORGE_BY_SIMPLE as FORGE_SIMPLE
    from check_overrides import methods_of as vanilla_methods
    HAS_VANILLA = True
except Exception:                                        # pragma: no cover
    HAS_VANILLA = False

_VANILLA_CACHE = {}


def vanilla_member_closure(simple, depth=0):
    """Walk a vanilla/Forge type and all of its ancestors, collecting member names."""
    if not HAS_VANILLA or depth > 6:
        return set(), False
    names = set()
    ok = False
    queue = list(VANILLA_SIMPLE.get(simple, [])) + list(FORGE_SIMPLE.get(simple, []))
    seen = set()
    while queue:
        cand = queue.pop(0)
        if cand in seen:
            continue
        seen.add(cand)
        if not (cand.startswith("net.minecraft") or cand.startswith("com.mojang")
                or cand.startswith("net.minecraftforge")):
            continue
        src = fetch_vanilla(cand)
        if src is None:
            continue
        ok = True
        names |= {n for n, _c in vanilla_methods(src)}
        names |= set(re.findall(r"\b(?:public|protected)\s+[\w.<>\[\], ?]+\s+(\w+)\s*[=(;,)]",
                                src))
        from check_overrides import parents_of
        for parent in parents_of(src):
            if parent in ("Object", "Comparable", "Iterable"):
                continue
            queue.extend(VANILLA_SIMPLE.get(parent, [])[:2])
            queue.extend(FORGE_SIMPLE.get(parent, [])[:2])
    return names, ok

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "src", "main", "java")

OPEN = {"{": "}", "(": ")", "[": "]"}
CLOSE = {v: k for k, v in OPEN.items()}


def strip_and_tokenize(text):
    """Returns (code_without_literals, balanced?, first_problem) — strings become 'S', chars 'C'."""
    out = []
    i = 0
    n = len(text)
    line = 1
    stack = []
    problem = None
    while i < n:
        ch = text[i]
        if ch == "\n":
            line += 1
            out.append(ch)
            i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                if text[i] == "\n":
                    line += 1
                i += 1
            i += 2
            continue
        if ch == '"':
            if text[i:i + 3] == '"""':          # text block
                i += 3
                while i + 1 < n and text[i:i + 3] != '"""':
                    if text[i] == "\n":
                        line += 1
                    i += 1
                i += 3
            else:
                i += 1
                while i < n and text[i] != '"':
                    if text[i] == "\\":
                        i += 1
                    i += 1
                i += 1
            out.append('"S"')
            continue
        if ch == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            out.append("'C'")
            continue
        if ch in OPEN:
            stack.append((ch, line))
        elif ch in CLOSE:
            if not stack or stack[-1][0] != CLOSE[ch]:
                if problem is None:
                    problem = f"unmatched '{ch}' at line {line}"
            else:
                stack.pop()
        out.append(ch)
        i += 1
    if stack and problem is None:
        ch, ln = stack[-1]
        problem = f"unclosed '{ch}' opened at line {ln}"
    return "".join(out), problem


class Type:
    def __init__(self, fqn, kind, file, depth):
        self.fqn = fqn
        self.kind = kind
        self.file = file
        self.depth = depth
        self.members = set()
        self.method_params = defaultdict(list)   # name -> [param counts]
        self.ctor_params = []
        self.extends = []
        self.deps = set()
        self.record_components = {}
        self.enum_constants = []


TYPES = {}
SIMPLE = defaultdict(list)
FILES = []

METHOD_RE = re.compile(
    r"(?:^|\n)\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp)\s+)*"
    r"(?:<[^>{};]*>\s+)?"
    r"([A-Za-z_][\w.<>\[\], ?]*?)\s+"
    r"([a-zA-Z_]\w*)\s*\(([^;{}]*)\)\s*(?:throws [\w., ]+)?\{", re.S)

FIELD_RE = re.compile(
    r"(?:^|\n)\s*(?:(?:public|protected|private|static|final|volatile|transient)\s+)+"
    r"([A-Za-z_][\w.<>\[\], ?]*?)\s+([a-zA-Z_]\w*)\s*(?:=[^;\n]*)?;")

ENUM_CONST_RE = re.compile(r"(?:^|\n)\s*([A-Z][A-Z0-9_]{1,})\s*(?:\(|,|;)")
RECORD_RE = re.compile(r"record\s+([A-Z]\w*)\s*\(([^)]*)\)")
TYPE_RE = re.compile(r"(?:^|[\s{])(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\s+)*"
                     r"(class|interface|enum|record)\s+([A-Z]\w*)")
EXTENDS_RE = re.compile(r"(?:extends|implements)\s+([A-Za-z_][\w.]*)")


def count_params(text):
    """Count top-level comma separated parameters/arguments, honouring brackets and literals."""
    text = text.strip()
    if not text:
        return 0
    varargs = "..." in text
    depth = 0
    count = 1
    i = 0
    while i < len(text):
        ch = text[i]
        if ch in "[{(":
            depth += 1
        elif ch in "]})":
            depth -= 1
        elif ch == '"' or ch == "'":
            quote = ch
            i += 1
            while i < len(text) and text[i] != quote:
                if text[i] == "\\":
                    i += 1
                i += 1
        elif ch == "," and depth == 0:
            count += 1
        i += 1
    return -count if varargs else count


def slice_call(code, start):
    """Given an index just after the '(' of a call, return the argument text and end index."""
    depth = 1
    i = start
    while i < len(code):
        ch = code[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
            if depth == 0:
                return code[start:i], i
        i += 1
    return code[start:], len(code)


def parse_file(path):
    text = open(path, encoding="utf-8").read()
    code, problem = strip_and_tokenize(text)
    rel = os.path.relpath(path, ROOT)
    pkg = ""
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", code, re.M)
    if m:
        pkg = m.group(1)
    FILES.append((rel, problem))
    # find type declarations with their brace spans so members can be attributed
    events = []
    for tm in TYPE_RE.finditer(code):
        kind, name = tm.group(1), tm.group(2)
        brace = code.find("{", tm.end())
        if brace < 0:
            continue
        events.append((tm.start(), kind, name, brace))
    spans = []
    for idx, (start, kind, name, brace) in enumerate(events):
        depth = 0
        j = brace
        while j < len(code):
            if code[j] == "{":
                depth += 1
            elif code[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        spans.append((start, j, kind, name, brace))

    # attribute each type to the innermost enclosing type
    for start, end, kind, name, brace in spans:
        enclosing = [s for s in spans if s[0] < start < s[1]]
        enclosing.sort(key=lambda s: s[0])
        prefix = ""
        depth = 0
        for s in enclosing:
            depth += 1
            prefix += s[3] + "."
        fqn = (pkg + "." if pkg else "") + prefix + name
        t = Type(fqn, kind, rel, depth)
        body = code[brace + 1:end]
        # members: search the whole body, then drop the nested type bodies
        nested_bodies = ""
        for s in spans:
            if s[0] > brace and s[1] < end:
                nested_bodies += code[s[0]:s[1]]
        own = body
        for em in EXTENDS_RE.finditer(code[max(0, start - 200):brace]):
            t.extends.append(em.group(1))
        for mm in METHOD_RE.finditer(own):
            ret, name2 = mm.group(1).strip(), mm.group(2)
            if name2 not in ("if", "for", "while", "switch", "catch", "return", "new"):
                if not re.match(r"^(package|import|class|interface|enum|record|else|case)$", ret):
                    t.members.add(name2)
                    t.method_params[name2].append(count_params(mm.group(3)))
        # constructors: no return type, same name as the type
        for cm in re.finditer(r"(?:^|\n)\s*(?:(?:public|protected|private)\s+)?"
                              + re.escape(name) + r"\s*\(([^;{}]*)\)\s*(?:throws [\w., ]+)?\{", own):
            if count_params(cm.group(1)) >= 0:
                t.ctor_params.append(count_params(cm.group(1)))
        for fm in FIELD_RE.finditer(own):
            t.members.add(fm.group(2))
        rm = RECORD_RE.search(code[max(0, start - 0):brace])
        if kind == "record" and rm:
            for part in rm.group(2).split(","):
                part = part.strip()
                if part:
                    cm = re.search(r"([A-Za-z_]\w*)\s*$", part)
                    if cm:
                        t.record_components[cm.group(1)] = part
                        t.members.add(cm.group(1))
        if kind == "enum":
            for em in ENUM_CONST_RE.finditer(own):
                name2 = em.group(1)
                if name2 not in ("IF", "FOR", "WHILE", "NEW", "RETURN", "CASE", "NULL", "TRUE",
                                 "FALSE", "INT", "LONG", "FLOAT", "DOUBLE", "BOOLEAN", "BYTE",
                                 "CHAR", "SHORT", "VOID", "CLASS", "RECORD", "PUBLIC", "STATIC",
                                 "PRIVATE", "IMPORT", "PACKAGE"):
                    t.enum_constants.append(name2)
                    t.members.add(name2)
        TYPES[fqn] = t
        SIMPLE[name].append(fqn)
    return code


# members every class has without declaring them (enums, Object); never worth reporting
JAVA_BASE_FOR_INSTANCE = {"ordinal", "name", "values", "valueOf", "equals", "hashCode", "toString",
                          "getClass", "clone", "wait", "notify", "notifyAll"}


def main():
    files = []
    for base, _dirs, names in os.walk(SRC):
        for name in names:
            if name.endswith(".java"):
                files.append(os.path.join(base, name))
    codes = {}
    for path in sorted(files):
        codes[path] = parse_file(path)

    print("=" * 78)
    print("STRUCTURE")
    print("=" * 78)
    bad = 0
    for rel, problem in FILES:
        if problem:
            bad += 1
            print(f"  BRACKET  {rel}: {problem}")
    print(f"  {len(FILES)} files, {bad} bracket problems")

    print()
    print("=" * 78)
    print("UNRESOLVED PROJECT MEMBER REFERENCES  (candidate compile errors)")
    print("=" * 78)
    ref_re = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*\.\s*([a-z_][A-Za-z0-9_]*)\b")
    unresolved = defaultdict(set)
    external = defaultdict(set)
    KNOWN_LITERALS = {"S", "C"}
    for path, code in codes.items():
        rel = os.path.relpath(path, ROOT)
        for m in ref_re.finditer(code):
            typ, member = m.group(1), m.group(2)
            if typ in KNOWN_LITERALS:
                continue
            cands = SIMPLE.get(typ, [])
            project = [c for c in cands if c in TYPES and c.startswith("com.fireworkshaders")]
            if not project:
                external[typ].add(member)
                continue
            ok = False
            seen = set()
            stack = list(project)
            while stack:
                fqn = stack.pop()
                if fqn in seen:
                    continue
                seen.add(fqn)
                t = TYPES[fqn]
                if member in t.members:
                    ok = True
                    break
                for parent in t.extends:
                    pname = parent.split(".")[-1]
                    for pfqn in SIMPLE.get(pname, []):
                        if pfqn in TYPES:
                            stack.append(pfqn)
            if not ok:
                unresolved[rel].add(f"{typ}.{member}")
    for rel in sorted(unresolved):
        print(f"  {rel}")
        for item in sorted(unresolved[rel]):
            print(f"      {item}")

    print()
    print("=" * 78)
    print("PROJECT TYPES REFERENCED BUT NEVER DECLARED   (in-comment references excluded)")
    print("=" * 78)
    # every "com.fireworkshaders." import and every project simple name used in `new X(`, `X.`
    missing = defaultdict(set)
    for path, code in codes.items():
        rel = os.path.relpath(path, ROOT)
        for m in re.finditer(r"import\s+(?:static\s+)?(com\.fireworkshaders[\w.]*)\s*;", code):
            fqn = m.group(1)
            if fqn not in TYPES and not any(k.startswith(fqn + ".") for k in TYPES):
                missing[rel].add(fqn)
    for rel in sorted(missing):
        print(f"  {rel}")
        for item in sorted(missing[rel]):
            print(f"      import {item}")

    print()
    print("=" * 78)
    print("INSTANCE CALLS ON LOCALS/PARAMS OF A PROJECT TYPE THAT LACK THE MEMBER")
    print("(advisory: variable names are resolved by declaration, so a name reused for two")
    print(" different types in one file - 'builder', 'state', 'saved' - can surface here)")
    print("=" * 78)
    decl_re = re.compile(r"\b([A-Z]\w*)(?:\s*<[^>;()]*>)?\s+(\w+)\s*(?=[=;,)]|\s*:[^:=])")
    inst_re = re.compile(r"\b(\w+)\s*\.\s*([a-zA-Z_]\w*)\s*\(")
    inst_hits = 0
    for path, code in codes.items():
        rel = os.path.relpath(path, ROOT)
        declared = {}
        for m in decl_re.finditer(code):
            typ, var = m.group(1), m.group(2)
            if var in ("if", "for", "while", "switch", "return", "new", "this", "super"):
                continue
            declared.setdefault(var, set()).add(typ)
        if not declared:
            continue
        for m in inst_re.finditer(code):
            var, member = m.group(1), m.group(2)
            if var not in declared or member in JAVA_BASE_FOR_INSTANCE:
                continue
            reported = False
            for simple in declared[var]:
                if simple in {"String", "Math", "System", "List", "Map", "Set", "Optional", "Random",
                              "Objects", "Arrays", "Collections", "EnumSet", "ArrayList", "Stream"}:
                    continue
                cands = [c for c in SIMPLE.get(simple, [])
                         if c in TYPES and c.startswith("com.fireworkshaders")]
                if not cands:
                    continue
                found = False
                unknown = False
                seen2 = set()
                stack = list(cands)
                while stack:
                    fqn = stack.pop()
                    if fqn in seen2:
                        continue
                    seen2.add(fqn)
                    t = TYPES[fqn]
                    if member in t.members:
                        found = True
                        break
                    for parent in t.extends:
                        psimple = parent.split(".")[-1]
                        pfqns = [c for c in SIMPLE.get(psimple, []) if c in TYPES]
                        if pfqns:
                            stack.extend(pfqns)
                        elif HAS_VANILLA:
                            # inherited from Minecraft or Forge: consult the decompiled source
                            if psimple not in _VANILLA_CACHE:
                                _VANILLA_CACHE[psimple] = vanilla_member_closure(psimple)
                            names, ok = _VANILLA_CACHE[psimple]
                            if not ok:
                                unknown = True
                            elif member in names:
                                found = True
                                break
                        else:
                            unknown = True
                if not found and not unknown:
                    line = code.count("\n", 0, m.start()) + 1
                    print(f"  {rel}:{line}: {var} ({simple}).{member}(...)")
                    inst_hits += 1
                    reported = True
                    break
            if reported:
                continue
    print(f"  {inst_hits} unresolved instance calls")

    print()
    print("=" * 78)
    print("FULLY-QUALIFIED PROJECT REFERENCES TO TYPES THAT DO NOT EXIST")
    print("=" * 78)
    fq_missing = defaultdict(set)
    for path, code in codes.items():
        rel = os.path.relpath(path, ROOT)
        for m in re.finditer(r"\bcom\.fireworkshaders(\.\w+)+", code):
            fqn = m.group(0)
            # walk the reference back to the longest prefix that names a real type, so
            # Type.MEMBER and Type.CONSTANT.get() resolve to Type
            parts = fqn.split(".")
            resolved = False
            for end in range(len(parts), 2, -1):
                prefix = ".".join(parts[:end])
                if prefix in TYPES or any(k.startswith(prefix + ".") for k in TYPES):
                    resolved = True
                    break
            if not resolved:
                fq_missing[rel].add(fqn)
    total_missing = 0
    for rel in sorted(fq_missing):
        for item in sorted(fq_missing[rel]):
            print(f"  {rel}: {item}")
            total_missing += 1
    print(f"  {total_missing} dangling references")

    print()
    print("=" * 78)
    print("CALL-SITE ARITY MISMATCHES  (project type, wrong number of arguments)")
    print("=" * 78)
    call_re = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*\.\s*([a-z_]\w*)\s*\(")
    arity_hits = 0
    for path, code in codes.items():
        rel = os.path.relpath(path, ROOT)
        flat = re.sub(r"\s+", " ", code)
        for m in call_re.finditer(code):
            typ, member = m.group(1), m.group(2)
            cands = [c for c in SIMPLE.get(typ, []) if c in TYPES and c.startswith("com.fireworkshaders")]
            if not cands:
                continue
            args, _end = slice_call(code, m.end())
            arg_count = count_params(args)
            counts = []
            seen = set()
            stack = list(cands)
            while stack:
                fqn = stack.pop()
                if fqn in seen:
                    continue
                seen.add(fqn)
                t = TYPES[fqn]
                counts.extend(t.method_params.get(member, []))
                for parent in t.extends:
                    for pfqn in SIMPLE.get(parent.split(".")[-1], []):
                        if pfqn in TYPES:
                            stack.append(pfqn)
            if not counts:
                continue
            allowed = False
            for c in counts:
                if c >= 0 and c == arg_count:
                    allowed = True
                if c < 0 and arg_count >= (-c) - 1:     # varargs: mandatory params plus extras
                    allowed = True
            if allowed:
                continue
            print(f"  {rel}: {typ}.{member}(...) called with {arg_count} args; declared {sorted(set(counts))}")
            arity_hits += 1
    print(f"  {arity_hits} arity mismatches")

    print()
    print("=" * 78)
    print("BARE CALLS NOT RESOLVABLE IN THE ENCLOSING TYPE  (typos, or a helper that moved)")
    print("=" * 78)
    JAVA_BASE = {"ordinal", "name", "values", "valueOf",
                 "equals", "hashCode", "toString", "getClass", "clone", "notify", "notifyAll",
                 "wait", "format", "parse", "valueOf", "values", "of", "requireNonNull",
                 "asList", "min", "max", "abs", "round", "random"}
    CONTROL = {"if", "for", "while", "switch", "catch", "return", "new", "do", "case", "throw",
               "synchronized", "assert", "try", "else", "super", "this", "instanceof"}
    bare_re = re.compile(r"(?<![.\w])([a-z_]\w*)\s*\(")
    bare_hits = 0
    for path, code in codes.items():
        rel = os.path.relpath(path, ROOT)
        # methods available in every type declared in this file, plus project parents
        available = set()
        seen = set()
        # declarations without a body (interface/abstract methods) count as available
        available |= set(re.findall(
            r"(?:^|\n)\s*(?:(?:public|protected|private|static|final|abstract|default)\s+)*"
            r"[A-Za-z_][\w.<>\[\], ?]*?\s+([a-z_]\w*)\s*\([^;{}]*\)\s*"
            r"(?:throws [\w., ]+)?;", code))
        vanilla_seen = set()
        for fqn, t in TYPES.items():
            if t.file != rel:
                continue
            stack = [fqn]
            while stack:
                cur = stack.pop()
                if cur in seen:
                    continue
                seen.add(cur)
                ct = TYPES[cur]
                available |= set(ct.method_params.keys())
                for parent in ct.extends:
                    psimple = parent.split(".")[-1]
                    pfqns = [c for c in SIMPLE.get(psimple, []) if c in TYPES]
                    if pfqns:
                        stack.extend(pfqns)
                    elif psimple not in vanilla_seen:
                        vanilla_seen.add(psimple)
                        names, ok = vanilla_member_closure(psimple)
                        if ok:
                            available |= names
        for m in bare_re.finditer(code):
            name = m.group(1)
            if name in CONTROL or name in JAVA_BASE or name in available:
                continue
            line = code.count("\n", 0, m.start()) + 1
            print(f"  {rel}:{line}: {name}(...)")
            bare_hits += 1
    print(f"  {bare_hits} unresolved bare calls")

    print()
    print("=" * 78)
    print(f"EXTERNAL TYPES USED ({len(external)}) — informational, verify the risky ones")
    print("=" * 78)
    interesting = {k: v for k, v in external.items() if k not in
                   {"String", "Math", "System", "Objects", "List", "Map", "Set", "EnumSet",
                    "ArrayList", "HashMap", "HashSet", "Locale", "Optional", "Arrays", "Collections",
                    "Random", "Double", "Float", "Integer", "Long", "Boolean", "Character", "Byte",
                    "Short", "CompoundTag", "ListTag", "ItemStack", "Component", "BlockPos", "Vec3",
                    "ResourceLocation", "Direction", "Level", "ServerLevel", "ServerPlayer", "Player",
                    "Item", "Items", "Blocks", "BlockState", "SoundEvents", "SoundSource", "RandomSource",
                    "ChatFormatting", "ChatFormatting".upper(), "AbstractContainerMenu", "Inventory",
                    "GuiGraphics", "Caption", "Builder", "Stream", "Builder", "Supplier", "Consumer",
                    "FriendlyByteBuf", "RegistryObject", "DeferredRegister", "ForgeRegistries"}}
    for typ in sorted(interesting):
        print(f"  {typ}: {', '.join(sorted(interesting[typ]))[:110]}")


if __name__ == "__main__":
    main()
