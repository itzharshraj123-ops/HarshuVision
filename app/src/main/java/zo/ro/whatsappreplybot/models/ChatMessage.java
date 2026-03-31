package zo.ro.whatsappreplybot.models;

public class ChatMessage {
    private long id;
    private long sessionId;
    private long contactId;
    private String senderName;
    private String messageText;
    private String aiReply;
    private long timestamp;
    private String platform; // whatsapp, whatsapp_business, telegram

    public ChatMessage() {}

    public ChatMessage(long sessionId, long contactId, String senderName,
                       String messageText, String aiReply, long timestamp, String platform) {
        this.sessionId   = sessionId;
        this.contactId   = contactId;
        this.senderName  = senderName;
        this.messageText = messageText;
        this.aiReply     = aiReply;
        this.timestamp   = timestamp;
        this.platform    = platform;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public long getId()                     { return id; }
    public void setId(long id)              { this.id = id; }

    public long getSessionId()              { return sessionId; }
    public void setSessionId(long v)        { this.sessionId = v; }

    public long getContactId()              { return contactId; }
    public void setContactId(long v)        { this.contactId = v; }

    public String getSenderName()           { return senderName; }
    public void setSenderName(String v)     { this.senderName = v; }

    public String getMessageText()          { return messageText; }
    public void setMessageText(String v)    { this.messageText = v; }

    public String getAiReply()              { return aiReply; }
    public void setAiReply(String v)        { this.aiReply = v; }

    public long getTimestamp()              { return timestamp; }
    public void setTimestamp(long v)        { this.timestamp = v; }

    public String getPlatform()             { return platform; }
    public void setPlatform(String v)       { this.platform = v; }

    @Override
    public String toString() {
        return "[" + senderName + "] " + messageText + " → " + aiReply;
    }
}
