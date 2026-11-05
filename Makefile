# GNU Make 3.81+ (including the macOS system make). See docs/development.md.
.DEFAULT_GOAL := help
.NOTPARALLEL:
.DELETE_ON_ERROR:
.SUFFIXES:
SHELL := /bin/sh

GRADLEW ?= ./gradlew
GRADLE_ARGS ?=
PYTHON ?= python3
CLIENT_DIR ?= run/make/client
SERVER_DIR ?= run/make/server
GAMETEST_DIR ?= run/make/gametest
DEV_CLIENT_DIR ?= run/client
DEV_SERVER_DIR ?= run/server
QA_DIR ?=
TICKS ?= 1200
BENCHMARK_TICKS ?= 36000
TEST ?=

export GRADLEW GRADLE_ARGS
export PYTHONDONTWRITEBYTECODE := 1
GRADLE = "$(GRADLEW)" $(GRADLE_ARGS) --dependency-verification strict
QA_LOCATION = $(if $(strip $(QA_DIR)),--run-dir "$(QA_DIR)",)

.PHONY: help all build assemble jar package checksums check test test-tools lint format \
        gametest gametest-dev verify ci client run play client-dev server server-dev \
        smoke smoke-load smoke-recovery benchmark assets inventory integrity audit \
        dependencies tasks info clean rebuild stop

help: ## List targets and common configuration overrides (default).
	@printf 'ModularCraft development and packaged validation\n\n'
	@awk 'BEGIN { FS = ":.*## " } /^[a-zA-Z][a-zA-Z0-9_-]*:.*## / { printf "  %-18s %s\n", $$1, $$2 }' Makefile
	@printf '\nOverrides: GRADLE_ARGS="--offline" TEST="*SceneTest" TICKS=400 QA_DIR=run/my-fresh-qa\n'
	@printf 'Paths: CLIENT_DIR, SERVER_DIR, GAMETEST_DIR (managed instances under run/).\n'
	@printf 'Tools: GRADLEW, PYTHON. More examples and safety details: docs/development.md\n'

all: build ## Alias for build.

build: test-tools ## Run checks and create the runtime JAR, sources JAR, and project ZIP.
	$(GRADLE) build

assemble: ## Create all archives without running checks.
	$(GRADLE) assemble

jar: ## Build the current runtime JAR and write its version-independent path descriptor.
	$(GRADLE) runtimeArtifact

package: build ## Build, inspect deliverables, and write SHA256SUMS.
	"$(PYTHON)" scripts/check-delivery.py

checksums: assemble ## Inspect assembled archives and refresh SHA256SUMS.
	"$(PYTHON)" scripts/check-delivery.py

check: test-tools ## Run formatting, static analysis, and all Java tests.
	$(GRADLE) check

test: ## Run JUnit; optionally select TEST='*SceneTest'.
	$(GRADLE) test $(if $(strip $(TEST)),--tests "$(TEST)",)

test-tools: ## Test launcher isolation, JAR staging, and subprocess failure handling.
	"$(PYTHON)" scripts/release.py check
	"$(PYTHON)" -m unittest discover -s scripts/tests -v

lint: ## Check Java formatting, Checkstyle, and PMD across all source sets.
	$(GRADLE) formatCheck checkstyleMain checkstyleTest checkstyleGametest pmdMain pmdTest pmdGametest

format: ## Apply the pinned Java formatter.
	$(GRADLE) formatJava

gametest: jar ## Run headless GameTests using the built JAR in a managed instance.
	"$(PYTHON)" scripts/run_validation.py gametest --run-dir "$(GAMETEST_DIR)"

gametest-dev: ## Run headless GameTests against development classes.
	$(GRADLE) runGameTestServer

verify: test-tools ## Full automated checks, packaged headless GameTests, and artifact inspection.
	$(GRADLE) check build runtimeArtifact
	"$(PYTHON)" scripts/run_validation.py gametest --run-dir "$(GAMETEST_DIR)"
	"$(PYTHON)" scripts/check-delivery.py

ci: override GRADLE_ARGS += --no-daemon
ci: verify ## Run verify without a persistent Gradle daemon; never opens a game window.

client: jar ## Build and launch the actual JAR for interactive play; saves persist.
	"$(PYTHON)" scripts/run_validation.py client --run-dir "$(CLIENT_DIR)"

run: client ## Alias for client (built-JAR validation).

play: client ## Alias for client (built-JAR validation).

client-dev: ## Launch against development classes in DEV_CLIENT_DIR.
	$(GRADLE) runClient "-PclientRunDir=$(DEV_CLIENT_DIR)"

server: jar ## Launch a normal headless server using the built JAR; EULA is operator-managed.
	"$(PYTHON)" scripts/run_validation.py server --run-dir "$(SERVER_DIR)"

server-dev: ## Launch a normal headless server against development classes.
	$(GRADLE) runServer "-PserverRunDir=$(DEV_SERVER_DIR)"

smoke: jar ## Build JAR, create a fresh RP-001 world, stream audio, then exit after TICKS.
	"$(PYTHON)" scripts/run_validation.py smoke --ticks "$(TICKS)" $(QA_LOCATION)

smoke-load: jar ## Fresh 64-module/96-cable/eight-speaker native fixture, then exit.
	"$(PYTHON)" scripts/run_validation.py smoke-load --ticks "$(TICKS)" $(QA_LOCATION)

smoke-recovery: jar ## Fresh RP-001 plus 20 native audio teardown/recovery cycles; TICKS >= 400.
	"$(PYTHON)" scripts/run_validation.py smoke-recovery --ticks "$(TICKS)" $(QA_LOCATION)

benchmark: jar ## Run the single-client load fixture for BENCHMARK_TICKS (default 30 minutes).
	"$(PYTHON)" scripts/run_validation.py smoke-load --ticks "$(BENCHMARK_TICKS)" $(QA_LOCATION)

assets: ## Regenerate original textures, models, localization, and editable SVGs.
	"$(PYTHON)" scripts/generate-assets.py

inventory: ## Refresh resolved dependency coordinates and hashes without changing pins.
	$(GRADLE) dependencyInventory

integrity: ## Compare recorded hashes with trusted upstream records (network on cache misses).
	"$(PYTHON)" scripts/verify-upstream.py

audit: ## Query OSV for public dependency coordinates and save the advisory report (network).
	"$(PYTHON)" scripts/audit-dependencies.py

dependencies: ## Show Gradle dependency configurations.
	$(GRADLE) dependencies

tasks: ## List available Gradle tasks.
	$(GRADLE) tasks --all

info: ## Show Gradle/JVM and Python versions.
	"$(GRADLEW)" --version
	"$(PYTHON)" --version

clean: ## Delete Gradle build outputs only; retain every run directory, save, and log.
	$(GRADLE) clean

rebuild: clean build ## Clean build outputs, then build and check again.

stop: ## Stop Gradle daemons (does not terminate Minecraft or delete worlds).
	"$(GRADLEW)" --stop
