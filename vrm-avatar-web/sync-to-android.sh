#!/bin/bash
set -e

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_ASSETS_DIR="$SCRIPT_DIR/../Android/src/voice-chat-app/src/main/assets/vrm-web"

echo "=== 1. 执行前端生产打包 ==="
cd "$SCRIPT_DIR"
npm run build

echo "=== 2. 同步静态产物到 Android Assets ==="
mkdir -p "$TARGET_ASSETS_DIR"
rm -rf "$TARGET_ASSETS_DIR"/*
cp -r "$SCRIPT_DIR/dist/"* "$TARGET_ASSETS_DIR/"

echo "✅ 同步完成！产物已部署至: $TARGET_ASSETS_DIR"
ls -la "$TARGET_ASSETS_DIR"
