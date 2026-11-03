# TACZ VR 0.1.0-beta.3-pre.3 — Forge 1.20.1

本版为预发布测试版，替代存在客户端启动问题的 pre.2。

## 修复

- 修复手部 Mixin 优先级低于 Vivecraft 合并方法而导致的客户端启动崩溃。
- 手部绘制仅在实际接管原生手后补绘，改善 MR 和隐藏模式的重复显示。
- 握点历史按模型保存，资源重载和登出时清理，改善替换模型后的缺锚点回退。
- 服务端在消费姿态和生成弹丸时重验枪口，失效时回退 TACZ 原起点。
- 包含此前镜内放大、倍率稳定、射击姿态快照及 LRTactical 兼容修复。

## 安装

Minecraft 1.20.1、Forge 47.3.19、Java 17、TACZ 1.1.8-hotfix、Vivecraft 1.20.1-1.3.15-forge。
将 JAR 放入 mods，移除旧版 TACZ VR；依赖另行安装。多人客户端和服务器使用相同附属版本。

## 验证

2026-11-03 强制重建 check/reobfJar，通过 325 项测试和生产 JAR 检查。Forge 开发客户端已确认修复后的手部包装调用实际注入，完成初始化并正常退出；既有开发专服有/无 Vivecraft 启动检查通过。

HMD 视觉、MR 实际绘制、多人射击、光影性能及生产 JAR 安装运行仍待验证，阶段保持 CONDITIONAL。

制品 tacz-vr-0.1.0-beta.3-pre.3+mc1.20.1.jar，490210 字节。
SHA256：964208A73E8E5A60878C1AE3C55BED69960122991AAE670F3855A316948DFACA。

GPL-3.0-only；不包含 TACZ 或 Vivecraft 的代码副本。
