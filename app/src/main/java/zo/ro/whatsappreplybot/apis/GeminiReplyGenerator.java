package zo.ro.whatsappreplybot.apis;

import android.content.Context;
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
import zo.ro.whatsappreplybot.helpers.DatabaseHelper;
import zo.ro.whatsappreplybot.helpers.MemoryManager;
import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

public class GeminiReplyGenerator {

    private static final String TAG = "GeminiReply";

    public interface ReplyCallback {
        void onReply(String reply);
        void onError(String error);
    }

    private final Context        context;
    private final String         apiKey;
    private final OkHttpClient   client;
    private final DatabaseHelper db;
    private final MemoryManager  memory;

    public GeminiReplyGenerator(Context context, String apiKey) {
        this.context = context;
        this.apiKey  = apiKey;
        this.db      = DatabaseHelper.getInstance(context);
        this.memory  = MemoryManager.getInstance(context);
        this.client  = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void generateReply(String sender, String message, ReplyCallback callback) {
        try {
            long contactId      = db.getOrCreateContact(sender, "whatsapp");
            ChatSession session = db.getOrCreateActiveSession(contactId, memory.getMaxMessages());
            List<ChatMessage> history = db.getRecentMessages(
                    session.getId(), memory.getHistorySize());

            // Build prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append(db.buildAdminMemoryBlock()).append("\n");
            prompt.append(db.buildActiveRulesBlock()).append("\n");
            prompt.append("Chat history:\n");
            for (ChatMessage m : history) {
                prompt.append(m.getSenderName()).append(": ").append(m.getMessageText()).append("\n");
                if (m.getAiReply() != null) prompt.append("You: ").append(m.getAiReply()).append("\n");
            }
            prompt.append(sender).append(": ").append(message).append("\nYour reply:");

            JSONObject part = new JSONObject();
            part.put("text", prompt.toString());

            JSONArray parts = new JSONArray();
            parts.put(part);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject body = new JSONObject();
            body.put("contents", contents);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-pro:generateContent?key=" + apiKey;

            RequestBody reqBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(reqBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError("API error " + response.code());
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(bodyStr);
                        String reply = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                                .trim();
                        callback.onReply(reply);
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error", e);
            callback.onError("Error: " + e.getMessage());
        }
    }
}
