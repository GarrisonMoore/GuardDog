# Guard Dog NOC Bridge

**Guard Dog NOC Bridge** is a high-performance Java application designed to process, index, and visualize system logs in real-time. Built as a specialized Network Operations Center (NOC) tool, it allows for seamless monitoring of large-scale log streams with advanced filtering, categorization, and historical browsing capabilities.

---

## Overview
The application acts as a bridge between raw log files (e.g., `/var/log/syslog`) and a structured monitoring interface. It tails logs as they are written, passes them through a sophisticated multi-parser engine, and indexes them into memory for instant querying. All processed logs are persisted to an embedded SQLite database, ensuring data longevity across application restarts.

---

##  Design Choices

### 1. Multi-Parser Architecture
The engine uses a chain-of-responsibility pattern for log parsing. Each raw line is tested against multiple parsers:
- **SyslogParser (RFC-5424):** Handles modern structured syslog formats.
- **JSONParser:** Decodes structured logs emitted by modern applications and microservices.
- **BSDParser:** Supports legacy RFC-3164 formats.
- **HeuristicParser:** A fallback mechanism that uses pattern matching to extract useful data from non-standard logs.

### 2. Intelligent Categorization
Beyond simple parsing, the `CategorizationMaster` analyzes log messages to assign:
- **Severity Levels:** Maps keywords (e.g., `fatal`, `crit`, `error`, `warn`) to standardized levels.
- **Functional Categories:** Groups logs into domains like `Security`, `Network`, `Auth & Access`, and `System Services`.
- **Noise Filtering:** Automatically discards high-volume, low-value "noise" logs to keep the operator's view focused on critical events.

### 3. Dual-Layer Storage (In-Memory + Disk)
- **In-Memory Indexing:** Uses `ConcurrentHashMap` and `ConcurrentSkipListMap` to provide O(1) and O(log N) retrieval for live filtering by Host, Severity, Category, and Time.
- **SQLite Persistence:** Provides a robust backend for historical data. The application loads the last 14 days of logs into memory on startup for immediate browsing.

---

## Optimizations & Performance

### 1. Non-Blocking I/O & Threading
- **Background Tailing:** The `LogTailer` runs in a dedicated daemon thread, ensuring that log ingestion never blocks the user interface.
- **Asynchronous Database Commits:** Instead of writing to disk for every log (which is slow in SQLite), the `DatabaseCommitTask` batches insertions and flushes them to disk on a background thread every 500ms. This prevents "UI stutter" during high-volume log bursts.

### 2. Efficient Data Structures
- **Concurrent Skip Lists:** Used for the `TimeIndex` to allow for efficient range queries (e.g., "show me logs between 2:00 PM and 3:00 PM") while remaining thread-safe for live updates.
- **Memory Management:** The application starts tailing from the *end* of files by default and limits the initial database load to the most recent 14 days to keep the memory footprint lean.

### 3. Modern UI with FlatLaf
Utilizes the **FlatLaf (Flat Dark)** look-and-feel with custom UI tweaks (rounded arcs, electric blue accents) to provide a professional, dark-mode-native experience suitable for NOC environments.

---

## Getting Started

### Prerequisites
- **Java 25** or higher.
- Included libraries: `flatlaf-3.4.1.jar`, `gson-2.13.2.jar`, `sqlite-jdbc-3.51.3.0.jar`.

### Running the Application
You can launch the application by passing a log file as an argument or by using the built-in file chooser:

```bash
# Via Command Line
java -jar GuardDogProcessor.jar /path/to/your/log/file.log

# Or simply run and use the GUI to browse:
java -jar GuardDogProcessor.jar
```

---

## Project Structure
- `SentryStack/`: Core logic (Main, Indexing, Database, Tailer).
- `GUI/`: Swing-based user interface components.
- `Parsers/`: Specialized parsing logic for different log formats.
- `Interfaces/`: Common contracts for parsers and categorization.
- `lib/`: Third-party dependencies.
