import * as THREE from 'three';

export interface SceneContext {
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  renderer: THREE.WebGLRenderer;
  clock: THREE.Clock;
}

export function createScene(container: HTMLElement): SceneContext {
  const scene = new THREE.Scene();

  const aspect = container.clientWidth / container.clientHeight;
  const camera = new THREE.PerspectiveCamera(30.0, aspect, 0.1, 20.0);
  // 相机聚焦于数字人上半身/胸部上方视线，VRM 原点通常在脚底，人高约 1.5m，面部约在 1.35m
  camera.position.set(0.0, 1.35, 1.25);
  camera.lookAt(0.0, 1.30, 0.0);

  const renderer = new THREE.WebGLRenderer({
    alpha: true,
    antialias: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(container.clientWidth, container.clientHeight);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.0;
  container.appendChild(renderer.domElement);

  // 灯光配置：柔和主光 + 环境光 + 轮廓背光
  const ambientLight = new THREE.AmbientLight(0xffffff, 1.2);
  scene.add(ambientLight);

  const keyLight = new THREE.DirectionalLight(0xfff6ea, 1.5);
  keyLight.position.set(1.0, 2.0, 1.5).normalize();
  scene.add(keyLight);

  const fillLight = new THREE.DirectionalLight(0xdde9ff, 0.8);
  fillLight.position.set(-1.0, 1.5, 1.0).normalize();
  scene.add(fillLight);

  const backLight = new THREE.DirectionalLight(0xffffff, 0.6);
  backLight.position.set(0.0, 2.0, -1.5).normalize();
  scene.add(backLight);

  const clock = new THREE.Clock();

  // 窗口自适应
  window.addEventListener('resize', () => {
    const width = container.clientWidth;
    const height = container.clientHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
  });

  return { scene, camera, renderer, clock };
}
