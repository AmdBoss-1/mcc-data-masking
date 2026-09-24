# MCC脱敏工具 · PROJECT_MAP

> 项目群第 13 个项目（2026-09-24 立项，同日 v1 实现完成）。一句话：**本机离线的数据脱敏/还原命令行工具**——源数据脱敏后发 AI，AI 产物再按码本还原成真值版本。

## 状态

🟢 **v1 已实现**（v1.0.0）：44 项测试全绿 + 真 jar 端到端实测过（脱敏→还原**字节完全相同**）。设计正本：`项目群文档中心\08-项目设计\MCC脱敏工具-v1设计-20260924.md`。

## 产物与用法

- 打包：`powershell -File tools\build.ps1` → `dist\mcc-mask.jar`（约 30MB，含 POI/H2/PDFBox；sha256 同目录 `.sha256`）
- 运行需本机 Java（17+，你机器已有 JDK 21）

```bash
java -jar dist/mcc-mask.jar mask   台账.csv  -o 台账.脱敏.csv --book mb   # 脱敏（口令交互输入）
java -jar dist/mcc-mask.jar unmask AI报告.md  -o AI报告.真值.md --book mb   # 还原任何含假值的文件
java -jar dist/mcc-mask.jar lookup 苏Y·UB8JK --book mb                     # 单点反查
java -jar dist/mcc-mask.jar purge --book mb                                # 用完销毁码本
```

批处理免交互：加 `--pass-file 口令文件`（该文件权限自管，不进 git）。

## 下一步：网页控制台（设计中，待用户审 spec）

引擎已有 CLI，缺 UI。网页控制台 v1 设计正本：`项目群文档中心\08-项目设计\MCC脱敏工具-网页控制台-v1设计-20260924.md`——本机 `127.0.0.1:8098`、JDK 自带 HttpServer 零新依赖、批量+对照表、工作目录与临时上传双轨、口令只存内存且刷新即锁。**不改引擎**，新增 `web` 包三类（WebServer / Api / Workspace）。审过才排实施计划。

## 能力与已知边界

- **格式**：xlsx / xls / docx / doc / pptx / ppt / csv / txt·log·md / SQL dump / H2 `.mv.db`（均为双向）
- **PDF**：只检测+红字报告（第几页命中哪类敏感串），不替换
- **`.mccbak` 整机包不直读**：包内模块数据是套件 `MccBakCrypto` 加密（明文=备份 SQL/JSON）→ 要脱敏请先复制 `.mv.db` 或在套件导出 SQL
- **自由文本里的公司/人名**无列上下文时不替换（设计如此）：要么走表格列名命中，要么该值已在码本里；可用 `--rules` 扩词表
- **数值默认不扰动**（保证 AI 汇总能对上账）；`--jitter` 才做 ±3% 微扰
- **凭据类**（下载码/客户码/私钥/口令哈希）绝不替换，只红字告警
- `.doc/.ppt` 写回必过**自验**（重开+回读+处数核对），不过则删产物报错，不产坏文件
- 码本 `maskbook/book.mccmask`：AES-256-GCM + PBKDF2 60 万迭代；目录自动写 `.gitignore`；**不上云、不进仓库**

## 目录

```
MCC脱敏工具/
├─ PROJECT_MAP.md            ← 本文件
├─ pom.xml                   ← Java 17 + POI 5.2.5 + H2 + PDFBox + picocli
├─ src/main/java/com/mcc/mask/{Main,MaskException, cli, codebook, rules, scan, rw, util, e2e测试见下}
├─ src/main/resources/default-rules.json   ← 默认规则表（正则+列名词表+凭据词）
├─ src/test/java/...          ← 44 项测试（含 AiLoopTest 主闭环、ZeroLeakTest 零泄漏矩阵）
├─ src/test/resources/sample.doc           ← .doc 夹具（Word COM 生成的纯假数据）
├─ tools/build.ps1            ← 打包脚本
└─ docs/log/变更日志.md        ← 项目级记账（设计文档正本在群级 08-项目设计）
```
