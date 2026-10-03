import * as THREE from 'three';

export interface SceneContext {
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  renderer: THREE.WebGLRenderer;
  clock: THREE.Clock;
}

export function createScene(container: HTMLElement): SceneContext {
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xF5F7FB);

  const getWidth = () => container.clientWidth > 0 ? container.clientWidth : (window.innerWidth > 0 ? window.innerWidth : 360);
  const getHeight = () => container.clientHeight > 0 ? container.clientHeight : (window.innerHeight > 0 ? window.innerHeight : 600);

  const initialWidth = getWidth();
  const initialHeight = getHeight();
  const aspect = initialWidth / initialHeight;

  const camera = new THREE.PerspectiveCamera(30.0, aspect, 0.1, 20.0);
  // 全屏视图下的自然半身视角
  camera.position.set(0.0, 1.10, 2.20);
  camera.lookAt(0.0, 1.05, 0.0);

  const renderer = new THREE.WebGLRenderer({
    alpha: false,
    antialias: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(initialWidth, initialHeight);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.0;
  container.appendChild(renderer.domElement);

  // 灯光配置：柔和主光 + 环境光 + 轮廓背光
  const ambientLight = new THREE.AmbientLight(0xffffff, 1.4);
  scene.add(ambientLight);

  const keyLight = new THREE.DirectionalLight(0xfff6ea, 1.5);
  keyLight.position.set(1.0, 2.0, 1.5).normalize();
  scene.add(keyLight);

  const fillLight = new THREE.DirectionalLight(0xdde9ff, 0.9);
  fillLight.position.set(-1.0, 1.5, 1.0).normalize();
  scene.add(fillLight);

  const backLight = new THREE.DirectionalLight(0xffffff, 0.6);
  backLight.position.set(0.0, 2.0, -1.5).normalize();
  scene.add(backLight);

  const clock = new THREE.Clock();

  // 尺寸动态监听：优先 ResizeObserver，兼顾 window resize
  const updateSize = () => {
    const width = getWidth();
    const height = getHeight();
    if (width > 0 && height > 0) {
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height);
    }
  };

  const observer = new ResizeObserver(() => updateSize());
  observer.observe(container);
  window.addEventListener('resize', updateSize);

  return { scene, camera, renderer, clock };
}

export type CameraMode = 'full' | 'upper' | 'portrait';

/**
 * 切换摄像机景别模式
 * - 'full': 全身镜头 (包含头部至双脚)
 * - 'upper': 3/4 优雅站姿 (默认，包含头部、胸部、手臂及腰臀)
 * - 'portrait': 特写镜头 (专注于面部与表情)
 */
export function setCameraMode(camera: THREE.PerspectiveCamera, mode: CameraMode) {
  if (mode === 'full') {
    // 全身视角：在窄屏手机上完整呈现头顶到脚尖
    camera.position.set(0.0, 0.82, 3.25);
    camera.lookAt(0.0, 0.80, 0.0);
  } else if (mode === 'portrait') {
    // 特写视角：专注面部微笑与说话嘴型
    camera.position.set(0.0, 1.35, 1.35);
    camera.lookAt(0.0, 1.30, 0.0);
  } else {
    // 默认 upper (优雅半身)
    camera.position.set(0.0, 1.10, 2.20);
    camera.lookAt(0.0, 1.05, 0.0);
  }
}

