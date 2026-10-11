#!/usr/bin/env bash
#
# Deploys one build of ResolveX onto ONE app EC2 instance.
#
# Jenkins runs this on each app server in turn, over SSH:
#   ssh ec2-user@<server> bash -s -- <registry> <backend-repo> <frontend-repo> <tag> <region> < deploy/deploy-app.sh
#
# Before running it, Jenkins writes the backend's settings and secrets to
# ~/resolvex/backend.env on the server (mode 600), so no secret ever appears on
# a command line or in the build log.
#
# The server needs Docker, the AWS CLI and an IAM role that can pull from ECR -
# all set up when the instance was launched.

set -euo pipefail

REGISTRY="$1"
BACKEND_REPO="$2"
FRONTEND_REPO="$3"
TAG="$4"
REGION="$5"

BACKEND_IMAGE="${REGISTRY}/${BACKEND_REPO}:${TAG}"
FRONTEND_IMAGE="${REGISTRY}/${FRONTEND_REPO}:${TAG}"
ENV_FILE="${HOME}/resolvex/backend.env"
NETWORK="resolvex-net"
LOG_OPTS=(--log-opt max-size=10m --log-opt max-file=3)

echo "==> $(hostname): deploying build ${TAG}"

echo "==> Logging in to ECR"
aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${REGISTRY}" >/dev/null

echo "==> Pulling images"
docker pull -q "${BACKEND_IMAGE}"
docker pull -q "${FRONTEND_IMAGE}"

# Both containers join one network so Nginx can reach the backend by its
# container name (proxy_pass http://resolvex-backend:8080).
docker network inspect "${NETWORK}" >/dev/null 2>&1 || docker network create "${NETWORK}"

echo "==> Starting backend"
docker rm -f resolvex-backend >/dev/null 2>&1 || true
# Published on 127.0.0.1 only: reachable for the checks below, never from outside.
docker run -d --name resolvex-backend --network "${NETWORK}" --restart unless-stopped \
  --env-file "${ENV_FILE}" -p 127.0.0.1:8081:8080 "${LOG_OPTS[@]}" \
  "${BACKEND_IMAGE}" >/dev/null

echo "==> Waiting for the backend to start"
healthy=false
for _ in $(seq 1 36); do
  if curl -fsS --max-time 3 http://127.0.0.1:8081/api/health >/dev/null 2>&1; then
    healthy=true
    break
  fi
  sleep 5
done
if [ "${healthy}" != true ]; then
  echo "!! Backend did not become healthy within 3 minutes. Last log lines:"
  docker logs --tail 150 resolvex-backend
  exit 1
fi

echo "==> Starting frontend"
docker rm -f resolvex-frontend >/dev/null 2>&1 || true
docker run -d --name resolvex-frontend --network "${NETWORK}" --restart unless-stopped \
  -p 80:8080 "${LOG_OPTS[@]}" \
  "${FRONTEND_IMAGE}" >/dev/null

echo "==> Checking the full path: Nginx -> Spring Boot"
healthy=false
for _ in $(seq 1 12); do
  if curl -fsS --max-time 3 http://127.0.0.1/api/health >/dev/null 2>&1; then
    healthy=true
    break
  fi
  sleep 5
done
if [ "${healthy}" != true ]; then
  echo "!! Nginx could not reach the backend. Last log lines:"
  docker logs --tail 50 resolvex-frontend
  exit 1
fi

# Informational: includes the database and Redis. Not a deploy gate, because the
# application keeps working while Redis is down.
echo "==> Actuator health: $(curl -s --max-time 5 http://127.0.0.1:8081/actuator/health || echo unavailable)"

# Each build has a new tag, so old images pile up on the 15 GB disk.
docker image prune -af --filter "until=24h" >/dev/null || true

echo "==> $(hostname): build ${TAG} is live"
