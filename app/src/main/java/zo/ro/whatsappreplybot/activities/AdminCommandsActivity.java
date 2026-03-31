package zo.ro.whatsappreplybot.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import zo.ro.whatsappreplybot.helpers.DatabaseHelper;
import zo.ro.whatsappreplybot.helpers.MemoryManager;

public class AdminCommandsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private MemoryManager  memory;

    private LinearLayout layoutMemoryList;
    private EditText     etMemKey;
    private EditText     etMemValue;

    private LinearLayout layoutRulesList;
    private EditText     etRuleType;
    private EditText     etRuleValue;

    private EditText etGroqKey;
    private EditText etMaxMessages;
    private EditText etHistorySize;
    private Switch   swAutoReply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResources().getIdentifier(
                "activity_admin_commands", "layout", getPackageName()));

        db     = DatabaseHelper.getInstance(this);
        memory = MemoryManager.getInstance(this);

        initViews();
        loadAll();
    }

    private void initViews() {
        layoutMemoryList = findViewById(resId("layout_memory_list"));
        etMemKey         = findViewById(resId("et_mem_key"));
        etMemValue       = findViewById(resId("et_mem_value"));
        Button btnAddMem = findViewById(resId("btn_add_memory"));
        btnAddMem.setOnClickListener(v -> addMemoryEntry());

        layoutRulesList  = findViewById(resId("layout_rules_list"));
        etRuleType       = findViewById(resId("et_rule_type"));
        etRuleValue      = findViewById(resId("et_rule_value"));
        Button btnAddRule = findViewById(resId("btn_add_rule"));
        btnAddRule.setOnClickListener(v -> addRule());

        etGroqKey     = findViewById(resId("et_groq_key"));
        etMaxMessages = findViewById(resId("et_max_messages"));
        etHistorySize = findViewById(resId("et_history_size"));
        swAutoReply   = findViewById(resId("sw_auto_reply"));

        Button btnSave = findViewById(resId("btn_save_settings"));
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadAll() {
        loadMemoryEntries();
        loadRules();
        loadSettings();
    }

    // ── Memory+ ───────────────────────────────────────────────────────────

    private void loadMemoryEntries() {
        layoutMemoryList.removeAllViews();
        for (String[] entry : db.getAllMemoryEntries()) {
            long   id    = Long.parseLong(entry[0]);
            String key   = entry[1];
            String value = entry[2];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 12, 8, 12);

            TextView tv = new TextView(this);
            tv.setText("🔑 " + key + "\n📝 " + value);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setTextSize(13f);

            Button btnEdit = new Button(this);
            btnEdit.setText("✏️");
            btnEdit.setOnClickListener(v -> showEditMemoryDialog(id, key, value));

            Button btnDel = new Button(this);
            btnDel.setText("🗑️");
            btnDel.setOnClickListener(v -> {
                db.deleteMemory(id);
                loadMemoryEntries();
            });

            row.addView(tv);
            row.addView(btnEdit);
            row.addView(btnDel);
            layoutMemoryList.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFF333333);
            layoutMemoryList.addView(divider);
        }
    }

    private void addMemoryEntry() {
        String key   = etMemKey.getText().toString().trim();
        String value = etMemValue.getText().toString().trim();
        if (key.isEmpty() || value.isEmpty()) {
            Toast.makeText(this, "Key aur value dono chahiye", Toast.LENGTH_SHORT).show();
            return;
        }
        db.setMemory(key, value);
        etMemKey.setText("");
        etMemValue.setText("");
        loadMemoryEntries();
        Toast.makeText(this, "Memory save ho gayi ✓", Toast.LENGTH_SHORT).show();
    }

    private void showEditMemoryDialog(long id, String key, String currentValue) {
        EditText input = new EditText(this);
        input.setText(currentValue);
        new AlertDialog.Builder(this)
                .setTitle("Edit: " + key)
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newVal = input.getText().toString().trim();
                    if (!newVal.isEmpty()) {
                        db.setMemory(key, newVal);
                        loadMemoryEntries();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Rules ─────────────────────────────────────────────────────────────

    private void loadRules() {
        layoutRulesList.removeAllViews();
        for (String[] rule : db.getAllRules()) {
            long    id     = Long.parseLong(rule[0]);
            String  type   = rule[1];
            String  value  = rule[2];
            boolean active = "1".equals(rule[3]);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 12, 8, 12);

            TextView tv = new TextView(this);
            tv.setText((active ? "✅" : "⭕") + " [" + type + "]\n" + value);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setTextSize(13f);

            Button btnToggle = new Button(this);
            btnToggle.setText(active ? "OFF" : "ON");
            btnToggle.setOnClickListener(v -> {
                db.toggleRule(id, !active);
                loadRules();
            });

            Button btnDel = new Button(this);
            btnDel.setText("🗑️");
            btnDel.setOnClickListener(v -> {
                db.deleteRule(id);
                loadRules();
            });

            row.addView(tv);
            row.addView(btnToggle);
            row.addView(btnDel);
            layoutRulesList.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFF333333);
            layoutRulesList.addView(divider);
        }
    }

    private void addRule() {
        String type  = etRuleType.getText().toString().trim();
        String value = etRuleValue.getText().toString().trim();
        if (type.isEmpty() || value.isEmpty()) {
            Toast.makeText(this, "Type aur value dono chahiye", Toast.LENGTH_SHORT).show();
            return;
        }
        db.addRule(type, value);
        etRuleType.setText("");
        etRuleValue.setText("");
        loadRules();
        Toast.makeText(this, "Rule add ho gaya ✓", Toast.LENGTH_SHORT).show();
    }

    // ── Settings ──────────────────────────────────────────────────────────

    private void loadSettings() {
        etGroqKey.setText(memory.getGroqApiKey());
        etMaxMessages.setText(String.valueOf(memory.getMaxMessages()));
        etHistorySize.setText(String.valueOf(memory.getHistorySize()));
        swAutoReply.setChecked(memory.isAutoReplyEnabled());
    }

    private void saveSettings() {
        String key = etGroqKey.getText().toString().trim();
        if (!key.isEmpty()) memory.setGroqApiKey(key);

        try {
            int max = Integer.parseInt(etMaxMessages.getText().toString().trim());
            if (max > 0) memory.setMaxMessages(max);
        } catch (NumberFormatException ignored) {}

        try {
            int hist = Integer.parseInt(etHistorySize.getText().toString().trim());
            if (hist > 0) memory.setHistorySize(hist);
        } catch (NumberFormatException ignored) {}

        memory.setAutoReplyEnabled(swAutoReply.isChecked());
        Toast.makeText(this, "Settings save ho gayi ✓", Toast.LENGTH_SHORT).show();

        if (!memory.isGroqKeyValid()) {
            Toast.makeText(this, "⚠️ Groq key galat lag rahi hai (gsk_ se shuru honi chahiye)",
                    Toast.LENGTH_LONG).show();
        }
    }

    private int resId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }
}
