#!/usr/bin/env bash
set -euo pipefail

APP_NAME="qilingos-safeops-agent"
APP_USER="${SAFEOPS_APP_USER:-safeops}"
APP_GROUP="${SAFEOPS_APP_GROUP:-safeops}"
APP_HOME="${SAFEOPS_APP_HOME:-/opt/qilingos-safeops-agent}"
SERVICE_FILE="/etc/systemd/system/${APP_NAME}.service"
SOURCE_JAR="${1:-target/qilingos-safeops-agent-0.1.0-SNAPSHOT.jar}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "install script must run as root" >&2
  exit 1
fi

if [[ ! -f "${SOURCE_JAR}" ]]; then
  echo "jar not found: ${SOURCE_JAR}" >&2
  exit 1
fi

if ! getent group "${APP_GROUP}" >/dev/null; then
  groupadd --system "${APP_GROUP}"
fi

if ! id "${APP_USER}" >/dev/null 2>&1; then
  useradd --system --gid "${APP_GROUP}" --home-dir "${APP_HOME}" --shell /usr/sbin/nologin "${APP_USER}"
fi

install -d -o "${APP_USER}" -g "${APP_GROUP}" "${APP_HOME}"
install -d -o "${APP_USER}" -g "${APP_GROUP}" "${APP_HOME}/data"
install -m 0644 "${SOURCE_JAR}" "${APP_HOME}/${APP_NAME}.jar"
chown "${APP_USER}:${APP_GROUP}" "${APP_HOME}/${APP_NAME}.jar"
install -m 0644 "deploy/${APP_NAME}.service" "${SERVICE_FILE}"

systemctl daemon-reload
systemctl enable "${APP_NAME}"

echo "installed ${APP_NAME}"
echo "start with: systemctl start ${APP_NAME}"
echo "logs with: journalctl -u ${APP_NAME} -f"
