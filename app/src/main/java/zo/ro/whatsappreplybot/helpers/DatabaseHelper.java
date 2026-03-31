package zo.ro.whatsappreplybot.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

/**
 * HarshuVision — DatabaseHelper
 *
 * Tables:
 *   contacts      — unique person per platform
 *   chat_sessions — context windows per contact (rotated at 75%)
 *   chat_messages — sender / message / ai_reply per session
 *   admin_memory  — "Harshu memory+" key-value store
 *   admin_rules   — engineering commands / restrictions
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG      = "HarshuDB";
    private static final String DB_NAME  = "harshu_vision.db";
    private static final int    DB_VER   = 3;

    // ── Table names ────────────────────────────────────────────────────────
    private static final String TBL_CONTACTS = "contacts";
    private static final String TBL_SESSIONS = "chat_sessions";
    private static final String TBL_MESSAGES = "chat_messages";
    private static final String TBL_MEMORY   = "admin_memory";
    private static final String TBL_RULES    = "admin_rules";

    // ── contacts columns ───────────────────────────────────────────────────
    private static final String COL_C_ID       = "id";
    private static final String COL_C_NAME     = "name";
    private static final String COL_C_PHONE    = "phone";       // nullable
    private static final String COL_C_PLATFORM = "platform";   // whatsapp | telegram | etc.
    private static final String COL_C_CREATED  = "created_at";

    // ── chat_sessions columns ──────────────────────────────────────────────
    private static final String COL_S_ID         = "id";
    private static final String COL_S_CONTACT_ID = "contact_id";
    private static final String COL_S_NAME       = "session_name";
    private static final String COL_S_CREATED    = "created_at";
    private static final String COL_S_ACTIVE     = "is_active";
    private static final String COL_S_MSG_COUNT  = "message_count";
    private static final String COL_S_MAX_MSG    = "max_messages";

    // ── chat_messages columns ──────────────────────────────────────────────
    private static final String COL_M_ID         = "id";
    private static final String COL_M_SESSION_ID = "session_id";
    private static final String COL_M_CONTACT_ID = "contact_id";
    private static final String COL_M_SENDER     = "sender_name";
    private static final String COL_M_MESSAGE    = "message_text";
    private static final String COL_M_AI_REPLY   = "ai_reply";
    private static final String COL_M_TIMESTAMP  = "timestamp";
    private static final String COL_M_PLATFORM   = "platform";

    // ── admin_memory columns ───────────────────────────────────────────────
    private static final String COL_MEM_ID      = "id";
    private static final String COL_MEM_KEY     = "mem_key";
    private static final String COL_MEM_VALUE   = "mem_value";
    private static final String COL_MEM_UPDATED = "updated_at";

    // ── admin_rules columns ────────────────────────────────────────────────
    private static final String COL_R_ID        = "id";
    private static final String COL_R_TYPE      = "rule_type";   // restriction | persona | tone | etc.
    private static final String COL_R_VALUE     = "rule_value";
    private static final String COL_R_ACTIVE    = "is_active";
    private static final String COL_R_CREATED   = "created_at";

    // ── Singleton ──────────────────────────────────────────────────────────
    private static DatabaseHelper instance;
    public static synchronized DatabaseHelper getInstance(Context ctx) {
        if (instance == null)
            instance = new DatabaseHelper(ctx.getApplicationContext());
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VER);
    }

    // ── onCreate ───────────────────────────────────────────────────────────
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TBL_CONTACTS + " ("
                + COL_C_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_C_NAME     + " TEXT NOT NULL, "
                + COL_C_PHONE    + " TEXT, "
                + COL_C_PLATFORM + " TEXT NOT NULL DEFAULT 'whatsapp', "
                + COL_C_CREATED  + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE " + TBL_SESSIONS + " ("
                + COL_S_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_S_CONTACT_ID + " INTEGER NOT NULL, "
                + COL_S_NAME       + " TEXT, "
                + COL_S_CREATED    + " INTEGER NOT NULL, "
                + COL_S_ACTIVE     + " INTEGER NOT NULL DEFAULT 1, "
                + COL_S_MSG_COUNT  + " INTEGER NOT NULL DEFAULT 0, "
                + COL_S_MAX_MSG    + " INTEGER NOT NULL DEFAULT 100, "
                + "FOREIGN KEY(" + COL_S_CONTACT_ID + ") REFERENCES " + TBL_CONTACTS + "(" + COL_C_ID + ")"
                + ");");

        db.execSQL("CREATE TABLE " + TBL_MESSAGES + " ("
                + COL_M_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_M_SESSION_ID + " INTEGER NOT NULL, "
                + COL_M_CONTACT_ID + " INTEGER NOT NULL, "
                + COL_M_SENDER     + " TEXT NOT NULL, "
                + COL_M_MESSAGE    + " TEXT NOT NULL, "
                + COL_M_AI_REPLY   + " TEXT, "
                + COL_M_TIMESTAMP  + " INTEGER NOT NULL, "
                + COL_M_PLATFORM   + " TEXT NOT NULL DEFAULT 'whatsapp', "
                + "FOREIGN KEY(" + COL_M_SESSION_ID + ") REFERENCES " + TBL_SESSIONS + "(" + COL_S_ID + ")"
                + ");");

        db.execSQL("CREATE TABLE " + TBL_MEMORY + " ("
                + COL_MEM_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_MEM_KEY     + " TEXT UNIQUE NOT NULL, "
                + COL_MEM_VALUE   + " TEXT NOT NULL, "
                + COL_MEM_UPDATED + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE " + TBL_RULES + " ("
                + COL_R_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_R_TYPE    + " TEXT NOT NULL, "
                + COL_R_VALUE   + " TEXT NOT NULL, "
                + COL_R_ACTIVE  + " INTEGER NOT NULL DEFAULT 1, "
                + COL_R_CREATED + " INTEGER NOT NULL"
                + ");");

        // Seed default admin memory
        long now = System.currentTimeMillis();
        db.execSQL("INSERT INTO " + TBL_MEMORY + " VALUES(NULL,'admin_name','Harshu'," + now + ");");
        db.execSQL("INSERT INTO " + TBL_MEMORY + " VALUES(NULL,'admin_language','Hinglish'," + now + ");");
        db.execSQL("INSERT INTO " + TBL_MEMORY + " VALUES(NULL,'admin_persona','Friendly, helpful, concise'," + now + ");");
        db.execSQL("INSERT INTO " + TBL_MEMORY + " VALUES(NULL,'admin_context','I am Harshu. I run a business. Reply on my behalf professionally but warmly.'," + now + ");");

        Log.d(TAG, "Database created with all 5 tables");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TBL_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TBL_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TBL_CONTACTS);
        db.execSQL("DROP TABLE IF EXISTS " + TBL_MEMORY);
        db.execSQL("DROP TABLE IF EXISTS " + TBL_RULES);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTACTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get or create a contact.
     * If record exists (name + platform match) → return existing id.
     * Else → insert new record.
     */
    public long getOrCreateContact(String name, String platform) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TBL_CONTACTS,
                new String[]{COL_C_ID},
                COL_C_NAME + "=? AND " + COL_C_PLATFORM + "=?",
                new String[]{name, platform},
                null, null, null);

        if (c.moveToFirst()) {
            long id = c.getLong(0);
            c.close();
            return id;
        }
        c.close();

        ContentValues cv = new ContentValues();
        cv.put(COL_C_NAME,     name);
        cv.put(COL_C_PLATFORM, platform);
        cv.put(COL_C_CREATED,  System.currentTimeMillis());
        long id = db.insert(TBL_CONTACTS, null, cv);
        Log.d(TAG, "New contact created: " + name + " [" + platform + "] id=" + id);
        return id;
    }

    public List<String> getAllContactNames() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_C_NAME + " FROM " + TBL_CONTACTS
                + " ORDER BY " + COL_C_NAME + " ASC", null);
        while (c.moveToNext()) list.add(c.getString(0));
        c.close();
        return list;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SESSIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get active session for a contact.
     * If none exists or current is ≥75% full → create new session.
     */
    public ChatSession getOrCreateActiveSession(long contactId, int maxMessages) {
        SQLiteDatabase db = getWritableDatabase();

        // Find existing active session
        Cursor c = db.query(TBL_SESSIONS,
                null,
                COL_S_CONTACT_ID + "=? AND " + COL_S_ACTIVE + "=1",
                new String[]{String.valueOf(contactId)},
                null, null, COL_S_CREATED + " DESC", "1");

        if (c.moveToFirst()) {
            ChatSession session = cursorToSession(c);
            c.close();
            session.setMaxMessages(maxMessages);

            // Check 75% threshold
            if (session.isNearFull()) {
                Log.d(TAG, "Session " + session.getId() + " reached 75% — rotating.");
                closeSession(session.getId());
                return createNewSession(db, contactId, maxMessages);
            }
            return session;
        }
        c.close();
        return createNewSession(db, contactId, maxMessages);
    }

    private ChatSession createNewSession(SQLiteDatabase db, long contactId, int maxMessages) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put(COL_S_CONTACT_ID, contactId);
        cv.put(COL_S_NAME,       "Chat " + now);
        cv.put(COL_S_CREATED,    now);
        cv.put(COL_S_ACTIVE,     1);
        cv.put(COL_S_MSG_COUNT,  0);
        cv.put(COL_S_MAX_MSG,    maxMessages);
        long id = db.insert(TBL_SESSIONS, null, cv);

        ChatSession session = new ChatSession(contactId, "Chat " + now, now, maxMessages);
        session.setId(id);
        Log.d(TAG, "New session created id=" + id + " for contact=" + contactId);
        return session;
    }

    public void closeSession(long sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_S_ACTIVE, 0);
        db.update(TBL_SESSIONS, cv, COL_S_ID + "=?", new String[]{String.valueOf(sessionId)});
    }

    public void renameSession(long sessionId, String newName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_S_NAME, newName);
        db.update(TBL_SESSIONS, cv, COL_S_ID + "=?", new String[]{String.valueOf(sessionId)});
    }

    private void incrementSessionCount(long sessionId) {
        getWritableDatabase().execSQL(
                "UPDATE " + TBL_SESSIONS + " SET " + COL_S_MSG_COUNT + "=" + COL_S_MSG_COUNT + "+1"
                        + " WHERE " + COL_S_ID + "=" + sessionId);
    }

    public List<ChatSession> getSessionsForContact(long contactId) {
        List<ChatSession> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_SESSIONS, null,
                COL_S_CONTACT_ID + "=?",
                new String[]{String.valueOf(contactId)},
                null, null, COL_S_CREATED + " DESC");
        while (c.moveToNext()) list.add(cursorToSession(c));
        c.close();
        return list;
    }

    private ChatSession cursorToSession(Cursor c) {
        ChatSession s = new ChatSession();
        s.setId(c.getLong(c.getColumnIndexOrThrow(COL_S_ID)));
        s.setContactId(c.getLong(c.getColumnIndexOrThrow(COL_S_CONTACT_ID)));
        s.setSessionName(c.getString(c.getColumnIndexOrThrow(COL_S_NAME)));
        s.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(COL_S_CREATED)));
        s.setActive(c.getInt(c.getColumnIndexOrThrow(COL_S_ACTIVE)) == 1);
        s.setMessageCount(c.getInt(c.getColumnIndexOrThrow(COL_S_MSG_COUNT)));
        s.setMaxMessages(c.getInt(c.getColumnIndexOrThrow(COL_S_MAX_MSG)));
        return s;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MESSAGES
    // ══════════════════════════════════════════════════════════════════════

    public long saveMessage(ChatMessage msg) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_M_SESSION_ID, msg.getSessionId());
        cv.put(COL_M_CONTACT_ID, msg.getContactId());
        cv.put(COL_M_SENDER,     msg.getSenderName());
        cv.put(COL_M_MESSAGE,    msg.getMessageText());
        cv.put(COL_M_AI_REPLY,   msg.getAiReply());
        cv.put(COL_M_TIMESTAMP,  msg.getTimestamp());
        cv.put(COL_M_PLATFORM,   msg.getPlatform());
        long id = db.insert(TBL_MESSAGES, null, cv);
        incrementSessionCount(msg.getSessionId());
        return id;
    }

    /** Get last N messages for a session (for AI context window) */
    public List<ChatMessage> getRecentMessages(long sessionId, int limit) {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + TBL_MESSAGES
                        + " WHERE " + COL_M_SESSION_ID + "=?"
                        + " ORDER BY " + COL_M_TIMESTAMP + " DESC LIMIT " + limit,
                new String[]{String.valueOf(sessionId)});
        while (c.moveToNext()) list.add(0, cursorToMessage(c)); // reverse to chronological
        c.close();
        return list;
    }

    /** Get ALL messages for a session (for history view) */
    public List<ChatMessage> getMessagesForSession(long sessionId) {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_MESSAGES, null,
                COL_M_SESSION_ID + "=?",
                new String[]{String.valueOf(sessionId)},
                null, null, COL_M_TIMESTAMP + " ASC");
        while (c.moveToNext()) list.add(cursorToMessage(c));
        c.close();
        return list;
    }

    private ChatMessage cursorToMessage(Cursor c) {
        ChatMessage m = new ChatMessage();
        m.setId(c.getLong(c.getColumnIndexOrThrow(COL_M_ID)));
        m.setSessionId(c.getLong(c.getColumnIndexOrThrow(COL_M_SESSION_ID)));
        m.setContactId(c.getLong(c.getColumnIndexOrThrow(COL_M_CONTACT_ID)));
        m.setSenderName(c.getString(c.getColumnIndexOrThrow(COL_M_SENDER)));
        m.setMessageText(c.getString(c.getColumnIndexOrThrow(COL_M_MESSAGE)));
        m.setAiReply(c.getString(c.getColumnIndexOrThrow(COL_M_AI_REPLY)));
        m.setTimestamp(c.getLong(c.getColumnIndexOrThrow(COL_M_TIMESTAMP)));
        m.setPlatform(c.getString(c.getColumnIndexOrThrow(COL_M_PLATFORM)));
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADMIN MEMORY (Harshu Memory+)
    // ══════════════════════════════════════════════════════════════════════

    public void setMemory(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MEM_KEY,     key);
        cv.put(COL_MEM_VALUE,   value);
        cv.put(COL_MEM_UPDATED, System.currentTimeMillis());
        // Insert or replace
        db.insertWithOnConflict(TBL_MEMORY, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getMemory(String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_MEMORY, new String[]{COL_MEM_VALUE},
                COL_MEM_KEY + "=?", new String[]{key},
                null, null, null);
        String val = null;
        if (c.moveToFirst()) val = c.getString(0);
        c.close();
        return val;
    }

    /** Build complete admin memory block for AI system prompt */
    public String buildAdminMemoryBlock() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_MEMORY, new String[]{COL_MEM_KEY, COL_MEM_VALUE},
                null, null, null, null, COL_MEM_KEY + " ASC");
        StringBuilder sb = new StringBuilder("=== HARSHU MEMORY+ ===\n");
        while (c.moveToNext()) {
            sb.append(c.getString(0)).append(": ").append(c.getString(1)).append("\n");
        }
        c.close();
        return sb.toString();
    }

    public List<String[]> getAllMemoryEntries() {
        List<String[]> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_MEMORY, new String[]{COL_MEM_ID, COL_MEM_KEY, COL_MEM_VALUE},
                null, null, null, null, COL_MEM_KEY + " ASC");
        while (c.moveToNext()) {
            list.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
        }
        c.close();
        return list;
    }

    public void deleteMemory(long id) {
        getWritableDatabase().delete(TBL_MEMORY, COL_MEM_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADMIN RULES (Engineering Commands)
    // ══════════════════════════════════════════════════════════════════════

    public long addRule(String type, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_R_TYPE,    type);
        cv.put(COL_R_VALUE,   value);
        cv.put(COL_R_ACTIVE,  1);
        cv.put(COL_R_CREATED, System.currentTimeMillis());
        return db.insert(TBL_RULES, null, cv);
    }

    public void toggleRule(long ruleId, boolean active) {
        ContentValues cv = new ContentValues();
        cv.put(COL_R_ACTIVE, active ? 1 : 0);
        getWritableDatabase().update(TBL_RULES, cv,
                COL_R_ID + "=?", new String[]{String.valueOf(ruleId)});
    }

    public void deleteRule(long ruleId) {
        getWritableDatabase().delete(TBL_RULES, COL_R_ID + "=?",
                new String[]{String.valueOf(ruleId)});
    }

    /** Build active rules block for AI system prompt */
    public String buildActiveRulesBlock() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_RULES, new String[]{COL_R_TYPE, COL_R_VALUE},
                COL_R_ACTIVE + "=1", null, null, null, COL_R_TYPE + " ASC");
        if (!c.moveToFirst()) { c.close(); return ""; }
        StringBuilder sb = new StringBuilder("=== ACTIVE RULES ===\n");
        do {
            sb.append("[").append(c.getString(0)).append("] ").append(c.getString(1)).append("\n");
        } while (c.moveToNext());
        c.close();
        return sb.toString();
    }

    public List<String[]> getAllRules() {
        List<String[]> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TBL_RULES,
                new String[]{COL_R_ID, COL_R_TYPE, COL_R_VALUE, COL_R_ACTIVE},
                null, null, null, null, COL_R_TYPE + " ASC");
        while (c.moveToNext()) {
            list.add(new String[]{c.getString(0), c.getString(1),
                    c.getString(2), c.getString(3)});
        }
        c.close();
        return list;
    }
}
