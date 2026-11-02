A Java SE desktop application that estimates **password strength** by measuring how long a brute-force SHA-256 attack would actually take to crack it — comparing a single-threaded implementation against a multi-threaded one on an identical algorithm, with real, reproducible speedup measurements.

---

## Why this project

Most "password strength meters" use arbitrary heuristics (length, character variety) with no grounding in actual attack cost. This tool instead:

1. Takes a password and a configurable attacker search space (character set + max length).
2. Brute-forces it with a **sequential** scan and a **parallel** scan (both implementing the exact same algorithm).
3. Reports the measured throughput, the real speedup achieved, and an estimated average crack time — which is what a strength rating should actually be based on.

It doubles as a controlled benchmark harness for studying multithreading behavior (Amdahl's Law, thread-pool scaling, synchronization overhead) on real hardware.

---

## Features

- **Sequential vs. parallel brute-force cracking** of a user-supplied password, with live Speedup reporting.
- **Fixed-workload benchmark suite** (N = 1, 2, 4, 8, 16 threads) decoupled from the crack-search logic, for a fair, reproducible throughput comparison — exportable to CSV.
- **Password strength rating** (Weak / Moderate / Strong / Very strong) derived from measured attacker throughput, not arbitrary rules.
- **Responsive GUI** — all hashing runs on a background thread (`SwingWorker`); the window never freezes during a scan.

---

## Repository layout

```
.
├── password-auditor/                   The Java project
│   └── src/com/hiast/passwordauditor/  Source code
├── benchmark_results.csv               Exported benchmark data (N=1,2,4,8,16)
├── privacy report.csv
├── Report.pdf                          Full technical report: architecture, benchmarks, Amdahl's Law analysis
└── README.md                           This file
```

> All build and run commands below assume you start from the repository root and `cd` into `password-auditor/` first.

### Source structure

```
password-auditor/src/com/hiast/passwordauditor/
├── Main.java                          Entry point
├── core/
│   ├── CandidateSpace.java            Index <-> candidate-string mapping (stateless)
│   ├── HashCracker.java               Algorithm interface
│   ├── BruteForceHashCracker.java     Sequential + parallel crack implementation
│   ├── CrackResult.java               Result DTO
│   └── PasswordStrengthEvaluator.java Crack-time -> strength rating
├── benchmark/
│   ├── BenchmarkRunner.java           Fixed-workload N=1..16 throughput harness
│   └── BenchmarkResult.java           Benchmark row DTO
├── gui/
│   └── MainWindow.java                Swing UI (SwingWorker-driven)
└── util/
    └── Sha256Util.java                Thread-safety-aware SHA-256 helper
```

`core` and `benchmark` have no dependency on `gui` — the threading/algorithm logic is fully decoupled from the user interface.

### Parallel decomposition

The full candidate space is expressed as a pure function, `CandidateSpace.candidateAt(index)`, mapping any index to its candidate string with no shared state. This lets the index range be split into contiguous, roughly equal blocks and handed to worker threads with **no shared iterator or queue** — classic data parallelism.

---

## Getting started

```bash
# From the repository root
cd password-auditor
javac -d out $(find src -name "*.java")
java -cp out com.hiast.passwordauditor.Main
```

No build tool is required — the project uses only the Java standard library (`java.security`, `java.util.concurrent`, `javax.swing`).

### IDE setup

Open the **password-auditor/** folder (not the repository root) in IntelliJ IDEA / Eclipse / VS Code, mark `src` as the **Sources Root**, set the project SDK to JDK 17+, and run `Main.java`.

---

## Usage

1. **Test Password Strength** — enter a password, pick a character set and max length that actually cover it, choose a thread count, and run. The app prints the sequential and parallel results, the measured speedup, and a strength rating.
2. **Run Benchmark Suite (N = 1,2,4,8,16)** — runs a fixed-size hashing workload at each thread count for a fair, apples-to-apples comparison, then lets you export the results to CSV.

> Keep the password's length within the configured "Max password length" and character set, or the sequential scan will exhaustively (and slowly) search the whole space without finding it — which is itself a valid demonstration of a genuinely strong password.

---
