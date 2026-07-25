#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root_dir"

require() {
    local pattern=$1
    local file=$2

    if ! grep -Eq "$pattern" "$file"; then
        echo "Local infrastructure contract violation: expected '$pattern' in $file" >&2
        exit 1
    fi
}

reject() {
    local pattern=$1
    local file=$2

    if grep -Eq "$pattern" "$file"; then
        echo "Local infrastructure contract violation: forbidden '$pattern' in $file" >&2
        exit 1
    fi
}

require '^spring-boot = "3\.5\.14"$' gradle/libs.versions.toml
require '^checkstyle = "13\.8\.0"$' gradle/libs.versions.toml
require 'activateDependencyLocking\(\)' build.gradle.kts
require 'MYSQL_DATABASE: menta' docker-compose.yml
require 'MYSQL_USER: \$\{MYSQL_APP_USER' docker-compose.yml
require 'MYSQL_PASSWORD: \$\{MYSQL_APP_PASSWORD' docker-compose.yml
require 'maxmemory-policy' docker-compose.yml
require 'noeviction' docker-compose.yml
require 'otel-collector:' docker-compose.yml
require 'loki:' docker-compose.yml
require 'jdbc:mysql://localhost:3306/menta' api/app/src/main/resources/application.yml
require 'username: \$\{MYSQL_APP_USER' api/app/src/main/resources/application.yml
require 'password: \$\{MYSQL_APP_PASSWORD\}' api/app/src/main/resources/application.yml
require 'ddl-auto: validate' api/app/src/main/resources/application.yml
require 'schemas: menta' api/app/src/main/resources/application.yml
require 'CREATE TABLE auth_users' api/app/src/main/resources/db/migration/V1__baseline.sql
reject 'name: admin|password: admin' bff/src/main/resources/application.yml

printf '%s\n' 'Local infrastructure contract verified.'
