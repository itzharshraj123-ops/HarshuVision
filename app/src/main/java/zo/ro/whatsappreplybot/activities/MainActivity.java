package zo.ro.whatsappreplybot.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import zo.ro.whatsappreplybot.helpers.DatabaseHelper;
import zo.ro.whatsappreplybot.helpers.MemoryManager;

/**
 * HarshuVision — MainActivity
 *
 * Buttons:
 *  ① Grant Notification Access
 *  ② Chat History
 *  ③ Admin Commands (Engineering Panel)
 *  ④ Bot Settings (existing)
 */
public class MainActivity extends AppCompatActivity {

    private MemoryManager memory;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResources().getIdentifier("activity_main", "layout", getPackageName()));

        memory = MemoryManager.getInstance(this);
        DatabaseHelper.getInstance(this); // init DB early

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusUI();
    }

    private void initViews() {
        tvStatus = findViewById(resId("tv_status"));

        // ── Notification Access ────────────────────────────────────────────
        Button btnNotif = findViewById(resId("btn_notification_access"));
        if (btnNotif != null) {
            btnNotif.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }

        // ── Chat History ───────────────────────────────────────────────────
        Button btnHistory = findViewById(resId("btn_chat_history"));
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v ->
                    startActivity(new Intent(this, ChatHistoryActivity.class)));
        }

        // ── Admin Commands ─────────────────────────────────────────────────
        Button btnAdmin = findViewById(resId("btn_admin_commands"));
        if (btnAdmin != null) {
            btnAdmin.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminCommandsActivity.class)));
        }

        // ── Bot Settings ───────────────────────────────────────────────────
        Button btnSettings = findViewById(resId("btn_bot_settings"));
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, BotSettingsActivity.class)));
        }
    }

    private void updateStatusUI() {
        if (tvStatus == null) return;
        boolean keyValid = memory.isGroqKeyValid();
        boolean autoOn   = memory.isAutoReplyEnabled();

        String status = "🤖 HarshuVision\n"
                + (keyValid ? "✅ Groq API: Connected\n" : "❌ Groq API key not set → Go to Admin Commands\n")
                + (autoOn   ? "✅ Auto-reply: ON\n"      : "⭕ Auto-reply: OFF\n")
                + "📊 Context window: " + memory.getMaxMessages() + " msgs max";
        tvStatus.setText(status);
    }

    private int resId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }
}
