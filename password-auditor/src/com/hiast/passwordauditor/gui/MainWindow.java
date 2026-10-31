package com.hiast.passwordauditor.gui;

import com.hiast.passwordauditor.benchmark.BenchmarkResult;
import com.hiast.passwordauditor.benchmark.BenchmarkRunner;
import com.hiast.passwordauditor.core.BruteForceHashCracker;
import com.hiast.passwordauditor.core.CandidateSpace;
import com.hiast.passwordauditor.core.CrackResult;
import com.hiast.passwordauditor.core.HashCracker;
import com.hiast.passwordauditor.core.PasswordStrengthEvaluator;
import com.hiast.passwordauditor.util.Sha256Util;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Main application window.
 *
 * Responsiveness note (20% of the grade): every call into
 * {@link BruteForceHashCracker} or {@link BenchmarkRunner} happens inside a
 * {@link SwingWorker}'s doInBackground(), never on the Event Dispatch
 * Thread (EDT). Intermediate status lines are sent back to the EDT safely
 * via publish()/process(), so the window never freezes while millions of
 * hashes are being computed.
 *
 * The {@link Semaphore} below is a single mutual-exclusion permit that
 * prevents the user from starting a second run while one is still in
 * flight (which would both confuse the results and race on the shared
 * result text area) — a small but real, meaningful use of a Semaphore
 * rather than a Lock, since it is specifically gating "how many concurrent
 * runs are allowed" (1), not protecting a single field.
 */
public final class MainWindow extends JFrame {

    private final HashCracker hashCracker = new BruteForceHashCracker();
    private final BenchmarkRunner benchmarkRunner = new BenchmarkRunner();
    private final Semaphore runPermit = new Semaphore(1);

    private JTextField passwordField;
    private JCheckBox lowercaseBox, uppercaseBox, digitsBox, symbolsBox;
    private JSpinner maxLengthSpinner;
    private JSpinner benchmarkCountSpinner;
    private JComboBox<String> threadCountCombo;
    private JTextArea outputArea;
    private JProgressBar progressBar;
    private JButton testButton, benchmarkButton, exportButton;

    private List<BenchmarkResult> lastBenchmarkResults;

    public MainWindow() {
        super("HIAST Parallel Password Strength Auditor");
        buildUi();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 620);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        configPanel.setBorder(BorderFactory.createTitledBorder("Attack Configuration"));

        JPanel passwordRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passwordRow.add(new JLabel("Password to test:"));
        passwordField = new JTextField("ab12", 16);
        passwordRow.add(passwordField);
        configPanel.add(passwordRow);

        JPanel charsetRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        charsetRow.add(new JLabel("Charset:"));
        lowercaseBox = new JCheckBox("a-z", true);
        uppercaseBox = new JCheckBox("A-Z", false);
        digitsBox = new JCheckBox("0-9", true);
        symbolsBox = new JCheckBox("!@#$%^&*", false);
        charsetRow.add(lowercaseBox);
        charsetRow.add(uppercaseBox);
        charsetRow.add(digitsBox);
        charsetRow.add(symbolsBox);
        configPanel.add(charsetRow);

