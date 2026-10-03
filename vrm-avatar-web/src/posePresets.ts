/**
 * 25 个精选动漫/速写参考动作姿态定义表。
 * 每个姿态包含根骨骼（hips）的高度位移、旋转以及关键人形骨骼的欧拉角 (弧度)。
 */
export interface PoseDefinition {
  id: number;
  name: string;
  hipsPos?: [number, number, number];
  hipsRot?: [number, number, number];
  bones: Record<string, [number, number, number]>;
}

// 默认自然站姿 (Pose 0)
export const DEFAULT_POSE: PoseDefinition = {
  id: 0,
  name: '自然站姿',
  hipsPos: [0, 0, 0],
  hipsRot: [0, 0, 0],
  bones: {
    leftUpperArm: [0.1, 0.0, -1.22],
    rightUpperArm: [0.1, 0.0, 1.22],
    leftLowerArm: [0.0, 0.25, 0.0],
    rightLowerArm: [0.0, -0.25, 0.0],
    spine: [0, 0, 0],
    chest: [0, 0, 0],
    head: [0, 0, 0],
    leftUpperLeg: [0, 0, 0],
    rightUpperLeg: [0, 0, 0],
    leftLowerLeg: [0, 0, 0],
    rightLowerLeg: [0, 0, 0],
  },
};

