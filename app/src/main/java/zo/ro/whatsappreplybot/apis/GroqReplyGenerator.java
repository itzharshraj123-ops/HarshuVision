package zo.ro.whatsappreplybot.apis;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import zo.ro.whatsappreplybot.models.ChatMessage;

/**
 * HarshuVision — GroqReplyGenerator
 *
 * Uses Groq's OpenAI-compatible API.
 * Endpoint  : https://api.groq.com/openai/v1/chat/completions
 * Model     : llama-3.3-70b-versatile (fast, high quality)
 *
 * System prompt structure:
 *   1. Harshu Memory+  (who you are, your context)
 *   2. Active Rules    (restrictions, persona, tone)
 *   3. Chat history    (last N messages with this person)
 *   4. Current message
 */
public class GroqReplyGenerator {

    private static final String TAG      = "GroqReply";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.3-70b-versatile";
    private static final int    MAX_TOKENS = 512;

    public interface ReplyCallback {
        void onReply(String reply);
        void onError(String error);
    }

    private final OkHttpClient client;
    private final String apiKey;

    public GroqReplyGenerator(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate a smart reply.
     *
     * @param senderName   Name of the person who sent the message
     * @param newMessage   The incoming message text
     * @param adminMemory  Harshu Memory+ block (from DatabaseHelper.buildAdminMemoryBlock)
     * @param activeRules  Active rules block (from DatabaseHelper.buildActiveRulesBlock)
     * @param chatHistory  Recent chat messages (from DatabaseHelper.getRecentMessages)
     * @param callback     Result callback
     */
    public void generateReply(
            String senderName,
            String newMessage,
            String adminMemory,
            String activeRules,
            List<ChatMessage> chatHistory,
            ReplyCallback callback) {

        try {
            JSONArray messages = new JSONArray();

            // ── System prompt ──────────────────────────────────────────
            String systemContent = buildSystemPrompt(adminMemory, activeRules, senderName);
            messages.put(buildMessage("system", systemContent));

            // ── Chat history as alternating user/assistant turns ───────
            for (ChatMessage hist : chatHistory) {
                messages.put(buildMessage("user", hist.getSenderName() + ": " + hist.getMessageText()));
                if (hist.getAiReply() != null && !hist.getAiReply().isEmpty()) {
                    messages.put(buildMessage("assistant", hist.getAiReply()));
                }
            }

            // ── Current message ────────────────────────────────────────
            messages.put(buildMessage("user", senderName + ": " + newMessage));

            // ── Build request body ─────────────────────────────────────
            JSONObject body = new JSONObject();
            body.put("model",      MODEL);
            body.put("messages",   messages);
            body.put("max_tokens", MAX_TOKENS);
            body.put("temperature", 0.7);

            RequestBody reqBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type",  "application/json")
                    .post(reqBody)
                    .build();

            Log.d(TAG, "Sending to Groq → sender=" + senderName + " msg=" + newMessage);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Groq request failed", e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Groq error " + response.code() + ": " + bodyStr);
                        callback.onError("API error " + response.code() + ": " + bodyStr);
                        return;
                    }
                    try {
                        JSONObject json   = new JSONObject(bodyStr);
                        JSONArray choices = json.getJSONArray("choices");
                        String reply = choices
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim();
                        Log.d(TAG, "Groq reply: " + reply);
                        callback.onReply(reply);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Build error", e);
            callback.onError("Build error: " + e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildSystemPrompt(String adminMemory, String activeRules, String senderName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are replying on behalf of the admin based on their memory and rules below.\n");
        sb.append("Reply ONLY with the reply text. No preamble. No 'Reply:' prefix. Be natural.\n\n");

        if (adminMemory != null && !adminMemory.isEmpty()) {
            sb.append(adminMemory).append("\n");
        }
        if (activeRules != null && !activeRules.isEmpty()) {
            sb.append(activeRules).append("\n");
        }
        sb.append("=== CURRENT SENDER ===\nYou are replying to: ").append(senderName).append("\n");
        return sb.toString();
    }

    private JSONObject buildMessage(String role, String content) throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("role",    role);
        msg.put("content", content);
        return msg;
    }
}
