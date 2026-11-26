#!/usr/bin/env python3
"""Build myau_intermediary*.tsv for Minecraft 1.8.9 under Ornithe.

Ornithe (the loader OneClient's 1.8.9 instances use) names the game:
  classes  net/minecraft/unmapped/C_1234567
  fields   f_7654321
  methods  m_1234567
The maven artifact net.ornithemc:calamus:1.8.9 ships official -> calamus as a
tiny v1 table; runtime classes carry an extra "unmapped/" segment, which is
added here.

Inputs: myau_obf.tsv, myau_mcp.tsv, calamus mappings.tiny
Outputs: myau_intermediary.tsv (SRG keys, for the class remapper)
         myau_intermediary_named.tsv (MCP keys, for reflection)
"""

import sys

PREFIX = "net/minecraft/"
UNMAPPED = "net/minecraft/unmapped/"


def read_obf(path):
    classes, methods, fields = {}, {}, {}
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if parts[0] == "C" and len(parts) >= 3:
                classes[parts[1]] = parts[2]
            elif parts[0] == "M" and len(parts) >= 5:
                methods[(parts[1], parts[2], parts[3])] = parts[4]
            elif parts[0] == "F" and len(parts) >= 4:
                fields[(parts[1], parts[2])] = parts[3]
    return classes, methods, fields


def read_named(path):
    methods, fields = {}, {}
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if parts[0] == "M" and len(parts) >= 5:
                methods[(parts[1], parts[2], parts[3])] = parts[4]
            elif parts[0] == "F" and len(parts) >= 4:
                fields[(parts[1], parts[2])] = parts[3]
    return methods, fields


def read_calamus(path):
    classes, methods, fields = {}, {}, {}
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            kind = parts[0]
            if kind == "CLASS" and len(parts) >= 3:
                classes[parts[1]] = parts[2]
            elif kind == "METHOD" and len(parts) >= 5:
                methods[(parts[1], parts[3], parts[2])] = parts[4]
            elif kind == "FIELD" and len(parts) >= 5:
                fields[(parts[1], parts[3], parts[2])] = parts[4]
    return classes, methods, fields


def unmapped(name):
    # Ornithe wraps id-style names (net/minecraft/C_1234) in an "unmapped/"
    # package at runtime, but whether it also does so for real names is build
    # specific, so the table keeps calamus names verbatim and
    # Obfuscation.mapCalamus() applies the prefix after probing the game.
    return name


def map_desc(desc, classes):
    out, i = [], 0
    while i < len(desc):
        c = desc[i]
        if c != "L":
            out.append(c)
            i += 1
            continue
        end = desc.find(";", i)
        if end < 0:
            out.append(desc[i:])
            break
        name = desc[i + 1:end]
        out.append("L" + classes.get(name, name) + ";")
        i = end + 1
    return "".join(out)


def main():
    obf, mcp, calamus, out, out_named = sys.argv[1:6]
    classes, methods, fields = read_obf(obf)
    named, named_fields = read_named(mcp)
    ccls, cmeth, cfld = read_calamus(calamus)

    rows, alias = [], []
    stats = dict.fromkeys(
        ("class", "method", "field", "alias", "miss_class", "miss_method", "miss_field"), 0)

    for mcp_class, notch in sorted(classes.items()):
        target = ccls.get(notch)
        if not target:
            stats["miss_class"] += 1
            continue
        line = "C\t%s\t%s" % (mcp_class, unmapped(target))
        rows.append(line)
        alias.append(line)
        stats["class"] += 1

    for (owner, srg, mdesc), notch_member in sorted(methods.items()):
        notch_owner = classes.get(owner)
        if not notch_owner:
            stats["miss_method"] += 1
            continue
        target = cmeth.get((notch_owner, notch_member, map_desc(mdesc, classes)))
        if not target:
            stats["miss_method"] += 1
            continue
        rows.append("M\t%s\t%s\t%s\t%s" % (owner, srg, mdesc, target))
        stats["method"] += 1
        # rows whose "srg" column already holds a real name (channelRead0,
        # access$000, ...) are their own alias
        simple = named.get((owner, srg, mdesc))
        if simple is None and not srg.startswith("func_"):
            simple = srg
        if simple:
            alias.append("M\t%s\t%s\t%s\t%s" % (owner, simple, mdesc, target))
            stats["alias"] += 1

    # overrides the obf/mcp tables only list on a parent class (1.8.9 keeps
    # EntityPlayerSP.onUpdate off the list even though the class declares its
    # own t_() override), so a second pass fills them in from the calamus
    # table by matching the global signature of the member
    signature_srg = {}
    for (owner, srg, mdesc), notch_member in methods.items():
        if classes.get(owner):
            signature_srg[(notch_member, map_desc(mdesc, classes))] = srg
    simple_by_signature = {}
    for (owner, srg, mdesc), simple in named.items():
        simple_by_signature.setdefault((srg, mdesc), simple)
    mcp_of_notch = {v: k for k, v in classes.items()}
    emitted = set((owner, srg) for (owner, srg, mdesc) in methods.keys())
    for (cowner, cname, cdesc), target in sorted(cmeth.items()):
        owner = mcp_of_notch.get(cowner)
        if not owner:
            continue
        srg = signature_srg.get((cname, cdesc))
        if not srg or (owner, srg) in emitted:
            continue
        simple = simple_by_signature.get((srg, cdesc))
        if not simple:
            continue
        rows.append("M\t%s\t%s\t%s\t%s" % (owner, srg, cdesc, target))
        alias.append("M\t%s\t%s\t%s\t%s" % (owner, simple, cdesc, target))
        emitted.add((owner, srg))
        stats["method"] += 1
        stats["alias"] += 1

    for (owner, srg), notch_member in sorted(fields.items()):
        notch_owner = classes.get(owner)
        if not notch_owner:
            stats["miss_field"] += 1
            continue
        target = None
        for key, value in cfld.items():
            if key[0] == notch_owner and key[1] == notch_member:
                target = value
                break
        if not target:
            stats["miss_field"] += 1
            continue
        rows.append("F\t%s\t%s\t%s" % (owner, srg, target))
        stats["field"] += 1
        simple = named_fields.get((owner, srg))
        if simple is None and not srg.startswith("field_"):
            simple = srg
        if simple:
            alias.append("F\t%s\t%s\t%s" % (owner, simple, target))

    for path, body in ((out, rows), (out_named, alias)):
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(body))
            handle.write("\n")
    print("classes=%(class)d methods=%(method)d fields=%(field)d aliases=%(alias)d "
          "missed=%(miss_class)d/%(miss_method)d/%(miss_field)d" % stats)


if __name__ == "__main__":
    main()
