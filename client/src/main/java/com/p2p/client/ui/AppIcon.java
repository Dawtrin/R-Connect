package com.p2p.client.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Pure vector icons drawn with Graphics2D.
 * Guarantees crisp rendering without tofu/square boxes on any OS or font configuration.
 */
public final class AppIcon implements Icon {
    public enum Type {
        SEND, ATTACHMENT, DOWNLOAD, OPEN_FILE, REFRESH, FILE, CHAT, FOLDER, SETTINGS, LOGOUT,
        FEED, PEOPLE, BROADCAST, NOTIFICATION, SHIELD, SEARCH, HEART, HEART_FILL, LOCK, SERVER, CHECK, USER, DOT,
        R_CONNECT_MARK, RINNSAN_MARK, PLUS, CLOSE, SMILE, MIC, PLAY, PAUSE, STOP,
        CALL, CALL_END, MIC_OFF, VIDEO, VIDEO_OFF, SCREEN
    }

    private final Type type;
    private final Color color;
    private final int size;

    public AppIcon(Type type, Color color, int size) {
        this.type = type;
        this.color = color;
        this.size = size;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.7f, size / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double scale = size / 24d;
        g.scale(scale, scale);

        switch (type) {
            case SEND -> paintSend(g);
            case ATTACHMENT -> paintAttachment(g);
            case DOWNLOAD -> paintDownload(g);
            case OPEN_FILE -> paintOpen(g);
            case REFRESH -> paintRefresh(g);
            case FILE -> paintFile(g);
            case CHAT -> paintChat(g);
            case FOLDER -> paintFolder(g);
            case SETTINGS -> paintSettings(g);
            case LOGOUT -> paintLogout(g);
            case FEED -> paintFeed(g);
            case PEOPLE -> paintPeople(g);
            case BROADCAST -> paintBroadcast(g);
            case NOTIFICATION -> paintNotification(g);
            case SHIELD -> paintShield(g);
            case SEARCH -> paintSearch(g);
            case HEART -> paintHeart(g, false);
            case HEART_FILL -> paintHeart(g, true);
            case LOCK -> paintLock(g);
            case SERVER -> paintServer(g);
            case CHECK -> paintCheck(g);
            case USER -> paintUser(g);
            case DOT -> paintDot(g);
            case R_CONNECT_MARK, RINNSAN_MARK -> paintRinnSanMark(g);
            case PLUS -> { line(g, 12, 5, 12, 19); line(g, 5, 12, 19, 12); }
            case CLOSE -> { line(g, 6, 6, 18, 18); line(g, 18, 6, 6, 18); }
            case SMILE -> paintSmile(g);
            case MIC -> paintMic(g);
            case PLAY -> paintPlay(g);
            case PAUSE -> paintPause(g);
            case STOP -> paintStop(g);
            case CALL -> paintCall(g);
            case CALL_END -> paintCallEnd(g);
            case MIC_OFF -> paintMicOff(g);
            case VIDEO -> paintVideo(g);
            case VIDEO_OFF -> paintVideoOff(g);
            case SCREEN -> paintScreen(g);
        }
        g.dispose();
    }

    private static void paintSend(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(3, 11.2); path.lineTo(20.5, 3.5); path.lineTo(14.1, 20.5);
        path.lineTo(10.6, 13.5); path.closePath();
        g.draw(path);
        line(g, 10.6, 13.5, 20.5, 3.5);
    }

    private static void paintAttachment(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(8.2, 12.7); path.lineTo(14.4, 6.5);
        path.curveTo(16.2, 4.7, 19.1, 4.7, 20.9, 6.5);
        path.curveTo(22.7, 8.3, 22.7, 11.2, 20.9, 13);
        path.lineTo(11.5, 22.4);
        path.curveTo(8.8, 25.1, 4.5, 25.1, 1.8, 22.4);
        path.curveTo(-0.9, 19.7, -0.9, 15.4, 1.8, 12.7);
        path.lineTo(11.2, 3.3);
        g.draw(path);
    }

    private static void paintDownload(Graphics2D g) {
        line(g, 12, 3, 12, 15);
        line(g, 7.5, 10.7, 12, 15.2);
        line(g, 16.5, 10.7, 12, 15.2);
        line(g, 5, 20, 19, 20);
    }

