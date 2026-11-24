#!/usr/bin/env bash
# Build the Tymora images (unitime-web with the Claude Code CLI, as in approach 4, and unitime-ui) and push them
# to the public ECR repositories. The two images are built in parallel, in the background; each is pushed as soon
# as its own build has finished, and the script ends when both are pushed:
#   public.ecr.aws/p3y5w7c0/lms/unitime-web
#   public.ecr.aws/p3y5w7c0/lms/unitime-ui
#
# Usage (from anywhere in the repository):
#   Documentation/Docker/build-and-push.sh [options]
#
# Options:
#   --tag TAG        release tag pushed besides "latest" (default: yyyymmdd-hhmm)
#   --no-latest      push only the release tag, not "latest"
#   --skip-war       don't run Maven; use the existing target/UniTime.war
#   --only web|ui    build and push one image only
#   --no-push        build and tag locally, don't log in or push
#   --dry-run        print the commands without running them
#
# Output: lines are prefixed [web] / [ui]; each job also writes a log file (path printed at the start, kept when a
# job fails). Ctrl+C stops both jobs.
#   -h, --help       show this help
#
# Settings (environment variables):
#   PUBLIC_REPO         default public.ecr.aws/p3y5w7c0/lms
#   CLAUDE_CLI_VERSION  Claude Code version in the web image (default 2.1.329)
#   PLATFORM            image platform (default linux/amd64)
#   ECR_PROFILE         AWS CLI profile with push rights on the public repositories (default ifas-sit;
#                       set it empty to use the AWS CLI's default credentials or AWS_PROFILE)
#
# Needs: docker (with buildx), the AWS CLI v2, Maven and a JDK 17 (unless --skip-war).
set -euo pipefail

PUBLIC_REPO="${PUBLIC_REPO:-public.ecr.aws/p3y5w7c0/lms}"
CLAUDE_CLI_VERSION="${CLAUDE_CLI_VERSION:-2.1.329}"
PLATFORM="${PLATFORM:-linux/amd64}"
ECR_PROFILE="${ECR_PROFILE-ifas-sit}"
TAG="$(date +%Y%m%d-%H%M)"
LATEST=1
SKIP_WAR=0
ONLY=""
PUSH=1
DRY_RUN=0

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
    case "$1" in
        --tag) TAG="${2:?--tag needs a value}"; shift 2 ;;
        --no-latest) LATEST=0; shift ;;
        --skip-war) SKIP_WAR=1; shift ;;
        --only) ONLY="${2:?--only needs web or ui}"; shift 2 ;;
        --no-push) PUSH=0; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
