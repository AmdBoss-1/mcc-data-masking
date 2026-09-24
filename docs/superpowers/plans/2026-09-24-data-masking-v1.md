# MCC脱敏工具 v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 本机离线 Java 单 jar CLI：源数据脱敏→发 AI→AI 产物按码本还原成真值版本（mask / unmask / lookup / purge）。

**Architecture:** 四组件：cli（picocli 子命令）/ rw（十种格式读写器，双向）/ rules（规则表+变换器）/ codebook（AES-256-GCM 加密码本）。mask=规则命中→码本取/生成假值→写副本；unmask=码本反向往返替换任意含假值文件。

**Tech Stack:** Java 17、Maven（shade 单 jar）、Apache POI（xlsx/xls/docx/doc/pptx/ppt）、H2 JDBC、PDFBox（仅检测）、picocli、snakeyaml、Jackson、JUnit5。

**Spec:** `C:\Users\17123\Desktop\项目群文档中心\08-项目设计\MCC脱敏工具-v1设计-20260924.md`

## Global Constraints

- 原文件永远只读；所有产出先写临时文件再原子替换（`util/AtomicWrite`）
- 码本 `.mccmask`：AES-256-GCM + PBKDF2-HMAC-SHA256 600000 迭代；口令只交互输入不落盘；默认目录 `./maskbook/`，初始化时自动追加 `.gitignore` 排除
- 同码本内同原值恒同假值；unmask 输入=任何含假值文件
- 数值默认不扰动（`--jitter` 才开，±3% 保小数位）；凭据类（下载码/客户码/私钥/口令哈希）绝不替换、只红字告警
- `.doc/.ppt` 写回必须自验（POI 重开+回读：能打开+替换处数对得上），验不过报错出局不产坏文件
- 不支持/未知格式硬报错+另存提示，绝不静默跳过；PDF 仅检测+红字报告
- 结构化值（手机/身份证/车牌/邮箱/税号）mask 用正则整串命中；unmask 一律码本反向往返、名称类最长优先
- 每条命令结束打印摘要：替换 N 处 / 唯一值 M / 未还原 K / 码本路径 / 凭据告警 / .doc/.ppt 自验结果
- 仓库= `C:\Users\17123\Desktop\mcc-projects\summary\MCC脱敏工具\`；每任务结束 git commit（repo-local 身份已设）

---

### Task 1: 工程骨架 + 测试基建

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/mcc/mask/Main.java`
- Create: `src/test/java/com/mcc/mask/SmokeTest.java`

**Interfaces:** Produces: 可 `mvn -q test` 的绿色工程；deps 版本锁定：poi-ooxml 5.2.5、h2 2.2.224、pdfbox 3.0.2、picocli 4.7.6、snakeyaml 2.2、jackson-databind 2.17.1、junit-jupiter 5.10.2；maven-shade-plugin 3.5.1（mainClass=com.mcc.mask.Main）。

- [ ] **Step 1: 写 pom.xml**（Java 17、上述 deps、shade 插件、surefire 3.2.5）
- [ ] **Step 2: 写失败测试 SmokeTest**：`assertEquals("mask-tool", Main.toolName());`
- [ ] **Step 3: 跑测试确认失败**：`mvn -q test` → 编译错（Main 不存在）
- [ ] **Step 4: 写 Main 最小实现**：`public static String toolName(){return "mask-tool";}` + `main` 暂打印用法
- [ ] **Step 5: 跑测试通过**：`mvn -q test` → PASS
- [ ] **Step 6: Commit**：`git commit -m "feat: 工程骨架+测试基建"`

### Task 2: Crypto + CodebookStore（加密码本落盘）

**Files:**
- Create: `src/main/java/com/mcc/mask/codebook/Crypto.java`
- Create: `src/main/java/com/mcc/mask/codebook/CodebookJson.java`
- Create: `src/main/java/com/mcc/mask/codebook/CodebookStore.java`
- Test: `src/test/java/com/mcc/mask/codebook/CodebookStoreTest.java`

**Interfaces:** Produces: `Crypto.deriveKey(char[] pass, byte[] salt)`→SecretKey；`Crypto.encrypt(SecretKey, byte[])`→byte[](IV||ct||tag)；`Crypto.decrypt(SecretKey, byte[])`→byte[]（错密钥抛 AEADBadTagException）。`CodebookJson`=POJO`{long created; List<String> files; String rulesVersion; Map<String,Map<String,String>> maps;}`（Jackson 可序列化）。`CodebookStore.save(Path file, char[] pass, CodebookJson json)`；`CodebookJson CodebookStore.load(Path file, char[] pass)`（错口令抛 `MaskException("口令错误")`）。文件格式 JSON：`{version:1,kdf:{algo:"PBKDF2WithHmacSHA256",salt:b64,iters:600000},cipher:"AES-256-GCM",data:b64}`。

