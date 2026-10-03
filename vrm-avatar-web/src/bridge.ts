import { VRMManager } from './vrmManager';

// 声明暴露给 Android Native 和调试工具的全局接口
export interface AvatarController {
  speak: (volume: number) => void;
  setExpression: (name: string) => void;
  resetExpression: () => void;
  loadModel: (url: string) => Promise<boolean>;
  isReady: () => boolean;
}

declare global {
  interface Window {
    avatarController?: AvatarController;
    // Android 原生通过 WebView.addJavascriptInterface 注入的对象
    AndroidBridge?: {
      onAvatarReady?: () => void;
      onAvatarClicked?: () => void;
      log?: (message: string) => void;
    };
  }
}

export function setupBridge(vrmManager: VRMManager): AvatarController {
  const controller: AvatarController = {
    speak: (volume: number) => {
      vrmManager.speak(volume);
    },
    setExpression: (name: string) => {
      vrmManager.setExpression(name);
    },
    resetExpression: () => {
      vrmManager.resetExpression();
    },
    loadModel: async (url: string): Promise<boolean> => {
      try {
        await vrmManager.loadModel(url);
        window.AndroidBridge?.onAvatarReady?.();
        return true;
      } catch (err) {
        console.error('加载 VRM 模型失败:', err);
        return false;
      }
    },
    isReady: () => {
      return vrmManager.currentVrm !== null;
    },
  };

  window.avatarController = controller;
  return controller;
}
