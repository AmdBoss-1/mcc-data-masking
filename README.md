# AI Local Data Masking Tool / AI 本地数据脱敏工具

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-8+-orange.svg)](https://openjdk.java.net/)

A privacy-first data masking tool designed for AI workflows. Keeps codebooks 100% local, works fully offline, and supports reversible masking for safe AI analysis. Supports 10+ file formats (Excel, CSV, JSON, XML, etc.).

专为 **AI 工作流设计**的本地数据脱敏工具——码本不上云、完全离线、可逆脱敏，让 AI 安全分析敏感数据。支持 10+ 种文件格式（Excel/CSV/JSON/XML 等）。

## ✨ Features / 特性

- **🔒 Offline-First / 离线优先**: No internet required. Codebooks never leave your machine.
- **📁 Batch Processing / 批量处理**: Drag & drop entire folders. Auto-detects supported formats.
- **🔄 Reversible / 可逆脱敏**: Generate masked files for AI analysis, then restore originals locally.
- **🌐 Web Console / 网页控制台**: Visual interface at `http://127.0.0.1:8098` with drag-and-drop support.
- **💻 CLI Mode / 命令行模式**: Four commands (`mask`, `unmask`, `book`, `serve`) for automation.
- **📊 Comparison Table / 对照表**: Track what was masked and download as ZIP.

## 🚀 Quick Start / 快速开始

### Prerequisites / 前置要求

- Java 8 or higher
- Maven (for building from source)

### Build / 构建

```bash
mvn clean package
```

### Run Web Console / 运行网页控制台

```bash
java -jar target/masking-tool-1.0.0.jar serve
```

Then open http://127.0.0.1:8098 in your browser.

### CLI Usage / 命令行用法

```bash
# Mask a directory
java -jar target/masking-tool-1.0.0.jar mask --dir /path/to/data

# Unmask with codebook
java -jar target/masking-tool-1.0.0.jar unmask --dir /path/to/masked --book /path/to/codebook.json

# Create new codebook
java -jar target/masking-tool-1.0.0.jar book new --output mybook.json

# List supported formats
java -jar target/masking-tool-1.0.0.jar book list
```

## 📂 Supported Formats / 支持的文件格式

| Format | Extensions | Notes |
|--------|-----------|-------|
| Excel | `.xlsx`, `.xls` | Preserves formulas and formatting |
| CSV | `.csv` | Auto-detects delimiter |
| JSON | `.json` | Handles nested structures |
| XML | `.xml` | Preserves schema |
| Text | `.txt`, `.log` | UTF-8 encoding |
| PDF | `.pdf` | Text extraction only |
| Word | `.docx` | Preserves styles |
| PowerPoint | `.pptx` | Extracts text from slides |
| SQLite | `.db`, `.sqlite` | Reads all tables |
| Images | `.png`, `.jpg` | OCR-based (experimental) |

## 🔐 Security Model / 安全模型

- **Codebooks are local**: The `codebook.json` file stays on your machine. Never uploaded.
- **No cloud dependency**: All processing happens locally. Works in air-gapped environments.
- **Single-file mount**: Upload directory is isolated per session. Browser cannot access real paths.

## 🏗️ Architecture / 架构

- **Core Engine**: T1-T12 format readers/writers
- **CLI Layer**: Four commands (`mask`, `unmask`, `book`, `serve`)
- **Web Server**: Embedded Jetty serving static HTML + REST API
- **Session Management**: In-memory, no database required

## 📝 License / 许可证

Apache License 2.0. See [LICENSE](LICENSE) for details.

## 🤝 Contributing / 贡献

Pull requests welcome! Please read [CONTRIBUTING.md](docs/CONTRIBUTING.md) first.

---

**Made with ❤️ by MCC Team**
