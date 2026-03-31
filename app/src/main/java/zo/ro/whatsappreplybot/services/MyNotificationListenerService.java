package zo.ro.whatsappreplybot.services;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

import zo.ro.whatsappreplybot.apis.GroqReplyGenerator;
import zo.ro.whatsappreplybot.helpers.DatabaseHelper;
import zo.ro.whatsappreplybot.helpers.MemoryManager;
import zo.ro.whatsappreplybot.helpers.NotificationHelper;
import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

/**
 * HarshuVision — MyNotificationListenerService
 *
 * Listens to notifications from:
 *   - com.whatsapp              (WhatsApp)
 *   - com.whatsapp.w4b          (WhatsApp Business)
 *   - org.telegram.messenger    (Telegram)
 *
 * Flow:
 *   1. Notification arrives → extract sender + message + platform
 *   2. Get/create contact in DB
 *   3. Get/create active session (auto-rotates at 75%)
 *   4. Load Harshu Memory+ + Active Rules + Chat History
 *   5. Send to Groq → get smart reply
 *   6. Save to DB (sender / message / ai_reply)
 *   7. Auto-reply via notification action
 */
public class MyNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "HarshuListener";

    // Supported packages
    private static final String PKG_WHATSAPP    = "com.whatsapp";
    private static final String PKG_WA_BUSINESS = "com.whatsapp.w4b";
    private static final String PKG_TELEGRAM    = "org.telegram.messenger";

    private DatabaseHelper db;
    private MemoryManager  memory;

    @Override
    public void onCreate() {
        super.onCreate();
        db     = DatabaseHelper.getInstance(this);
        memory = MemoryManager.getInstance(this);
        Log.d(TAG, "HarshuVision NotificationListener started");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!memory.isAutoReplyEnabled()) return;
        if (!memory.isGroqKeyValid()) {
            Log.w(TAG, "Groq API key not set or invalid — skipping");
            return;
        }

        String pkg = sbn.getPackageName();
        if (!isSupportedPackage(pkg)) return;

        String platform = resolvePlatform(pkg);
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        if (extras == null) return;

        // Extract sender and message text
        String senderName = extractText(extras, Notification.EXTRA_TITLE);
        String messageText = extractText(extras, Notification.EXTRA_TEXT);

        if (senderName == null || senderName.isEmpty()) return;
        if (messageText == null || messageText.isEmpty()) return;

        // Skip group notifications (contain "@" in title or have multiple senders listed)
        if (senderName.contains(":") || messageText.startsWith("Messages from")) return;

        Log.d(TAG, "New message from [" + senderName + "] on " + platform + ": " + messageText);

        // ── DB: Get/create contact ─────────────────────────────────────────
        long contactId = db.getOrCreateContact(senderName, platform);

        // ── DB: Get/create active session ──────────────────────────────────
        ChatSession session = db.getOrCreateActiveSession(contactId, memory.getMaxMessages());

        // ── Build AI context ───────────────────────────────────────────────
        String adminMemory   = db.buildAdminMemoryBlock();
        String activeRules   = db.buildActiveRulesBlock();
        List<ChatMessage> history = db.getRecentMessages(session.getId(), memory.getHistorySize());

        // ── Generate reply via Groq ────────────────────────────────────────
        GroqReplyGenerator groq = new GroqReplyGenerator(memory.getGroqApiKey());

        // Capture notification action for auto-reply
        Notification.Action[] actions = notification.actions;

        String finalSenderName = senderName;
        String finalMessageText = messageText;
        long sessionId = session.getId();

        groq.generateReply(senderName, messageText, adminMemory, activeRules, history,
                new GroqReplyGenerator.ReplyCallback() {
                    @Override
                    public void onReply(String reply) {
                        Log.d(TAG, "Reply generated: " + reply);

                        // ── Save to DB ────────────────────────────────────
                        ChatMessage chatMsg = new ChatMessage(
                                sessionId,
                                contactId,
                                finalSenderName,
                                finalMessageText,
                                reply,
                                System.currentTimeMillis(),
                                platform);
                        db.saveMessage(chatMsg);

                        // ── Auto-reply via notification after delay ───────
                        int delayMs = memory.getReplyDelayMs();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            sendReplyViaNotification(actions, reply, sbn.getKey());
                        }, delayMs);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Groq error: " + error);

                        // Save the message without AI reply so history is preserved
                        ChatMessage chatMsg = new ChatMessage(
                                sessionId,
                                contactId,
                                finalSenderName,
                                finalMessageText,
                                "[AI error: " + error + "]",
                                System.currentTimeMillis(),
                                platform);
                        db.saveMessage(chatMsg);
                    }
                });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not used currently
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isSupportedPackage(String pkg) {
        return PKG_WHATSAPP.equals(pkg)
                || PKG_WA_BUSINESS.equals(pkg)
                || PKG_TELEGRAM.equals(pkg);
    }

    private String resolvePlatform(String pkg) {
        switch (pkg) {
            case PKG_WA_BUSINESS: return "whatsapp_business";
            case PKG_TELEGRAM:    return "telegram";
            default:              return "whatsapp";
        }
    }

    private String extractText(Bundle extras, String key) {
        CharSequence cs = extras.getCharSequence(key);
        return cs != null ? cs.toString().trim() : null;
    }

    /**
     * Send a reply via notification's RemoteInput action (direct reply).
     * WhatsApp/Telegram both support this via their reply action.
     */
    private void sendReplyViaNotification(Notification.Action[] actions,
                                           String replyText, String notificationKey) {
        if (actions == null || replyText == null || replyText.isEmpty()) return;

        for (Notification.Action action : actions) {
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                try {
                    Bundle results = new Bundle();
                    results.putCharSequence(
                            action.getRemoteInputs()[0].getResultKey(),
                            replyText);

                    android.app.RemoteInput.addResultsToIntent(
                            action.getRemoteInputs(), action.actionIntent.getIntent(), results);
                    action.actionIntent.send(getApplicationContext(), 0,
                            action.actionIntent.getIntent());

                    Log.d(TAG, "Auto-reply sent: " + replyText);

                    // Cancel the notification after replying
                    cancelNotification(notificationKey);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Auto-reply failed", e);
                }
            }
        }
        Log.w(TAG, "No reply action found in notification");
    }
}