- [ ] **Step 1: 失败测试**：save→load 往返相等；错口令 load 抛 MaskException 且消息含"口令"；文件明文 grep 不含原值（测试里断言 `new String(Files.readAllBytes(f)).contains("昆山精密五金厂")==false`）
- [ ] **Step 2: 跑确认失败**
- [ ] **Step 3: 实现 Crypto+CodebookStore**（PBKDF2 600000、GCM 12B IV 随机、Jackson 序列化）
- [ ] **Step 4: 跑通过**
- [ ] **Step 5: Commit** `feat: 加密码本落盘（AES-GCM+PBKDF2）`

### Task 3: Codebook 内存模型（同值同假值+累积+反查）

**Files:**
- Create: `src/main/java/com/mcc/mask/codebook/Codebook.java`
- Test: `src/test/java/com/mcc/mask/codebook/CodebookTest.java`

**Interfaces:** Consumes: CodebookJson。Produces: `interface FakeGen{ String fake(String orig); }`（本任务定义，Task4 的 Transformer 以 lambda 适配它）；`String getOrFake(String ruleId, String orig, FakeGen g)`（同 orig 恒同假值）；`String reverse(String fake)`（未命中返回 null）；`Map<String,Map<String,String>> maps()`；`void touchFile(String name)`；`int replacedCount()` 累加器。

- [ ] **Step 1: 失败测试**：同 orig 两次 getOrFake 相等；reverse(fake)==orig；reverse(未知)==null；累积：两个 Codebook 实例共享同一 Json 时映射一致
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: 码本内存模型`

### Task 4: 变换器与假值池（格式保留+校验位重算）

**Files:**
- Create: `src/main/java/com/mcc/mask/rules/Transformer.java`
- Create: `src/main/java/com/mcc/mask/rules/FakePools.java`
- Create: `src/main/java/com/mcc/mask/rules/Transformers.java`
- Test: `src/test/java/com/mcc/mask/rules/TransformersTest.java`

**Interfaces:** Produces: `interface Transformer extends 兼容 FakeGen（以 lambda 传入 getOrFake）{ String fake(String orig, long seed); }`；Transformers 静态工厂：`phone()`（1[3-9]\d{9} 形态）、`idCard()`（18 位、末位校验位按 GB11643 重算）、`plate()`（`^[省简称][A-Z][A-Z0-9]{5,6}$` 形态保留）、`email()`、`taxNo()`（18 位 USCC 校验位重算）、`maskPhone()`（138****0000）、`namePool()`、`factoryPool()`、`jitter(double pct)`。FakePools：厂名=地名表×行业表×字号表组合（≥500 种子、无生僻字）；人名=百家姓×常用名。

- [ ] **Step 1: 失败测试**：phone 假值匹配 `1[3-9]\d{9}` 且≠原值；idCard 假值校验位自验通过；plate 假值匹配车牌正则且首字=原省份；jitter(42.317,3%)∈[41.04,43.59] 且小数位=3；namePool 两次 seed 不同值不同、同 seed 同值
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: 变换器与假值池（校验位重算）`

### Task 5: RuleTable（默认规则+列名词表+JSON 覆盖）

**Files:**
- Create: `src/main/java/com/mcc/mask/rules/RuleTable.java`
- Create: `src/main/resources/default-rules.json`
- Test: `src/test/java/com/mcc/mask/rules/RuleTableTest.java`

**Interfaces:** Produces: `RuleTable.defaultTable()`；`RuleTable.load(Path overrideJson)`（合并覆盖）；`List<StructRule> structRules()`（ruleId+regex+Transformer 名）；`Map<String,String> columnRules()`（列名小写→ruleId）；`List<String> credentialPatterns()`。default-rules.json 内容：struct=phone/idcard/plate/email/taxno 五条正则；columns={厂名/工厂/客户/供应商/联系人/姓名/地址/电话/手机/车牌/司机→对应 ruleId}；credentials=下载码/客户码/私钥/password/secret/token 关键词模式。

