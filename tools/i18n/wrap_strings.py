#!/usr/bin/env python3
"""
Entoure les textes affichés de l'application Android de tr(…) pour la traduction.

Le français reste la langue du code ; tr("Texte {1}", valeur) renvoie la traduction
(anglais) quand le téléphone n'est pas en français. Les textes variables
"Connecté à $nom" deviennent tr("Connecté à {1}", nom).

Usage : wrap_strings.py <dossier source> <catalogue.txt>
Le catalogue liste (une par ligne) les clés, telles qu'écrites dans le code Kotlin.
Idempotent : un texte déjà dans tr(…) n'est pas repris.
"""
import os
import re
import sys

EXCLUDED_DIRS = {"protocol", "ir", "i18n"}
EXCLUDED_FILES = {"VoiceCommands.kt", "TextKeys.kt", "Keys.kt", "TvAdb.kt"}
TECHNICAL = [
    re.compile(r"^[A-Z0-9_ .:-]+$"),          # constantes, codes
    re.compile(r"://"),                       # adresses
    re.compile(r"^[\w.+-]+/[\w.+*-]+$"),      # types MIME, chemins
    re.compile(r"^(HH|yyyy|dd|%)"),           # formats de date ou printf
    re.compile(r"^[a-z0-9_.:-]+$"),           # clés en minuscules
]


def find_literals(src):
    """Positions (début, fin) des chaînes "…" hors commentaires et chaînes brutes."""
    i, n, out = 0, len(src), []
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            i = src.find("\n", i)
            if i < 0:
                break
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            i = j + 1
        elif c == '"':
            j = skip_string(src, i)
            out.append((i, j))
            i = j
        else:
            i += 1
    return out


def skip_string(src, i):
    """Fin (exclue) de la chaîne commençant au guillemet i, gabarits ${…} compris."""
    j = i + 1
    while j < len(src):
        c = src[j]
        if c == "\\":
            j += 2
        elif c == '"':
            return j + 1
        elif src.startswith("${", j):
            j = skip_template(src, j + 2)
        else:
            j += 1
    return j


def skip_template(src, j):
    depth = 1
    while j < len(src) and depth:
        if src[j] == '"':
            j = skip_string(src, j)
            continue
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
        j += 1
    return j


def split_template(body):
    """Texte (avec {n}) et expressions d'une chaîne Kotlin (contenu sans guillemets)."""
    text, exprs, i = [], [], 0
    while i < len(body):
        c = body[i]
        if c == "\\":
            text.append(body[i:i + 2])
            i += 2
        elif body.startswith("${", i):
            end = skip_template(body, i + 2)
            exprs.append(body[i + 2:end - 1])
            text.append("{%d}" % len(exprs))
            i = end
        elif c == "$" and i + 1 < len(body) and (body[i + 1].isalpha() or body[i + 1] == "_"):
            m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", body[i + 1:])
            exprs.append(m.group(0))
            text.append("{%d}" % len(exprs))
            i += 1 + len(m.group(0))
        else:
            text.append(c)
            i += 1
    return "".join(text), exprs


def visible(text):
    plain = re.sub(r"\{\d+\}", "", text).strip()
    if not re.search(r"[A-Za-zÀ-ÿ]", plain):
        return False
    if any(p.search(plain) for p in TECHNICAL):
        return False
    return " " in plain or re.search(r"[À-ÿ«»…→]", plain) is not None or plain[0].isupper()


def rewrite(src, catalog):
    out, last = [], 0
    for start, end in find_literals(src):
        if start < last:
            continue
        line_start = src.rfind("\n", 0, start) + 1
        line_end = src.find("\n", end)
        line = src[line_start:line_end if line_end >= 0 else len(src)]
        before = src[line_start:start]
        after = src[end:line_end if line_end >= 0 else len(src)].lstrip()
        stripped = line.strip()
        if (
            stripped.startswith("@")
            or "const val" in line
            or re.search(r"@\w+\(", before)
            or "Regex(" in before
            or after.startswith("->")
            or re.search(r"\btr\($", before.rstrip())
            or before.rstrip().endswith("tr(")
        ):
            continue
        body = src[start + 1:end - 1]
        text, exprs = split_template(body)
        if not visible(text):
            continue
        exprs = [rewrite(e, catalog) for e in exprs]
        catalog.add(text)
        args = "".join(", " + e for e in exprs)
        out.append(src[last:start])
        out.append('tr("%s"%s)' % (text, args))
        last = end
    out.append(src[last:])
    return "".join(out)


def add_import(src):
    if "import io.github.itlearner0.tvremote.i18n.tr" in src:
        return src
    m = re.search(r"^package [^\n]+\n", src, re.M)
    return src[:m.end()] + "\nimport io.github.itlearner0.tvremote.i18n.tr\n" + src[m.end():]


def main(root, catalog_path):
    catalog = set()
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]
        for name in files:
            if not name.endswith(".kt") or name in EXCLUDED_FILES:
                continue
            path = os.path.join(base, name)
            src = open(path, encoding="utf-8").read()
            new = rewrite(src, catalog)
            if new != src:
                open(path, "w", encoding="utf-8").write(add_import(new))
    # Clés déjà présentes (fichiers déjà traités) : on les recense aussi.
    for base, dirs, files in os.walk(root):
        for name in files:
            if name.endswith(".kt"):
                src = open(os.path.join(base, name), encoding="utf-8").read()
                for m in re.finditer(r'\btr\("((?:[^"\\]|\\.)*)"', src):
                    catalog.add(m.group(1))
    with open(catalog_path, "w", encoding="utf-8") as f:
        for key in sorted(catalog):
            f.write(key + "\n")
    print(len(catalog), "textes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
