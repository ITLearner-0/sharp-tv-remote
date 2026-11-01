#!/usr/bin/env python3
"""
Produit les dictionnaires de langues de l'application à partir de :
- core.tsv : textes visibles (français<TAB>anglais), dans l'ordre de numérotation ;
- tr/<code>.txt : « numéro<TAB>traduction » pour chaque langue.
Les espaces et retours à la ligne de début et de fin sont recopiés du texte
français ; les valeurs variables {1}, {2}… sont vérifiées.
Les textes absents (journaux de diagnostic) retombent sur l'anglais.
"""
import os
import re
import sys

here = os.path.dirname(os.path.abspath(__file__))
out_dir = sys.argv[1]
core = [l.rstrip("\n").split("\t", 1)[0] for l in open(os.path.join(here, "core.tsv"), encoding="utf-8") if "\t" in l]
codes = []
for name in sorted(os.listdir(os.path.join(here, "tr"))):
    if not name.endswith(".txt"):
        continue
    code = name[:-4]
    entries = {}
    for line in open(os.path.join(here, "tr", name), encoding="utf-8"):
        line = line.rstrip("\n")
        if "\t" not in line:
            continue
        idx, text = line.split("\t", 1)
        entries[int(idx)] = text.strip()
    problems = []
    pairs = []
    for i, fr in enumerate(core):
        text = entries.get(i)
        if not text:
            problems.append("manque %d" % i)
            continue
        if set(re.findall(r"\{\d\}", fr)) != set(re.findall(r"\{\d\}", text)):
            problems.append("valeurs %d : %s" % (i, text))
        assert "$" not in text, text
        lead = re.match(r"^\s*", fr).group(0)
        trail = re.search(r"(?:\s|\\n)*$", fr).group(0)
        pairs.append((fr, lead + text + trail))
    if problems:
        print(code, "\n  ".join(problems))
        sys.exit(1)
    ident = "L_" + code.upper()
    path = os.path.join(out_dir, "Strings_%s.kt" % code)
    with open(path, "w", encoding="utf-8") as f:
        f.write("// Fichier produit par tools/i18n/build_langs.py (tools/i18n/tr/%s.txt) : ne pas modifier.\n" % code)
        f.write("package io.github.itlearner0.tvremote.i18n\n\n")
        f.write("internal val %s: Map<String, String> by lazy {\n    HashMap<String, String>(%d).also { m ->\n" % (ident, len(pairs) * 2))
        f.write("        %sa(m)\n        %sb(m)\n    }\n}\n" % (ident.lower(), ident.lower()))
        half = len(pairs) // 2
        for suffix, chunk in (("a", pairs[:half]), ("b", pairs[half:])):
            f.write("\nprivate fun %s%s(m: HashMap<String, String>) {\n" % (ident.lower(), suffix))
            for fr, text in chunk:
                f.write('    m["%s"] = "%s"\n' % (fr, text.replace('"', '\\"')))
            f.write("}\n")
    codes.append(code)
    print(code, len(pairs), "textes")
with open(os.path.join(out_dir, "Languages.kt"), "w", encoding="utf-8") as f:
    f.write("// Fichier produit par tools/i18n/build_langs.py : ne pas modifier.\n")
    f.write("package io.github.itlearner0.tvremote.i18n\n\n")
    f.write("/** Dictionnaires des langues autres que le français et l'anglais. */\n")
    f.write("internal fun dictionary(language: String): Map<String, String>? = when (language) {\n")
    for code in codes:
        f.write('    "%s" -> L_%s\n' % (code, code.upper()))
    f.write("    else -> null\n}\n")
