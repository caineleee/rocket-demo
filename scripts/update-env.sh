#!/usr/bin/env bash
# ============================================================
# 自动检测宿主机局域网 IP，并同步更新 .env 中的 HOST_IP / NACOS_SERVER_ADDR
# ------------------------------------------------------------
# 解决问题：
#   宿主机切换网段(如 192.168.31.x -> 192.168.5.x)后，.env 里的 HOST_IP 失效，
#   导致 RocketMQ broker 还用旧 brokerIP1 注册到 nameserver，
#   宿主机侧的 MQ producer 拿到不可达地址，异步发送报 "unknown reason"。
#
# 用法：
#   ./scripts/update-env.sh
#   bash scripts/update-env.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"

# ------------------------------------------------------------
# 检测局域网 IP
# 策略：优先 macOS 主网卡(en0/en1)，兜底 ifconfig 解析
# 排除：127.0.0.1 回环、172.x docker/bridge、169.254.x link-local、10.0.2.x 虚拟机NAT
# ------------------------------------------------------------
detect_ip() {
    local ip=""

    # macOS: 优先尝试 en0(Wi-Fi) / en1(以太网)
    if command -v ipconfig >/dev/null 2>&1; then
        for iface in en0 en1; do
            ip=$(ipconfig getifaddr "${iface}" 2>/dev/null || true)
            [ -n "${ip}" ] && break
        done
    fi

    # 兜底：ifconfig 解析(macOS/Linux 通用)
    if [ -z "${ip}" ]; then
        ip=$(ifconfig 2>/dev/null \
            | awk '/inet / && !/127.0.0.1/ {print $2}' \
            | grep -E '^[0-9]+\.' \
            | grep -vE '^(172\.(1[6-9]|2[0-9]|3[01])\.|169\.254\.|10\.0\.2\.)' \
            | head -1)
    fi

    echo "${ip}"
}

NEW_IP=$(detect_ip)
if [ -z "${NEW_IP}" ]; then
    echo "[ERROR] 无法自动检测局域网 IP，请手动在 .env 设置 HOST_IP" >&2
    exit 1
fi

if [ ! -f "${ENV_FILE}" ]; then
    echo "[ERROR] 未找到 .env 文件: ${ENV_FILE}" >&2
    echo "   请先执行: cp .env.example .env" >&2
    exit 1
fi

# 读取旧值
OLD_IP=$(grep -E '^HOST_IP=' "${ENV_FILE}" | cut -d= -f2- || true)

if [ "${OLD_IP}" = "${NEW_IP}" ]; then
    echo "[OK] HOST_IP 已是最新: ${NEW_IP} (无需更新)"
    exit 0
fi

# macOS 的 sed -i 需要备份后缀参数(传空串表示不备份)，Linux 的 sed -i 直接用
if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' "s|^HOST_IP=.*|HOST_IP=${NEW_IP}|" "${ENV_FILE}"
    sed -i '' "s|^NACOS_SERVER_ADDR=.*|NACOS_SERVER_ADDR=${NEW_IP}:8848|" "${ENV_FILE}"
else
    sed -i "s|^HOST_IP=.*|HOST_IP=${NEW_IP}|" "${ENV_FILE}"
    sed -i "s|^NACOS_SERVER_ADDR=.*|NACOS_SERVER_ADDR=${NEW_IP}:8848|" "${ENV_FILE}"
fi

echo "[OK] HOST_IP 已更新: ${OLD_IP:-(空)} -> ${NEW_IP}"
echo "[OK] NACOS_SERVER_ADDR 已更新: ${NEW_IP}:8848"
echo ""
echo "[NOTE] 若 RocketMQ broker 已在运行，需重建容器使其用新 IP 重新注册 brokerIP1:"
echo "       docker compose up -d --force-recreate broker-a-master"
