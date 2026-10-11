# AI Local Data Masking Tool · MCC 脱敏工具

**Hand your ledgers to AI without handing over the real data.**
A fully offline, reversible data-masking tool for AI workflows: mask source files into a "usable fake" copy, send only that copy to AI, then unmask the AI's output back to real values using a local codebook. The codebook never leaves your machine — no cloud, no network, no telemetry.

专为 **AI 工作流**设计的本机离线数据脱敏／还原工具。**码本不上云、全程不联网、遇到不支持的格式直接报错而不静默跳过。**

```
mask 源数据 ──▶ 脱敏副本（+ 加密码本，码本留本机）
                    │
                    ▼   只把脱敏副本发给 AI
              AI 产出报告 / 表格（含假值）
                    │
                    ▼   unmask
              按码本换回真值  ← 只在本机完成
```

## 与「手工糊数据」的区别

|  | 手工替换 | 本工具 |
| :--- | :--- | :--- |
| 可逆 | 换不回来，AI 报告只能当废纸 | 码本制；**任意**含假值的文件都能还原，不限原文件副本 |
| 格式 | 破坏格式，AI 认不出车牌／手机号 | OOXML 走 **run 级保格式替换**，假值仍像真的 |
| 留痕 | 改了哪些值说不清 | 对照表可查、可导出 |
| 安全 | 明文映射表躺在桌面 | **AES-256-GCM** 加密码本，初始化自动写 `.gitignore` |

## 快速开始

前置：**Java 17 或更高**（从源码构建需 Maven）。

```bash
# 打包 → dist/mcc-mask.jar（约 30 MB，同目录附 .sha256）
powershell -File tools\build.ps1
# 或： mvn -q clean package
```

### 命令行

```bash
# 脱敏：产出 .masked 副本 + 加密码本（口令交互输入）
java -jar dist/mcc-mask.jar mask 台账.csv -o 台账.masked.csv --book mb

# 还原：AI 产物里带假值也照样换回真值
java -jar dist/mcc-mask.jar unmask AI报告.md -o AI报告.真值.md --book mb

# 单点反查（默认 假→真，--rev 反向）
java -jar dist/mcc-mask.jar lookup 苏Y·UB8JK --book mb
java -jar dist/mcc-mask.jar lookup 苏A12345 --book mb --rev

# 用完销毁码本（覆写后删除）
java -jar dist/mcc-mask.jar purge --book mb
```

`mask` 可选参数：`--new` 另开新码本、`--rules <yaml>` 覆盖字段规则表、`--jitter` 打开数值微扰。
批量免交互：`--pass-file <口令文件>`（该文件权限请自管，不要提交）。

### 本机网页控制台

```bash
java -jar dist/mcc-mask.jar serve --root D:\台账 --port 8098 --open
```

浏览器打开 `http://127.0.0.1:8098/`（**只绑本机，局域网不可达**）。可拖入文件／文件夹批量脱敏，把 AI 产物拖回来还原，并在对照表里搜索、反查、导出。码本默认落在 `root\maskbook\`，`--idle-min` 可改自动锁时长。页面零外链、打进 jar，**断网可用**。

## 支持的格式

| 分组 | 扩展名 | 处理方式 | 可逆 |
| :--- | :--- | :--- | :--- |
| OOXML | `.xlsx` `.docx` `.pptx` | POI 读写，run 级保格式替换 | 双向 |
| OLE 老二进制 | `.xls` `.doc` `.ppt` | HSSF / HWPF / HSLF，**写回自验**兜底 | 双向（自验不过则出局） |
| 文本类 | `.csv` `.txt` `.log` `.md` | 正则就地替换 | 双向 |
| 数据库 | H2 `.mv.db`、SQL dump | 敏感列取值建映射后回写 | 双向 |
| PDF | `.pdf` | **仅检测**：红字报告第几页命中哪类串 | 不替换 |

`.doc` / `.ppt` 写回后会立即重开、回读、核对处数；验不过就报错出局，**绝不产坏文件**。

## 默认替换规则（可用 `--rules` 覆盖）

- **结构化串**（任何列、任何文本都扫）：手机号、身份证、车牌、邮箱、税号／统一社会信用代码 —— 其中身份证与税号**重算校验位**，假值也过校验。
- **列名命中**（表格类）：厂名／客户／供应商／联系人／姓名／地址／电话／车牌／司机 等表头词。
- **名称假值池**：按「地名 + 行业 + 字号」「百家姓 + 常用名」组合生成，长度贴原值、无生僻字 —— **耐抄**，AI 逐字引用不易变形。
- **地址**：省／市保留，街道门牌换假。
- **数值**：默认**原样不动**（保证 AI 汇总对得上账）；`--jitter` 才做 ±3% 微扰并保留小数位。
- **凭据黑名单**：下载码、客户码、私钥、口令哈希 —— **绝不替换**，只红字告警。

## 安全口径

- 码本单文件 `.mccmask`：**AES-256-GCM** 加密，口令经 **PBKDF2-HMAC-SHA256 60 万次**迭代派生。
- 口令**只交互输入**，不落盘、不进环境变量、不进命令行历史。
- 码本默认存 `./maskbook/`，初始化自动写 `.gitignore`；**不上云、不进仓库**。
- 服务只绑 `127.0.0.1`；口令只在服务端内存，**刷新页面即锁**，默认 30 分钟空闲自动锁。
- **原文件永远只读**；所有产出先写临时文件再原子替换，不留半截文件。
- `purge`：覆写后删除码本 —— 还原完就毁，不留后手。

## 已知边界

这些是当前版本的真实限制，不打算用「后续支持」含糊带过：

1. **自由文本里的人名／公司名不替换** —— 没有列上下文时无从判断，设计如此。走表格列名命中，或该值已在码本里；也可用 `--rules` 扩词表。
2. **PDF 只检测、不替换** —— 逐页扫出敏感串并红字报告命中位置，不改动 PDF 内容。
3. **套件整机包 `.mccbak` 不直读** —— 包内数据是套件自有加密：先复制 `.mv.db`，或在套件内导出 SQL，再交给本工具。
4. **数值默认不扰动** —— 这是特性不是缺陷：不扰动才能让 AI 的汇总跟你的账对得上。
5. **凭据类一律不替换，只告警** —— 命中时红字提示「检测到疑似凭据」，不阻断流程。
6. **无云端能力** —— 没有联网、没有遥测、没有自动更新检查；也**不支持** JSON / XML / SQLite / 图片 OCR。

## 质量与验证

- 引擎 49 项 + 网页控制台 22 项 = **71 项测试全绿**。
- **往返一致**：`mask → unmask` 后与源文件**字节完全相同**。
- **零泄漏**：脱敏产物跑敏感正则 + **原值黑名单 grep**，命中即失败。
- **AI 闭环仿真**：脱敏 → 把假值嵌进新的 md / xlsx → 还原 → 与期望真值文档逐字比对。
- 控制台全流程无头浏览器回归 69 项断言，实测截图见 `docs/verify/`。

## 构建环境

Java 17 · Maven 单模块 shade 单 jar · Apache POI 5.2.5（含 scratchpad，用于 `.doc` / `.ppt`）· H2 2.2.224 · PDFBox 3.0.2 · picocli 4.7.6 · SnakeYAML 2.2。

## 许可证

Apache License 2.0 —— 可商用、可修改、可分发、含专利授权。详见 [LICENSE](LICENSE)。

> 商标不在授权范围内：`MCC` 品牌归原作者所有。

## 仓库

<https://github.com/AmdBoss-1/mcc-data-masking>

---

*Offline · Reversible · The codebook never leaves your machine*
