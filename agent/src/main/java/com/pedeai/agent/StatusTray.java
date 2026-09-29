package com.pedeai.agent;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ícone na bandeja do Windows (docs/04-impressao.md#o-agente): verde com tudo certo, amarelo com impressora com
 * problema, vermelho sem conexão com o PedeAí. Onde não há bandeja (servidor, testes), não faz nada.
 */
final class StatusTray {
    enum Status {
        OK(new Color(0x2F, 0x9E, 0x44)),
        PRINTER_PROBLEM(new Color(0xF0, 0x8C, 0x00)),
        OFFLINE(new Color(0xE0, 0x31, 0x31));

        private final Color color;

        Status(Color color) {
            this.color = color;
        }
    }

    private final TrayIcon icon;
    private final Map<Status, BufferedImage> images = new EnumMap<>(Status.class);
    private Status current;

    private StatusTray(TrayIcon icon) {
        this.icon = icon;
    }

    /** Sem ícone: para os testes e para quando não há bandeja. */
    static StatusTray none() {
        return new StatusTray(null);
    }

    /** Ícone na bandeja, ou um que não mostra nada quando o sistema não tem bandeja. */
    static StatusTray create(String name) {
        if (!SystemTray.isSupported()) {
            return new StatusTray(null);
        }
        TrayIcon icon = new TrayIcon(circle(Status.OFFLINE.color), "PedeAí: " + name + " iniciando");
        icon.setImageAutoSize(true);
        try {
            SystemTray.getSystemTray().add(icon);
            return new StatusTray(icon);
        } catch (AWTException | SecurityException e) {
            return new StatusTray(null);
        }
    }

    /** Muda cor e dica só quando o estado muda, para não piscar a cada consulta. */
    void show(Status status, String tooltip) {
        if (icon == null) {
            return;
        }
        if (status != current) {
            icon.setImage(images.computeIfAbsent(status, key -> circle(key.color)));
            current = status;
        }
        icon.setToolTip(tooltip.length() > 120 ? tooltip.substring(0, 120) : tooltip);
    }

    void notify(String title, String message) {
        if (icon != null) {
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    private static BufferedImage circle(Color color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(color);
        graphics.fillOval(1, 1, 14, 14);
        graphics.dispose();
        return image;
    }
}
