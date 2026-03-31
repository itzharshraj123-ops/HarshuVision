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
import zo.ro.whatsappreplybot.helpers.WhatsAppMessageHandler;
import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

public class ChatGPTReplyGenerator {

    private static final String TAG      = "ChatGPTReply";
    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL    = "gpt-3.5-turbo";

    public interface ReplyCallback {
        void onReply(String reply);
        void onError(String error);
    }

    private final Context context;
    private final String  apiKey;
    private final OkHttpClient client;
    private final DatabaseHelper db;
    private final MemoryManager  memory;

    public ChatGPTReplyGenerator(Context context, String apiKey) {
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
            List<ChatMessage> history = db.getRecentMessages(session.getId(), memory.getHistorySize());

            JSONArray messages = new JSONArray();

            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", db.buildAdminMemoryBlock() + "\n" + db.buildActiveRulesBlock());
            messages.put(system);

            for (ChatMessage hist : history) {
                JSONObject u = new JSONObject();
                u.put("role", "user");
                u.put("content", hist.getSenderName() + ": " + hist.getMessageText());
                messages.put(u);
                if (hist.getAiReply() != null && !hist.getAiReply().isEmpty()) {
                    JSONObject a = new JSONObject();
                    a.put("role", "assistant");
                    a.put("content", hist.getAiReply());
                    messages.put(a);
                }
            }

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", sender + ": " + message);
            messages.put(userMsg);

            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("max_tokens", 512);

            RequestBody reqBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
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
                        String reply = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
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
