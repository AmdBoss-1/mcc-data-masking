# MCC脱敏工具 · PROJECT_MAP

> 项目群第 13 个项目（2026-09-24 立项）。一句话：**本机离线的数据脱敏/还原命令行工具**——源数据脱敏后发 AI，AI 产物再按码本还原成真值版本。

## 状态

🟡 设计完成、待实施。设计文档：`docs/superpowers/specs/2026-09-24-data-masking-design.md`（**接手先读它**）。

## 形态与位置

- Java 单 jar CLI（maven shade），离线运行；命令 `mask / unmask / lookup / purge`
- 码本 `.mccmask`（AES-256-GCM）默认存 `./maskbook/`，自动 .gitignore，**不上云不进仓库**
- v1 格式：xlsx/xls/docx/doc/pptx/ppt/csv/文本日志/H2(.mv.db,.mccbak)/SQL dump；PDF 仅检测+报告

## 目录

```
MCC脱敏工具/
├─ PROJECT_MAP.md            ← 本文件
├─ docs/
│  ├─ superpowers/specs/     ← 设计文档（brainstorming 产出）
│  └─ log/变更日志.md         ← 项目级记账
├─ src/                      ← 实施期创建（cli / rw / rules / codebook 四组件）
└─ pom.xml                   ← 实施期创建
```

## 约定

- 原文件永远只读；产出先临时后原子替换
- .doc/.ppt 写回必须过"自验"（重开+回读），验不过报错出局
- 凭据类（下载码/客户码/私钥/口令哈希）绝不替换，只红字告警
- 群级文档回写（交接索引/速查表 12→13）等 v1 落地一并做，清单不挂半成品