- [ ] **Step 1: 失败测试**：默认表含 5 条 struct、columnRules 含"车牌"；override 文件加一条列名后生效
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: 规则表与默认规则`

### Task 6: TextScanner（mask 命中 + unmask 反向往返）

**Files:**
- Create: `src/main/java/com/mcc/mask/scan/TextScanner.java`
- Test: `src/test/java/com/mcc/mask/scan/TextScannerTest.java`

**Interfaces:** Consumes: RuleTable、Codebook、Transformers。Produces: `ScanResult maskText(String text)`（结构化正则整串命中→codebook.getOrFake→替换；返回替换处数+告警列表）；`ScanResult unmaskText(String text)`（码本全部 fake→orig 反向替换，**最长优先**排序）；`ScanResult maskCell(String column, String value)`（列名命中→整格变换）。凭据告警：命中 credentialPatterns 只记告警不替换。

- [ ] **Step 1: 失败测试**：maskText("苏E·D1234 今天到厂") 产出假牌且码本有映射；unmaskText(上一步产物)="苏E·D1234 今天到厂"；两个假名嵌套（假名A包含假名B子串）最长优先不错位；凭据串 maskText 后原文不变且告警数=1
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: TextScanner 双向替换`

### Task 7: TextRw + CsvRw + 原子写 + 摘要

**Files:**
- Create: `src/main/java/com/mcc/mask/util/AtomicWrite.java`
- Create: `src/main/java/com/mcc/mask/util/Summary.java`
- Create: `src/main/java/com/mcc/mask/rw/Rw.java`、`RwFactory.java`、`TextRw.java`、`CsvRw.java`
- Create: `src/main/java/com/mcc/mask/MaskException.java`
- Test: `src/test/java/com/mcc/mask/rw/TextCsvRwTest.java`

**Interfaces:** Produces: `interface Rw{ String name(); RwResult process(Path in, Path out, Ctx ctx); }`；`RwResult{int hits; List<String> warnings;}`；`Ctx{Codebook book; RuleTable rules; boolean reverse; Summary sum;}`；RwFactory.byPath(Path)→Rw（扩展名+魔数：PK\x03\x04=zip 系、%PDF=pdf、D0CF11E0=OLE、其余文本；未知→MaskException）。CsvRw：首行表头→columnRules 命中列整格 mask；未命中列走 maskText。

- [ ] **Step 1: 失败测试**：txt 往返一致；csv 表头"车牌"列整格换假、其他列结构化命中；unmask 往返=原文件字节；输出不存在半截文件（模拟写失败：out 目录只读→抛 MaskException 且 out 不存在）
- [ ] **Step 2-4: 失败→实现→通过**（AtomicWrite=同目录 .tmp+Files.move ATOMIC）
- [ ] **Step 5: Commit** `feat: 文本/CSV 读写器+原子写`

### Task 8: XlsxRw（POI，保公式/样式/合并）

**Files:** Create: `src/main/java/com/mcc/mask/rw/XlsxRw.java`、`XlsRw.java`；Test: `src/test/java/com/mcc/mask/rw/XlsxRwTest.java`

**Interfaces:** 遍历 sheet/row/cell；cell 类型 STRING→按列头（该行 sheet 首行）columnRules 或 maskText；FORMULA→跳过；其他类型跳过。写回仅 setCellValue，样式/合并/公式天然保留。XlsRw 同逻辑 HSSF。

- [ ] **Step 1: 失败测试**：构造 xlsx（表头 车牌/重量kg/备注；3 行数据；1 个合并单元格；1 个公式单元格 =SUM）；mask 后车牌全假、重量不变、公式仍在、合并区域仍在；unmask 往返内容树相等
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: xlsx/xls 读写器`

### Task 9: DocxRw + PptxRw（run 级保格式替换）

**Files:** Create: `src/main/java/com/mcc/mask/rw/DocxRw.java`、`PptxRw.java`、`rw/RunReplace.java`；Test: `src/test/java/com/mcc/mask/rw/DocxPptxRwTest.java`

**Interfaces:** RunReplace.replaceInParagraph(List<RunLike> runs, String orig, String fake)：拼串定位→匹配边界切 run→首命中 run 注入 fake、其余命中段清空；RunLike 适配 XWPFRun 与 XSLFTextRun。Docx 覆盖 body 段落+表格单元格段落+页眉页脚；Pptx 覆盖 slides+notes 的 TextShape。

- [ ] **Step 1: 失败测试**：docx 段落文字被拆成 3 个 run（"昆山精"/"密五金"/"厂"）mask 后整段=假名且 run 数≥1、加粗 run 的加粗属性保留；pptx slide 文本替换；两者 unmask 往返=原文
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: docx/pptx run 级保格式替换`

### Task 10: DocRw + PptRw + 写回自验

**Files:** Create: `src/main/java/com/mcc/mask/rw/DocRw.java`、`PptRw.java`、`rw/SelfVerify.java`；Test: `src/test/java/com/mcc/mask/rw/OleRwTest.java`

