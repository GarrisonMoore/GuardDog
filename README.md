# PROJECT: "LasiLog" SIEM / SOAR
### Primary Architect : Garrison
### Build Environment : OpenJDK 21 / FlatLaf / SQLite 3 / GSON
### System Status     : Operational - Version 1.0.1
--------------------------------------------------------------------------------

I. GETTING STARTED
----------------------
Download and launch the executable - https://github.com/GarrisonMoore/GuardDog/releases/download/1.0.1/GuardDogProcessor.jar

Or launch via command line:
#### Standard execution with file selector:
```bash
java -jar LasiLog.jar
```         
#### Launch with target log:
```bash
java -jar LasiLog.jar /path/to/log.file
```

II. OPERATIONAL MISSION
-----------------------

Network administrators typically have to tape together multiple different log aggregation tools to monitor their environments, ultimately suffering from a "Legacy Bottleneck" where high-volume telemetry causes GUI saturation and stalls incident response.

LasiLog is built to replace that fragmented workflow with an all-in-one solution. It serves as a high-performance, agnostic ingestion engine that utilizes a chain-of-responsibility multi-parser architecture to normalize raw data streams. The result is a unified, highly responsive Network Operations Center (NOC) dashboard that brings every log into one searchable interface without the lag.


III. SYSTEM SPECIFICATIONS
--------------------------
* CORE ENGINE     : Java-based multi-threaded processing.
* INTERFACE       : FlatLaf-integrated Swing GUI (GUI.java).
* PERSISTENCE     : SQLite JDBC for asynchronous historical logging.
* MEMORY MGMT     : ConcurrentSkipListMap for sub-millisecond table indexing.
* DATA FORMATS    : RFC 5424 (Syslog), JSON, BSD (RFC 3164), and Unstructured Heuristics.
* DEPENDENCIES    : flatlaf-3.4.1.jar, gson-2.13.2.jar, sqlite-jdbc-3.51.3.0.jar.


IV. ARCHITECTURAL TIER BREAKDOWN
----------------------------------

1. INGESTION & DATA ACQUISITION
   The system is built for reactive telemetry processing, focusing on 
   high-fidelity log tailing.

    - THE FILE BRIDGE: An integrated file explorer and command-line entry
      point (Main.java) that allows analysts to target any local text file.
    - BACKGROUND TAILER: A dedicated daemon thread (LogTailer.java) that
      monitors file growth and processes new entries in real-time.

2. THE "MULTI-PARSER" PIPELINE
   Engine utilizes an intelligent, layered parsing hierarchy (ParserMaster.java)
   to ensure zero-drop telemetry:

    - SYSLOG LAYER  : SyslogParser.java (RFC 5424) for modern structured logs.
    - JSON MODULE   : JSONParser.java for structured application payloads.
    - BSD MODULE    : BSDparser.java (RFC 3164) for legacy network gear.
    - HEURISTIC LAYER: HeuristicParser.java fallback that scans for date patterns
      and common delimiters to "guess" the structure of unknown logs.

3. DYNAMIC CATEGORIZATION & ENRICHMENT
   Incoming data is processed through the CategorizationMaster.java to
   automatically assign functional tags (e.g., Security, Network, Auth & Access)
   and normalized severity levels (CRIT, WARN, INFO).

4. HYBRID STORAGE STRATEGY (DUAL-LAYER)
    - HOT STORAGE: IndexingEngine.java manages an in-memory datastore using
      ConcurrentHashMap and SkipListMaps for instantaneous GUI filtering.
    - COLD STORAGE: DatabaseEngine.java ensures every processed 'LogObject'
      is committed to 'guarddog_logs.db' using asynchronous batching to 
      prevent UI latency.


V. PERFORMANCE & OPTIMIZATIONS
--------------------------------
- ASYNCHRONOUS COMMITS: The 'DatabaseCommitTask' batches SQLite I/O operations
  every 500ms on a background thread, eliminating disk-write stutter.
- EFFICIENT INDEXING: Concurrent Skip Lists allow O(log N) range queries
  (e.g., time-based slicing) without locking the main thread.
- MODERN UI ENGINE: Utilizes FlatLaf (Dark Mode) with custom arc tweaks
  and electric blue accents for high-visibility NOC environments.


VI. OPERATIONAL PROTOCOLS
-------------------------
1. INITIALIZATION: Execute Main.java. The system automatically verifies
   database connectivity and initializes the UI.
2. SOURCE SELECTION: Pass a log file as a command-line argument or use the
   GUI File Chooser to select a target for tailing.
3. ANALYSIS: Utilize the Search and Sidebar filters to isolate specific
   Critical or Security events within the unified feed.
4. ARCHIVAL: Historical data is automatically indexed; the system loads the
   last 14 days of logs into memory on startup for immediate browsing.


VII. PROJECT METRICS (CODEBASE)
------------------------------
* TOTAL FILES     : 22 Java Classes
* TOTAL LINES     : 3,149 Lines of Code
* TOTAL CHARS     : 118,235 Characters
--------------------------------------------------------------------------------
| PACKAGE         | CLASSES | TOTAL LINES | TOTAL CHARS |
|-----------------|:-------:|:-----------:|:-----------:|
| GUI             |    7    |    1,313    |    50,744   |
| Engine     |    7    |      890    |    34,320   |
| Parsers         |    4    |      553    |    22,878   |
| Interfaces      |    4    |      193    |    10,293   |
--------------------------------------------------------------------------------


VIII. FUTURE DEPLOYMENT ROADMAP
------------------------------
* DECRYPTOR INTEGRATION: Activation of the DecryptorMaster.java interface
  for handling encrypted or obfuscated log payloads.
* ADVANCED HEURISTICS: Refined pattern recognition for proprietary
  industrial and medical data formats.
* NETWORK TOPOLOGY VIEW: Visual mapping of log frequency per-host to
  identify anomaly spikes.
