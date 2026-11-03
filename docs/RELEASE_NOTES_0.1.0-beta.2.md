# TACZ VR 0.1.0-beta.2

两个独立测试版本，选择与 Minecraft 和加载器相符的一个安装。本版本已在本地编译，尚未上传公共发布渠道。

| 版本 | 构建基线 | 本轮生产端 | Java | TACZ | Vivecraft |
| --- | --- | --- | --- | --- | --- |
| Forge 1.20.1 | Forge 47.3.19 | Forge 47.4.21 | 17 | 1.1.8-hotfix | 1.20.1-1.3.15-forge |
| NeoForge 1.21.1 | NeoForge 21.1.233 | NeoForge 21.1.236 | 21 | NeoForge 1.1.8-hotfix-r4 迁移版 | 1.21.1-1.3.15-neoforge |

NeoForge 依赖指定 TACZ 迁移构建，普通 Forge TACZ JAR 不能替代。附属 JAR 不包含 TACZ、Vivecraft 或第三方枪包；Neo 依赖文件摘要和源码构建准备见 BUILDING_BETA2.md。

## 安装与使用

退出游戏，将对应平台的 TACZ VR JAR 放入实例 mods 目录，替换旧的 TACZ VR JAR，避免同时放入两个附属版本。保留对应版本的 TACZ 和 Vivecraft。不要将 Forge 和 NeoForge 两个包混装。

已有配置中的 false 会保留。需要体验新光学功能时，检查 config/taczvr-client.toml 中 enableVrPhysicalScopeGate、enableVrScopeMagnification、enableVrMagnificationScaledStabilization 和 enableVrScopeZoomChord 是否开启。

瞄准状态下，按住使用键再按右手轮盘键切档；Oculus 默认绑定为 A+B，其他控制器以实际绑定为准。固定倍率镜不发无效切档请求，也不触发轮盘换弹；多倍率或不同瞄准视图仍可切换。稳定滑条为0时保持关闭，倍率增强在25倍封顶，快速运动仍按原规则释放。

## 本次内容

- 镜内倍率、每眼物理目镜及闭镜预热。
- 修复雨天/远处透明像素漏出无倍率背景。
- 修复 NeoForge 眼睛坐标与圆形遮罩在不同朝向下不一致的问题。
- 修复固定倍率镜切档产生无效物品同步。
- 连射遇到同槽位、同种枪的物品对象同步替换时，在客户端线程重新取得当前姿态与枪口，保留包顺序及服务端验证。
- 无光影时不因 Oculus/Iris 的兼容开关或版本限制而禁用普通倍率；默认日志提示兼容拒绝原因。

## 验证范围

Forge 原生无光影 HMD 的雨天镜内漏图回归通过。NeoForge 的目镜/倍率回归及QBZ-191空旷自动连射由维护者确认通过；连射记录142发接受并使用VR枪口姿态，其中47发同步后成功重采样，无缺失姿态回退。最后一项同步修复在Forge端有自动测试，未宣称同轮Forge HMD连射已另行复测。

两端都有单元测试、Mixin/打包检查和真实OpenGL探针。本轮没有重新验收专服多人、所有枪包、所有武器或所有光影组合。记录中过两次Vivecraft/OpenVR AlreadySubmitted纹理提交错误；未观察到因此重现本次枪口问题，原因仍需观察。

## 光影与性能

光影适配仍为实验范围：Forge候选为Oculus1.8.0，Neo候选为Iris1.8.12 + Sodium0.6.13，后者官方发行文件内嵌版本为1.8.12-snapshot+mc1.21.1-local。需要 Vivecraft disableShaderOptimization=true 并重载光影建立独立管线。未通过兼容检查时会回退，不代表支持所有shader pack。

预热可在镜口尚未打开时增加一次世界渲染。若帧时间异常，关闭enableVrScopeMagnification作对比。请在反馈中注明平台、完整模组版本、枪械/瞄具、左右眼、光影配置和复现步骤，并附日志。
