package zo.ro.whatsappreplybot.helpers;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

/**
 * WhatsAppMessageHandler — updated to use new DatabaseHelper singleton.
 * Old methods (insertMessage, deleteOldMessages, getChatHistoryBySender,
 * getAllMessagesBySender) replaced with new DB API.
 */
public class WhatsAppMessageHandler {

    private static final String TAG = "WAMsgHandler";

    private final DatabaseHelper db;
    private final MemoryManager  memory;

    public WhatsAppMessageHandler(Context context) {
        db     = DatabaseHelper.getInstance(context);
        memory = MemoryManager.getInstance(context);
    }

    /**
     * Save an incoming message + AI reply to the database.
     */
    public void saveMessage(String senderName, String messageText,
                            String aiReply, String platform) {
        try {
            long contactId      = db.getOrCreateContact(senderName, platform);
            ChatSession session = db.getOrCreateActiveSession(
                    contactId, memory.getMaxMessages());

            ChatMessage msg = new ChatMessage(
                    session.getId(),
                    contactId,
                    senderName,
                    messageText,
                    aiReply,
                    System.currentTimeMillis(),
                    platform);

            db.saveMessage(msg);
            Log.d(TAG, "Message saved for: " + senderName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save message", e);
        }
    }

    /**
     * Get recent chat history for a sender (for AI context).
     */
    public List<ChatMessage> getChatHistory(String senderName, String platform) {
        try {
            long contactId      = db.getOrCreateContact(senderName, platform);
            ChatSession session = db.getOrCreateActiveSession(
                    contactId, memory.getMaxMessages());
            return db.getRecentMessages(session.getId(), memory.getHistorySize());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get history", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get all messages for a sender across all sessions.
     */
    public List<ChatMessage> getAllMessages(String senderName, String platform) {
        try {
            long contactId      = db.getOrCreateContact(senderName, platform);
            List<ChatSession> sessions = db.getSessionsForContact(contactId);
            List<ChatMessage> all = new ArrayList<>();
            for (ChatSession s : sessions) {
                all.addAll(db.getMessagesForSession(s.getId()));
            }
            return all;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get all messages", e);
            return new ArrayList<>();
        }
    }
}
