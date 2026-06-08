SHELL := /bin/bash
SCRIPT_DIR := $(shell cd "$(dirname "")" && pwd)
TASK_PLUGIN_DIRS := $(wildcard task_impl*/)

RUSTFS_ENDPOINT   := http://localhost:9002
RUSTFS_ACCESS_KEY := root
RUSTFS_SECRET_KEY := example
RUSTFS_REGION     := us-east-1
RUSTFS_BUCKET     := scheduler-files

.PHONY: build-plugins compose-up

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

## Start the demo stack via Docker Compose, wait for RustFS, then create the scheduler-files bucket
compose-up:
	@echo "Starting demo stack..."
	docker compose -f docker-compose.demo.yml up -d --build
	@echo "Waiting for RustFS to be ready at $(RUSTFS_ENDPOINT)..."
	@retries=30; \
	until curl -s --max-time 2 $(RUSTFS_ENDPOINT) > /dev/null 2>&1; do \
		retries=$$((retries - 1)); \
		if [ $$retries -le 0 ]; then \
			echo ""; \
			echo "ERROR: RustFS did not become ready in time." >&2; \
			exit 1; \
		fi; \
		printf '.'; \
		sleep 2; \
	done; \
	echo " RustFS is ready."
	@echo "Creating '$(RUSTFS_BUCKET)' bucket..."
	@AWS_ACCESS_KEY_ID=$(RUSTFS_ACCESS_KEY) \
	 AWS_SECRET_ACCESS_KEY=$(RUSTFS_SECRET_KEY) \
	 AWS_DEFAULT_REGION=$(RUSTFS_REGION) \
	 aws s3 mb s3://$(RUSTFS_BUCKET) --endpoint-url $(RUSTFS_ENDPOINT) 2>&1 \
	 | grep -v "BucketAlreadyOwnedByYou" || true
	@echo "Demo stack is up. Scheduler API: http://localhost:8080"

