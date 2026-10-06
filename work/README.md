# work/ —— 开发工作区

正式的工具都在 [`tools/`](../tools/)，这里的脚本是开发过程中的**一次性探针与历史脚本**，
留作过程记录，不参与构建：

| 文件 | 当时用来干什么 |
| --- | --- |
| `ref_full.jpg` / `ref_face.jpg` | **参考截图（必需）** —— `build.ps1` 用它生成精灵图 |
| `SpriteMaker.java` | 最早的抠图脚本（容差过大、带羽化，把官方配色改坏了，已被 `tools/StrictCut.java` 取代） |
| `ExportAssets.java` | 早期素材导出（已由 `tools/ExportStrict.java` 取代） |
| `Probe*.java` | 逐帧打印物理状态，用来定位"速度为什么会到 291px/s"这类问题 |
| `ArcPick.java` / `ArcFinal.java` / `RingParam*.java` | 扫描按钮半径 × 弧跨度的可行区间（结论：单弧只有 60dp + 155° 一组解） |
| `Peek.java` | 采样原图特定像素，确认手臂是不是被背景色吞掉 |
| `MenuCheckRingOld.java` / `MenuSize.java` | 旧版菜单几何检查 |
| `EdgeCheck.java` | 统计精灵边缘与背景色的距离分布，判断"蓝晕"有多严重 |
| `FullCheck.java` | 一次性全量审计（界面颜色是否来自原图 + 每张资源图的半透明像素数） |
| `DumpStrings.java` | 导出 APK 里所有中文字符串，逐条确认没有我编的台词 |
| `VerifyApk.java` | 校验 dex 里类与字符串是否真的打进 APK 了 |

> `work/classes`、`work/strict`、`work/out` 等是构建产物，已在 `.gitignore` 里排除。
