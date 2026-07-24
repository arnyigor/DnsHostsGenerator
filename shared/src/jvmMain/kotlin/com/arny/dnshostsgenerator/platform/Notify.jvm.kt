package com.arny.dnshostsgenerator.platform

import androidx.compose.runtime.Composable
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import javax.swing.JOptionPane

// jvmMain (desktop)
@Composable
internal actual fun Notify(message: String) {
    if (SystemTray.isSupported()) {
        val tray = SystemTray.getSystemTray()
        val image = Toolkit.getDefaultToolkit().createImage("logo.webp")
        val trayIcon = TrayIcon(image, "Notification")
        tray.add(trayIcon)
        trayIcon.displayMessage("Notification", message, TrayIcon.MessageType.INFO)
    } else {
        JOptionPane.showMessageDialog(null, message, "Notification", JOptionPane.INFORMATION_MESSAGE)
    }
}