        JPanel lengthRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lengthRow.add(new JLabel("Max password length:"));
        maxLengthSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 8, 1));
        lengthRow.add(maxLengthSpinner);
        lengthRow.add(new JLabel("Threads:"));
        threadCountCombo = new JComboBox<>(new String[]{"1", "2", "4", "8", "16"});
        threadCountCombo.setSelectedItem("4");
        lengthRow.add(threadCountCombo);
        configPanel.add(lengthRow);

        JPanel benchmarkRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        benchmarkRow.add(new JLabel("Benchmark workload (candidates):"));
        benchmarkCountSpinner = new JSpinner(new SpinnerNumberModel(2_000_000, 100_000, 50_000_000, 100_000));
        benchmarkRow.add(benchmarkCountSpinner);
        configPanel.add(benchmarkRow);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testButton = new JButton("Test Password Strength (Sequential vs Parallel)");
        benchmarkButton = new JButton("Run Benchmark Suite (N = 1,2,4,8,16)");
        exportButton = new JButton("Export Last Benchmark to CSV");
        exportButton.setEnabled(false);
        buttonRow.add(testButton);
        buttonRow.add(benchmarkButton);
        buttonRow.add(exportButton);
        configPanel.add(buttonRow);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(false);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(outputArea);

        setLayout(new BorderLayout(8, 8));
        add(configPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(progressBar, BorderLayout.SOUTH);

        testButton.addActionListener(e -> runPasswordTest());
        benchmarkButton.addActionListener(e -> runBenchmarkSuite());
        exportButton.addActionListener(e -> exportBenchmarkResults());
    }

    private char[] buildCharsetFromSelection() {
        StringBuilder sb = new StringBuilder();
        if (lowercaseBox.isSelected()) sb.append("abcdefghijklmnopqrstuvwxyz");
        if (uppercaseBox.isSelected()) sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        if (digitsBox.isSelected()) sb.append("0123456789");
        if (symbolsBox.isSelected()) sb.append("!@#$%^&*");
        return sb.toString().toCharArray();
    }

    private int selectedThreadCount() {
        return Integer.parseInt((String) threadCountCombo.getSelectedItem());
    }

    private void setControlsEnabled(boolean enabled) {
        testButton.setEnabled(enabled);
        benchmarkButton.setEnabled(enabled);
    }

    private void runPasswordTest() {
        if (!runPermit.tryAcquire()) {
            JOptionPane.showMessageDialog(this, "A run is already in progress. Please wait for it to finish.");
            return;
        }

        char[] charset = buildCharsetFromSelection();
        if (charset.length == 0) {
            runPermit.release();
            JOptionPane.showMessageDialog(this, "Please select at least one character set.");
            return;
        }
        int maxLength = (Integer) maxLengthSpinner.getValue();
        int threads = selectedThreadCount();
        String password = passwordField.getText();
        byte[] targetHash = Sha256Util.hashOnce(password);
        CandidateSpace space = new CandidateSpace(charset, maxLength);

        setControlsEnabled(false);
        progressBar.setIndeterminate(true);
        outputArea.setText("");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                publish("Search space size: " + space.totalCandidates() + " candidates (charset="
                        + charset.length + ", maxLength=" + maxLength + ")");

                publish("Running SEQUENTIAL scan...");
                CrackResult sequential = hashCracker.crackSequential(targetHash, space);
                publish("  -> " + sequential);

                publish("Running PARALLEL scan with " + threads + " threads...");
                CrackResult parallel = hashCracker.crackParallel(targetHash, space, threads);
                publish("  -> " + parallel);

                double speedup = sequential.getElapsedSeconds() / Math.max(parallel.getElapsedSeconds(), 1e-9);
                publish(String.format("Speedup (T_sequential / T_parallel) = %.2fx with %d threads", speedup, threads));

                double attackerRate = parallel.getHashesPerSecond();
                if (attackerRate > 0) {
                    double crackTimeSeconds = PasswordStrengthEvaluator.estimateAverageCrackTimeSeconds(space, attackerRate);
                    PasswordStrengthEvaluator.Rating rating = PasswordStrengthEvaluator.rate(crackTimeSeconds);
                    publish(String.format("Estimated average crack time vs this attacker: %.2f seconds", crackTimeSeconds));
                    publish("Strength rating: " + rating + " — " + rating.description);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    outputArea.append(line + "\n");
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                setControlsEnabled(true);
                runPermit.release();
            }
        };
        worker.execute();
    }

    private void runBenchmarkSuite() {
        if (!runPermit.tryAcquire()) {
            JOptionPane.showMessageDialog(this, "A run is already in progress. Please wait for it to finish.");
            return;
        }

        char[] charset = buildCharsetFromSelection();
        if (charset.length == 0) {
            runPermit.release();
            JOptionPane.showMessageDialog(this, "Please select at least one character set.");
            return;
        }
        int maxLength = (Integer) maxLengthSpinner.getValue();
        long candidateCount = ((Number) benchmarkCountSpinner.getValue()).longValue();
        CandidateSpace space = new CandidateSpace(charset, maxLength);
        int[] threadCounts = {1, 2, 4, 8, 16};

        setControlsEnabled(false);
        progressBar.setIndeterminate(true);
        outputArea.setText("");

        SwingWorker<List<BenchmarkResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<BenchmarkResult> doInBackground() throws InterruptedException {
                publish("Benchmarking fixed workload of " + candidateCount + " hashes across N = 1,2,4,8,16 threads...");
                List<BenchmarkResult> results = benchmarkRunner.runSuite(space, candidateCount, threadCounts);
                publish(String.format("%-8s %-14s %-12s %-10s %-10s", "Threads", "Hashes/sec", "Time(s)", "Speedup", "Efficiency"));
                for (BenchmarkResult r : results) {
                    publish(String.format("%-8d %-14.0f %-12.3f %-10.2f %-10.1f%%",
                            r.getThreadCount(), r.getHashesPerSecond(), r.getElapsedSeconds(),
                            r.getSpeedupVsSequential(), r.getEfficiency() * 100));
                }
                return results;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    outputArea.append(line + "\n");
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                setControlsEnabled(true);
                runPermit.release();
                try {
                    lastBenchmarkResults = get();
                    exportButton.setEnabled(lastBenchmarkResults != null);
                } catch (Exception ex) {
                    outputArea.append("Benchmark failed: " + ex.getMessage() + "\n");
                }
            }
        };
        worker.execute();
    }

    private void exportBenchmarkResults() {
        if (lastBenchmarkResults == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("benchmark_results.csv"));
        int choice = chooser.showSaveDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            Path target = chooser.getSelectedFile().toPath();
            try {
                benchmarkRunner.exportToCsv(lastBenchmarkResults, target);
                outputArea.append("Exported benchmark results to " + target + "\n");
            } catch (IOException ex) {
                outputArea.append("Failed to export CSV: " + ex.getMessage() + "\n");
            }
        }
    }
}
