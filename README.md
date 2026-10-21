# mod-fabric26.3

这里集中存放适用于 Minecraft 26.3 Fabric 的不同模组。每个模组都是独立的 Gradle 项目，源码、构建配置、使用说明、许可和发布文件放在自己的目录中。

## 模组目录

### 智能合成器（Smart Crafter）

为原版合成器添加配方模板和漏斗按模板输入功能。

- [功能介绍与使用说明](mods/smart-crafter/README.md)
- [下载 smart-crafter-1.0.0-reset-button.jar](mods/smart-crafter/dist/smart-crafter-1.0.0-reset-button.jar)
- Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25+
- SHA-256：`E913881AAD49DDA91C833043A657579A3A3DDE0624DFB71F3A560F24E19D7691`

### 机动装置（Mobility Device）

腰部装备槽、八向喷气、滑铲、蹬墙跑、击杀连杀经验与 TaCZ 可选兼容。

- [功能介绍与安装说明](mods/mobility-device/README.md)
- [完整开发日志（1.0.0–1.2.15）](mods/mobility-device/DEVELOPMENT_LOG.md)
- [下载 mobility-device-1.2.15.jar](mods/mobility-device/dist/mobility-device-1.2.15.jar)
- Minecraft 26.3 · Fabric Loader 0.19.5+ · Fabric API 0.162.0+26.3 · Java 25+
- SHA-256：`1C24E59AA08B659B4A8E09209BB956A8723D0B092DA59BAD67E61EA6C2FA7A1D`

## 构建模组

每个模组独立构建。以 Smart Crafter 为例，在 PowerShell 中运行：

```powershell
cd mods/smart-crafter
.\gradlew.bat build
```

机动装置也可以独立构建：

```powershell
cd mods/mobility-device
.\gradlew.bat build
```

## 添加新模组

在 `mods/` 下为新模组建立独立文件夹，例如 `mods/my-mod/`，放入自己的 Gradle 配置、源码、README 和 LICENSE，然后在本页添加目录链接。

同一个仓库里的 Git 标签必须唯一，发布版本时请在标签中加上模组名，例如 `smart-crafter-v1.0.0`。
