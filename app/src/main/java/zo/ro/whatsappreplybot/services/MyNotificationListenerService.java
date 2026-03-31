package zo.ro.whatsappreplybot.services;

import android.app.Notification;
import android.app.RemoteInput;
import android.content.Intent;
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
import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

public class MyNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "HarshuListener";

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
            Log.w(TAG, "Groq API key not set — skipping");
            return;
        }

        String pkg = sbn.getPackageName();
        if (!isSupportedPackage(pkg)) return;

        String platform = resolvePlatform(pkg);
        Bundle extras   = sbn.getNotification().extras;
        if (extras == null) return;

        String senderName  = extractText(extras, Notification.EXTRA_TITLE);
        String messageText = extractText(extras, Notification.EXTRA_TEXT);

        if (senderName == null || senderName.isEmpty()) return;
        if (messageText == null || messageText.isEmpty()) return;
        if (senderName.contains(":") || messageText.startsWith("Messages from")) return;

        Log.d(TAG, "Message from [" + senderName + "] on " + platform + ": " + messageText);

        long contactId      = db.getOrCreateContact(senderName, platform);
        ChatSession session = db.getOrCreateActiveSession(contactId, memory.getMaxMessages());

        String adminMemory        = db.buildAdminMemoryBlock();
        String activeRules        = db.buildActiveRulesBlock();
        List<ChatMessage> history = db.getRecentMessages(session.getId(), memory.getHistorySize());

        Notification.Action[] actions = sbn.getNotification().actions;
        long sessionId = session.getId();

        new GroqReplyGenerator(memory.getGroqApiKey()).generateReply(
                senderName, messageText, adminMemory, activeRules, history,
                new GroqReplyGenerator.ReplyCallback() {
                    @Override
                    public void onReply(String reply) {
                        Log.d(TAG, "Reply: " + reply);

                        ChatMessage msg = new ChatMessage(
                                sessionId, contactId, senderName,
                                messageText, reply,
                                System.currentTimeMillis(), platform);
                        db.saveMessage(msg);

                        new Handler(Looper.getMainLooper()).postDelayed(() ->
                                sendReply(actions, reply, sbn.getKey()),
                                memory.getReplyDelayMs());
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Groq error: " + error);
                        ChatMessage msg = new ChatMessage(
                                sessionId, contactId, senderName,
                                messageText, "[AI error: " + error + "]",
                                System.currentTimeMillis(), platform);
                        db.saveMessage(msg);
                    }
                });
    }

    private void sendReply(Notification.Action[] actions, String replyText, String notifKey) {
        if (actions == null || replyText == null || replyText.isEmpty()) return;

        for (Notification.Action action : actions) {
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs != null && remoteInputs.length > 0) {
                try {
                    Intent intent = new Intent();
                    Bundle results = new Bundle();
                    results.putCharSequence(remoteInputs[0].getResultKey(), replyText);
                    RemoteInput.addResultsToIntent(remoteInputs, intent, results);
                    action.actionIntent.send(getApplicationContext(), 0, intent);
                    Log.d(TAG, "Reply sent: " + replyText);
                    cancelNotification(notifKey);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Reply failed", e);
                }
            }
        }
        Log.w(TAG, "No reply action found");
    }

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
}
