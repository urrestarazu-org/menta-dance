#!/bin/bash

set -e

echo "🚀 Starting Menta Dance infrastructure..."

# Start Docker Compose services
docker-compose up -d

# Wait for MySQL to be healthy
echo "⏳ Waiting for MySQL to be ready..."
until docker-compose exec -T mysql mysqladmin ping -h localhost -u root --password="$MYSQL_ROOT_PASSWORD" --silent; do
    echo "MySQL is unavailable - sleeping"
    sleep 2
done

echo "✅ MySQL is ready!"

# Wait for Redis to be healthy
echo "⏳ Waiting for Redis to be ready..."
until docker-compose exec -T redis redis-cli ping | grep -q PONG; do
    echo "Redis is unavailable - sleeping"
    sleep 1
done

echo "✅ Redis is ready!"
echo "✅ All infrastructure is ready!"
echo ""
echo "🔗 Services:"
echo "  MySQL:   localhost:3306 (application user: ${MYSQL_APP_USER:-menta_app})"
echo "  Redis:   localhost:6379"
echo "  Loki:    localhost:3100"
echo "  Grafana: http://localhost:3000"
echo ""
echo "📊 To view logs: docker-compose logs -f"
echo "🛑 To stop: docker-compose down"
