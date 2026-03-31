package zo.ro.whatsappreplybot.models;

public class ChatSession {
    private long id;
    private long contactId;
    private String contactName;   // joined from contacts table
    private String sessionName;   // user-given name
    private long createdAt;
    private boolean isActive;
    private int messageCount;
    private int maxMessages;      // from MemoryManager settings
    private int contextUsagePercent;

    public ChatSession() {}

    public ChatSession(long contactId, String sessionName, long createdAt, int maxMessages) {
        this.contactId    = contactId;
        this.sessionName  = sessionName;
        this.createdAt    = createdAt;
        this.isActive     = true;
        this.messageCount = 0;
        this.maxMessages  = maxMessages;
        this.contextUsagePercent = 0;
    }

    /** Recalculate context % from current message count */
    public void recalcUsage() {
        if (maxMessages <= 0) maxMessages = 100;
        contextUsagePercent = (int) ((messageCount * 100.0) / maxMessages);
    }

    /** Returns true if session has reached 75% capacity */
    public boolean isNearFull() {
        recalcUsage();
        return contextUsagePercent >= 75;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public long getId()                          { return id; }
    public void setId(long id)                   { this.id = id; }

    public long getContactId()                   { return contactId; }
    public void setContactId(long v)             { this.contactId = v; }

    public String getContactName()               { return contactName; }
    public void setContactName(String v)         { this.contactName = v; }

    public String getSessionName()               { return sessionName; }
    public void setSessionName(String v)         { this.sessionName = v; }

    public long getCreatedAt()                   { return createdAt; }
    public void setCreatedAt(long v)             { this.createdAt = v; }

    public boolean isActive()                    { return isActive; }
    public void setActive(boolean v)             { this.isActive = v; }

    public int getMessageCount()                 { return messageCount; }
    public void setMessageCount(int v)           { this.messageCount = v; recalcUsage(); }

    public int getMaxMessages()                  { return maxMessages; }
    public void setMaxMessages(int v)            { this.maxMessages = v; recalcUsage(); }

    public int getContextUsagePercent()          { return contextUsagePercent; }
    public void setContextUsagePercent(int v)    { this.contextUsagePercent = v; }
}
