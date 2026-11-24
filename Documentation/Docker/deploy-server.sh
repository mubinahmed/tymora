#!/usr/bin/env bash
# Redeploy Tymora on the server from the public ECR images: pull the new image(s), then replace the container(s).
# A container stays on the image it was created from, so `docker restart` is not enough; it is removed and run again.
# Settings, the Claude token and the unitime-data volume live outside the containers and are kept.
#
# Usage:
#   deploy-server.sh [web|ui|both] [options]      (default: both)
#
# Options:
#   --tag TAG     image tag to deploy (default: latest), e.g. a tag printed by build-and-push.sh
#   --no-pull     use the images already on the server
#   --no-wait     don't wait for Tomcat to finish starting after replacing unitime-web
#   --prune       remove old, unused images afterwards (docker image prune -f)
#   --dry-run     print the commands without running them
#   -h, --help    show this help
#
# Settings (environment variables; the defaults match the server setup in README.md):
#   WEB_IMAGE      public.ecr.aws/p3y5w7c0/lms/unitime-web
#   UI_IMAGE       public.ecr.aws/p3y5w7c0/lms/unitime-ui
#   TYMORA_DIR     /opt/tymora          (tymora-web.env, secrets/claude-token, ui-nginx.conf)
#   BACKEND_PORT   18080                (Tomcat on 172.17.0.1, called by the UI's nginx)
#   LEGACY_PORT    8888                 (legacy UI on 127.0.0.1)
#   UI_PORT        8000                 (UI on 127.0.0.1, proxied by the server's nginx)
#   WAIT_SECONDS   300                  (how long to wait for "Server startup in")
#
# Back up the database before deploying a new web image: Tymora upgrades the schema when a newer version starts.
set -euo pipefail

WEB_IMAGE="${WEB_IMAGE:-public.ecr.aws/p3y5w7c0/lms/unitime-web}"
UI_IMAGE="${UI_IMAGE:-public.ecr.aws/p3y5w7c0/lms/unitime-ui}"
TYMORA_DIR="${TYMORA_DIR:-/opt/tymora}"
BACKEND_PORT="${BACKEND_PORT:-18080}"
LEGACY_PORT="${LEGACY_PORT:-8888}"
UI_PORT="${UI_PORT:-8000}"
WAIT_SECONDS="${WAIT_SECONDS:-300}"

TARGET=both
TAG=latest
PULL=1
WAIT=1
PRUNE=0
DRY_RUN=0

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
    case "$1" in
        web|ui|both) TARGET="$1"; shift ;;
        --tag) TAG="${2:?--tag needs a value}"; shift 2 ;;
        --no-pull) PULL=0; shift ;;
        --no-wait) WAIT=0; shift ;;
        --prune) PRUNE=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
    esac
