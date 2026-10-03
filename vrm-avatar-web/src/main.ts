import { createScene } from './scene';
import { VRMManager } from './vrmManager';
import { setupBridge } from './bridge';

async function bootstrap() {
  const container = document.getElementById('canvas-container');
  const loadingOverlay = document.getElementById('loading-overlay');
  const loadingText = document.getElementById('loading-text');

  if (!container) {
    console.error('未找到 canvas-container 元素');
    return;
  }

  // 1. 初始化 Three.js 场景
  const { scene, camera, renderer, clock } = createScene(container);

  // 2. 初始化 VRM 管理器与桥接器
  const vrmManager = new VRMManager(scene);
  const controller = setupBridge(vrmManager, camera);

  // 3. 加载初始 VRM 模型
  try {
    if (loadingText) loadingText.textContent = '正在加载 3D 数字人模型...';
    await controller.loadModel('./models/avatar.vrm');

    // 彻底移除加载遮罩
    if (loadingOverlay) {
      loadingOverlay.style.opacity = '0';
      setTimeout(() => {
        loadingOverlay.remove();
      }, 300);
    }
    console.log('✅ VRM 数字人初始化完成，就绪！');
  } catch (error) {
    console.error('模型加载失败:', error);
    if (loadingText) {
      loadingText.textContent = '数字人加载异常，请检查模型文件路径';
      loadingText.style.color = '#d32f2f';
    }
  }

  // 4. 调试按钮交互绑定
  setupDebugToolbar(controller);

  // 5. 触摸/点击互动检测
  container.addEventListener('pointerdown', () => {
    window.AndroidBridge?.onAvatarClicked?.();
  });

  // 6. 渲染循环 (60fps)
  function animate() {
    requestAnimationFrame(animate);
    const deltaTime = clock.getDelta();

    // 更新 VRM 动画与眨眼
    vrmManager.update(deltaTime);

    // 渲染画面
    renderer.render(scene, camera);
  }

  animate();
}

function setupDebugToolbar(controller: ReturnType<typeof setupBridge>) {
  const btnSpeak = document.getElementById('btn-speak');
  const btnHappy = document.getElementById('btn-happy');
  const btnBlink = document.getElementById('btn-blink');
  const btnReset = document.getElementById('btn-reset');

  let speakInterval: number | null = null;

  btnSpeak?.addEventListener('click', () => {
    if (speakInterval) {
      clearInterval(speakInterval);
      speakInterval = null;
      controller.speak(0);
      btnSpeak.textContent = '张嘴说话';
    } else {
      btnSpeak.textContent = '停止说话';
      // 模拟说话振幅波动
      speakInterval = window.setInterval(() => {
        const fakeVolume = 0.3 + Math.random() * 0.7;
        controller.speak(fakeVolume);
      }, 100);
    }
  });

  btnHappy?.addEventListener('click', () => {
    controller.setExpression('happy');
  });

  btnBlink?.addEventListener('click', () => {
    // 触发单次眨眼
    controller.setExpression('blink');
    setTimeout(() => controller.resetExpression(), 300);
  });

  btnReset?.addEventListener('click', () => {
    if (speakInterval) {
      clearInterval(speakInterval);
      speakInterval = null;
      if (btnSpeak) btnSpeak.textContent = '张嘴说话';
    }
    controller.speak(0);
    controller.resetExpression();
  });
}

// 启动应用
bootstrap();
