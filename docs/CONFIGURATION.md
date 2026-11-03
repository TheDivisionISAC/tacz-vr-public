# TACZ VR 配置指南

版本 `0.1.0-beta.2` 的光影兼容开关只控制**已开启 shader pack**的适配。安装 Oculus/Iris 但未启用光影时，关闭兼容开关或非锁定光影加载器版本不会单独禁用普通镜内倍率；其他渲染器排除和回退检查仍保留。活跃光影继续要求锁定版本、Vivecraft `disableShaderOptimization=true` 与重载后的独立管线。

倍率因兼容性被拒绝时，默认日志会按原因在当前会话提示一次操作建议；hook/pipeline 等待持续五秒才提示，正常未持枪、无请求或菜单状态不刷屏。完整逐帧诊断仍需 JVM 属性 `-Dtaczvr.debugScopeMagnification=true`。断线或资源重载会重置提示记录。

首次安装建议使用默认配置；实验功能和待验收项在下文单独说明。排查枪包差异、渲染冲突或服务器误判时再逐项调整。

## 配置文件

- 客户端：实例 `config/taczvr-client.toml`，控制本机姿态稳定、显示、手部反馈和轮盘操作；射击姿态仍由服务端校验。
- 服务端：世界目录 `serverconfig/taczvr-server.toml`，控制服务器接受 VR 射击姿态的安全范围。
- 多人游戏应在客户端与服务端安装同版本 TACZ VR。客户端关闭某项能力时会回退 TACZ 原行为；服务端拒绝异常姿态时只回退该发的方向或出膛点，不取消合法射击。

## 常用客户端设置