    private static void paintOpen(Graphics2D g) {
        Path2D folder = new Path2D.Double();
        folder.moveTo(3, 7); folder.lineTo(9, 7); folder.lineTo(11, 9); folder.lineTo(21, 9);
        folder.lineTo(19, 20); folder.lineTo(3, 20); folder.closePath();
        g.draw(folder);
        line(g, 13, 15, 21, 7);
        line(g, 16.5, 7, 21, 7);
        line(g, 21, 7, 21, 11.5);
    }

    private static void paintRefresh(Graphics2D g) {
        g.draw(new Arc2D.Double(4, 4, 16, 16, 35, 285, Arc2D.OPEN));
        line(g, 16.5, 4.5, 20.5, 4.5);
        line(g, 20.5, 4.5, 20.5, 8.5);
    }

    private static void paintFile(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(5, 2.5, 14, 19, 2, 2));
        line(g, 14, 3, 14, 8);
        line(g, 14, 8, 19, 8);
        line(g, 8.5, 13, 15.5, 13);
        line(g, 8.5, 17, 14, 17);
    }

    private static void paintChat(Graphics2D g) {
        Path2D bubble = new Path2D.Double();
        bubble.moveTo(4, 5); bubble.lineTo(20, 5); bubble.lineTo(20, 16);
        bubble.lineTo(14, 16); bubble.lineTo(9, 21); bubble.lineTo(9, 16); bubble.lineTo(4, 16); bubble.closePath();
        g.draw(bubble);
    }

    private static void paintFolder(Graphics2D g) {
        Path2D folder = new Path2D.Double();
        folder.moveTo(3, 7); folder.lineTo(9, 7); folder.lineTo(11, 9); folder.lineTo(21, 9);
        folder.lineTo(20, 19); folder.lineTo(4, 19); folder.closePath();
        g.draw(folder);
    }

    private static void paintSettings(Graphics2D g) {
        g.draw(new Ellipse2D.Double(8, 8, 8, 8));
        g.draw(new Ellipse2D.Double(3, 10.5, 3, 3));
        g.draw(new Ellipse2D.Double(18, 10.5, 3, 3));
        line(g, 6, 12, 8, 12); line(g, 16, 12, 18, 12);
        line(g, 12, 6, 12, 8); line(g, 12, 16, 12, 18);
    }

    private static void paintLogout(Graphics2D g) {
        line(g, 4, 12, 16, 12); line(g, 12, 8, 16, 12); line(g, 12, 16, 16, 12);
        line(g, 5, 5, 5, 19); line(g, 5, 5, 11, 5);
    }

    private static void paintFeed(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(4, 3, 16, 18, 2, 2));
        line(g, 7, 7, 17, 7);
        line(g, 7, 11, 17, 11);
        line(g, 7, 15, 13, 15);
    }

    private static void paintPeople(Graphics2D g) {
        // Main user
        g.draw(new Ellipse2D.Double(6, 4, 6, 6));
        Path2D body1 = new Path2D.Double();
        body1.moveTo(3, 19);
        body1.curveTo(3, 14, 6, 13, 9, 13);
        body1.curveTo(12, 13, 15, 14, 15, 19);
        g.draw(body1);

        // Secondary user
        g.draw(new Ellipse2D.Double(14, 6, 5, 5));
        Path2D body2 = new Path2D.Double();
        body2.moveTo(17, 13.5);
        body2.curveTo(19, 14, 21, 15, 21, 19);
        g.draw(body2);
    }

    private static void paintBroadcast(Graphics2D g) {
        Path2D horn = new Path2D.Double();
        horn.moveTo(4, 10);
        horn.lineTo(10, 7);
        horn.lineTo(17, 4);
        horn.lineTo(17, 18);
        horn.lineTo(10, 15);
        horn.lineTo(4, 12);
        horn.closePath();
        g.draw(horn);
        line(g, 7, 13.5, 7, 20);
        g.draw(new Arc2D.Double(18, 7, 4, 8, -60, 120, Arc2D.OPEN));
    }

    private static void paintNotification(Graphics2D g) {
        Path2D bell = new Path2D.Double();
        bell.moveTo(12, 3);
        bell.curveTo(8, 3, 6, 7, 6, 12);
        bell.lineTo(4, 17);
        bell.lineTo(20, 17);
        bell.lineTo(18, 12);
        bell.curveTo(18, 7, 16, 3, 12, 3);
        bell.closePath();
        g.draw(bell);
        g.draw(new Arc2D.Double(10, 18, 4, 3, 180, 180, Arc2D.OPEN));
    }

    private static void paintShield(Graphics2D g) {
        Path2D shield = new Path2D.Double();
        shield.moveTo(12, 3);
        shield.lineTo(20, 6);
        shield.lineTo(20, 13);
        shield.curveTo(20, 18, 16, 21, 12, 22);
        shield.curveTo(8, 21, 4, 18, 4, 13);
        shield.lineTo(4, 6);
        shield.closePath();
        g.draw(shield);
    }

    private static void paintSearch(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4, 4, 11, 11));
        line(g, 12.5, 12.5, 19.5, 19.5);
    }

    private static void paintHeart(Graphics2D g, boolean fill) {
        Path2D heart = new Path2D.Double();
        heart.moveTo(12, 20);
        heart.curveTo(6, 15, 3, 10, 3, 6.5);
        heart.curveTo(3, 4, 5, 2.5, 7.5, 2.5);
        heart.curveTo(9.5, 2.5, 11, 3.5, 12, 5);
        heart.curveTo(13, 3.5, 14.5, 2.5, 16.5, 2.5);
        heart.curveTo(19, 2.5, 21, 4, 21, 6.5);
        heart.curveTo(21, 10, 18, 15, 12, 20);
        heart.closePath();
        if (fill) {
            g.fill(heart);
        } else {
            g.draw(heart);
        }
    }

    private static void paintLock(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(5, 10, 14, 11, 3, 3));
        g.draw(new Arc2D.Double(7.5, 4, 9, 8, 0, 180, Arc2D.OPEN));
        line(g, 7.5, 8, 7.5, 10);
        line(g, 16.5, 8, 16.5, 10);
    }

    private static void paintServer(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(4, 4, 16, 6, 2, 2));
        line(g, 7, 7, 9, 7);
        g.draw(new RoundRectangle2D.Double(4, 14, 16, 6, 2, 2));
        line(g, 7, 17, 9, 17);
    }

    private static void paintCheck(Graphics2D g) {
        Path2D check = new Path2D.Double();
        check.moveTo(4, 12);
        check.lineTo(9.5, 17.5);
        check.lineTo(20, 6.5);
        g.draw(check);
    }

    private static void paintUser(Graphics2D g) {
        g.draw(new Ellipse2D.Double(7, 3, 10, 10));
        Path2D body = new Path2D.Double();
        body.moveTo(4, 21);
        body.curveTo(4, 16, 7, 15, 12, 15);
        body.curveTo(17, 15, 20, 16, 20, 21);
        g.draw(body);
    }

    private static void paintDot(Graphics2D g) {
        g.fill(new Ellipse2D.Double(6, 6, 12, 12));
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    /**
     * R-Connect Connection Monogram: a geometric "R" whose three terminals are nodes of a small graph —
     * stem head, stem foot and leg tip. Strokes are in 24-unit grid space so the mark stays crisp at any size.
     */
    private static void paintRinnSanMark(Graphics2D g) {
        g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float r = new Path2D.Float();
        r.moveTo(6.5f, 19f);
        r.lineTo(6.5f, 5f);
        r.lineTo(12f, 5f);
        r.curveTo(14.9f, 5f, 17f, 6.9f, 17f, 9.25f);
        r.curveTo(17f, 11.6f, 14.9f, 13.5f, 12f, 13.5f);
        r.lineTo(6.5f, 13.5f);
        g.draw(r);
        g.draw(new java.awt.geom.Line2D.Float(12f, 13.5f, 17.5f, 19f));
        float n = 2.0f;
        g.fill(new Ellipse2D.Float(6.5f - n, 5f - n, 2 * n, 2 * n));
        g.fill(new Ellipse2D.Float(6.5f - n, 19f - n, 2 * n, 2 * n));
        g.fill(new Ellipse2D.Float(17.5f - n, 19f - n, 2 * n, 2 * n));
    }

    private static void paintSmile(Graphics2D g) {
        g.draw(new Ellipse2D.Float(3.5f, 3.5f, 17f, 17f));
        g.fill(new Ellipse2D.Float(8f, 8.5f, 2f, 2f));
        g.fill(new Ellipse2D.Float(14f, 8.5f, 2f, 2f));
        g.draw(new Arc2D.Float(7.5f, 9.5f, 9f, 7.5f, 205, 130, Arc2D.OPEN));
    }

    private static void paintMic(Graphics2D g) {
        g.draw(new RoundRectangle2D.Float(9f, 3.5f, 6f, 10f, 6f, 6f));
        g.draw(new Arc2D.Float(6f, 7f, 12f, 9f, 180, 180, Arc2D.OPEN));
        g.draw(new Line2D.Float(12f, 16f, 12f, 20f));
        g.draw(new Line2D.Float(8.5f, 20f, 15.5f, 20f));
    }

    private static void paintPlay(Graphics2D g) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(8.5f, 5.5f);
        p.lineTo(18.5f, 12f);
        p.lineTo(8.5f, 18.5f);
        p.closePath();
        g.fill(p);
    }

    private static void paintPause(Graphics2D g) {
        g.fill(new RoundRectangle2D.Float(7f, 5.5f, 3.2f, 13f, 2f, 2f));
        g.fill(new RoundRectangle2D.Float(13.8f, 5.5f, 3.2f, 13f, 2f, 2f));
    }

    private static void paintStop(Graphics2D g) {
        g.fill(new RoundRectangle2D.Float(6.5f, 6.5f, 11f, 11f, 2.5f, 2.5f));
    }

    private static void paintCall(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(5.0, 4.5);
        p.curveTo(4.0, 5.5, 3.5, 7.5, 4.5, 10.5);
        p.curveTo(6.0, 15.0, 9.0, 18.0, 13.5, 19.5);
        p.curveTo(16.5, 20.5, 18.5, 20.0, 19.5, 19.0);
        p.lineTo(17.2, 15.8);
        p.curveTo(16.5, 15.0, 15.2, 14.8, 14.2, 15.3);
        p.lineTo(13.2, 15.8);
        p.curveTo(11.2, 14.6, 9.4, 12.8, 8.2, 10.8);
        p.lineTo(8.7, 9.8);
        p.curveTo(9.2, 8.8, 9.0, 7.5, 8.2, 6.8);
        p.closePath();
        g.draw(p);
    }

    private static void paintCallEnd(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(4.5, 14.5);
        p.curveTo(5.5, 11.5, 8.5, 9.5, 12.0, 9.5);
        p.curveTo(15.5, 9.5, 18.5, 11.5, 19.5, 14.5);
        p.lineTo(16.5, 16.5);
        p.curveTo(15.5, 15.5, 14.5, 14.8, 13.5, 14.5);
        p.lineTo(13.5, 16.0);
        p.curveTo(13.5, 16.8, 12.8, 17.5, 12.0, 17.5);
        p.curveTo(11.2, 17.5, 10.5, 16.8, 10.5, 16.0);
        p.lineTo(10.5, 14.5);
        p.curveTo(9.5, 14.8, 8.5, 15.5, 7.5, 16.5);
        p.closePath();
        g.draw(p);
    }

    private static void paintMicOff(Graphics2D g) {
        paintMic(g);
        line(g, 4.0, 4.0, 20.0, 20.0);
    }
    private static void paintVideo(Graphics2D g) {
        g.draw(new RoundRectangle2D.Float(3f, 6.5f, 12f, 11f, 3f, 3f));
        Path2D p = new Path2D.Float();
        p.moveTo(15f, 10.5f);
        p.lineTo(21f, 7f);
        p.lineTo(21f, 17f);
        p.lineTo(15f, 13.5f);
        p.closePath();
        g.draw(p);
    }

    private static void paintVideoOff(Graphics2D g) {
        paintVideo(g);
        line(g, 4.0, 4.0, 20.0, 20.0);
    }

    private static void paintScreen(Graphics2D g) {
        g.draw(new RoundRectangle2D.Float(3.5f, 4.5f, 17f, 12f, 2.5f, 2.5f));
        line(g, 9.5, 19.5, 14.5, 19.5);
        line(g, 12.0, 16.5, 12.0, 19.5);
    }
}
