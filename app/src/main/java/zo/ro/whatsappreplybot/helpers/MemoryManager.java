package zo.ro.whatsappreplybot.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * HarshuVision — MemoryManager
 *
 * Manages the AI context window per contact session.
 *
 * Logic:
 *   - Each session has a max message capacity (configurable, default 100)
 *   - When a session reaches 75% of capacity → session is "near full"
 *   - DatabaseHelper.getOrCreateActiveSession() checks this and auto-rotates
 *   - Context history sent to AI = last min(historySize, msgCount) messages
 *
 * Settings stored in SharedPreferences:
 *   - max_messages_per_session  (default 100)
 *   - history_messages_for_ai   (default 20 — how many recent msgs sent to Groq)
 *   - groq_api_key
 *   - auto_reply_enabled
 */
public class MemoryManager {

    private static final String TAG   = "MemoryManager";
    private static final String PREFS = "harshu_memory_prefs";

    // SharedPreference keys
    public static final String KEY_MAX_MESSAGES     = "max_messages_per_session";
    public static final String KEY_HISTORY_SIZE     = "history_messages_for_ai";
    public static final String KEY_GROQ_API_KEY     = "groq_api_key";
    public static final String KEY_AUTO_REPLY       = "auto_reply_enabled";
    public static final String KEY_REPLY_DELAY_MS   = "reply_delay_ms";

    // Defaults
    public static final int    DEFAULT_MAX_MESSAGES  = 100;
    public static final int    DEFAULT_HISTORY_SIZE  = 20;
    public static final int    DEFAULT_DELAY_MS      = 1500;
    public static final int    THRESHOLD_PERCENT     = 75;

    private final SharedPreferences prefs;

    private static MemoryManager instance;
    public static synchronized MemoryManager getInstance(Context ctx) {
        if (instance == null)
            instance = new MemoryManager(ctx.getApplicationContext());
        return instance;
    }

    private MemoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public int getMaxMessages() {
        return prefs.getInt(KEY_MAX_MESSAGES, DEFAULT_MAX_MESSAGES);
    }

    public int getHistorySize() {
        return prefs.getInt(KEY_HISTORY_SIZE, DEFAULT_HISTORY_SIZE);
    }

    public String getGroqApiKey() {
        return prefs.getString(KEY_GROQ_API_KEY, "");
    }

    public boolean isAutoReplyEnabled() {
        return prefs.getBoolean(KEY_AUTO_REPLY, true);
    }

    public int getReplyDelayMs() {
        return prefs.getInt(KEY_REPLY_DELAY_MS, DEFAULT_DELAY_MS);
    }

    // ── Setters ────────────────────────────────────────────────────────────

    public void setMaxMessages(int max) {
        prefs.edit().putInt(KEY_MAX_MESSAGES, max).apply();
        Log.d(TAG, "Max messages per session set to " + max);
    }

    public void setHistorySize(int size) {
        prefs.edit().putInt(KEY_HISTORY_SIZE, size).apply();
    }

    public void setGroqApiKey(String key) {
        prefs.edit().putString(KEY_GROQ_API_KEY, key).apply();
    }

    public void setAutoReplyEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_REPLY, enabled).apply();
    }

    public void setReplyDelayMs(int ms) {
        prefs.edit().putInt(KEY_REPLY_DELAY_MS, ms).apply();
    }

    // ── Context Calculation ────────────────────────────────────────────────

    /**
     * Calculate how full the context is as a percentage.
     * @param messageCount current messages in session
     * @return 0–100 (or more if overflow)
     */
    public int calculateContextPercent(int messageCount) {
        int max = getMaxMessages();
        if (max <= 0) max = DEFAULT_MAX_MESSAGES;
        return (int) ((messageCount * 100.0) / max);
    }

    /**
     * Returns a human-readable status string for UI display.
     */
    public String getContextStatusLabel(int messageCount) {
        int pct = calculateContextPercent(messageCount);
        if (pct < 50)  return "🟢 " + pct + "% used";
        if (pct < 75)  return "🟡 " + pct + "% used";
        return "🔴 " + pct + "% — session closed, new one started";
    }

    /**
     * True if session should be rotated.
     */
    public boolean shouldRotateSession(int messageCount) {
        return calculateContextPercent(messageCount) >= THRESHOLD_PERCENT;
    }

    // ── Validation ─────────────────────────────────────────────────────────

    public boolean isGroqKeyValid() {
        String key = getGroqApiKey();
        return key != null && key.startsWith("gsk_") && key.length() > 20;
    }
}
