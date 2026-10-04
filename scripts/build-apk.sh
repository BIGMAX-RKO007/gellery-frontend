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

# 检测与配置 Android SDK
if [ -z "${ANDROID_HOME:-}" ]; then
    for candidate in /usr/local/android-sdk /opt/android-sdk /opt/android /usr/lib/android-sdk /root/android-sdk "$HOME/android-sdk" /usr/local/lib/android/sdk; do
        if [ -d "$candidate" ]; then
            export ANDROID_HOME="$candidate"
            export ANDROID_SDK_ROOT="$candidate"
            echo "自动检测到 Android SDK 路径: ${ANDROID_HOME}"
            break
        fi
    done
fi

# 若环境无 Android SDK (如裸容器环境)，尝试轻量级自动化安装
if [ -z "${ANDROID_HOME:-}" ] && [ ! -f "${ANDROID_DIR}/local.properties" ]; then
    echo "未检测到预装的 ANDROID_HOME，尝试初始化轻量 Android SDK..."
    SDK_DIR="/tmp/android-sdk"
    mkdir -p "${SDK_DIR}/cmdline-tools"
    export ANDROID_HOME="${SDK_DIR}"
    export ANDROID_SDK_ROOT="${SDK_DIR}"

    CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    if [ ! -d "${SDK_DIR}/cmdline-tools/latest" ]; then
        echo "正在下载 Android Command-line Tools..."
        if command -v curl &> /dev/null; then
            curl -sSLo /tmp/cmdline-tools.zip "${CMDLINE_TOOLS_URL}"
        elif command -v wget &> /dev/null; then
            wget -qO /tmp/cmdline-tools.zip "${CMDLINE_TOOLS_URL}"
        fi
        if [ -f /tmp/cmdline-tools.zip ]; then
            unzip -q -o /tmp/cmdline-tools.zip -d "${SDK_DIR}/cmdline-tools"
            mv "${SDK_DIR}/cmdline-tools/cmdline-tools" "${SDK_DIR}/cmdline-tools/latest" 2>/dev/null || true
            rm -f /tmp/cmdline-tools.zip
        fi
    fi

    if [ -x "${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager" ]; then
        export PATH="${SDK_DIR}/cmdline-tools/latest/bin:${SDK_DIR}/platform-tools:${PATH}"
        echo "正在自动安装 Android 35 Platform 与 Build-tools..."
        yes | "${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
        "${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /dev/null 2>&1 || true
    fi
fi

if [ -n "${ANDROID_HOME:-}" ]; then
    echo "写入 local.properties: sdk.dir=${ANDROID_HOME}"
    echo "sdk.dir=${ANDROID_HOME}" > "${ANDROID_DIR}/local.properties"
fi

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