| 配置 | 默认值 | 用途 |
| --- | ---: | --- |
| `enableVrShotDirection` | `true` | 使用主手控制器方向射击 |
| `enableVrMuzzleOrigin` | `true` | 从模型枪口附近生成实体子弹 |
| `enableVrAimStabilization` | `true` | 对持枪方向应用速度自适应稳定 |
| `enableVrMagnificationScaledStabilization` | `true` | 随所装光学镜当前倍率增强稳定，25 倍以上封顶；关闭后恢复原稳定曲线 |
| `enableVrViewRelativeStabilization` | `true` | 增加有上限的 HMD 相对完整姿态稳定，包括控制器 roll |
| `vrStabilizationLevel` | `50` | 轮盘“稳定”滑条基础强度，范围 `0-100`；再按光学倍率计算增强，`0` 仍关闭稳定 |
| `vrHipStabilizationStrength` | `0.55` | 普通持枪的方向与位置稳定强度 |
| `vrAdsStabilizationStrength` | `0.58` | TACZ 瞄准状态的稳定强度，仅略高于腰射 |
| `vrStabilizationReleaseSpeed` | `120` | 快速转向完全释放滤波的角速度阈值 |
| `vrHipStabilizationMaxPositionOffset` | `0.015` | 腰射位置最多落后真实手柄 1.5 cm |
| `vrAdsStabilizationMaxPositionOffset` | `0.017` | 瞄准位置最多落后真实手柄 1.7 cm |
| `vrStabilizationPositionReleaseSpeed` | `0.80` | 快速平移完全释放位置滤波的速度阈值 |
| `vrHipViewRotationMaxOffset` | `0.55` | 腰射渲染姿态相对 HMD 的最大角度校正，单位为度 |
| `vrAdsViewRotationMaxOffset` | `0.60` | TACZ 瞄准状态下渲染姿态相对 HMD 的最大角度校正，单位为度 |
| `vrHipViewPositionMaxOffset` | `0.004` | 腰射渲染姿态相对 HMD 的最大位置校正，单位为方块 |
| `vrAdsViewPositionMaxOffset` | `0.0045` | TACZ 瞄准状态下渲染姿态相对 HMD 的最大位置校正，单位为方块 |
| `enableVrGunRendering` | `true` | 将 TACZ 第一人称枪模绑定到 VR 主手 |
| `vrHandDisplayMode` | `VIVECRAFT_SNAP` | 手部显示策略，可由持枪轮盘循环切换 |
| `hideTaczModelHands` | `true` | 普通持枪时隐藏 TACZ 模型手 |
| `postRenderVivecraftGunHand` | `true` | 避免复杂枪模遮住 Vivecraft 主手 |
| `enableVrSupportHandSnap` | `true` | Vivecraft 手可见时允许空副手靠近枪械握点吸附，可由持枪轮盘切换 |
| `enableVrNearMainHandSupportSnap` | `false` | 无实体枪架时可选：持非手枪武器时，空左手靠近持枪右手即可触发该枪原生护木/握把锚点吸附；只扩展进入判定，不改变最终动画位置 |
| `enableVrShieldHandSnap` | `true` | Vivecraft 手可见时允许具备盾牌格挡能力的副手物品吸附，可由持枪轮盘独立切换 |
| `enableVrAnimationHandSnap` | `true` | 换弹、检视等动作动画驱动 VR 手 |
| `enableVrShootHandAnimation` | `true` | SHOOT 动画提供受限的主手反馈 |
| `decoupleVrTracerOffset` | `true` | 曳光视觉不再使用桌面相机偏移 |
| `correctVrMuzzleFlashPerEye` | `true` | 左右眼分别校正枪口火焰 |
| `hideVivecraftCrosshairWithGun` | `true` | 持枪时隐藏 Vivecraft 世界准心 |
| `enableVrPhysicalScopeGate` | `true` | 由当前渲染眼与光学镜目镜的物理对准独立控制本眼 TACZ 镜内开口；红点不变 |
| `enableVrScopeMagnification` | `true` | 要求物理目镜开关开启，在镜片内合成当前 TACZ 倍率的独立世界画面 |
| `enableVrScopeOculusCompat` | `true` | Oculus 1.8.0 适配入口；活动光影仍要求 Vivecraft 逐通道模式和有效独立管线 |
| `disableTaczWorldScopeFov` | `true` | 防止 TACZ ADS 改变整个 VR 世界 FOV |
| `disableTaczCameraRecoil` | `true` | 防止枪械后坐力转动 HMD 视角 |
| `disableTaczLevelCameraAnimation` | `true` | 防止枪械 camera animation 转动 VR 世界 |
| `disableTaczSprintAnimationInVr` | `true` | 持枪奔跑时用 TACZ walk 状态代替 run 动画 |
| `enableHaptics` | `true` | 启用开火触觉反馈 |
| `enableVrGunRadialMenu` | `true` | 持枪时使用 TACZ 专用右手轮盘 |
| `enableVrScopeZoomChord` | `true` | VR 中按住使用/瞄准键 A，再短按右手轮盘键 B，循环光学镜原生倍率档位 |
| `enableVrGunHotbarRetreat` | `true` | 持主手 TACZ 枪械时同步后移 Vivecraft 手部 HUD 与可触碰热栏 |
| `vrGunHotbarRetreatDistance` | `0.10` | HUD 与触碰面的固定物理后退距离，单位为方块 |
| `enableLrTacticalThrowableVrCompat` | `true` | 安装 LRTactical 时显示 VR 投掷物模型，并把主扳机桥接到其原生按住/释放流程 |
| `enableLrTacticalConsumableVrRendering` | `true` | 让 LRTactical 食物和药品使用已验收的手雷掌心对齐；不改变使用与治疗逻辑 |
| `enableLrTacticalMeleeVrRendering` | `true` | 让 LRTactical 近战武器使用已验收的手雷掌心对齐；不改变攻击判定 |
| `vrLrTacticalThrowableScale` | `0.60` | 手雷、消耗品和近战模型共用的 LRTactical 第一人称缩放基准 |

持枪轮盘的换弹按钮位于几何中心。在 Vivecraft 的按住式轮盘模式下，保持指针居中短按轮盘键即可换弹；关闭轮盘但未命中任何按钮时不会再自动换弹。

倍率稳定默认开启。它读取安装或内置光学镜的当前 TACZ 倍率，分别在现有腰射/ADS 基础强度与总滑条之后增强，不要求额外按 A，也不依赖镜片开合进度。无光学镜、纯红点、倍率不大于 1 或无效倍率保持原强度。示例：基础强度 0.55 时，4.25 倍约为 0.782，10 倍约为 0.858，25 倍及以上为 0.91。轮盘滑条数值本身不变。

