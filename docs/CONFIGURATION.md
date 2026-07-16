# TACZ VR 配置指南

TACZ VR 默认配置即为当前 HMD 实机验证值。首次安装建议直接使用默认值；只有在排查枪包差异、渲染冲突或服务器误判时再逐项调整。

## 配置文件

- 客户端：实例 `config/taczvr-client.toml`，只影响本机显示、手部反馈和轮盘操作。
- 服务端：世界目录 `serverconfig/taczvr-server.toml`，控制服务器接受 VR 射击姿态的安全范围。
- 多人游戏应在客户端与服务端安装同版本 TACZ VR。客户端关闭某项能力时会回退 TACZ 原行为；服务端拒绝异常姿态时只回退该发的方向或出膛点，不取消合法射击。

## 常用客户端设置

| 配置 | 默认值 | 用途 |
| --- | ---: | --- |
| `enableVrShotDirection` | `true` | 使用主手控制器方向射击 |
| `enableVrMuzzleOrigin` | `true` | 从模型枪口附近生成实体子弹 |
| `enableVrAimStabilization` | `true` | 对持枪方向应用速度自适应稳定 |
| `vrHipStabilizationStrength` | `0.55` | 普通持枪的方向与位置稳定强度 |
| `vrAdsStabilizationStrength` | `0.96` | TACZ 瞄准状态的强稳定强度 |
| `vrStabilizationReleaseSpeed` | `120` | 快速转向完全释放滤波的角速度阈值 |
| `vrHipStabilizationMaxPositionOffset` | `0.015` | 腰射位置最多落后真实手柄 1.5 cm |
| `vrAdsStabilizationMaxPositionOffset` | `0.04` | 瞄准位置最多落后真实手柄 4 cm |
| `vrStabilizationPositionReleaseSpeed` | `0.80` | 快速平移完全释放位置滤波的速度阈值 |
| `enableVrGunRendering` | `true` | 将 TACZ 第一人称枪模绑定到 VR 主手 |
| `vrHandDisplayMode` | `VIVECRAFT_SNAP` | 手部显示策略，可由持枪轮盘循环切换 |
| `hideTaczModelHands` | `true` | 普通持枪时隐藏 TACZ 模型手 |
| `postRenderVivecraftGunHand` | `true` | 避免复杂枪模遮住 Vivecraft 主手 |
| `enableVrSupportHandSnap` | `true` | Vivecraft 手可见时允许空副手靠近枪械握点吸附，可由持枪轮盘切换 |
| `enableVrShieldHandSnap` | `true` | Vivecraft 手可见时允许具备盾牌格挡能力的副手物品吸附，可由持枪轮盘独立切换 |
| `enableVrAnimationHandSnap` | `true` | 换弹、检视等动作动画驱动 VR 手 |
| `enableVrShootHandAnimation` | `true` | SHOOT 动画提供受限的主手反馈 |
| `decoupleVrTracerOffset` | `true` | 曳光视觉不再使用桌面相机偏移 |
| `correctVrMuzzleFlashPerEye` | `true` | 左右眼分别校正枪口火焰 |
| `hideVivecraftCrosshairWithGun` | `true` | 持枪时隐藏 Vivecraft 世界准心 |
| `disableTaczWorldScopeFov` | `true` | 防止 TACZ ADS 改变整个 VR 世界 FOV |
| `disableTaczCameraRecoil` | `true` | 防止枪械后坐力转动 HMD 视角 |
| `disableTaczLevelCameraAnimation` | `true` | 防止枪械 camera animation 转动 VR 世界 |
| `disableTaczSprintAnimationInVr` | `true` | 持枪奔跑时用 TACZ walk 状态代替 run 动画 |
| `enableHaptics` | `true` | 启用开火触觉反馈 |
| `enableVrGunRadialMenu` | `true` | 持枪时使用 TACZ 专用右手轮盘 |
| `reloadOnEmptyRadialRelease` | `true` | 未选择轮盘项目直接松开时换弹 |

## 枪模与手部微调

