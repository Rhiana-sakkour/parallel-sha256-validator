package com.hiast.passwordauditor;

import com.hiast.passwordauditor.gui.MainWindow;

import javax.swing.SwingUtilities;

/**
 * Application entry point. Swing requires all UI construction and updates
 * to happen on the Event Dispatch Thread (EDT); SwingUtilities.invokeLater
 * schedules the window creation there correctly.
 */
public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
