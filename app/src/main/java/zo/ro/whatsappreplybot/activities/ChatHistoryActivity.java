package zo.ro.whatsappreplybot.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;
import java.util.List;

import zo.ro.whatsappreplybot.helpers.DatabaseHelper;
import zo.ro.whatsappreplybot.helpers.MemoryManager;
import zo.ro.whatsappreplybot.models.ChatMessage;
import zo.ro.whatsappreplybot.models.ChatSession;

public class ChatHistoryActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private MemoryManager  memory;

    private Spinner      spinnerContacts;
    private TextView     tvContextPercent;
    private ProgressBar  progressContext;
    private LinearLayout layoutSessions;
    private LinearLayout layoutMessages;
    private RecyclerView rvSessions;
    private RecyclerView rvMessages;
    private TextView     tvEmptyState;

    private List<String> contacts;
    private long selectedContactId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResources().getIdentifier(
                "activity_chat_history", "layout", getPackageName()));

        db     = DatabaseHelper.getInstance(this);
        memory = MemoryManager.getInstance(this);

        initViews();
        loadContacts();
    }

    private void initViews() {
        spinnerContacts  = findViewById(resId("spinner_contacts"));
        tvContextPercent = findViewById(resId("tv_context_percent"));
        progressContext  = findViewById(resId("progress_context"));
        layoutSessions   = findViewById(resId("layout_sessions"));
        layoutMessages   = findViewById(resId("layout_messages"));
        rvSessions       = findViewById(resId("rv_sessions"));
        rvMessages       = findViewById(resId("rv_messages"));
        tvEmptyState     = findViewById(resId("tv_empty_state"));

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadContacts() {
        contacts = db.getAllContactNames();
        if (contacts.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            spinnerContacts.setVisibility(View.GONE);
            return;
        }
        tvEmptyState.setVisibility(View.GONE);
        spinnerContacts.setVisibility(View.VISIBLE);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, contacts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerContacts.setAdapter(adapter);

        spinnerContacts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String name = contacts.get(pos);
                selectedContactId = db.getOrCreateContact(name, "whatsapp");
                loadSessionsForContact(selectedContactId);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSessionsForContact(long contactId) {
        List<ChatSession> sessions = db.getSessionsForContact(contactId);
        layoutSessions.setVisibility(View.VISIBLE);
        layoutMessages.setVisibility(View.GONE);
        rvSessions.setAdapter(new SessionAdapter(sessions));
    }

    private void loadMessagesForSession(ChatSession session) {
        layoutMessages.setVisibility(View.VISIBLE);

        int pct = session.getContextUsagePercent();
        tvContextPercent.setText(memory.getContextStatusLabel(session.getMessageCount()));
        progressContext.setProgress(pct);

        List<ChatMessage> messages = db.getMessagesForSession(session.getId());
        rvMessages.setAdapter(new MessageAdapter(this, messages));
    }

    // ── Session Adapter ────────────────────────────────────────────────────

    private class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {
        private final List<ChatSession> list;
        SessionAdapter(List<ChatSession> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChatSession s = list.get(position);
            h.text1.setText(s.getSessionName() + (s.isActive() ? " 🟢" : " ⬜"));
            h.text2.setText(s.getMessageCount() + " messages · "
                    + memory.calculateContextPercent(s.getMessageCount()) + "% used");

            h.itemView.setOnClickListener(v -> loadMessagesForSession(s));
            h.itemView.setOnLongClickListener(v -> {
                showRenameDialog(s);
                return true;
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }

    // ── Message Adapter ────────────────────────────────────────────────────

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
        private final Context context;
        private final List<ChatMessage> list;

        MessageAdapter(Context ctx, List<ChatMessage> list) {
            this.context = ctx;
            this.list = list;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(32, 24, 32, 24);
            card.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChatMessage msg = list.get(position);
            String time = DateFormat.format("dd MMM, hh:mm a",
                    new Date(msg.getTimestamp())).toString();

            h.tvHeader.setText("👤 " + msg.getSenderName() + "  ·  " + time
                    + "  [" + msg.getPlatform() + "]");
            h.tvMessage.setText("💬 " + msg.getMessageText());
            h.tvReply.setText("🤖 " + (msg.getAiReply() != null
                    ? msg.getAiReply() : "(no reply)"));

            h.itemView.setBackgroundColor(position % 2 == 0 ? 0xFF1A1A1A : 0xFF111111);
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvHeader, tvMessage, tvReply;
            VH(LinearLayout layout) {
                super(layout);
                tvHeader  = make(layout, 12f, 0xFF888888);
                tvMessage = make(layout, 14f, 0xFFCCCCCC);
                tvReply   = make(layout, 14f, 0xFF4FC3F7);
            }
            private static TextView make(LinearLayout p, float sp, int color) {
                TextView tv = new TextView(p.getContext());
                tv.setTextSize(sp);
                tv.setTextColor(color);
                tv.setPadding(0, 4, 0, 4);
                p.addView(tv);
                return tv;
            }
        }
    }

    // ── Rename Dialog ──────────────────────────────────────────────────────

    private void showRenameDialog(ChatSession session) {
        EditText input = new EditText(this);
        input.setText(session.getSessionName());
        new AlertDialog.Builder(this)
                .setTitle("Session rename karo")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        db.renameSession(session.getId(), name);
                        loadSessionsForContact(selectedContactId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int resId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }
}