done
case "$ONLY" in ""|web|ui) ;; *) echo "--only must be web or ui" >&2; exit 2 ;; esac
[[ "$TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || { echo "Invalid tag: $TAG" >&2; exit 2; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

step() { printf '\n==> %s\n' "$*"; }
run() {
    printf '+ %s\n' "$*"
    [ "$DRY_RUN" = 1 ] || "$@"
}
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing: $1 ($2)" >&2; exit 1; }; }

need docker "https://docs.docker.com/engine/install/"
[ "$PUSH" = 0 ] || need aws "AWS CLI v2"
[ "$SKIP_WAR" = 1 ] || [ "$ONLY" = ui ] || need mvn "Maven"

REVISION="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
if [ "$REVISION" != unknown ] && [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    REVISION="$REVISION-dirty"
    echo "Note: the working tree has uncommitted changes; they are included in the images (revision $REVISION)."
fi

WEB_REPO="$PUBLIC_REPO/unitime-web"
UI_REPO="$PUBLIC_REPO/unitime-ui"
tags_for() { # repo local-name
    local t="-t $1:$TAG -t $2:latest"
    [ "$LATEST" = 1 ] && t="$t -t $1:latest"
    echo "$t"
}
LABELS=(--label "org.opencontainers.image.revision=$REVISION" --label "org.opencontainers.image.created=$(date -u +%Y-%m-%dT%H:%M:%SZ)")

echo "Tag: $TAG$([ "$LATEST" = 1 ] && echo ' + latest')   Platform: $PLATFORM   Revision: $REVISION   Repository: $PUBLIC_REPO"

# Jobs. They run in background subshells, where `set -e` is not reliable (it is ignored inside `a || b`), so every
# step is chained with && and a job stops at its first failing command.
push_repo() { # repo
    run docker push "$1:$TAG" &&
    if [ "$LATEST" = 1 ]; then run docker push "$1:latest"; fi
}
# The local names docker-unitime-web / docker-unitime-ui are the ones Docker Compose uses in this folder, so a local
# `docker compose ... up --no-build` runs the same build.
web_job() {
    if [ "$SKIP_WAR" = 1 ]; then
        echo "Using the existing target/UniTime.war"
    else
        step "Building the WAR (Maven)" && run mvn -q -DskipTests package
    fi &&
    step "Building the image (Claude Code CLI $CLAUDE_CLI_VERSION)" &&
    # shellcheck disable=SC2046
    run docker build --progress=plain --platform "$PLATFORM" -f Documentation/Docker/tomcat.Dockerfile \
        --build-arg INSTALL_CLAUDE_CLI=true --build-arg CLAUDE_CLI_VERSION="$CLAUDE_CLI_VERSION" \
        "${LABELS[@]}" $(tags_for "$WEB_REPO" docker-unitime-web) . &&
    if [ "$PUSH" = 1 ]; then step "Pushing $WEB_REPO" && push_repo "$WEB_REPO"; fi
}
ui_job() {
    step "Building the image" &&
    # shellcheck disable=SC2046
    run docker build --progress=plain --platform "$PLATFORM" -f ui/Dockerfile \
        "${LABELS[@]}" $(tags_for "$UI_REPO" docker-unitime-ui) ui &&
    if [ "$PUSH" = 1 ]; then step "Pushing $UI_REPO" && push_repo "$UI_REPO"; fi
}

JOBS=()
[ "$ONLY" = ui ] || JOBS+=(web)
[ "$ONLY" = web ] || JOBS+=(ui)

if [ "$SKIP_WAR" = 1 ] && [ "$ONLY" != ui ] && [ "$DRY_RUN" = 0 ] && [ ! -f target/UniTime.war ]; then
    echo "target/UniTime.war not found; run without --skip-war" >&2; exit 1
fi

# 1. Log in first, so that missing or wrong credentials stop the script before the long builds, and each job can push
# as soon as its build is done. Public ECR logins always use us-east-1; the login is valid for 12 hours.
if [ "$PUSH" = 1 ]; then
    PROFILE_ARGS=()
    [ -z "$ECR_PROFILE" ] || PROFILE_ARGS=(--profile "$ECR_PROFILE")
    step "Logging in to public ECR${ECR_PROFILE:+ (profile $ECR_PROFILE)}"
    if [ "$DRY_RUN" = 1 ]; then
        echo "+ aws ecr-public get-login-password --region us-east-1 ${PROFILE_ARGS[*]} | docker login --username AWS --password-stdin public.ecr.aws"
    else
        aws ecr-public get-login-password --region us-east-1 "${PROFILE_ARGS[@]}" | docker login --username AWS --password-stdin public.ecr.aws
    fi
fi

# 2. Start the jobs in parallel.
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tymora-build.XXXXXX")"
trap 'trap - INT TERM; echo; echo "Interrupted: stopping the builds (logs: $LOG_DIR)" >&2; kill 0' INT TERM

start_job() { # name
    local name="$1"
    (
        start=$SECONDS
        rc=0
        "${name}_job" || rc=$?
        echo "$rc $((SECONDS - start))" > "$LOG_DIR/$name.status"
        if [ "$rc" = 0 ]; then echo "==> Finished in $((SECONDS - start))s"; else echo "==> FAILED (exit $rc) after $((SECONDS - start))s"; fi
    ) 2>&1 | tee "$LOG_DIR/$name.log" | sed -u "s/^/[$name] /" &
}

step "Starting in parallel: ${JOBS[*]}   (logs: $LOG_DIR)"
for j in "${JOBS[@]}"; do start_job "$j"; done

# 3. Wait for both. Each job pushes its own image as soon as its build is done.
wait

step "Summary"
FAILED=0
for j in "${JOBS[@]}"; do
    read -r rc secs < "$LOG_DIR/$j.status" 2>/dev/null || { rc=1; secs="?"; }
    repo="$WEB_REPO"; [ "$j" = ui ] && repo="$UI_REPO"
    if [ "$rc" = 0 ]; then
        printf '  %-4s ok      %5ss  %s\n' "$j" "$secs" "$([ "$PUSH" = 1 ] && echo "pushed $repo:$TAG" || echo "built locally")"
    else
        printf '  %-4s FAILED  %5ss  see %s\n' "$j" "$secs" "$LOG_DIR/$j.log"
        FAILED=1
    fi
done

if [ "$FAILED" = 1 ]; then
    echo "Logs kept in $LOG_DIR" >&2
    exit 1
fi
rm -rf "$LOG_DIR"
[ "$PUSH" = 0 ] || echo "On the server: pull and replace the containers (README, \"Deploying on a server that already runs nginx\", Updating)."
