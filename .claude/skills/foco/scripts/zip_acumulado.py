"""Zip acumulado de Foco: todo lo que cambió o se añadió desde la versión base.

Uso: python3 -I zip_acumulado.py <base> <actual> <salida.zip> [BORRAR.txt]
Las rutas del zip son relativas a <actual>. Si se da BORRAR.txt, va dentro del zip.
"""
import os
import subprocess
import sys
import zipfile

SKIP = ("/build/", "/.gradle/", "/.idea/", "local.properties")


def changed(base, cur):
    out = subprocess.run(["diff", "-rq", base, cur], capture_output=True, text=True).stdout.splitlines()
    files = []
    for line in out:
        if line.startswith("Files "):
            files.append(line.split(" and ")[1].split(" differ")[0])
        # Ojo: "Only in Foco-Lab-before-v21" también empieza por "Only in Foco-Lab".
        elif line.startswith("Only in " + cur + ":") or line.startswith("Only in " + cur + "/"):
            d, n = line[len("Only in "):].split(": ", 1)
            p = os.path.join(d, n)
            if os.path.isdir(p):
                for r, _, fs in os.walk(p):
                    files.extend(os.path.join(r, f) for f in fs)
            else:
                files.append(p)
    return sorted(f for f in files if not any(s in f for s in SKIP))


def main():
    base, cur, out = sys.argv[1:4]
    borrar = sys.argv[4] if len(sys.argv) > 4 else None
    files = changed(base.rstrip("/"), cur.rstrip("/"))
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in files:
            z.write(f, os.path.relpath(f, cur))
        if borrar:
            z.write(borrar, "BORRAR.txt")
    print(len(files), "archivos")


if __name__ == "__main__":
    main()
