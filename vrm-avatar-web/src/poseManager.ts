import * as THREE from 'three';
import { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';
import { DEFAULT_POSE, POSE_PRESETS } from './posePresets';

/**
 * VRM 3D 姿态控制与平滑插值引擎。
 * 负责在不同的身体姿态之间平滑插值 (Damping)，驱动 VRM 人形骨骼 (Humanoid)。
 */
export class PoseManager {
  private vrm: VRM | null = null;
  private currentPoseId: number = 0;

  // 记录所有骨骼当前的实际旋转值与 Hips 位置，供每帧平滑插值使用
  private currentBoneRotations: Map<string, THREE.Euler> = new Map();
  private targetBoneRotations: Map<string, THREE.Euler> = new Map();

  private currentHipsPos = new THREE.Vector3(0, 0, 0);
  private targetHipsPos = new THREE.Vector3(0, 0, 0);
  private initialHipsPosition = new THREE.Vector3(0, 0, 0);

  private currentHipsRot = new THREE.Euler(0, 0, 0);
  private targetHipsRot = new THREE.Euler(0, 0, 0);

  public attachVRM(vrm: VRM) {
    this.vrm = vrm;
    const hipsNode = vrm.humanoid?.getNormalizedBoneNode('hips');
    if (hipsNode) {
      this.initialHipsPosition.copy(hipsNode.position);
    }
    this.resetToDefault();
  }

  public setPose(id: number) {
    this.currentPoseId = id;
    const pose = POSE_PRESETS[id] || DEFAULT_POSE;

    // 设置目标 Hips 位移
    if (pose.hipsPos) {
      this.targetHipsPos.set(pose.hipsPos[0], pose.hipsPos[1], pose.hipsPos[2]);
    } else {
      this.targetHipsPos.set(0, 0, 0);
    }

    // 设置目标 Hips 旋转
    if (pose.hipsRot) {
      this.targetHipsRot.set(pose.hipsRot[0], pose.hipsRot[1], pose.hipsRot[2]);
    } else {
      this.targetHipsRot.set(0, 0, 0);
    }

    // 默认骨骼姿态作为基准
    const mergedBones = { ...DEFAULT_POSE.bones, ...(pose.bones || {}) };

    // 更新各骨骼目标值
    for (const [boneName, rot] of Object.entries(mergedBones)) {
      let targetEuler = this.targetBoneRotations.get(boneName);
      if (!targetEuler) {
        targetEuler = new THREE.Euler();
        this.targetBoneRotations.set(boneName, targetEuler);
      }
      targetEuler.set(rot[0], rot[1], rot[2]);

      if (!this.currentBoneRotations.has(boneName)) {
        this.currentBoneRotations.set(boneName, new THREE.Euler(rot[0], rot[1], rot[2]));
      }
    }
  }

  public resetToDefault() {
    this.setPose(0);
  }

  public getCurrentPoseId(): number {
    return this.currentPoseId;
  }

  /**
   * 渲染循环帧更新，执行骨骼插值过渡
   */
  public update(deltaTime: number) {
    if (!this.vrm || !this.vrm.humanoid) return;
    const humanoid = this.vrm.humanoid;

    // 插值平滑速度系数 (约 0.35 秒完成过渡)
    const dampAlpha = Math.min(1.0, deltaTime * 8.0);

    // 1. 平滑更新 Hips 根骨骼位移 (基于初始高度做相对偏移)
    this.currentHipsPos.lerp(this.targetHipsPos, dampAlpha);
    const hipsNode = humanoid.getNormalizedBoneNode('hips');
    if (hipsNode) {
      hipsNode.position.copy(this.initialHipsPosition).add(this.currentHipsPos);

      // Hips 旋转插值
      this.currentHipsRot.x = THREE.MathUtils.lerp(this.currentHipsRot.x, this.targetHipsRot.x, dampAlpha);
      this.currentHipsRot.y = THREE.MathUtils.lerp(this.currentHipsRot.y, this.targetHipsRot.y, dampAlpha);
      this.currentHipsRot.z = THREE.MathUtils.lerp(this.currentHipsRot.z, this.targetHipsRot.z, dampAlpha);
      hipsNode.rotation.copy(this.currentHipsRot);
    }

    // 2. 平滑更新所有人形骨骼旋转
    for (const [boneName, targetEuler] of this.targetBoneRotations.entries()) {
      let curEuler = this.currentBoneRotations.get(boneName);
      if (!curEuler) {
        curEuler = new THREE.Euler();
        this.currentBoneRotations.set(boneName, curEuler);
      }

      curEuler.x = THREE.MathUtils.lerp(curEuler.x, targetEuler.x, dampAlpha);
      curEuler.y = THREE.MathUtils.lerp(curEuler.y, targetEuler.y, dampAlpha);
      curEuler.z = THREE.MathUtils.lerp(curEuler.z, targetEuler.z, dampAlpha);

      const boneNode = humanoid.getNormalizedBoneNode(boneName as VRMHumanBoneName);
      if (boneNode) {
        boneNode.rotation.copy(curEuler);
      }
    }
  }
}
