# MCC脱敏工具 · PROJECT_MAP

> 项目群第 13 个项目（2026-09-24 立项，同日 v1 实现完成）。一句话：**本机离线的数据脱敏/还原命令行工具**——源数据脱敏后发 AI，AI 产物再按码本还原成真值版本。

## 状态

🟢 **v1.1.0**：引擎 49 项测试 + 网页控制台 22 项 = **71 项全绿**；真 jar 端到端实测过（脱敏→还原**字节完全相同**），控制台无头浏览器全流程 69 项断言通过并留截图。设计正本：`项目群文档中心\08-项目设计\MCC脱敏工具-v1设计-20260924.md`（引擎）+ `MCC脱敏工具-网页控制台-v1设计-20260924.md`（UI）。

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

## 网页控制台（v1.1.0 已落地）

命令行之外多一个本机网页版：批量脱敏、拖入 AI 产物还原、对照表核查。

```bash
java -jar dist/mcc-mask.jar serve --root D:\台账 --port 8098 --open
# → 浏览器开 http://127.0.0.1:8098/ （只绑本机，局域网不可达）
```

- 码本默认落在 `root\maskbook\`（可用 `--book` 换目录）；`--idle-min` 改自动锁时长（默认 30 分钟）
- 页内动线：解锁/新建码本 → 勾文件（可多选批量）→ 脱敏或还原 → 结果卡下载产物 / **打开位置**（本机资源管理器选中它）→ 对照表搜索/导出 → 单点反查
- 对照表**按新旧排**：最近一次脱敏产出的映射排最前并标「本次」（底色），跑完自动回第 1 页；产物不在工作目录的默认不显示（可切「显示全部」）
- 拖拽：**拖到页面任何位置都收**（整屏「松手即收」遮罩），文件和文件夹都行；文件夹连子目录落到 `root\.mcc-upload\<文件夹>\`（≤300 件、单件 ≤512MB、≤12 层，点开头目录与 Thumbs.db 不收），收完自动进该目录并切「含子目录」视图一次全选、自动切「还原」；**落在哪个绝对路径直接写在投放区底下**，旁边有「打开这个文件夹」；也可点「选择文件 / 选择文件夹」
- 换工作目录走文件区右上角的**目录选择器**（拖文件夹做不到：浏览器不把拖入项的真实磁盘路径交给网页），码本不跟着换
- 零外链：页面打进 jar，断网可用；口令只在服务端内存，刷新页面即锁
- 三栏各自内滚（文件清单 / 执行结果 / 对照表），页面本身不滚；内容变长会撑破面板这类坑由 `ai\.tools\uitest\mask-web-scroll.mjs`（400 条映射 + 250 文件夹具，18 项断言）守着；全流程回归是 `mask-web-e2e.mjs`（69 项），排版测量是 `mask-web-shots.mjs`（四视口）
- 实测截图：`docs/verify/*.png`（20 张，含错口令 / 不支持格式 / 刷新即锁三个错误态 + 拖文件遮罩 / 拖入文件夹 / 整批还原 / 本次新增与打开位置 / 内滚回归 / 投放区精简）

## 能力与已知边界

- **格式**：xlsx / xls / docx / doc / pptx / ppt / csv / txt·log·md / SQL dump / H2 `.mv.db`（均为双向）
- **PDF**：只检测+红字报告（第几页命中哪类敏感串），不替换
- **`.mccbak` 整机包不直读**：包内模块数据是套件 `MccBakCrypto` 加密（明文=备份 SQL/JSON）→ 要脱敏请先复制 `.mv.db` 或在套件导出 SQL
- **自由文本里的公司/人名**无列上下文时不替换（设计如此）：要么走表格列名命中，要么该值已在码本里；可用 `--rules` 扩词表
- **数值默认不扰动**（保证 AI 汇总能对上账）；`--jitter` 才做 ±3% 微扰
- **凭据类**（下载码/客户码/私钥/口令哈希）绝不替换，只红字告警
- `.doc/.ppt` 写回必过**自验**（重开+回读+处数核对），不过则删产物报错，不产坏文件
- 码本 `maskbook/book.mccmask`：AES-256-GCM + PBKDF2 60 万迭代；目录自动写 `.gitignore`；**不上云、不进仓库**
- 对照表列 = 类别 / 原值 / 假值（**没有"出现次数"列**：码本结构只存映射不存计数，加计数要改码本格式，v1 不动引擎）；每文件替换处数在结果卡上
- 网页控制台**不做**服务端任务队列：批量由前端逐个发请求，单文件秒级返回

## 目录

```
MCC脱敏工具/
├─ PROJECT_MAP.md            ← 本文件
├─ pom.xml                   ← Java 17 + POI 5.2.5 + H2 + PDFBox + picocli
├─ src/main/java/com/mcc/mask/{Main,MaskException, cli, codebook, rules, scan, rw, util, web}
├─ src/main/resources/{default-rules.json, web/index.html}   ← 默认规则表 + 控制台单文件页面（零外链）
├─ src/test/java/...          ← 71 项测试（引擎 49 + web 22：Workspace/Session/ApiHttp/ServeSmoke）
├─ src/test/resources/sample.doc           ← .doc 夹具（Word COM 生成的纯假数据）
├─ tools/build.ps1            ← 打包脚本
├─ docs/verify/*.png          ← 控制台无头浏览器实测截图（20 张，含三个错误态 + 拖拽 / 打开位置 / 内滚 / 精简）
└─ docs/log/变更日志.md        ← 项目级记账（设计文档正本在群级 08-项目设计）
```
