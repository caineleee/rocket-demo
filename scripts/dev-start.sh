#!/usr/bin/env bash
# ============================================================
# 微服务统一启动脚本
#   - 自动检测局域网 IP 并同步 .env（解决切网段后连不上 broker 的问题）
#   - 加载 .env 环境变量（供 Spring Boot 的 ${NACOS_SERVER_ADDR} 等占位符解析）
#   - 关闭 Spring Boot Docker Compose 自动管理（子模块找不到 compose.yaml 会报错）
#   - 在模块目录下执行 mvn spring-boot:run（与项目约定一致，不带 -am）
#
# 用法：
#   ./scripts/dev-start.sh user-service        # 启动单个服务
#   ./scripts/dev-start.sh all                  # 启动所有微服务（资源占用高，慎用）
#   ./scripts/dev-start.sh gateway-service      # 启动网关
# ============================================================
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "用法: $0 <service-name | all>"
    echo "可用服务: user-service goods-service coupon-service order-service pay-service gateway-service"
    exit 1
fi

SERVICE="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."

ALL_SERVICES=(user-service goods-service coupon-service order-service pay-service gateway-service)

# 校验服务名
valid=0
for s in "${ALL_SERVICES[@]}"; do
    [ "$s" = "$SERVICE" ] && valid=1
done
if [ "$valid" = 0 ] && [ "$SERVICE" != "all" ]; then
    echo "❌ 未知服务: $SERVICE"
    echo "可用: ${ALL_SERVICES[*]} | all"
    exit 1
fi

# 1. 检测并更新 IP
echo "==> [1/3] 检测局域网 IP ..."
bash "$SCRIPT_DIR/update-env.sh"

# 2. 加载 .env 环境变量
echo "==> [2/3] 加载 .env 环境变量 ..."
if [ ! -f "$ROOT_DIR/.env" ]; then
    echo "❌ 未找到 .env，请先执行: cp .env.example .env" >&2
    exit 1
fi
set -a
# shellcheck disable=SC1091
source "$ROOT_DIR/.env"
set +a
echo "    HOST_IP=$HOST_IP"
echo "    NACOS_SERVER_ADDR=$NACOS_SERVER_ADDR"
echo "    MYSQL_HOST=${MYSQL_HOST:-localhost}"

# 关闭 Spring Boot Docker Compose 自动管理
export SPRING_DOCKER_COMPOSE_ENABLED=false

# 3. 启动服务
echo "==> [3/3] 启动 $SERVICE ..."

start_one() {
    local svc="$1"
    echo "---- 启动 $svc ----"
    (cd "$ROOT_DIR/$svc" && mvn spring-boot:run)
}

case "$SERVICE" in
    all)
        for s in "${ALL_SERVICES[@]}"; do
            start_one "$s" &
        done
        wait
        ;;
    *)
        start_one "$SERVICE"
        ;;
esac
