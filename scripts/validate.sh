#!/bin/bash
set -e

echo "[validate.sh] 서비스 상태 확인"

RETRY=0
MAX_RETRY=10

until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
    RETRY=$((RETRY + 1))
    if [ $RETRY -ge $MAX_RETRY ]; then
        echo "[validate.sh] ERROR: 헬스체크 실패 (${MAX_RETRY}회 시도)"
        exit 1
    fi
    echo "[validate.sh] 헬스체크 대기 중... ($RETRY/$MAX_RETRY)"
    sleep 10
done

echo "[validate.sh] 헬스체크 성공"