export const POSE_PRESETS: Record<number, PoseDefinition> = {
  // 1: 跪坐仰首
  1: {
    id: 1,
    name: '跪坐仰首',
    hipsPos: [0, -0.48, 0],
    bones: {
      leftUpperLeg: [-1.4, 0, 0],
      leftLowerLeg: [2.5, 0, 0],
      rightUpperLeg: [-1.4, 0, 0],
      rightLowerLeg: [2.5, 0, 0],
      spine: [-0.2, 0, 0],
      head: [-0.4, 0, 0],
      leftUpperArm: [0.1, 0, -1.15],
      rightUpperArm: [0.1, 0, 1.15],
    },
  },
  // 2: 跪姿后视翘臀
  2: {
    id: 2,
    name: '猫式后视',
    hipsPos: [0, -0.25, 0.1],
    bones: {
      leftUpperLeg: [-1.6, 0.1, 0],
      leftLowerLeg: [1.8, 0, 0],
      rightUpperLeg: [-1.6, -0.1, 0],
      rightLowerLeg: [1.8, 0, 0],
      spine: [0.3, 0.4, 0],
      head: [0.1, 0.8, 0],
      leftUpperArm: [-0.9, 0, -0.5],
      rightUpperArm: [-0.9, 0, 0.5],
    },
  },
  // 3: 规整跪坐微前倾
  3: {
    id: 3,
    name: '跪坐前倾',
    hipsPos: [0, -0.48, 0],
    bones: {
      leftUpperLeg: [-1.4, 0, 0],
      leftLowerLeg: [2.5, 0, 0],
      rightUpperLeg: [-1.4, 0, 0],
      rightLowerLeg: [2.5, 0, 0],
      spine: [0.2, 0, 0],
      chest: [0.1, 0, 0],
      leftUpperArm: [0.2, 0, -0.9],
      rightUpperArm: [0.2, 0, 0.9],
    },
  },
  // 4: 爬行前探
  4: {
    id: 4,
    name: '爬行前探',
    hipsPos: [0, -0.3, 0],
    bones: {
      leftUpperLeg: [-1.7, 0, 0],
      leftLowerLeg: [1.6, 0, 0],
      rightUpperLeg: [-1.3, 0, 0],
      rightLowerLeg: [1.9, 0, 0],
      spine: [0.35, 0, 0],
      head: [-0.3, 0, 0],
      leftUpperArm: [-1.1, 0, -0.3],
      rightUpperArm: [-1.1, 0, 0.3],
    },
  },
  // 5: 趴卧翘脚托腮
  5: {
    id: 5,
    name: '趴卧托腮',
    hipsPos: [0, -0.65, 0],
    hipsRot: [-1.4, 0, 0],
    bones: {
      leftLowerLeg: [-2.0, 0, 0],
      rightLowerLeg: [-2.0, 0, 0],
      spine: [-0.4, 0, 0],
      head: [0.3, 0, 0],
      leftUpperArm: [-1.3, 0.2, -0.4],
      rightUpperArm: [-1.3, -0.2, 0.4],
      leftLowerArm: [0, 0, 1.3],
      rightLowerArm: [0, 0, -1.3],
    },
  },
  // 6: 侧卧侧靠
  6: {
    id: 6,
    name: '侧卧靠臂',
    hipsPos: [0, -0.6, 0],
    hipsRot: [0, 0, -1.4],
    bones: {
      spine: [0, 0, 0.3],
      leftUpperArm: [0, 0, -1.1],
      leftLowerArm: [0, 0, 1.2],
    },
  },
  // 7: 侧坐伸腿
  7: {
    id: 7,
    name: '侧坐伸腿',
    hipsPos: [0, -0.5, 0],
    bones: {
      leftUpperLeg: [-1.3, 0.3, 0.5],
      leftLowerLeg: [1.8, 0, 0],
      rightUpperLeg: [-1.2, -0.3, -0.5],
      rightLowerLeg: [2.1, 0, 0],
      spine: [0, 0.2, -0.1],
      leftUpperArm: [0.2, 0, -1.1],
      rightUpperArm: [-0.3, 0, 1.2],
    },
  },
  // 8: 低姿俯趴
  8: {
    id: 8,
    name: '低姿俯趴',
    hipsPos: [0, -0.28, 0.1],
    bones: {
      leftUpperLeg: [-1.7, 0, 0],
      leftLowerLeg: [1.6, 0, 0],
      rightUpperLeg: [-1.7, 0, 0],
      rightLowerLeg: [1.6, 0, 0],
      spine: [0.45, 0, 0],
      head: [-0.35, 0, 0],
      leftUpperArm: [-1.2, 0, -0.3],
      rightUpperArm: [-1.2, 0, 0.3],
    },
  },
  // 9: 跪坐膝间垂手
  9: {
    id: 9,
    name: '跪坐并手',
    hipsPos: [0, -0.48, 0],
    bones: {
      leftUpperLeg: [-1.4, 0.2, 0],
      leftLowerLeg: [2.5, 0, 0],
      rightUpperLeg: [-1.4, -0.2, 0],
      rightLowerLeg: [2.5, 0, 0],
      spine: [0.25, 0, 0],
      leftUpperArm: [0.2, 0, -0.5],
      rightUpperArm: [0.2, 0, 0.5],
    },
  },
  // 10: 屈膝斜坐
  10: {
    id: 10,
    name: '屈膝斜坐',
    hipsPos: [0, -0.52, 0],
    bones: {
      leftUpperLeg: [-1.5, 0.3, 0],
      leftLowerLeg: [1.9, 0, 0],
      rightUpperLeg: [-0.7, -0.2, 0],
      rightLowerLeg: [1.3, 0, 0],
      spine: [0.1, -0.2, 0],
      leftUpperArm: [0.2, 0, -1.0],
      rightUpperArm: [0.4, 0, 0.8],
    },
  },
  // 11: 拱背猫爬
  11: {
    id: 11,
    name: '拱背猫爬',
    hipsPos: [0, -0.25, 0.05],
    bones: {
      leftUpperLeg: [-1.6, 0, 0],
      leftLowerLeg: [1.7, 0, 0],
      rightUpperLeg: [-1.6, 0, 0],
      rightLowerLeg: [1.7, 0, 0],
      spine: [0.4, 0, 0],
      head: [0.2, 0, 0],
      leftUpperArm: [-1.2, 0, -0.4],
      rightUpperArm: [-1.2, 0, 0.4],
    },
  },
  // 12: 盘腿手撩发
  12: {
    id: 12,
    name: '盘坐撩发',
    hipsPos: [0, -0.52, 0],
    bones: {
      leftUpperLeg: [-1.3, 0.5, 0.5],
      leftLowerLeg: [1.9, 0, 0],
      rightUpperLeg: [-1.3, -0.5, -0.5],
      rightLowerLeg: [1.9, 0, 0],
      spine: [-0.05, 0.1, 0],
      leftUpperArm: [0.1, 0, -1.1],
      rightUpperArm: [-0.2, 0, 2.3],
      rightLowerArm: [0, 0, -1.8],
    },
  },
  // 13: 俯卧交叠双腿托腮
  13: {
    id: 13,
    name: '翘腿托腮',
    hipsPos: [0, -0.65, 0],
    hipsRot: [-1.4, 0, 0],
    bones: {
      leftLowerLeg: [-2.1, 0.2, 0],
      rightLowerLeg: [-2.1, -0.2, 0],
      spine: [-0.4, 0, 0],
      head: [0.35, 0, 0],
      leftUpperArm: [-1.3, 0.3, -0.3],
      rightUpperArm: [-1.3, -0.3, 0.3],
    },
  },
  // 14: 端庄正坐
  14: {
    id: 14,
    name: '端庄正坐',
    hipsPos: [0, -0.48, 0],
    bones: {
      leftUpperLeg: [-1.45, 0, 0],
      leftLowerLeg: [2.5, 0, 0],
      rightUpperLeg: [-1.45, 0, 0],
      rightLowerLeg: [2.5, 0, 0],
      spine: [0.05, 0, 0],
      head: [-0.05, 0, 0],
      leftUpperArm: [0.15, 0, -0.8],
      rightUpperArm: [0.15, 0, 0.8],
    },
  },
  // 15: 俯卧伸展式
  15: {
    id: 15,
    name: '俯卧伸展',
    hipsPos: [0, -0.35, -0.1],
    bones: {
      leftUpperLeg: [-1.8, 0, 0],
      leftLowerLeg: [2.3, 0, 0],
      rightUpperLeg: [-1.8, 0, 0],
      rightLowerLeg: [2.3, 0, 0],
      spine: [0.4, 0, 0],
      head: [0.2, 0, 0],
      leftUpperArm: [-2.4, 0, -0.2],
      rightUpperArm: [-2.4, 0, 0.2],
    },
  },
  // 16: 抱膝微缩侧坐
  16: {
    id: 16,
    name: '抱膝侧坐',
    hipsPos: [0, -0.5, 0],
    bones: {
      leftUpperLeg: [-1.6, 0.2, 0.2],
      leftLowerLeg: [2.2, 0, 0],
      rightUpperLeg: [-1.6, -0.2, -0.2],
      rightLowerLeg: [2.2, 0, 0],
      spine: [0.3, 0.2, 0],
      head: [0.2, 0, 0],
      leftUpperArm: [0.4, 0, -0.7],
      rightUpperArm: [0.4, 0, 0.7],
    },
  },
  // 17: 跪坐侧身回眸
  17: {
    id: 17,
    name: '回眸跪坐',
    hipsPos: [0, -0.48, 0],
    bones: {
      leftUpperLeg: [-1.4, 0, 0],
      leftLowerLeg: [2.4, 0, 0],
      rightUpperLeg: [-1.4, 0, 0],
      rightLowerLeg: [2.4, 0, 0],
      spine: [0.1, 1.2, 0],
      head: [0, 0.8, 0],
      leftUpperArm: [0.1, 0, -1.0],
      rightUpperArm: [0.1, 0, 1.0],
    },
  },
  // 18: 仰卧抱头
  18: {
    id: 18,
    name: '仰卧抱头',
    hipsPos: [0, -0.65, 0],
    hipsRot: [1.4, 0, 0],
    bones: {
      leftUpperLeg: [0.8, 0, 0],
      leftLowerLeg: [-1.2, 0, 0],
      rightUpperLeg: [0.8, 0, 0],
      rightLowerLeg: [-1.2, 0, 0],
      leftUpperArm: [0.8, 0, -2.1],
      rightUpperArm: [0.8, 0, 2.1],
    },
  },
  // 19: 深俯跪伏式
  19: {
    id: 19,
    name: '深俯跪伏',
    hipsPos: [0, -0.28, 0.15],
    bones: {
      leftUpperLeg: [-1.65, 0, 0],
      leftLowerLeg: [1.7, 0, 0],
      rightUpperLeg: [-1.65, 0, 0],
      rightLowerLeg: [1.7, 0, 0],
      spine: [0.55, 0, 0],
      head: [0.3, 0, 0],
      leftUpperArm: [0.4, 0, -0.5],
      rightUpperArm: [0.4, 0, 0.5],
    },
  },
  // 20: 抱胸屈膝坐
  20: {
    id: 20,
    name: '抱胸屈坐',
    hipsPos: [0, -0.54, 0],
    bones: {
      leftUpperLeg: [-1.4, 0, 0],
      leftLowerLeg: [1.5, 0, 0],
      rightUpperLeg: [-1.4, 0, 0],
      rightLowerLeg: [1.5, 0, 0],
      spine: [0.15, 0, 0],
      leftUpperArm: [0.5, 0, -0.6],
      leftLowerArm: [0, 0, 1.5],
      rightUpperArm: [0.5, 0, 0.6],
      rightLowerArm: [0, 0, -1.5],
    },
  },
  // 21: 侧身枕臂安睡
  21: {
    id: 21,
    name: '侧身枕臂',
    hipsPos: [0, -0.65, 0],
    hipsRot: [0, 0, 1.45],
    bones: {
      leftUpperLeg: [0.5, 0, 0],
      leftLowerLeg: [-1.1, 0, 0],
      rightUpperLeg: [0.4, 0, 0],
      rightLowerLeg: [-0.9, 0, 0],
      rightUpperArm: [0.5, 0, 2.2],
    },
  },
  // 22: 四肢侧跪探身
  22: {
    id: 22,
    name: '侧跪探身',
    hipsPos: [0, -0.32, 0],
    bones: {
      leftUpperLeg: [-1.5, 0.3, 0],
      leftLowerLeg: [1.7, 0, 0],
      rightUpperLeg: [-1.3, -0.2, 0],
      rightLowerLeg: [1.8, 0, 0],
      spine: [0.25, 0.4, 0],
      leftUpperArm: [-1.0, 0, -0.4],
      rightUpperArm: [-0.9, 0, 0.5],
    },
  },
  // 23: 鸭子坐前趴
  23: {
    id: 23,
    name: '鸭坐撑地',
    hipsPos: [0, -0.5, 0],
    bones: {
      leftUpperLeg: [-1.4, 0.6, 0.4],
      leftLowerLeg: [2.4, 0, -0.3],
      rightUpperLeg: [-1.4, -0.6, -0.4],
      rightLowerLeg: [2.4, 0, 0.3],
      spine: [0.25, 0, 0],
      leftUpperArm: [0.2, 0, -0.45],
      rightUpperArm: [0.2, 0, 0.45],
    },
  },
  // 24: 侧仰抬腿
  24: {
    id: 24,
    name: '侧仰抬腿',
    hipsPos: [0, -0.6, 0],
    hipsRot: [0.8, 0, 1.2],
    bones: {
      leftUpperLeg: [-0.9, 0, 0],
      leftLowerLeg: [1.2, 0, 0],
      rightUpperLeg: [-1.2, 0, 0],
      rightLowerLeg: [0.8, 0, 0],
    },
  },
  // 25: 贵妇侧卧
  25: {
    id: 25,
    name: '优雅侧卧',
    hipsPos: [0, -0.6, 0],
    hipsRot: [0, 0, -1.35],
    bones: {
      leftUpperLeg: [0.4, 0, 0],
      leftLowerLeg: [-0.9, 0, 0],
      rightUpperLeg: [0.1, 0, 0],
      rightLowerLeg: [-0.5, 0, 0],
      leftUpperArm: [-0.8, 0, -0.6],
      leftLowerArm: [0, 0, 1.1],
    },
  },
};
