#!/usr/bin/env bash

# manage.sh - Script universal de gestión de servicios Docker para Menta Dance
# Uso: ./manage.sh {start|stop|restart|status|logs|health|clean} [--no-nginx]

set -euo pipefail

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directorios
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DATABASE_DIR="${SCRIPT_DIR}/database"
APP_DIR="${SCRIPT_DIR}/app"
NGINX_DIR="${SCRIPT_DIR}/nginx"
ENV_FILE="${PROJECT_ROOT}/.env"

# Flags
USE_NGINX=true

# Parsear argumentos
parse_args() {
    for arg in "$@"; do
        case $arg in
            --no-nginx)
                USE_NGINX=false
                shift
                ;;
        esac
    done
}

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Verificar que Docker esté corriendo
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker no está corriendo. Por favor, inicia Docker Desktop."
        exit 1
    fi
}

# Verificar que .env exista
check_env() {
    if [[ ! -f "$ENV_FILE" ]]; then
        log_warning "Archivo .env no encontrado en ${PROJECT_ROOT}"
        log_info "Copiando .env.example a .env..."
        if [[ -f "${PROJECT_ROOT}/.env.example" ]]; then
            cp "${PROJECT_ROOT}/.env.example" "$ENV_FILE"
            log_success ".env creado. Por favor, edita las variables necesarias."
        else
            log_error ".env.example no encontrado. Crea manualmente .env con las variables necesarias."
            exit 1
        fi
    fi
}

load_environment() {
    if [[ -z "${ENVIRONMENT:-}" && -f "$ENV_FILE" ]]; then
        ENVIRONMENT="$(sed -n 's/^ENVIRONMENT=//p' "$ENV_FILE" | tail -n 1)"
    fi

    ENVIRONMENT="${ENVIRONMENT:-local}"
    if [[ "$ENVIRONMENT" != "local" && "$ENVIRONMENT" != "production" ]]; then
        log_error "ENVIRONMENT debe ser local o production"
        exit 1
    fi
    export ENVIRONMENT
}

docker_compose() {
    local compose_dir="$1"
    shift
    (cd "$compose_dir" && docker compose --env-file "$ENV_FILE" "$@")
}

nginx_compose() {
    if [[ "$ENVIRONMENT" == "production" ]]; then
        docker_compose "$NGINX_DIR" -f docker-compose.yml -f docker-compose.production.yml "$@"
    else
        docker_compose "$NGINX_DIR" "$@"
    fi
}

validate_production() {
    if [[ "$ENVIRONMENT" == "production" ]]; then
        local certificate="${NGINX_DIR}/certs/mentadance.com-fullchain.crt"
        local private_key="${NGINX_DIR}/certs/mentadance.com.key"
        if [[ ! -f "$certificate" || ! -f "$private_key" ]]; then
            log_error "Production mode requires TLS certificates"
            log_error "Missing: $certificate or $private_key"
            exit 1
        fi
    fi
}

# Iniciar servicios
start() {
    log_info "Iniciando servicios de Menta Dance..."
    check_env
    load_environment
    validate_production
    check_docker

    # 1. Iniciar infraestructura (MySQL, Redis, Observability)
    log_info "Iniciando servicios de infraestructura (MySQL, Redis, Observability)..."
    docker_compose "$DATABASE_DIR" up -d

    # 2. Esperar a que MySQL esté healthy
    log_info "Esperando a que MySQL esté listo..."
    local max_attempts=30
    local attempt=0
    while [[ $attempt -lt $max_attempts ]]; do
        if docker inspect --format='{{.State.Health.Status}}' menta-mysql 2>/dev/null | grep -q "healthy"; then
            log_success "MySQL está listo"
            break
        fi
        attempt=$((attempt + 1))
        sleep 2
    done

    if [[ $attempt -ge $max_attempts ]]; then
        log_error "MySQL no está listo después de esperar. Verifica los logs: docker logs menta-mysql"
        exit 1
    fi

    # 3. Iniciar aplicaciones (API + BFF)
    if [[ "$USE_NGINX" == true ]]; then
        log_info "Iniciando aplicaciones con Nginx..."
        nginx_compose up -d
    else
        log_info "Iniciando aplicaciones sin Nginx (desarrollo local)..."
        docker_compose "$APP_DIR" up -d
    fi

    log_success "Todos los servicios iniciados"
    echo ""
    status
}

# Detener servicios
stop() {
    log_info "Deteniendo servicios de Menta Dance..."

    # Detener nginx/app primero
    if [[ "$USE_NGINX" == true ]] && docker ps -q --filter "name=menta-nginx" > /dev/null 2>&1; then
        log_info "Deteniendo servicios con Nginx..."
        load_environment
        nginx_compose down
    fi

    if docker ps -q --filter "name=menta-api" > /dev/null 2>&1; then
        log_info "Deteniendo aplicaciones..."
        docker_compose "$APP_DIR" down
    fi

    # Detener infraestructura
    log_info "Deteniendo servicios de infraestructura..."
    docker_compose "$DATABASE_DIR" down

    log_success "Todos los servicios detenidos"
}

# Reiniciar servicios
restart() {
    log_info "Reiniciando servicios..."
    stop
    sleep 2
    start
}

