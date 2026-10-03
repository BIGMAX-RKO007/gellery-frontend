import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { VRM, VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

export class VRMManager {
  private scene: THREE.Scene;
  public currentVrm: VRM | null = null;

  // 眨眼状态机
  private blinkTimer = 0;
  private blinkInterval = 3.0; // 眨眼间隔
  private isBlinking = false;
  private blinkProgress = 0;

  // 呼吸与待机微动
  private elapsedTime = 0;

  // 嘴型同步 (Lip Sync)
  private targetMouthVolume = 0;
  private currentMouthVolume = 0;

  // 情绪表情状态
  private currentExpression: string | null = null;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  public async loadModel(url: string, onProgress?: (ratio: number) => void): Promise<VRM> {
    const loader = new GLTFLoader();
    loader.register((parser) => new VRMLoaderPlugin(parser));

    return new Promise((resolve, reject) => {
      loader.load(
        url,
        (gltf) => {
          const vrm = gltf.userData.vrm as VRM;
          if (!vrm) {
            reject(new Error('未在模型文件中发现有效的 VRM 元数据'));
            return;
          }

          // 移除旧模型
          if (this.currentVrm) {
            this.scene.remove(this.currentVrm.scene);
            VRMUtils.deepDispose(this.currentVrm.scene);
          }

          this.currentVrm = vrm;

          // VRM 1.0 模型默认朝向正向相机 (+Z)
          vrm.scene.rotation.y = 0;

          // 关闭部分无关物理阴影，优化移动端性能
          VRMUtils.removeUnnecessaryVertices(gltf.scene);
          VRMUtils.combineSkeletons(gltf.scene);

          this.scene.add(vrm.scene);
          resolve(vrm);
        },
        (progress) => {
          if (progress.total > 0 && onProgress) {
            onProgress(progress.loaded / progress.total);
          }
        },
        (error) => reject(error),
      );
    });
  }

  /**
   * 触发说话嘴型 (0.0 ~ 1.0)
   */
  public speak(volume: number) {
    this.targetMouthVolume = Math.max(0, Math.min(1, volume));
  }

  /**
   * 设置面部表情 (happy, angry, sad, relaxed, surprised, neutral)
   */
  public setExpression(name: string) {
    if (!this.currentVrm || !this.currentVrm.expressionManager) return;

    // 清理之前的非眨眼/非发音表情
    const manager = this.currentVrm.expressionManager;
    const knownExpressions = ['happy', 'angry', 'sad', 'relaxed', 'surprised'];
    for (const exp of knownExpressions) {
      if (exp !== name) {
        manager.setValue(exp, 0);
      }
    }

    if (name !== 'neutral' && knownExpressions.includes(name)) {
      manager.setValue(name, 1.0);
      this.currentExpression = name;
    } else {
      this.currentExpression = null;
    }
  }

  public resetExpression() {
    this.setExpression('neutral');
  }

  public getCurrentExpression(): string | null {
    return this.currentExpression;
  }

  /**
   * 渲染循环帧更新
   */
  public update(deltaTime: number) {
    if (!this.currentVrm) return;

    this.elapsedTime += deltaTime;
    const manager = this.currentVrm.expressionManager;

    // 1. 自动眨眼系统 (自然的眨眼曲线)
    this.updateBlink(deltaTime);

    // 2. 嘴型平滑过渡 (Lip Sync)
    this.currentMouthVolume = THREE.MathUtils.lerp(
      this.currentMouthVolume,
      this.targetMouthVolume,
      Math.min(1.0, deltaTime * 20.0),
    );
    if (manager) {
      // VRM 标准元音：'aa' 或 'oh'
      manager.setValue('aa', this.currentMouthVolume);
      manager.setValue('oh', this.currentMouthVolume * 0.4);
    }

    // 3. 待机胸腔自然呼吸与脊椎轻微晃动
    this.updateIdleBreathing();

    // 4. VRM 内部弹簧骨骼与表情引擎更新
    this.currentVrm.update(deltaTime);
  }

  private updateBlink(deltaTime: number) {
    if (!this.currentVrm || !this.currentVrm.expressionManager) return;
    const manager = this.currentVrm.expressionManager;

    this.blinkTimer += deltaTime;
    if (!this.isBlinking && this.blinkTimer >= this.blinkInterval) {
      this.isBlinking = true;
      this.blinkTimer = 0;
      this.blinkProgress = 0;
      // 随机下一次眨眼间隔 (2.5 ~ 5.5 秒)
      this.blinkInterval = 2.5 + Math.random() * 3.0;
    }

    if (this.isBlinking) {
      this.blinkProgress += deltaTime * 8.0; // 眨眼速度，约 0.25 秒完成一次闭合与睁开
      if (this.blinkProgress >= Math.PI) {
        this.isBlinking = false;
        manager.setValue('blink', 0.0);
      } else {
        // 使用正弦半波平滑计算眼皮开合度 (0 -> 1 -> 0)
        const blinkWeight = Math.sin(this.blinkProgress);
        manager.setValue('blink', blinkWeight);
      }
    }
  }

  private updateIdleBreathing() {
    if (!this.currentVrm || !this.currentVrm.humanoid) return;

    const chest = this.currentVrm.humanoid.getNormalizedBoneNode('chest');
    const spine = this.currentVrm.humanoid.getNormalizedBoneNode('spine');
    const head = this.currentVrm.humanoid.getNormalizedBoneNode('head');

    // 呼吸频率约每分钟 18 次 (频率 = 2 * PI * (18 / 60) ≈ 1.88)
    const breathCycle = Math.sin(this.elapsedTime * 1.8);

    if (chest) {
      chest.rotation.x = breathCycle * 0.015;
    }
    if (spine) {
      spine.rotation.x = breathCycle * 0.008;
    }
    if (head) {
      // 头部细微跟随微晃
      head.rotation.y = Math.sin(this.elapsedTime * 0.8) * 0.02;
      head.rotation.z = Math.cos(this.elapsedTime * 0.9) * 0.01;
    }
  }
}