以下参数属于高级校准。默认值是默认枪包的统一基线，全局修改可能修好一把枪却让其他枪错位；第三方枪包差异应优先由后续 weapon profile 处理。

| 配置 | 默认值 | 说明 |
| --- | ---: | --- |
| `vrGunScaleMultiplier` | `1.0` | 枪模整体比例 |
| `vrGunGripOffsetX/Y/Z` | `0.49 / 0.52 / 0.52` | 主手局部坐标偏移 |
| `vrGunAimRotationX/Y/Z` | `0 / 0 / 180` | Bedrock 模型轴到控制器 forward 的旋转校正 |
| `vrSupportHandSnapEnterDistance` | `0.12` | 副手开始吸附距离，单位为方块 |
| `vrSupportHandSnapExitDistance` | `0.19` | 副手释放距离，较大的滞回避免闪烁 |
| `vrShootHandMaxTranslation` | `0.08` | SHOOT 动画可拉动主手的最大距离 |
| `vrAnimationHandMaxTranslation` | `1.25` | 一次性动画可拉动手部的安全上限 |
| `vrTracerHorizontalCorrection` | `-0.04` | 曳光相机局部水平校正 |
| `vrTracerVerticalCorrection` | `0.12` | 曳光相机局部垂直校正 |

其余 `Smoothing` 与 `GraceMs` 项控制吸附过渡。数值越接近 `1.0` 跟随越快；出现动画结束闪回时优先恢复默认值，而不是扩大位移上限。

`vrHandDisplayMode` 可选 `VIVECRAFT_SNAP`、`HIDDEN` 和 `TACZ_DURING_MANIPULATION`。第一模式显示 Vivecraft 双手，右手手掌持续跟随枪械 `righthand_pos` 而腕部朝向保持真实追踪。第二模式隐藏全部手臂。第三模式平时显示 Vivecraft 双手，换弹或检视轨道活跃时隐藏 Vivecraft 双手并显示 TACZ 左右手。第一、第三模式在 Vivecraft 手可见时统一服从空副手与盾牌两个独立吸附开关；其他副手物品保持真实追踪，第三模式切换 TACZ 双手时自动释放吸附。

## 服务端安全设置

| 配置 | 默认值 | 说明 |
| --- | ---: | --- |
| `enableVrShotDirection` | `true` | 接受通过验证的 VR 射击方向 |
| `enableVrMuzzleOrigin` | `true` | 接受通过验证的 VR 出膛点 |
| `requireServerVivecraftPose` | `true` | 必须取得 Vivecraft 服务端手姿态；缺失时该发回退 TACZ 原视角 |
| `maxServerPoseAngleDegrees` | `60` | 与 Vivecraft 服务端主手方向的最大夹角 |
| `maxFallbackViewAngleDegrees` | `150` | 仅在关闭上述安全要求后使用的兼容角度；无法认证真实控制器方向 |
| `maxMuzzleHandDistance` | `2.5` | 出膛点到服务端主手的最大距离 |
| `maxMuzzleEyeDistance` | `3.0` | 出膛点到玩家眼位的最大距离 |
| `checkMuzzleObstruction` | `true` | 眼位路径被墙阻挡时把出膛点夹紧到近侧墙面；仅主手路径异常时回退 |
| `shotPoseTtlTicks` | `10` | 未消费 shot pose 的保留 tick 数 |
| `maxPendingShotPoses` | `16` | 每名玩家最多保留的待匹配姿态数 |

不要通过大幅放宽距离或角度来适配单把异常武器。安全默认要求服务端 Vivecraft pose；服务器缺少 pose、数据迟到、重复或验证失败时，TACZ VR 会按该发回退到玩家视角与眼位。只有有限且距离合法的 muzzle 被墙截断时，才会保留墙前夹紧位置。

## 故障隔离

遇到问题时先只关闭对应模块：枪模问题关闭 `enableVrGunRendering`，手部问题关闭对应 snap，曳光问题关闭 `decoupleVrTracerOffset`，轮盘问题关闭 `enableVrGunRadialMenu`。不要一次删除全部配置，这样更容易定位与其他模组争用的具体作用面。
