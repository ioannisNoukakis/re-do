SHELL := /bin/bash
SCRIPT_DIR := $(shell cd "$(dirname "")" && pwd)
TASK_PLUGIN_DIRS := $(wildcard task_impl*/)

.PHONY: all build-plugins compose-up

## Run everything: build plugins then start the stack
all: build-plugins compose-up

## Build all task plugins and copy them to task_handler_plugins/
build-plugins:
	@mkdir -p task_handler_plugins
	@echo "Building task plugins..."
	@for module_dir in $(TASK_PLUGIN_DIRS); do \
		module=$${module_dir%/}; \
		./gradlew ":$${module}:shadowJar" --no-daemon; \
		cp "$${module}/build/libs/"*.jar task_handler_plugins/; \
		echo "  -> $${module} copied to task_handler_plugins/"; \
	done

## Start the demo stack via Docker Compose
compose-up:
	@echo "Starting demo stack..."
	docker compose -f docker-compose.demo.yml up -d --build

