# Preserve /api literally, even when Git Bash is installed on PATH.
SHELL := cmd.exe
.SHELLFLAGS := /C
ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
-include $(ROOT)/.runtime/tools.mk
POWERSHELL ?= pwsh
NPM ?= npm.cmd
API_PORT ?= 8080
WEB_PORT ?= 8787
WEB_HOST ?= 0.0.0.0

LAUNCH = "$(POWERSHELL)" -NoProfile -ExecutionPolicy Bypass -File "$(ROOT)/scripts/start-local.ps1"

.DEFAULT_GOAL := help
.NOTPARALLEL:
.PHONY: help check lint typecheck test build up start restart stop firewall dev

help:
	@echo Frontend: up, start, restart, build, check, lint, typecheck, test, dev, stop, firewall

lint:
	"$(NPM)" run lint

typecheck:
	"$(NPM)" run typecheck

test:
	"$(NPM)" test

check: lint typecheck test

# This export follows the serving origin and never embeds a machine IP.
build: export CI := 1
build: export EXPO_PUBLIC_API_URL := /api
build: check
	"$(NPM)" run build:web

up: build restart

restart: stop start

start:
	$(LAUNCH) -Action frontend -ApiPort $(API_PORT) -WebPort $(WEB_PORT) -WebBindAddress "$(WEB_HOST)"

stop:
	$(LAUNCH) -Action frontend-stop

# Run once in an Administrator terminal to allow other devices on this Wi-Fi.
firewall:
	$(LAUNCH) -Action firewall -WebPort $(WEB_PORT)

# Optional Expo development server; the default up target uses the verified proxy.
dev: export EXPO_PUBLIC_API_URL := http://localhost:$(API_PORT)/api
dev:
	"$(NPM)" run web
