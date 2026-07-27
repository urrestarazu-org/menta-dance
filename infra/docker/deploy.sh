#!/usr/bin/env bash

# deploy.sh - Deployment completo con rebuild de imágenes
# Uso: ./deploy.sh [--no-nginx] [--rebuild]

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
USE_NGINX=true
FORCE_REBUILD=false

# Parsear argumentos
for arg in "$@"; do
    case $arg in
        --no-nginx)
            USE_NGINX=false
            shift
            ;;
        --rebuild)
            FORCE_REBUILD=true
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

# Verificar Docker
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker no está corriendo"
        exit 1
    fi
}

# Build completo con Gradle
build_gradle() {
    log_info "Ejecutando build de Gradle..."
    (cd "${PROJECT_ROOT}" && ./gradlew clean build)
    log_success "Build de Gradle completado"
}

# Build de imágenes Docker
build_docker_images() {
    log_info "Construyendo imágenes Docker..."

    # Build API
    log_info "Construyendo imagen menta-api..."
    docker build \
        -t "menta-api:${IMAGE_VERSION:-latest}" \
        -f "${SCRIPT_DIR}/app/Dockerfile.api" \
        "${PROJECT_ROOT}"

    # Build BFF
    log_info "Construyendo imagen menta-bff..."
    docker build \
        -t "menta-bff:${IMAGE_VERSION:-latest}" \
        -f "${SCRIPT_DIR}/app/Dockerfile.bff" \
        "${PROJECT_ROOT}"

    log_success "Imágenes Docker construidas"
}

# Deploy
deploy() {
    check_docker

    log_info "======================================"
    log_info " Menta Dance - Deployment Completo"
    log_info "======================================"
    echo ""

    # 1. Build Gradle
    build_gradle

    # 2. Build Docker images
    build_docker_images

    # 3. Detener servicios existentes
    log_info "Deteniendo servicios existentes..."
    "${SCRIPT_DIR}/manage.sh" stop 2>/dev/null || true

    # 4. Iniciar servicios
    if [[ "$USE_NGINX" == true ]]; then
        log_info "Iniciando servicios con Nginx..."
        "${SCRIPT_DIR}/manage.sh" start
    else
        log_info "Iniciando servicios sin Nginx..."
        "${SCRIPT_DIR}/manage.sh" start --no-nginx
    fi

    echo ""
    log_success "======================================"
    log_success " Deployment completado exitosamente"
    log_success "======================================"
    echo ""

    # Mostrar status
    "${SCRIPT_DIR}/manage.sh" status
}

# Main
deploy
