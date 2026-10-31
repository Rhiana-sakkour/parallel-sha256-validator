# HIAST Parallel Password Strength Auditor

A Java SE desktop application that estimates **password strength** by measuring how long a brute-force SHA-256 attack would actually take to crack it — comparing a single-threaded implementation against a multi-threaded one on an identical algorithm, with real, reproducible speedup measurements.

Built for the **Parallel Programming** course at HIAST (Higher Institute for Applied Sciences and Technology), Track 1 — Domain 5 (Simulation & Cybersecurity → Parallel Hashing).

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
- Pure Java SE, **zero external dependencies** — no Maven/Gradle required.

---

## Architecture

```
src/com/hiast/passwordauditor/
├── Main.java                         Entry point
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

### Synchronization primitives

| Mechanism | Where | Purpose |
|---|---|---|
| `ExecutorService` (fixed Thread Pool) | `crackParallel`, `runFixedWorkload` | Bounds execution to exactly N worker threads |
| `AtomicBoolean` | `crackParallel` | Lock-free flag letting every thread stop as soon as any thread finds a match |
| `AtomicLong` | `crackParallel` | Lock-free aggregate hash counter (batched updates to reduce contention) |
| `ReentrantLock` | `crackParallel` | Guards the short critical section that records the winning candidate |
| `Semaphore(1)` | `MainWindow` | Prevents two scans from running concurrently from the GUI |
| Per-thread `MessageDigest` | `Sha256Util` | `MessageDigest` is **not thread-safe** — each worker owns its own instance |
| `SwingWorker` | `MainWindow` | Moves all hashing off the Event Dispatch Thread so the UI stays responsive |

Only one lock exists in the entire codebase, held briefly and never while waiting on another resource — deadlock is structurally impossible.

---

## Getting started

### Prerequisites

- JDK 17 or newer ([Adoptium](https://adoptium.net) / your distro's package manager)

```bash
java -version
javac -version
```

### Build & run

```bash
# From the project root
javac -d out $(find src -name "*.java")
java -cp out com.hiast.passwordauditor.Main
```

No build tool is required — the project uses only the Java standard library (`java.security`, `java.util.concurrent`, `javax.swing`).

### IDE setup

Open the project folder in IntelliJ IDEA / Eclipse / VS Code, mark `src` as the **Sources Root**, set the project SDK to JDK 17+, and run `Main.java`.

---

## Usage

1. **Test Password Strength** — enter a password, pick a character set and max length that actually cover it, choose a thread count, and run. The app prints the sequential and parallel results, the measured speedup, and a strength rating.
2. **Run Benchmark Suite (N = 1,2,4,8,16)** — runs a fixed-size hashing workload at each thread count for a fair, apples-to-apples comparison, then lets you export the results to CSV.

> Keep the password's length within the configured "Max password length" and character set, or the sequential scan will exhaustively (and slowly) search the whole space without finding it — which is itself a valid demonstration of a genuinely strong password.

---

## Benchmark results

Measured on the reference machine (2 physical cores), fixed workload, varying thread count and data size:

| N | Speedup (2.5M hashes) | Speedup (7.5M) | Speedup (15.5M) |
|---|---|---|---|
| 1 | 1.00 | 1.00 | 1.00 |
| 2 | 1.93 | 2.34 | 1.81 |
| 4 | 2.04 | 2.03 | 1.83 |
| 8 | 2.04 | 2.02 | 1.88 |
| 16 | 1.87 | 2.00 | 2.00 |

Speedup consistently plateaus around **~2x**, matching the machine's physical core count regardless of workload size — strong evidence that the bottleneck is hardware-bound, not algorithmic. Full methodology, Amdahl's Law discussion, and additional runs are in [`technical_report.docx`](technical_report.docx).

---

## Known limitations / future work

- Benchmarks were run on a single 2-core machine; results will scale differently on higher-core-count hardware.
- No warm-up phase before the N=1 baseline measurement, which can introduce JIT-compilation bias in single-run comparisons (discussed in the technical report).
- No `Cancel` button for in-flight scans (acceptable for the current scope; the `Semaphore` prevents overlapping runs).
- Candidate generation assumes a fixed character set and max length; no dictionary-based or rule-based (e.g. leetspeak) attack modes.

---

## Project structure

```
.
├── src/com/hiast/passwordauditor/   Source code (see Architecture above)
├── technical_report.docx            Full technical report: architecture, benchmarks, Amdahl's Law analysis
└── README.md                        This file
```

---

## Course context

- **Institution:** HIAST — Higher Institute for Applied Sciences and Technology
- **Course:** Parallel Programming
- **Track:** 1 (Thread-based parallel algorithm + practical application)
- **Domain:** 5 — Simulation & Cybersecurity (Parallel Hashing)
- **Author:** R256

## License

Academic project submitted for coursework. Add a license here if you intend to make this repository public beyond the course submission (e.g. MIT).