**Interfaces:** DocRw=HWPF `Range.replaceText`；PptRw=HSLF slides.getTextRuns() setText。SelfVerify.verify(Path produced, Ctx ctx, int expectedHits)：POI 重开+全文回读，断言能打开 && 假值出现次数==expectedHits && 原值出现 0；失败抛 MaskException 且**删除产物**。夹具：测试内用 POI 生成 .ppt；.doc 夹具用仓库内 `src/test/resources/sample.doc`（一次性由 LibreOffice/Office 生成入库，纯假数据）。

- [ ] **Step 1: 失败测试**：.ppt 往返+自验通过；.doc 往返+自验通过；构造自验失败场景（expectedHits 故意+1）→抛 MaskException 且产物文件被删
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: doc/ppt 读写器+写回自验`

### Task 11: H2Rw + SqlDumpRw

**Files:** Create: `src/main/java/com/mcc/mask/rw/H2Rw.java`、`SqlDumpRw.java`；Test: `src/test/java/com/mcc/mask/rw/H2RwTest.java`

**Interfaces:** H2Rw：输入 .mv.db→复制到临时→`jdbc:h2:<tmp>` 开库→读 INFORMATION_SCHEMA 列名匹配 columnRules→每列 SELECT DISTINCT→codebook 映射→UPDATE；.mccbak=zip：解包→找 *.mv.db 处理→原结构回包。SqlDumpRw：纯文本走 maskText/unmaskText（INSERT 值里的结构化串命中）。

- [ ] **Step 1: 失败测试**：建临时 H2 库（表 weight_item(plate VARCHAR, kg DECIMAL)）插 3 行→mask→plate 全假、kg 不变→unmask→与原库逐行相等；.mccbak 往返 zip 条目清单不变
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: H2/SQL dump 读写器`

### Task 12: PdfScanRw（仅检测+红字报告）

**Files:** Create: `src/main/java/com/mcc/mask/rw/PdfScanRw.java`；Test: `src/test/java/com/mcc/mask/rw/PdfScanRwTest.java`

**Interfaces:** PDFBox 逐页 PDFTextStripper→structRules 命中→Summary 红字行"PDF 第 N 页命中：手机×2、车牌×1（仅检测不替换）"；mask/unmask 对 pdf 均返回该报告且不产出文件。

- [ ] **Step 1: 失败测试**：PDFBox 生成 2 页 pdf（第 2 页含手机号）→process 后报告含"第 2 页"、无产物文件
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: PDF 检测+报告`

### Task 13: CLI 组装（mask/unmask/lookup/purge）

**Files:** Create: `src/main/java/com/mcc/mask/cli/*.java`（MaskCmd/UnmaskCmd/LookupCmd/PurgeCmd/Console/Args）；Modify: `Main.java`（picocli 挂载）；Test: `src/test/java/com/mcc/mask/cli/CliE2ETest.java`

**Interfaces:** 参数见 spec §3；Console.readSecret() 交互口令（System.console 空则回退 stdin 并警告）；maskbook 目录初始化时追加 `.gitignore` 行 `maskbook/` 与 `*.mccmask`；摘要打印含凭据告警与自验结果；退出码 0=成功 2=MaskException。

- [ ] **Step 1: 失败测试**：端到端：csv mask→unmask 往返字节相等；lookup 假→真；purge 后码本文件不存在；错口令 unmask 退出码 2
- [ ] **Step 2-4: 失败→实现→通过**
- [ ] **Step 5: Commit** `feat: CLI 四命令组装`

### Task 14: AI 闭环仿真 + 零泄漏矩阵 + 打包实跑 + 记账

**Files:** Create: `src/test/java/com/mcc/mask/e2e/AiLoopTest.java`、`ZeroLeakTest.java`；Create: `tools/build.ps1`（mvn shade→target/mask.jar）；Modify: `PROJECT_MAP.md`（状态→🟢 v1 已实现）、`docs/log/变更日志.md`

**Interfaces:** AiLoopTest：真合成台账 xlsx mask→"假 AI"（测试内函数：读脱敏件、把出现的假值嵌进新 md+xlsx）→unmask 两份产物→与期望真值文档逐字相等。ZeroLeakTest：全格式（xlsx/xls/docx/doc/pptx/ppt/csv/txt/sql/H2）mask 产物跑 structRules 正则+原值黑名单 grep=0 命中。

- [ ] **Step 1: 写 AiLoopTest+ZeroLeakTest 并跑绿**
- [ ] **Step 2: `tools/build.ps1` 打包；本机实跑：合成 xlsx 全流程 mask→unmask 截图/日志留证**
- [ ] **Step 3: 记账：PROJECT_MAP 状态、变更日志 [功能] v1 实现；群级速查表补行（版本 v1.0.0/本机 CLI）**
- [ ] **Step 4: Commit** `feat: v1 完成（AI 闭环+零泄漏+打包）`；群级文档中心单独 commit 速查表行
