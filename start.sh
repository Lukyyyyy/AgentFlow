#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
FRONTEND_DIR="${ROOT_DIR}/frontend"
BACKEND_PID=""
FRONTEND_PID=""
REDIS_PID=""

info() {
  printf '\033[1;34m[启动]\033[0m %s\n' "$1"
}

error() {
  printf '\033[1;31m[错误]\033[0m %s\n' "$1" >&2
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    error "未找到 $1，请先安装后再重试。"
    exit 1
  fi
}

copy_env_if_missing() {
  local target="$1"
  local example="$2"

  if [[ ! -f "${target}" ]]; then
    cp "${example}" "${target}"
    info "已根据模板创建 ${target#"${ROOT_DIR}/"}，请按需修改其中的配置。"
  fi
}

terminate_process_tree() {
  local pid="$1"
  local child_pid

  while read -r child_pid; do
    [[ -n "${child_pid}" ]] && terminate_process_tree "${child_pid}"
  done < <(pgrep -P "${pid}" 2>/dev/null || true)

  kill "${pid}" 2>/dev/null || true
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ -n "${BACKEND_PID}" ]]; then
    terminate_process_tree "${BACKEND_PID}"
  fi
  if [[ -n "${FRONTEND_PID}" ]]; then
    terminate_process_tree "${FRONTEND_PID}"
  fi
  if [[ -n "${REDIS_PID}" ]]; then
    terminate_process_tree "${REDIS_PID}"
  fi

  [[ -n "${BACKEND_PID}" ]] && wait "${BACKEND_PID}" 2>/dev/null || true
  [[ -n "${FRONTEND_PID}" ]] && wait "${FRONTEND_PID}" 2>/dev/null || true
  [[ -n "${REDIS_PID}" ]] && wait "${REDIS_PID}" 2>/dev/null || true

  if [[ ${exit_code} -eq 0 ]]; then
    info "前后端服务已停止。"
  fi
}

trap cleanup EXIT
trap 'exit 130' INT TERM

require_command java
require_command node
require_command npm

redis_is_ready() {
  command -v redis-cli >/dev/null 2>&1 \
    && redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -q '^PONG$'
}

start_local_redis_if_needed() {
  local attempt

  if redis_is_ready; then
    info "Redis 已就绪：http://localhost:6379"
    return
  fi

  if ! command -v redis-server >/dev/null 2>&1; then
    error "Redis 未运行且未找到 redis-server。请安装并启动 Redis 后重试。"
    exit 1
  fi

  info "Redis 未运行，正在启动无持久化的本地开发实例：http://localhost:6379"
  redis-server \
    --save '' \
    --appendonly no \
    --bind 127.0.0.1 ::1 \
    --port 6379 \
    --daemonize no &
  REDIS_PID=$!

  for attempt in {1..30}; do
    if redis_is_ready; then
      return
    fi
    if ! kill -0 "${REDIS_PID}" 2>/dev/null; then
      break
    fi
    sleep 0.2
  done

  error "Redis 启动失败，请检查 6379 端口是否被占用。"
  exit 1
}

if [[ -x "${BACKEND_DIR}/mvnw" ]]; then
  MAVEN_COMMAND=("${BACKEND_DIR}/mvnw")
elif command -v mvn >/dev/null 2>&1; then
  MAVEN_COMMAND=(mvn)
else
  error "未找到 Maven。请安装 Maven 3.8+，或在 backend 目录提供可执行的 mvnw。"
  exit 1
fi

copy_env_if_missing "${BACKEND_DIR}/.env" "${BACKEND_DIR}/.env.example"
copy_env_if_missing "${FRONTEND_DIR}/.env.local" "${FRONTEND_DIR}/.env.example"

start_local_redis_if_needed

if [[ ! -d "${FRONTEND_DIR}/node_modules" ]]; then
  info "首次启动，正在安装前端依赖……"
  if [[ -f "${FRONTEND_DIR}/package-lock.json" ]]; then
    (cd "${FRONTEND_DIR}" && npm ci)
  else
    (cd "${FRONTEND_DIR}" && npm install)
  fi
fi

info "正在启动后端：http://localhost:8084"
(cd "${BACKEND_DIR}" && "${MAVEN_COMMAND[@]}" spring-boot:run) &
BACKEND_PID=$!

info "正在启动前端：http://localhost:5173"
(cd "${FRONTEND_DIR}" && npm run dev) &
FRONTEND_PID=$!

info "服务已启动。按 Ctrl+C 可同时停止前后端。"

while kill -0 "${BACKEND_PID}" 2>/dev/null && kill -0 "${FRONTEND_PID}" 2>/dev/null; do
  sleep 1
done

if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
  set +e
  wait "${BACKEND_PID}"
  exit_code=$?
  set -e
  error "后端进程已退出（状态码：${exit_code}）。"
else
  set +e
  wait "${FRONTEND_PID}"
  exit_code=$?
  set -e
  error "前端进程已退出（状态码：${exit_code}）。"
fi

exit "${exit_code}"