增益公式为 `1 - (1 - 基础强度) / sqrt(min(倍率, 25))`，仅在基础强度大于 0 且倍率大于 1 时应用。总稳定关闭、滑条为 0 或本选项关闭时不会被重新开启。快速运动释放阈值、位置及 HMD 相对偏移限制保持原值；渲染和射击快照使用相同参数，切档时丢弃不匹配的旧强度快照。新手感仍需 HMD 实测。

使用默认 Oculus/Quest 绑定时，光学镜倍率组合键为 **按住 A＋短按 B**，每次 B 按下切换一档；仅切换瞄具已有的 TACZ 档位，不额外扩大镜外 FOV。组合键成功处理后不会打开轮盘或在松开 B 时触发换弹，单独 B 保留原轮盘行为。此功能读取“使用键＋右手轮盘键”动作，若自行改过 SteamVR 绑定，则随这两个动作的实际按键变化。固定倍率瞄具由 TACZ 决定是否有下一档。

`enableVrPhysicalScopeGate` 默认开启。LEFT 与 RIGHT 各自根据本眼到真实 `ocular` 模型节点的距离、横向偏差和夹角维护独立物理进度；当前眼的作者半径倍率只乘以本眼进度，不再乘 TACZ 的 ADS 开启进度，未对准的另一眼保持黑色目镜封闭。A 键仍可选择较强的稳定参数，但不再打开或关闭镜内遮罩。覆盖只存在于当前第一人称光学镜附件的渲染调用栈，不修改 TACZ 全局瞄准状态；非眼通道、无效样本或身份不匹配时安全封闭，离开 VR 或关闭开关时恢复 TACZ 原行为。代表性实测不等于所有枪包均通过，不能用放宽阈值代替逐眼验证。

为保护其他渲染模组的遮罩，实验性末端分划重放遇到已有 stencil 附件的目标时会跳过，保留普通附件渲染；这类组合中水体或云层仍可能覆盖分划及倍率画面。

Oculus 1.8.0 的 `enableVrScopeOculusCompat` 默认开启。活动光影仍要求 Vivecraft `disableShaderOptimization=true`，并重新加载光影或重启以创建独立管线；倍率只在每眼最终画面合成。附属不会自动改写 Vivecraft 的设置，因为逐通道模式会增加渲染开销。其他 Oculus 版本、OptiFine、AR 与 ARCANA 保持回退；默认开启不代表所有光影包/渲染组合已通过。

`enableVrScopeMagnification` 默认开启。与物理目镜同时开启后，为大于 1 倍的光学镜在有限眼箱预热范围内增加至多一次世界渲染；镜外 HMD 投影不变，开口渐变不改变倍率。版本/管线不支持、已有原生 SCOPER、注入点缺失、请求过期或身份不符仍跳过附属放大；适配异常锁存关闭，资源重载或退出世界后复位。

以上默认值适用于新配置或缺失键。已经存在的 `false` 值会保留，升级 JAR 不会强行覆盖用户关闭的选项；要启用旧实例需自行修改对应值。

实测可用 JVM 参数 `-Dtaczvr.debugScopeMagnification=true` 开启限频日志，分别记录调度拒绝原因/缺失注入点、世界画面完成状态和左右眼合成结果。该参数不负责开启功能。旧请求诊断只报告 `stage=capture`，不能据此判断是否已产生放大画面。NeoForge 同步与验证安排在 Forge 倍率完成之后。

倍率开启后保持原有镜片开口曲线与时机。接近眼箱、镜片尚未打开时会在有限距离/角度/模型几何范围内提前准备倍率纹理；原来的 acquisition 小开口一出现，就在相同 stencil 内合成倍率画面。不会等待纹理来延迟开镜。该过渡已获本轮 Forge 原生 HMD 确认；从预热范围外瞬间进入或切换瞄具的冷启动仍受上一帧请求架构限制，不能保证任意突变都没有一帧准备间隔。

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

