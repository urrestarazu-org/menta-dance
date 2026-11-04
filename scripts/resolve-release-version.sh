#!/usr/bin/env bash

# resolve-release-version.sh - Versión del release a partir de la rama de origen del PR (#639).
#
# Uso: scripts/resolve-release-version.sh <rama>
#   scripts/resolve-release-version.sh release/0.4.0   # imprime 0.4.0
#   scripts/resolve-release-version.sh hotfix/0.4.1    # imprime 0.4.1
#
# Lo usa release-tag.yml para decidir qué tag crear (vX.Y.Z) al mergear a main un PR desde
# release/X.Y.Z o hotfix/X.Y.Z. Solo acepta versiones X.Y.Z sin sufijo, con el mismo patrón
# numérico que valida release.yml: los pre-releases (vX.Y.Z-rc.N) se siguen creando a mano.
#
# Si la rama es válida imprime la versión en stdout y sale con 0. Si no, no imprime nada en stdout,
# explica el motivo en stderr y sale con 1 (2 si el uso es incorrecto). El nombre de la rama viene
# del autor del PR: solo se compara con una expresión regular, nunca se ejecuta ni se evalúa.

set -euo pipefail

readonly EXPECTED='release/X.Y.Z o hotfix/X.Y.Z (X, Y y Z numéricos, sin sufijo)'

if [[ $# -ne 1 ]]; then
    echo "Uso: resolve-release-version.sh <rama>; la rama debe ser ${EXPECTED}" >&2
    exit 2
fi

readonly BRANCH="$1"

if [[ ! "$BRANCH" =~ ^(release|hotfix)/([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    # %q escapa saltos de línea y caracteres de control: el mensaje queda en una sola línea
    printf "La rama %q no tiene un nombre de release válido; se esperaba %s\n" "$BRANCH" "$EXPECTED" >&2
    exit 1
fi

printf '%s\n' "${BASH_REMATCH[2]}"
