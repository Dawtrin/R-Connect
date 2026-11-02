package com.p2p.client.ui.social;

import com.p2p.client.network.social.*;
import com.p2p.client.ui.AppIcon;
import com.p2p.client.ui.Brand;
import com.p2p.client.ui.Dialogs;
import com.p2p.client.ui.ModernUI;
import com.p2p.client.ui.NavItem;
import com.p2p.client.ui.Theme;
import com.p2p.common.social.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.prefs.Preferences;
import static com.p2p.client.ui.social.Values.*;
import com.p2p.client.ui.social.SocialWindow.Page;

/** Shared helpers for extracted screens: forwards to the window so screen code reads the same as before. */
abstract class ViewSupport {
    final SocialWindow app;
    boolean isActive() { return app.isActive(); }
    ViewSupport(SocialWindow app) { this.app = app; }

    void shutdown() { app.shutdown(); }
    JButton button(String text, Runnable action) { return app.button(text, action); }
    JButton button(String text, Icon icon, Runnable action) { return app.button(text, icon, action); }
    JButton ghost(String text, Icon icon, Runnable action) { return app.ghost(text, icon, action); }
    JButton ghost(String text, Runnable action) { return app.ghost(text, action); }
    JButton danger(String text, Runnable action) { return app.danger(text, action); }
    JButton primary(String text, Runnable action) { return app.primary(text, action); }
    JButton primary(String text, Icon icon, Runnable action) { return app.primary(text, icon, action); }
    void runAction(JButton source, Runnable action) { app.runAction(source, action); }
    JPanel horizontal(Component... components) { return app.horizontal(components); }
    JPanel vertical() { return app.vertical(); }
    JPanel card() { return app.card(); }
    JLabel label(String text, boolean heading) { return app.label(text, heading); }
    JTextArea text(String value, int rows) { return app.text(value, rows); }
    JScrollPane framed(JTextArea area) { return app.framed(area); }
    JTextArea readable(String value) { return app.readable(value); }
    JTextArea readableMessage(String value) { return app.readableMessage(value); }
    JScrollPane columnScroll(JPanel content, int maxWidth) { return app.columnScroll(content, maxWidth); }
    JScrollPane scroll(Component c) { return app.scroll(c); }
    static JPanel centered(JComponent child, int maxWidth) { return SocialWindow.centered(child, maxWidth); }
    JComponent avatar(Map<String, Object> user, int size) { return app.avatar(user, size); }
    JButton photo(String mediaId, String fileName) { return app.photo(mediaId, fileName); }
    JPanel titled(String title, Component... actions) { return app.titled(title, actions); }
    void styleTabs(JTabbedPane tabs) { app.styleTabs(tabs); }
    void setContext(JComponent component) { app.setContext(component); }
    void applyContextVisibility() { app.applyContextVisibility(); }
    void setView(String name, JPanel panel, Runnable refresh) { app.setView(name, panel, refresh); }
    void updateNavSelection(String currentView) { app.updateNavSelection(currentView); }
    void invoke(Runnable r) { app.invoke(r); }
    Map<String, Object> blocking(String type, Map<String, Object> payload, String key) { return app.blocking(type, payload, key); }
    Map<String, Object> blocking(SocialClient c, String type, Map<String, Object> payload, String key) { return app.blocking(c, type, payload, key); }
    void query(String type, Map<String, Object> payload, Consumer<Map<String, Object>> success) { app.query(type, payload, success); }
    void mutate(String type, Map<String, Object> payload, JButton source, Consumer<Map<String, Object>> success) { app.mutate(type, payload, source, success); }
    void command(String type, Map<String, Object> payload, String key, JButton source, Consumer<Map<String, Object>> success, boolean retry) { app.command(type, payload, key, source, success, retry); }
    Throwable unwrap(Throwable t) { return app.unwrap(t); }
    String friendly(Throwable error) { return app.friendly(error); }
    void showError(Throwable e) { app.showError(e); }
    void info(String s) { app.info(s); }
    void warn(String s) { app.warn(s); }
    boolean confirm(String s) { return app.confirm(s); }
    void showAuth(boolean register) { app.showAuth(register); }
    static boolean isConnectionProblem(Throwable e) { return SocialWindow.isConnectionProblem(e); }
    JLabel smallLabel(String text) { return app.smallLabel(text); }
    void addField(JPanel p, String name, Component input) { app.addField(p, name, input); }
    static void styleAuthInput(JTextField input) { SocialWindow.styleAuthInput(input); }
    void setupUdp(Map<String, Object> login) { app.setupUdp(login); }
    void refreshUdp() { app.refreshUdp(); }
    void showMain() { app.showMain(); }
    void installShortcuts() { app.installShortcuts(); }
    JLabel navSection(String text) { return app.navSection(text); }
    NavItem navButton(String key, String text, AppIcon.Type iconType, Runnable action) { return app.navButton(key, text, iconType, action); }
    void logout() { app.logout(); }
    void clearSession() { app.clearSession(); }
    void disconnected(Throwable error) { app.disconnected(error); }
    void disableButtons(Container c) { app.disableButtons(c); }
    void refreshCount() { app.refreshCount(); }
    void onEvent(WireMessage event) { app.onEvent(event); }
    void updateSelf(Map<String, Object> profile) { app.updateSelf(profile); }
    void invalidateVisiblePost(Container container, long postId) { app.invalidateVisiblePost(container, postId); }
    void showFeed() { app.showFeed(); }
    JComponent feedContext() { return app.feedContext(); }
    JButton fullWidthButton(String text, Runnable action) { return app.fullWidthButton(text, action); }
    JComponent friendRow(Map<String, Object> user, long uid, Map<Long, Theme.Avatar> faces, Map<Long, JLabel> states) { return app.friendRow(user, uid, faces, states); }
    JComponent postCard(Map<String, Object> post) { return app.postCard(post); }
    JButton authorLink(String text, Runnable action) { return app.authorLink(text, action); }
    JLabel muted(String text) { return app.muted(text); }
    String visibility(String code) { return app.visibility(code); }
    void composePost(Map<String, Object> existing) { app.composePost(existing); }
    void showPost(long postId) { app.showPost(postId); }
    JPanel actionRow(JComponent left, JButton secondary, JButton primaryAction) { return app.actionRow(left, secondary, primaryAction); }
    JDialog dialog(String title, int width, int height) { return app.dialog(title, width, height); }
    void showPeople() { app.showPeople(); }
    Map<String, Object> relationUser(Map<String, Object> r) { return app.relationUser(r); }
    void updatePresence(Container parent, long userId, boolean online, long asOf) { app.updatePresence(parent, userId, online, asOf); }
    void block(long uid, boolean blocked) { app.block(uid, blocked); }
    void showProfile(long uid) { app.showProfile(uid); }
    void showSettings() { app.showSettings(); }
    void openDirect(long target) { app.openDirect(target); }
    void receiveBroadcast(Map<String, Object> p) { app.receiveBroadcast(p); }
    void resetBroadcast() { app.resetBroadcast(); }
    void showBroadcast() { app.showBroadcast(); }
    void sendBroadcast(JTextArea input, JButton send, JLabel status) { app.sendBroadcast(input, send, status); }
    void renderBroadcasts() { app.renderBroadcasts(); }
    void showConversations() { app.showConversations(); }
    JPanel messagesShell(JComponent center) { return app.messagesShell(center); }
    void buildConversationList() { app.buildConversationList(); }
    JComponent conversationRow(Map<String, Object> conv) { return app.conversationRow(conv); }
    String shortStamp(long epochMillis) { return app.shortStamp(epochMillis); }
    void showGroupInvites() { app.showGroupInvites(); }
    JComponent chatContext(Map<String, Object> conv) { return app.chatContext(conv); }
    JLabel contextHeading(String text) { return app.contextHeading(text); }
    JComponent fullWidth(JComponent c) { return app.fullWidth(c); }
    void createGroup() { app.createGroup(); }
    void openChat(Map<String, Object> conv) { app.openChat(conv); }
    void loadHistory(boolean older) { app.loadHistory(older); }
    void syncChat(long cid) { app.syncChat(cid); }
    void scheduleConversationRefresh() { app.scheduleConversationRefresh(); }
    void upsertMessage(Map<String, Object> m) { app.upsertMessage(m); }
    int statusRank(String s) { return app.statusRank(s); }
    void ackMessages() { app.ackMessages(); }
    String translationKey(Map<String, Object> message) { return app.translationKey(message); }
    void requestTranslation(Map<String, Object> message) { app.requestTranslation(message); }
    JComponent translatedMessage(Map<String, Object> message) { return app.translatedMessage(message); }
    void renderMessages(boolean bottom) { app.renderMessages(bottom); }
    String messageStatus(String status) { return app.messageStatus(status); }
    void sendMessage(long cid, String mediaId, String content) { app.sendMessage(cid, mediaId, content); }
    void submitMessage(PendingMessage p) { app.submitMessage(p); }
    void retryMessage(PendingMessage p) { app.retryMessage(p); }
    void groupInfo(Map<String, Object> original) { app.groupInfo(original); }
    void selectFriend(LongConsumer selection) { app.selectFriend(selection); }
    void showNotifications() { app.showNotifications(); }
    String notificationTitle(String type) { return app.notificationTitle(type); }
    String roleText(String role) { return app.roleText(role); }
    String targetText(String type) { return app.targetText(type); }
    String statusText(String status) { return app.statusText(status); }
    void report(String type, long target) { app.report(type, target); }
    void showAdmin() { app.showAdmin(); }
    void reviewReport(long reportId) { app.reviewReport(reportId); }
    void showRecord(String title, Map<String, Object> record) { app.showRecord(title, record); }
    String recordText(Map<String, Object> data) { return app.recordText(data); }
    int getWidth() { return app.getWidth(); }
    int getHeight() { return app.getHeight(); }
    boolean isShowing() { return app.isShowing(); }
    JRootPane getRootPane() { return app.getRootPane(); }
    Window[] getOwnedWindows() { return app.getOwnedWindows(); }
    Container getContentPane() { return app.getContentPane(); }
    Point getLocationOnScreen() { return app.getLocationOnScreen(); }
}
