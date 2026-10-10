# Windows local runtime. Machine-specific tool paths can live in .runtime/tools.mk.
# Git Bash rewrites values such as /api into Windows paths; use the native shell.
SHELL := cmd.exe
.SHELLFLAGS := /C
ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
-include $(ROOT)/.runtime/tools.mk
POWERSHELL ?= pwsh
JAVA_HOME ?=
MAVEN_HOME ?=
DATABASE_PORT ?= 55432
API_PORT ?= 8080
WEB_PORT ?= 8787
ADMINER_PORT ?= 8082

LAUNCH = "$(POWERSHELL)" -NoProfile -ExecutionPolicy Bypass -File "$(ROOT)/scripts/start-local.ps1"
PORTS = -DatabasePort $(DATABASE_PORT) -ApiPort $(API_PORT) -WebPort $(WEB_PORT) -AdminerPort $(ADMINER_PORT)
JAVA = -JavaHome "$(JAVA_HOME)" -MavenHome "$(MAVEN_HOME)"

.DEFAULT_GOAL := help
.NOTPARALLEL:
.PHONY: help build test up start db adminer stop down status env

help:
	@echo Backend: up, start, build, test, db, adminer, stop, down, status, env

build:
	$(LAUNCH) -Action build $(JAVA)

test:
	$(LAUNCH) -Action test $(JAVA)

up: build start

start:
	$(LAUNCH) -Action start $(JAVA) $(PORTS)

db:
	$(LAUNCH) -Action database $(PORTS)

adminer:
	$(LAUNCH) -Action adminer $(PORTS)

stop:
	$(LAUNCH) -Action stop

# Stop PostgreSQL gracefully; database files and uploaded media are preserved.
down: stop
	$(LAUNCH) -Action database-stop

status:
	$(LAUNCH) -Action status

env:
	$(LAUNCH) -Action env $(JAVA) $(PORTS)
