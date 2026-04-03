package com.zenith.browser.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zenith.browser.R;
import com.zenith.browser.data.AppDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private AppDatabase db;
    private List<AppDatabase.HistoryItem> historyItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = AppDatabase.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_all) {
                db.clearHistory();
                loadHistory();
                return true;
            }
            return false;
        });

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        historyItems = db.getAllHistory();
        adapter.setItems(historyItems);
        findViewById(R.id.empty_view).setVisibility(historyItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openHistoryItem(AppDatabase.HistoryItem item) {
        Intent intent = new Intent();
        intent.putExtra("url", item.url);
        setResult(RESULT_OK, intent);
        finish();
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<AppDatabase.HistoryItem> items = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        void setItems(List<AppDatabase.HistoryItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppDatabase.HistoryItem item = items.get(position);
            holder.tvTitle.setText(item.title != null ? item.title : item.url);
            holder.tvUrl.setText(item.url);
            holder.tvDate.setText(sdf.format(new Date(item.createdAt)));
            holder.itemView.setOnClickListener(v -> openHistoryItem(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvUrl, tvDate;

            ViewHolder(View view) {
                super(view);
                tvTitle = view.findViewById(R.id.tv_title);
                tvUrl = view.findViewById(R.id.tv_url);
                tvDate = view.findViewById(R.id.tv_date);
            }
        }
    }
}