# Mostrar estado de servicios
status() {
    log_info "Estado de servicios de Menta Dance:"
    echo ""

    local containers=(
        "menta-mysql:MySQL"
        "menta-redis:Redis"
        "menta-otel-collector:OTEL Collector"
        "menta-loki:Loki"
        "menta-grafana:Grafana"
        "menta-api:API"
        "menta-bff:BFF"
        "menta-nginx:Nginx"
    )

    for container_info in "${containers[@]}"; do
        IFS=':' read -r container_name display_name <<< "$container_info"

        if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
            local health_status=$(docker inspect --format='{{.State.Health.Status}}' "${container_name}" 2>/dev/null || echo "N/A")
            local status_icon="✅"
            local status_text="RUNNING"

            if [[ "$health_status" != "N/A" ]]; then
                if [[ "$health_status" == "healthy" ]]; then
                    status_icon="✅"
                    status_text="RUNNING (healthy)"
                elif [[ "$health_status" == "unhealthy" ]]; then
                    status_icon="❌"
                    status_text="RUNNING (unhealthy)"
                elif [[ "$health_status" == "starting" ]]; then
                    status_icon="🔄"
                    status_text="RUNNING (starting)"
                fi
            fi

            printf "  ${status_icon} %-20s %s\n" "${display_name}:" "${status_text}"
        else
            printf "  ⏹️  %-20s STOPPED\n" "${display_name}:"
        fi
    done

    echo ""

    if [[ "$USE_NGINX" == true ]] && docker ps -q --filter "name=menta-nginx" > /dev/null 2>&1; then
        log_info "URLs de acceso:"
        echo "  🌐 Web (BFF):        http://localhost"
        echo "  🔌 API:              http://localhost/api"
        echo "  📊 Grafana:          http://localhost:3000"
    else
        log_info "URLs de acceso (desarrollo local):"
        echo "  🌐 Web (BFF):        http://localhost:8080"
        echo "  🔌 API:              http://localhost:8081"
        echo "  📊 Grafana:          http://localhost:3000"
    fi
    echo "  🗄️  MySQL:            localhost:3306"
    echo "  🔴 Redis:            localhost:6379"
    echo ""
}

# Ver logs
logs() {
    local service="${1:-}"

    if [[ -z "$service" ]]; then
        log_error "Uso: ./manage.sh logs {api|bff|nginx|db|redis|grafana|loki|otel}"
        exit 1
    fi

    case "$service" in
        api)
            docker logs -f --tail=100 menta-api
            ;;
        bff)
            docker logs -f --tail=100 menta-bff
            ;;
        nginx)
            docker logs -f --tail=100 menta-nginx
            ;;
        db|mysql)
            docker logs -f --tail=100 menta-mysql
            ;;
        redis)
            docker logs -f --tail=100 menta-redis
            ;;
        grafana)
            docker logs -f --tail=100 menta-grafana
            ;;
        loki)
            docker logs -f --tail=100 menta-loki
            ;;
        otel)
            docker logs -f --tail=100 menta-otel-collector
            ;;
        *)
            log_error "Servicio desconocido: $service"
            log_info "Servicios disponibles: api, bff, nginx, db, redis, grafana, loki, otel"
            exit 1
            ;;
    esac
}

# Verificar health checks
health() {
    log_info "Verificando health checks..."
    echo ""

    local containers=(
        "menta-mysql"
        "menta-redis"
        "menta-api"
        "menta-bff"
        "menta-nginx"
    )

    for container in "${containers[@]}"; do
        if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
            local health_status=$(docker inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null || echo "N/A")

            if [[ "$health_status" == "healthy" ]]; then
                log_success "${container}: HEALTHY"
            elif [[ "$health_status" == "unhealthy" ]]; then
                log_error "${container}: UNHEALTHY"
                log_info "Ver logs: docker logs ${container}"
            elif [[ "$health_status" == "starting" ]]; then
                log_warning "${container}: STARTING"
            else
                log_info "${container}: No health check configured"
            fi
        else
            log_warning "${container}: NOT RUNNING"
        fi
    done
    echo ""
}

# Limpiar contenedores detenidos
clean() {
    log_info "Limpiando recursos del proyecto..."

    # Solo limpiar volúmenes del proyecto
    docker volume ls --filter "name=menta-" --format "{{.Name}}" | \
        xargs -r docker volume rm 2>/dev/null || true

    # Solo limpiar redes del proyecto
    docker network ls --filter "name=menta-" --format "{{.Name}}" | \
        grep -v bridge | \
        xargs -r docker network rm 2>/dev/null || true

    log_success "Recursos del proyecto limpiados"
}

# Main
main() {
    if [[ $# -eq 0 ]]; then
        echo "Uso: $0 {start|stop|restart|status|logs|health|clean} [--no-nginx]"
        echo ""
        echo "Comandos:"
        echo "  start [--no-nginx]   Iniciar servicios"
        echo "  stop                 Detener servicios"
        echo "  restart              Reiniciar servicios"
        echo "  status               Mostrar estado de servicios"
        echo "  logs <service>       Ver logs de un servicio (api|bff|nginx|db|redis|grafana|loki|otel)"
        echo "  health               Verificar health checks"
        echo "  clean                Limpiar contenedores detenidos"
        echo ""
        echo "Flags:"
        echo "  --no-nginx           No usar Nginx (desarrollo local, puertos directos)"
        exit 1
    fi

    local command="$1"
    shift

    # Parsear flags adicionales
    parse_args "$@"

    case "$command" in
        start)
            start
            ;;
        stop)
            stop
            ;;
        restart)
            restart
            ;;
        status)
            status
            ;;
        logs)
            logs "$@"
            ;;
        health)
            health
            ;;
        clean)
            clean
            ;;
        *)
            log_error "Comando desconocido: $command"
            exit 1
            ;;
    esac
}

main "$@"
