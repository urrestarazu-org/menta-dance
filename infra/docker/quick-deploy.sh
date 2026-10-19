#!/usr/bin/env bash

# quick-deploy.sh - Deployment rápido sin rebuild (solo copia JARs)
# Uso: ./quick-deploy.sh [--skip-tests]

set -euo pipefail

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Directorios
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Flags
SKIP_TESTS=false

# Parsear argumentos
for arg in "$@"; do
    case $arg in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
    esac
done

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Verificar que contenedores estén corriendo
check_containers() {
    if ! docker ps | grep -q "menta-api"; then
        log_error "Contenedor menta-api no está corriendo"
        log_info "Ejecuta primero: ./deploy.sh"
        exit 1
    fi

    if ! docker ps | grep -q "menta-bff"; then
        log_error "Contenedor menta-bff no está corriendo"
        log_info "Ejecuta primero: ./deploy.sh"
        exit 1
    fi
}

# Build JARs con Gradle
build_jars() {
    local -a gradle_args=()

    if [[ "$SKIP_TESTS" == true ]]; then
        gradle_args+=(-x test)
        log_warning "Skipping tests"
    fi

    log_info "Compilando JARs con Gradle..."
    (cd "${PROJECT_ROOT}" && ./gradlew :api:app:bootJar :bff:bootJar "${gradle_args[@]}")
    log_success "JARs compilados"
}

# Copiar JARs a contenedores
copy_jars() {
    log_info "Copiando JARs a contenedores..."

    # Copiar API JAR
    local api_jar
    api_jar=$(find "${PROJECT_ROOT}/api/app/build/libs" -name "menta-dance-api.jar" | head -n 1)
    if [[ -z "$api_jar" ]]; then
        log_error "JAR de API no encontrado"
        exit 1
    fi

    log_info "Copiando API JAR: $(basename "$api_jar")"
    docker cp "$api_jar" menta-api:/app/app.jar

    # Copiar BFF JAR
    local bff_jar
    bff_jar=$(find "${PROJECT_ROOT}/bff/build/libs" -name "bff-*.jar" | head -n 1)
    if [[ -z "$bff_jar" ]]; then
        log_error "JAR de BFF no encontrado"
        exit 1
    fi

    log_info "Copiando BFF JAR: $(basename "$bff_jar")"
    docker cp "$bff_jar" menta-bff:/app/bff.jar

    log_success "JARs copiados"
}

# Reiniciar contenedores
restart_containers() {
    log_info "Reiniciando contenedores..."
    docker restart menta-api menta-bff
    log_success "Contenedores reiniciados"
}

# Verificar health
verify_health() {
    log_info "Verificando health checks..."

    # Esperar un poco para que arranquen
    sleep 5

    local max_attempts=30
    local attempt=0

    # Verificar API
    while [[ $attempt -lt $max_attempts ]]; do
        if docker inspect --format='{{.State.Health.Status}}' menta-api 2>/dev/null | grep -q "healthy"; then
            log_success "API está healthy"
            break
        fi
        attempt=$((attempt + 1))
        sleep 2
    done

    if [[ $attempt -ge $max_attempts ]]; then
        log_error "API no está healthy después de esperar"
        log_info "Ver logs: docker logs menta-api"
        exit 1
    fi

    # Verificar BFF
    attempt=0
    while [[ $attempt -lt $max_attempts ]]; do
        if docker inspect --format='{{.State.Health.Status}}' menta-bff 2>/dev/null | grep -q "healthy"; then
            log_success "BFF está healthy"
            break
        fi
        attempt=$((attempt + 1))
        sleep 2
    done

    if [[ $attempt -ge $max_attempts ]]; then
        log_error "BFF no está healthy después de esperar"
        log_info "Ver logs: docker logs menta-bff"
        exit 1
    fi
}

# Main
main() {
    log_info "======================================"
    log_info " Menta Dance - Quick Deploy"
    log_info "======================================"
    echo ""

    check_containers
    build_jars
    copy_jars
    restart_containers
    verify_health

    echo ""
    log_success "======================================"
    log_success " Quick Deploy completado exitosamente"
    log_success "======================================"
    echo ""

    "${SCRIPT_DIR}/manage.sh" status
}

main
