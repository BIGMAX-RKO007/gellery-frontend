#!/usr/bin/env bash
# ==============================================================================
# VoxMate Android APK 自动化构建脚本
# 适用环境: Linux/macOS, Gitee Go, GitHub Actions, 本地命令行
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${REPO_ROOT}/Android/src"
OUTPUT_DIR="${REPO_ROOT}/apk"

echo "=========================================="
echo " [1/3] 开始准备 VoxMate 构建环境..."
echo " 仓库根目录: ${REPO_ROOT}"
echo " Android源码目录: ${ANDROID_DIR}"
echo "=========================================="

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java 运行环境，请确保已安装 JDK 17 并配置了 PATH" >&2
    exit 1
fi
echo "当前 Java 版本:"
java -version

# 赋予 gradlew 执行权限
chmod +x "${ANDROID_DIR}/gradlew"

echo "=========================================="
echo " [2/3] 执行 Gradle 编译打包 (:voice-chat-app:assembleDebug)..."
echo "=========================================="
cd "${ANDROID_DIR}"
./gradlew :voice-chat-app:assembleDebug --no-daemon --stacktrace

echo "=========================================="
echo " [3/3] 收集产物并输出..."
echo "=========================================="
mkdir -p "${OUTPUT_DIR}"

SRC_APK="${ANDROID_DIR}/voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk"
TARGET_APK="${OUTPUT_DIR}/VoxMate-v0.1.0-3D-Avatar.apk"

if [ -f "${SRC_APK}" ]; then
    cp -f "${SRC_APK}" "${TARGET_APK}"
    APK_SIZE=$(du -h "${TARGET_APK}" | awk '{print $1}')
    echo "构建成功！"
    echo "APK 路径: ${TARGET_APK}"
    echo "APK 大小: ${APK_SIZE}"
else
    echo "错误: 未找到构建生成的 APK 文件: ${SRC_APK}" >&2
    exit 1
fi
