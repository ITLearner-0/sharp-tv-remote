#!/usr/bin/env python3
"""
Produit le dictionnaire anglais de l'application (i18n/EnStrings.kt) à partir de
en.tsv : une ligne par texte, « français<TAB>anglais », tels qu'écrits dans le code
Kotlin (\\n pour un retour à la ligne, {1} pour une valeur variable).
"""
import sys

src, out = sys.argv[1], sys.argv[2]
pairs = []
for line in open(src, encoding="utf-8"):
    line = line.rstrip("\n")
    if "\t" not in line:
        continue
    fr, en = line.split("\t", 1)
    assert '"' not in fr and "$" not in fr + en, line
    pairs.append((fr, en.replace('"', '\\"')))
with open(out, "w", encoding="utf-8") as f:
    f.write("// Fichier produit par tools/i18n/build_en.py à partir de tools/i18n/en.tsv : ne pas modifier.\n")
    f.write("package io.github.itlearner0.tvremote.i18n\n\n")
    f.write("/** Traductions anglaises des textes de l'application (clé : texte français). */\n")
    f.write("internal val EN: Map<String, String> by lazy { buildEn() }\n\n")
    # Plusieurs fonctions pour rester sous la taille maximale d'une méthode.
    chunks = [pairs[i:i + 150] for i in range(0, len(pairs), 150)]
    f.write("private fun buildEn(): Map<String, String> {\n    val m = HashMap<String, String>(%d)\n" % (len(pairs) * 2))
    for i in range(len(chunks)):
        f.write("    part%d(m)\n" % i)
    f.write("    return m\n}\n")
    for i, chunk in enumerate(chunks):
        f.write("\nprivate fun part%d(m: HashMap<String, String>) {\n" % i)
        for fr, en in chunk:
            f.write('    m["%s"] = "%s"\n' % (fr, en))
        f.write("}\n")
print(len(pairs), "traductions")
