package zo.ro.whatsappreplybot.apis;

import android.content.Context;
import android.util.Log;

import java.util.List;

import zo.ro.whatsappreplybot.helpers.DatabaseHelper;
import zo.ro.whatsappreplybot.helpers.MemoryManager;
import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

public class CustomReplyGenerator {

    private static final String TAG = "CustomReply";

    public interface ReplyCallback {
        void onReply(String reply);
        void onError(String error);
    }

    private final Context        context;
    private final DatabaseHelper db;
    private final MemoryManager  memory;

    public CustomReplyGenerator(Context context) {
        this.context = context;
        this.db      = DatabaseHelper.getInstance(context);
        this.memory  = MemoryManager.getInstance(context);
    }

    public void generateReply(String sender, String message, ReplyCallback callback) {
        new Thread(() -> {
            try {
                long contactId      = db.getOrCreateContact(sender, "whatsapp");
                ChatSession session = db.getOrCreateActiveSession(contactId, memory.getMaxMessages());
                List<ChatMessage> history = db.getRecentMessages(
                        session.getId(), memory.getHistorySize());

                // Build a simple rule-based reply using admin memory + rules
                String adminMemory = db.buildAdminMemoryBlock();
                String rules       = db.buildActiveRulesBlock();

                StringBuilder ctx = new StringBuilder();
                ctx.append(adminMemory).append("\n");
                ctx.append(rules).append("\n");
                ctx.append("Recent history:\n");
                for (ChatMessage m : history) {
                    ctx.append(m.getSenderName()).append(": ").append(m.getMessageText()).append("\n");
                    if (m.getAiReply() != null) ctx.append("Bot: ").append(m.getAiReply()).append("\n");
                }
                ctx.append(sender).append(": ").append(message);

                // Custom logic — override this with your own reply engine
                String reply = "Received: " + message;
                callback.onReply(reply);

            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
}
