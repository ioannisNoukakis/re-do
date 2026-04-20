#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p task_handler_plugins

echo "Building task plugins..."
for module_dir in task_impl*/; do
    module="${module_dir%/}"
    ./gradlew ":${module}:jar" --no-daemon
    cp "${module}/build/libs/"*.jar task_handler_plugins/
    echo "  -> ${module} copied to task_handler_plugins/"
done

echo "Starting demo stack..."
docker compose -f docker-compose.demo.yml up -d --build