`vrHandDisplayMode` 可选 `VIVECRAFT_SNAP`、`HIDDEN` 和 `TACZ_DURING_MANIPULATION`。第一模式显示 Vivecraft 双手，右手手掌持续跟随枪械 `righthand_pos` 而腕部朝向保持真实追踪；TACZ 左手只有换弹或检视轨道活跃时才强制跟随动画锚点，ADS 不触发强制吸附。第二模式隐藏全部手臂。第三模式平时显示 Vivecraft 双手，换弹或检视轨道活跃时隐藏 Vivecraft 双手并显示 TACZ 左右手。安装 LRTactical 且启用对应物品渲染兼容时，同一策略也用于投掷物、消耗品和近战武器；第三模式会读取其实际 TACZ 动画轨道，在 `draw`、`use`、`throw`、`melee`、`inspect` 或 `put_away` 等动作期间切换为模型自带双手，静止与移动循环不触发切换。在第一模式下，LRTactical 仅投掷物和药品的实际动作轨道会让左右 VR 手按检视方式跟随模型锚点；对齐器在静止帧记录稳定右掌基准，动作期间固定该基准，使物品和作者设计的左右手相对运动完整保留，而不会被逐帧反解抵消。静止状态、近战和移动循环不会强制吸附左手。第一、第三模式在 Vivecraft 手可见时统一服从空副手与盾牌两个独立吸附开关；其他副手物品保持真实追踪，第三模式切换模型双手时自动释放吸附。

## 服务端安全设置

| 配置 | 默认值 | 说明 |
| --- | ---: | --- |
| `enableVrShotDirection` | `true` | 接受通过验证的 VR 射击方向 |
| `enableVrMuzzleOrigin` | `true` | 接受通过验证的 VR 出膛点 |
| `enableVrPhysicalGripAccuracy` | `true` | VR 手枪始终使用 `AIM`（机瞄）精度；其他枪仅在左手实际吸附到护木/握点时使用机瞄精度，未吸附时按 TACZ 的腰射状态计算；启用物理目镜门控后，ADS 输入只保留为稳定模式选择 |
| `requireServerVivecraftPose` | `true` | 必须取得 Vivecraft 服务端手姿态；缺失时该发回退 TACZ 原视角 |
| `maxServerPoseAngleDegrees` | `60` | 与 Vivecraft 服务端主手方向的最大夹角 |
| `maxFallbackViewAngleDegrees` | `150` | 仅在关闭上述安全要求后使用的兼容角度；无法认证真实控制器方向 |
| `maxMuzzleHandDistance` | `2.5` | 出膛点到服务端主手的最大距离 |
| `maxMuzzleEyeDistance` | `3.0` | 出膛点到玩家眼位的最大距离 |
| `checkMuzzleObstruction` | `true` | 眼位路径被墙阻挡时把出膛点夹紧到近侧墙面；仅主手路径异常时回退 |
| `shotPoseTtlTicks` | `10` | 未消费 shot pose 的保留 tick 数 |
| `maxPendingShotPoses` | `16` | 每名玩家最多保留的待匹配姿态数 |
| `enableVrThrowDirection` | `true` | 安装 LRTactical 时接受通过验证的 VR 投掷方向；不接管初速、引信、冷却或物品扣除 |
| `throwPoseTtlTicks` | `4` | 每名玩家单个待消费 LRTactical 投掷方向的保留 tick 数 |

不要通过大幅放宽距离或角度来适配单把异常武器。安全默认要求服务端 Vivecraft pose；服务器缺少 pose、数据迟到、重复或验证失败时，TACZ VR 会按该发回退到玩家视角与眼位。只有有限且距离合法的 muzzle 被墙截断时，才会保留墙前夹紧位置。

## 故障隔离

遇到问题时先只关闭对应模块：枪模问题关闭 `enableVrGunRendering`，手部问题关闭对应 snap，稳定问题关闭 `enableVrAimStabilization` 或单独关闭 `enableVrViewRelativeStabilization`，热栏问题关闭 `enableVrGunHotbarRetreat`；LRTactical 手雷、消耗品和近战渲染分别关闭其对应开关；曳光问题关闭 `decoupleVrTracerOffset`，轮盘问题关闭 `enableVrGunRadialMenu`。不要一次删除全部配置，这样更容易定位与其他模组争用的具体作用面。
