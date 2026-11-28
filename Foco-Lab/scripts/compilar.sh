#!/usr/bin/env bash
# Compila Foco (perf, rápido) e instala en el teléfono conectado por USB.
# Uso: bash scripts/compilar.sh            (desde ~/Foco-Lab)
#      bash scripts/compilar.sh paquete.zip (descomprime primero ese zip encima)
set -o pipefail
cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/temurin-21-jdk}"

if [ -n "$1" ]; then
  echo "== Descomprimiendo $1"
  unzip -oq "$1" -d . || exit 1
  # Los zips de limpieza traen BORRAR.txt: una ruta por línea de archivos que ya no existen.
  if [ -f BORRAR.txt ]; then
    while IFS= read -r f; do
      case "$f" in ""|"#"*|/*|*..*) continue ;; esac
      [ -e "$f" ] && rm -f -- "$f" && echo "   borrado: $f"
    done < BORRAR.txt
    rm -f BORRAR.txt
  fi
fi

echo "== Parando compilaciones colgadas"
./gradlew --stop >/dev/null 2>&1

echo "== Compilando (versión rápida de prueba; tarda unos minutos)…"
LOG=build/compilar.log
mkdir -p build
if ./gradlew :app:assemblePerf -Prapido --console=plain 2>&1 | tee "$LOG"; then
  echo "== BUILD SUCCESSFUL"
  if adb get-state >/dev/null 2>&1; then
    echo "== Instalando en el teléfono"
    adb install -r app/build/outputs/apk/perf/app-perf.apk
  else
    echo "== Teléfono no conectado: instala después con"
    echo "   adb install -r app/build/outputs/apk/perf/app-perf.apk"
  fi
else
  echo "== BUILD FAILED. Errores (pégalos en el chat):"
  grep -E "^e: |error:|What went wrong" -A2 "$LOG" | head -40
  echo "(log completo en $LOG)"
  exit 1
fi
