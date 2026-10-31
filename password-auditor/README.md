# Parallel Password Strength Auditor
### HIAST — Parallel Programming Assignment — Section 1, Domain 5 (Simulation & Cybersecurity)

A desktop application (pure Java SE, no external dependencies) that measures
**how long it would take to brute-force a password via SHA-256 hashing**,
comparing a single-threaded scan against a multi-threaded one, and turns
that measurement into a practical password-strength verdict.

---

## 1. Why these design choices

- **Language: Java 17+ (no frameworks).** Java gives real OS-backed threads
  (`java.util.concurrent`), no GIL-like limitation (unlike CPython
  threading), and a native GUI toolkit (Swing) with zero external
  dependencies to install — so you can set this up in minutes with nothing
  but a JDK.
- **No Maven/Gradle.** Everything here is Java standard library
  (`java.security`, `java.util.concurrent`, `javax.swing`). You can compile
  and run it with plain `javac`/`java`.

---

## 2. Prerequisites (on YOUR machine)

1. Install a **JDK 17 or newer** (OpenJDK is fine — Temurin/Adoptium,
   Oracle, or your distro's package, e.g. `sudo apt install openjdk-21-jdk`
   on Ubuntu, or download from https://adoptium.net on Windows/macOS).
2. Verify installation:
   ```bash
   java -version
   javac -version
   ```
3. Any IDE works (IntelliJ IDEA Community, Eclipse, VS Code + "Extension
   Pack for Java"), or just a terminal — no IDE is required.

---

## 3. Project structure

```
password-auditor/
├── README.md
└── src/
    └── com/hiast/passwordauditor/
        ├── Main.java                          (entry point)
        ├── core/
        │   ├── CandidateSpace.java             (search-space ↔ string mapping)
        │   ├── HashCracker.java                (algorithm interface)
        │   ├── BruteForceHashCracker.java       (sequential + parallel algorithm)
        │   ├── CrackResult.java                 (result DTO)
        │   └── PasswordStrengthEvaluator.java   (crack-time → strength rating)
        ├── benchmark/
        │   ├── BenchmarkRunner.java             (fixed-workload N=1..16 harness)
        │   └── BenchmarkResult.java             (benchmark row DTO)
        ├── gui/
        │   └── MainWindow.java                  (Swing UI, SwingWorker-driven)
        └── util/
            └── Sha256Util.java                  (thread-safe-aware hashing helper)
```

This mirrors the rubric's explicit requirement to **separate threading
logic from the application interface**: everything under `core/` and
`benchmark/` has zero Swing imports and zero knowledge that a GUI exists;
`gui/MainWindow.java` is the only class that touches both.

---

## 4. How to compile and run

From the `password-auditor/` directory:

```bash
# Compile everything into an "out" folder
javac -d out $(find src -name "*.java")

# Run the application
java -cp out com.hiast.passwordauditor.Main
```

(On Windows PowerShell, replace the `find` command with:
`javac -d out (Get-ChildItem -Recurse -Filter *.java src | % FullName)`)

If you prefer an IDE: open the `password-auditor` folder as a project,
mark `src` as the sources root, and run `Main.java` directly.

---

## 5. Using the application

### A) "Test Password Strength" (the practical, single-password demo)
1. Type a password into **Password to test** — for a FAST demo, keep it
   short and within the selected charset, e.g. password `ab12` with
   charset `a-z` + `0-9` and **Max length = 4**.
2. Pick a thread count (try 4 or 8).
3. Click **Test Password Strength**. The app will:
   - run a full **sequential** brute-force scan,
   - then run the **parallel** scan with your chosen thread count,
   - print both results, the measured **Speedup**, and a strength
     **rating** (Weak / Moderate / Strong / Very strong).

> ⚠️ Keep `Max length` small (3–5) for this live demo with a broad charset,
> or the sequential scan alone can take a very long time (that is itself
> realistic — it is literally what "strong password" means). Use the
> **Benchmark Suite** below for the controlled N=1..16 measurements your
> report actually needs — it uses a fixed workload so it always finishes
> in predictable time regardless of charset/length.

### B) "Run Benchmark Suite (N = 1,2,4,8,16)" (what your report needs)
1. Set **Benchmark workload (candidates)** — default 2,000,000 is a good
   starting point; increase it on a fast machine so even N=16 takes at
   least ~1 second (too-short runs make timing noisy).
2. Click **Run Benchmark Suite**. It hashes the exact same fixed number
   of candidates at N = 1, 2, 4, 8, 16 threads (no target password, no
   early exit — a clean, fair comparison) and prints a table of:
   `Threads | Hashes/sec | Time(s) | Speedup | Efficiency`.
3. Click **Export Last Benchmark to CSV** and save it — open that CSV in
   Excel/LibreOffice/Google Sheets and insert a line chart
   (Threads on X-axis, Speedup on Y-axis) — **this is your required
   Benchmark Graph.**
4. Repeat with a couple of different workload sizes if you want to show
   how data size affects the curve (the rubric mentions "وحجم البيانات").

---

## 6. Where each required concept lives in the code (for your report)

| Requirement from the assignment | Where it is implemented |
|---|---|
| Thread creation / management | `Executors.newFixedThreadPool(...)` in `BruteForceHashCracker.crackParallel` and `BenchmarkRunner.runFixedWorkload` |
| Thread Pool | Same as above — a *fixed* pool sized to exactly N threads |
| Data/task splitting | `CandidateSpace.candidateAt(long)` turns the whole keyspace into a pure index→string function, so it can be split into contiguous `[start, end)` ranges per thread with no shared iterator |
| Atomic Variables | `AtomicBoolean found` and `AtomicLong totalTried` in `BruteForceHashCracker.crackParallel` |
| Locks (mutual exclusion / critical section) | `ReentrantLock resultLock` in `BruteForceHashCracker.crackParallel`, guarding the "record the winning password" critical section |
| Semaphore | `Semaphore runPermit` in `MainWindow`, limiting the GUI to one run in flight at a time |
| Synchronization barrier (join-equivalent) | `ExecutorService.invokeAll(tasks)` blocks until every worker has finished before results are read |
| Avoiding Race Conditions | Each thread owns its own `MessageDigest` instance (see `Sha256Util` javadoc — `MessageDigest` is NOT thread-safe); all shared mutable state goes through atomics or the lock, never raw shared fields |
| Avoiding Deadlocks | Only one lock exists in the whole codebase, held only briefly and never while waiting on anything else — no possibility of a circular wait |
| Non-freezing UI | `SwingWorker` in `MainWindow` runs all hashing off the Event Dispatch Thread; `publish()/process()` stream progress back safely |
| Speedup measurement | `CrackResult.getHashesPerSecond()`, `BenchmarkResult.getSpeedupVsSequential()` |

---

## 7. Writing the technical report (4–6 pages)

Suggested structure, mapped directly to the grading rubric:

1. **Introduction** — problem statement: estimating brute-force crack time
   of a password as a practical strength metric; chosen domain
   (Simulation & Cybersecurity → Parallel Hashing).
2. **Architecture** (maps to the "إدارة النياسب" criterion, 30%)
   - Diagram: `CandidateSpace` → index range partitioning → `ExecutorService`
     worker threads → shared `AtomicBoolean`/`AtomicLong`/`ReentrantLock`
     → aggregated `CrackResult`.
   - Explicitly list and justify each synchronization primitive using the
     table in Section 6 above.
   - Explain the MessageDigest-per-thread decision and why sharing one
     instance would be a hidden race condition.
3. **Benchmark methodology** — explain why a *separate*, fixed-workload
   `BenchmarkRunner` is used instead of timing the crack-search directly
   (fair, reproducible comparison, not dependent on where the target
   password happens to sit in the search space).
4. **Results** (maps to "كفاءة الخوارزمية والتسريع", 30%, and
   "التحليل التجريبي للأداء", 20%)
   - Insert your exported CSV as a table (N, time, hashes/sec, speedup,
     efficiency).
   - Insert the Speedup-vs-N line chart from the CSV.
   - Report your CPU's physical/logical core count (`Runtime.getRuntime()
     .availableProcessors()` — you can print this once from `Main` if you
     want it in the report) and compare it to where your speedup curve
     plateaus.
5. **Discussion of Amdahl's Law** — Amdahl's Law states that if a fraction
   *p* of the work is parallelizable, the maximum possible speedup is
   `1 / ((1 - p) + p / N)`. Discuss: does your measured curve bend below
   the ideal linear line as N grows? What is NOT parallelized in your
   implementation (task submission/result-collection overhead, the final
   lock-guarded write, thread-pool startup) and how does that explain the
   gap? This directly explains why efficiency drops below 100% at higher N.
6. **Bottleneck discussion** (20%) — likely candidates: memory bandwidth,
   `ExecutorService` scheduling overhead, your CPU's actual core count vs
   requested thread count (hyperthreading effects), JIT warm-up on short
   runs.
7. **Application demo** (20%) — screenshots of the "Test Password
   Strength" feature showing a weak vs. a strong password, with their
   different ratings.
8. **Conclusion.**

---

## 8. Sanity checklist before submission

- [ ] Both sequential and parallel versions are implemented for the
      *same* algorithm and produce the *same* correctness guarantee
      (identical password found).
- [ ] Benchmark table/graph covers N = 1, 2, 4, 8, 16 as required.
- [ ] Report explicitly names the synchronization mechanisms used (Locks,
      Semaphores, Atomic Variables, Thread Pools) — use the Section 6
      table as your checklist.
- [ ] Amdahl's Law is discussed with your actual numbers, not just
      mentioned in passing.
- [ ] UI does not freeze during a run (demonstrate this is being handled
      via `SwingWorker` in your explanation/screenshot).
- [ ] Code is pushed/zipped with this exact package structure so the
      grader can compile it with the two commands in Section 4.
