#!/bin/bash
set -e

echo "[start.sh] 애플리케이션 시작"

APP_DIR=/opt/spring/app
JAR_FILE=$(ls $APP_DIR/build/libs/*.jar 2>/dev/null | grep -v plain | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "[start.sh] ERROR: JAR 파일을 찾을 수 없음 ($APP_DIR/build/libs/)"
    exit 1
fi

echo "[start.sh] JAR: $JAR_FILE"

# 심볼릭 링크 업데이트
ln -sf "$JAR_FILE" /opt/spring/app.jar

systemctl start sol-lite
echo "[start.sh] sol-lite 서비스 시작 완료"