done
[[ "$TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || { echo "Invalid tag: $TAG" >&2; exit 2; }
for p in "$BACKEND_PORT" "$LEGACY_PORT" "$UI_PORT"; do
    [[ "$p" =~ ^[0-9]+$ ]] || { echo "Invalid port: $p" >&2; exit 2; }
done

DO_WEB=0; DO_UI=0
[ "$TARGET" = ui ] || DO_WEB=1
[ "$TARGET" = web ] || DO_UI=1

SUDO=""
[ "$(id -u)" = 0 ] || SUDO="sudo"

step() { printf '\n==> %s\n' "$*"; }
run() {
    printf '+ %s\n' "$*"
    [ "$DRY_RUN" = 1 ] || "$@"
}
fail() { echo "ERROR: $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker not found"

# 1. Checks, before anything is pulled or removed.
if [ "$DO_WEB" = 1 ] && [ "$DRY_RUN" = 0 ]; then
    [ -f "$TYMORA_DIR/tymora-web.env" ] || $SUDO test -f "$TYMORA_DIR/tymora-web.env" || fail "$TYMORA_DIR/tymora-web.env not found"
    [ -f "$TYMORA_DIR/secrets/claude-token" ] || $SUDO test -f "$TYMORA_DIR/secrets/claude-token" || fail "$TYMORA_DIR/secrets/claude-token not found"
fi

# 2. Pull. Done for both images before any container is touched, so a failed pull leaves the running site as it is.
if [ "$PULL" = 1 ]; then
    step "Pulling tag $TAG"
    [ "$DO_WEB" = 0 ] || run docker pull "$WEB_IMAGE:$TAG"
    [ "$DO_UI" = 0 ] || run docker pull "$UI_IMAGE:$TAG"
elif [ "$DRY_RUN" = 0 ]; then
    [ "$DO_WEB" = 0 ] || docker image inspect "$WEB_IMAGE:$TAG" >/dev/null 2>&1 || fail "$WEB_IMAGE:$TAG is not on this server (run without --no-pull)"
    [ "$DO_UI" = 0 ] || docker image inspect "$UI_IMAGE:$TAG" >/dev/null 2>&1 || fail "$UI_IMAGE:$TAG is not on this server (run without --no-pull)"
fi

# 3. The UI's nginx configuration, prepared before the UI container is removed: a copy of the one in the new image,
# with the backend port changed to BACKEND_PORT (the image calls unitime-web:8080).
if [ "$DO_UI" = 1 ]; then
    step "Preparing $TYMORA_DIR/ui-nginx.conf (backend port $BACKEND_PORT)"
    if [ "$DRY_RUN" = 1 ]; then
        echo "+ docker run --rm --entrypoint cat $UI_IMAGE:$TAG /etc/nginx/conf.d/default.conf | $SUDO tee $TYMORA_DIR/ui-nginx.conf"
        echo "+ $SUDO sed -i 's/server unitime-web:8080;/server unitime-web:$BACKEND_PORT;/' $TYMORA_DIR/ui-nginx.conf"
    else
        conf="$(docker run --rm --entrypoint cat "$UI_IMAGE:$TAG" /etc/nginx/conf.d/default.conf)"
        grep -q "server unitime-web:8080;" <<<"$conf" || fail "the UI image's nginx configuration has no 'server unitime-web:8080;' line; not changed"
        sed "s/server unitime-web:8080;/server unitime-web:$BACKEND_PORT;/" <<<"$conf" | $SUDO tee "$TYMORA_DIR/ui-nginx.conf.new" >/dev/null
        $SUDO grep -q "server unitime-web:$BACKEND_PORT;" "$TYMORA_DIR/ui-nginx.conf.new" || fail "port change in ui-nginx.conf failed"
        $SUDO mv "$TYMORA_DIR/ui-nginx.conf.new" "$TYMORA_DIR/ui-nginx.conf"
        $SUDO grep -n "server unitime-web" "$TYMORA_DIR/ui-nginx.conf"
    fi
fi

# 4. Web container.
if [ "$DO_WEB" = 1 ]; then
    step "Replacing unitime-web ($WEB_IMAGE:$TAG)"
    run docker rm -f unitime-web
    run docker run -d --name unitime-web \
        --restart unless-stopped \
        --env-file "$TYMORA_DIR/tymora-web.env" \
        --add-host host.docker.internal:host-gateway \
        --mount "type=bind,source=$TYMORA_DIR/secrets/claude-token,target=/run/secrets/claude-code-token,readonly" \
        -v unitime-data:/usr/local/tomcat/data \
        -p "172.17.0.1:$BACKEND_PORT:8080" \
        -p "127.0.0.1:$LEGACY_PORT:8080" \
        "$WEB_IMAGE:$TAG"

    if [ "$WAIT" = 1 ] && [ "$DRY_RUN" = 0 ]; then
        step "Waiting for Tomcat to start (up to ${WAIT_SECONDS}s)"
        started=0
        for ((i = 0; i < WAIT_SECONDS; i += 5)); do
            if docker logs unitime-web 2>&1 | grep -q "Server startup in"; then started=1; break; fi
            if [ "$(docker inspect -f '{{.State.Running}}' unitime-web 2>/dev/null)" != true ]; then
                docker logs --tail 40 unitime-web >&2 || true
                fail "unitime-web stopped while starting (last log lines above)"
            fi
            sleep 5
        done
        if [ "$started" = 1 ]; then
            docker logs unitime-web 2>&1 | grep "Server startup in" | tail -1
        else
            echo "WARNING: no \"Server startup in\" after ${WAIT_SECONDS}s; check: docker logs -f unitime-web" >&2
        fi
    fi
fi

# 5. UI container.
if [ "$DO_UI" = 1 ]; then
    step "Replacing unitime-ui ($UI_IMAGE:$TAG)"
    run docker rm -f unitime-ui
    run docker run -d --name unitime-ui \
        --restart unless-stopped \
        --add-host unitime-web:host-gateway \
        --mount "type=bind,source=$TYMORA_DIR/ui-nginx.conf,target=/etc/nginx/conf.d/default.conf,readonly" \
        -p "127.0.0.1:$UI_PORT:80" \
        "$UI_IMAGE:$TAG"
fi

if [ "$PRUNE" = 1 ]; then
    step "Removing unused images"
    run docker image prune -f
fi

step "Running containers"
if [ "$DRY_RUN" = 0 ]; then
    docker ps --filter name=unitime- --format '{{.Names}}  {{.Image}}  {{.Status}}'
    for c in unitime-web unitime-ui; do
        rev="$(docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$c" 2>/dev/null || true)"
        [ -z "$rev" ] || echo "  $c revision: $rev"
    done
fi
