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

/**
 * HarshuVision — ChatHistoryActivity
 *
 * Shows all contacts → tap contact → see sessions → tap session → see messages
 *
 * Layout: activity_chat_history.xml
 *   - Spinner (contacts)
 *   - RecyclerView sessions
 *   - RecyclerView messages
 *   - Context % bar
 */
public class ChatHistoryActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private MemoryManager  memory;

    // Views
    private Spinner          spinnerContacts;
    private TextView         tvContextPercent;
    private ProgressBar      progressContext;
    private LinearLayout     layoutSessions;
    private LinearLayout     layoutMessages;
    private RecyclerView     rvSessions;
    private RecyclerView     rvMessages;
    private TextView         tvEmptyState;

    private List<String>     contacts;
    private long             selectedContactId = -1;
    private long             selectedSessionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId("activity_chat_history"));

        db     = DatabaseHelper.getInstance(this);
        memory = MemoryManager.getInstance(this);

        initViews();
        loadContacts();
    }

    private void initViews() {
        spinnerContacts  = findViewById(getResId("spinner_contacts"));
        tvContextPercent = findViewById(getResId("tv_context_percent"));
        progressContext  = findViewById(getResId("progress_context"));
        layoutSessions   = findViewById(getResId("layout_sessions"));
        layoutMessages   = findViewById(getResId("layout_messages"));
        rvSessions       = findViewById(getResId("rv_sessions"));
        rvMessages       = findViewById(getResId("rv_messages"));
        tvEmptyState     = findViewById(getResId("tv_empty_state"));

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
                // We need contactId from name — re-query or cache. Using getOrCreate is safe here
                // because it won't create if already exists.
                selectedContactId = db.getOrCreateContact(name, "whatsapp"); // platform might differ; simplification
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
        selectedSessionId = session.getId();
        layoutMessages.setVisibility(View.VISIBLE);

        // Update context bar
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
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ChatSession s = list.get(position);
            holder.text1.setText(s.getSessionName() + (s.isActive() ? " 🟢" : " ⬜"));
            holder.text2.setText(s.getMessageCount() + " messages · "
                    + memory.calculateContextPercent(s.getMessageCount()) + "% used");

            holder.itemView.setOnClickListener(v -> loadMessagesForSession(s));
            holder.itemView.setOnLongClickListener(v -> {
                showRenameSessionDialog(s);
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
            // Inline card view — no need for separate layout
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(32, 24, 32, 24);
            card.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ChatMessage msg = list.get(position);
            String time = DateFormat.format("dd MMM, hh:mm a",
                    new Date(msg.getTimestamp())).toString();

            holder.tvHeader.setText("👤 " + msg.getSenderName() + "  ·  " + time
                    + "  [" + msg.getPlatform() + "]");
            holder.tvMessage.setText("💬 " + msg.getMessageText());
            holder.tvReply.setText("🤖 " + (msg.getAiReply() != null
                    ? msg.getAiReply() : "(no reply)"));

            // Alternate background for readability
            int bg = position % 2 == 0 ? 0xFFF5F5F5 : 0xFFFFFFFF;
            holder.itemView.setBackgroundColor(bg);
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvHeader, tvMessage, tvReply;
            VH(LinearLayout layout) {
                super(layout);
                tvHeader  = buildTextView(layout, 12f, 0xFF888888);
                tvMessage = buildTextView(layout, 14f, 0xFF222222);
                tvReply   = buildTextView(layout, 14f, 0xFF1565C0);
            }
            private static TextView buildTextView(LinearLayout parent, float sizeSp, int color) {
                TextView tv = new TextView(parent.getContext());
                tv.setTextSize(sizeSp);
                tv.setTextColor(color);
                tv.setPadding(0, 4, 0, 4);
                parent.addView(tv);
                return tv;
            }
        }
    }

    // ── Rename Session Dialog ──────────────────────────────────────────────

    private void showRenameSessionDialog(ChatSession session) {
        EditText input = new EditText(this);
        input.setText(session.getSessionName());
        input.setHint("Session name");

        new AlertDialog.Builder(this)
                .setTitle("Rename Session")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        db.renameSession(session.getId(), newName);
                        session.setSessionName(newName);
                        loadSessionsForContact(selectedContactId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Util ───────────────────────────────────────────────────────────────

    private int getLayoutId(String name) {
        return getResources().getIdentifier(name, "layout", getPackageName());
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }
}
