#!/bin/bash
set -e

echo "[stop.sh] 기존 애플리케이션 중지 시작"

if systemctl is-active --quiet sol-lite; then
    systemctl stop sol-lite
    echo "[stop.sh] sol-lite 서비스 중지 완료"
else
    echo "[stop.sh] sol-lite 서비스가 실행 중이 아님 — 스킵"
fi
