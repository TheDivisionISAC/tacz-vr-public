# TACZ VR 基础架构

0.1.0-beta.2 的延迟射击仍在客户端执行器中按“VR姿态、TACZ开火”顺序发送。手中物品对象被同步替换但槽位、物品类型和枪ID相同时，必须重新采样当前姿态/枪口；不将旧枪口直接用于新对象。不同武器或会话继续回退/丢弃，服务端验证与协议不变。

TACZ VR 是独立 Forge 1.20.1 附属。它通过小型 Mixin 和 Vivecraft API 把 VR 姿态接入 TACZ，继续复用 TACZ 的枪械数据、弹药、散布、Lua 脚本、伤害与实体弹丸流程。

## 模块

| 模块 | 职责 |
| --- | --- |
| `client.compat` | 隔离 Vivecraft 客户端 API，读取 VR 状态、主手姿态和触觉 |
| `client.render` | 对齐枪模、控制模型手可见性、同步动画握点 |
| `client.control` | 将 Vivecraft 轮盘动作映射到 TACZ 客户端操作 |
| `client.vr` | 保存不可变 shot pose，处理方向与位置稳定、muzzle 缓存 |
| `network` | 发送独立的 VR shot pose 消息，不修改 TACZ 网络包 |
| `server.shot` | 验证方向和出膛点，管理 TTL、单次消费与失败回退 |
| `mixin` | 进入 TACZ/Vivecraft 的目标调用点，并把行为转交给上述模块 |

## 射击路径

```text
Vivecraft main-hand pose
        |
        v
client main-thread snapshot
        |
        +----> gun rendering and stabilization
        |
        v
taczvr C2S pose message
        |
        v
server validation + timestamp correlation
        |
        v
TACZ shoot/spread/Lua/bullet flow
```

客户端提交方向、可选 muzzle 位置及副手吸附声明。姿态、枪口和吸附位在主线程一并捕获；延迟任务把附属姿态包与 TACZ 原射击包成对交回主线程，核对连接、世界、玩家和持有物身份。已结束会话的旧任务丢弃，同会话切物品时回退原射击包。服务端继续决定玩家能否射击，并验证有限值、方向夹角、眼位/手位距离、世界边界和方块遮挡；它不独立重建模型握点证明双手接触。缺包、迟到、非法方向或 Vivecraft pose 不可用时回退 TACZ 原视角与眼位，非法 muzzle 单独回退眼位，不绕过 TACZ 的弹药或伤害结算。

## Forge 实验性镜内放大

`client.vr` 捕获当前瞄具的 `scope_view`、实际倍率、模型口径与世界/物品身份。`client.compat.VivecraftScopeBridge` 只消费紧邻上一帧的请求，在 LEFT/RIGHT 之前插入一次 SCOPER，复用 Vivecraft 的右望远镜目标；普通 LEFT 仍保留第一眼更新职责。临时 `VRData.getEye(SCOPER)` 姿态同时服务世界相机、完整 roll 模型视图和 RVE，不改写 `VRData.t0`。

投影只替换对称镜内视锥的角尺度，保留 near/far；捕获的完整口径视场同时用于投影和当前眼纹理平面，防止开口渐变或前后移动额外乘上倍率。事务只有在相机、投影和目标验证全部完成后发布本帧纹理。TACZ 切到当前 division 的反转 stencil 后、绘制分划之前合成，同一入口也用于末端分划重放。GL、矩阵、纹理、program 和 buffer 状态均在返回时恢复；纹理不允许读写同一 FBO。

六类注入点必须全部观察到才允许调度。缺失依赖、异常姿态、过期/切物品/换世界、重复 pass 或冲突渲染器均回退。三个光学开关默认开启，仍由能力检查决定实际运行；原生已有 HMD 确认，Oculus 完整组合矩阵未收口，ARCANA/OptiFine 仍在此倍率路径的排除范围。NeoForge 1.21.1 已在独立迁移树接入，使用 Iris 适配和对应平台的坐标/顶点处理。

后续 Oculus 1.8.0 初版增加独立可选适配器与 pipeline begin/finalize 证明。使用 Vivecraft 逐通道模式、检查无共享阴影/SSBO/image 对象，保留原 LEFT 首眼语义；核对 Iris 捕获投影及最终颜色转换后，仅在每眼末端合成。此新增光影路径仍待运行验收，不能由此前原生 HMD 结果推定通过。

## 可选依赖

Vivecraft 是客户端可选依赖。未安装 Vivecraft 时，Mixin 插件跳过全部 VR 客户端注入，TACZ 保持原行为。多人服务器若要接受控制器方向，应安装兼容的 Vivecraft 服务端支持；安全默认会在缺少服务端手姿态时回退 TACZ 原视角。

## 版本基线

- Minecraft `1.20.1`
- Forge `47.3.19+`
- TACZ `1.1.8-hotfix`
- Vivecraft mod version `1.20.1-1.3.15`
- Java `17`

客户端渲染 Mixin 与上述 TACZ/Vivecraft 版本绑定。升级上游前应重新运行 `clean check reobfJar`，并执行客户端、HMD 与专用服务器回归。
