# TACZ VR 基础架构

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

客户端只提交方向和可选 muzzle 位置。服务端继续决定玩家能否射击，并验证有限值、方向夹角、眼位/手位距离、世界边界和方块遮挡。任何缺包、迟到、非法值或 Vivecraft API 失败都只让该发回退 TACZ 原视角与眼位，不会绕过 TACZ 的弹药或伤害结算。

## 可选依赖

Vivecraft 是客户端可选依赖。未安装 Vivecraft 时，Mixin 插件跳过全部 VR 客户端注入，TACZ 保持原行为。多人服务器若要接受控制器方向，应安装兼容的 Vivecraft 服务端支持；安全默认会在缺少服务端手姿态时回退 TACZ 原视角。

## 版本基线

- Minecraft `1.20.1`
- Forge `47.3.19+`
- TACZ `1.1.8-hotfix`
- Vivecraft mod version `1.20.1-1.3.13`
- Java `17`

客户端渲染 Mixin 与上述 TACZ/Vivecraft 版本绑定。升级上游前应重新运行 `clean check reobfJar`，并执行客户端、HMD 与专用服务器回归。
