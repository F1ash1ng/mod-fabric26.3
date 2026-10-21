# 腰部喷气装置模型 V2

本文件夹是外观阶段的可编辑模型素材。造型参考用户提供的威龙装置图片：腰带固定、左右小型机体、深灰金属和青白色喷气。V2 增加了前方与两侧喷口，并将机体略微向外、向后调整，使其贴近玩家又不插入静止站姿的人形。

## 模型与预览

- `waist_jet_idle.bbmodel`：待机模型。
- `waist_jet_forward.bbmodel`、`waist_jet_forward_right.bbmodel`、`waist_jet_right.bbmodel`、`waist_jet_backward_right.bbmodel`、`waist_jet_backward.bbmodel`、`waist_jet_backward_left.bbmodel`、`waist_jet_left.bbmodel`、`waist_jet_forward_left.bbmodel`：八个移动方向的喷气模型。
- `waist_jet_firing.bbmodel`：`waist_jet_forward.bbmodel` 的兼容副本，保留 V1 文件名。
- `directions_overview.png`：八方向俯视对照；箭头指玩家移动方向，喷气朝相反方向排出。
- `comparison.png`：待机与向前突进的背面斜视对照。
- `preview_*.png`：透明背景视角或俯视预览。预览中的灰蓝色玩家身体、手臂与腿**不在模型文件中**。
- `waist_jet_palette.png`：所有模型内嵌的共用像素色板。
- `generate_assets.py`：模型和预览的源脚本。
- `v1_reference/`：原待机、喷气模型留档，用于核对尾流形状。

## 喷口分配

玩家正面为 `-Z`，背面为 `+Z`。向前使用后方双喷口；向后使用前方双喷口；向左使用右侧喷口；向右使用左侧喷口。斜向同时开启相邻两组喷口，例如右前使用后方与左侧喷口。八个方向均复用 V1 的八段尾流方体，只平移或旋转，不改尺寸、长度或配色。

## 贴合检查

以躯干 8×4、双臂和双腿的人形尺寸为基准，再将外层占位盒向外扩 0.25 个模型像素。生成脚本逐一检查待机和八个喷气模型的所有方体与该占位盒的相交情况；当前最小距离为 0.11 个模型像素。此检查对应静止站姿。摆臂、不同外观层和游戏中的实际渲染，需要在佩戴实现阶段查看。

这些 `.bbmodel` 文件是 Blockbench 模型源文件；本阶段还没有绑定玩家模型、切换游戏状态或加入粒子。
