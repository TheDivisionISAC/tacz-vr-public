# TACZ VR

TACZ VR 是面向 Minecraft Forge 1.20.1 的 VR 兼容附属模组，让
[Timeless and Classics Guns: Zero](https://github.com/MCModderAnchor/TACZ) 能够在
[Vivecraft](https://github.com/Vivecraft/VivecraftMod) 中以手柄姿态完成持枪、瞄准和射击。

项目采用独立附属模组形式交付，不替换 TACZ 或 Vivecraft 的 JAR，也不要求额外安装手柄控制模组。
当前版本为 `0.1.0-alpha.7`，已完成默认枪包、专用服务器和 HMD 实机验证。

作者与维护方：[TheDivisionISAC](https://github.com/TheDivisionISAC)。公开源码：
[TheDivisionISAC/tacz-vr-public](https://github.com/TheDivisionISAC/tacz-vr-public)。

## 当前功能

- 第一人称枪模由主手控制器驱动，常见手枪、步枪和机枪保持一致的指向基准。
- 提供三种手部显示模式：Vivecraft 双手吸附、全部隐藏、平时显示 Vivecraft 双手并在换弹/检视时切换为 TACZ 双手。
- 第一模式下 Vivecraft 右手手掌持续贴合枪械 `righthand_pos`，避免动画结束后的回零下落；腕部朝向仍由真实手柄控制。第一、第三模式显示 Vivecraft 手时，轮盘可分别开关空副手与盾牌的近距离握点吸附，其他副手物品不会吸附。射击、换弹、检视和其他一次性枪械动画仍可驱动手部位置。
- 腰射使用受限的轻度方向/位置稳定，TACZ 瞄准状态使用更强六自由度稳定；快速转向或平移会自动释放滤波，枪模不会脱离手柄超过配置上限。
- 使用控制器方向计算弹道，并在服务端校验射击姿态。
- 使用枪械模型的枪口信息修正实体子弹出膛点，同时保留 TACZ 原有的散布、弹药和 Lua 逻辑。
- 曳光视觉使用独立 VR 校正，不再借用桌面相机偏移；枪口火焰按左右眼分别刷新姿态。
- 隔离会导致 VR 不适或画面错误的视场角、摄像机后坐力和摄像机动画。
- 射击时提供 Vivecraft 手柄触觉反馈。
- 手持 TACZ 武器时扩展 Vivecraft 右手轮盘，可执行换弹、检视、切换射击模式、配件操作、手部模式切换和丢弃。
- VR 持枪时不播放 TACZ 奔跑动画。
- 手持 TACZ 武器时隐藏 Vivecraft 世界空间准心。
- 原生无倍率 `SIGHT` 红点瞄具可以直接使用，不需要单独适配。

## 兼容范围

| 组件 | 当前基线 |
| --- | --- |
| Minecraft | `1.20.1` |
| Forge | 最低 `47.3.19`，已在 `47.4.21` 实际环境验证启动 |
| TACZ | `1.1.8-hotfix` |
| Vivecraft | `1.20.1-1.3.13-forge` |
| Java | `17` |

射击方向和出膛点包含服务端校验，因此多人游戏的客户端与服务端都应安装相同版本的 TACZ VR。
单人游戏只需像普通 Forge 模组一样安装到当前实例。

## 安装

在 Forge 1.20.1 实例的 `mods` 目录中安装以下模组：

1. TACZ `1.1.8-hotfix`
2. Vivecraft `1.20.1-1.3.13-forge`
3. TACZ VR 对应版本 JAR
4. TACZ 与 Vivecraft 各自声明的必要依赖

TACZ VR 不包含 TACZ 或 Vivecraft 的代码副本。不要同时安装早期直接修改 TACZ 本体的实验版本。

## 操作

手持 TACZ 枪械后，主手控制器决定枪口指向和实际射击方向。TACZ 的开火按键、弹药状态和射击模式仍由原有系统管理。

打开 Vivecraft 右手轮盘可以使用 TACZ 专用操作。轮盘开启后未选中其他操作而直接释放时，默认执行换弹；丢弃位于较难误触的位置。“空手吸附”和“盾牌吸附”按钮可分别控制第一模式的对应握点跟随。
“手部”按钮会按“VR 吸附 -> 全部隐藏 -> 动作时 TACZ 双手”的顺序循环，并写入本机客户端配置。第一、第三模式只要正在显示 Vivecraft 手，就统一服从“空手吸附”和“盾牌吸附”开关。第三模式在换弹或检视轨道开始后隐藏 Vivecraft 双手、显示 TACZ 左右手并自动释放副手吸附，动作结束后恢复 Vivecraft 双手和对应开关策略。

## 当前限制

- 变倍镜的镜内放大与独立渲染尚未完成。
- 当前副手吸附只负责视觉与持握反馈，不参与双手弹道解算或瞄准状态判定。
- 当前稳定功能是有位移上限的方向/位置阻尼，不模拟实体枪托、肩部接触或双手长枪约束。
- 虚拟枪托、物理换弹和枪体物理后坐力属于后续阶段；当前 SHOOT 仅同步 TACZ 已有动画位移。
- 不同枪包的枪族识别、机械结构和交互式动画需要在取得具体枪包后建立兼容配置。
- Vivecraft 可触碰热栏后移仍是未完整的 P2 实验功能，已移出 P1 构建；当前应使用 Vivecraft 自身的热栏设置规避持枪误触。
- 当前已验证 Oculus、Embeddium、Accelerated Rendering、Super Resolution 与 Sundial-Lite 光影的组合；这些上游渲染模组仍会输出彼此修改 Embeddium 的警告。

## 文档

- [配置指南](docs/CONFIGURATION.md)
- [基础架构](docs/ARCHITECTURE.md)

## 开发

项目使用独立 ForgeGradle 工程，并通过 `compileOnly` 和开发运行依赖接入 TACZ 与 Vivecraft。常用离线构建命令为：

```powershell
.\gradlew.bat clean build reobfJar --offline --no-daemon
```

`check` 会运行网络编解码测试、姿态与滤波测试、源码注释门禁、Mixin 清单/侧别检查和生产 JAR 命名空间检查。无 Vivecraft 客户端会跳过全部 VR 客户端注入；专服缺少 Vivecraft 服务端手姿态时，默认按发回退 TACZ 原视角。

## 许可证

TACZ VR 使用 [MIT License](LICENSE)。允许使用、修改和再发布，但必须保留原版权与许可证声明。

TACZ、Vivecraft 及相关资源分别受其自身许可证约束；本项目不改变这些上游项目的授权方式。
