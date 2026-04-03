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

import java.util.ArrayList;
import java.util.List;

public class BookmarksActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BookmarkAdapter adapter;
    private AppDatabase db;
    private List<AppDatabase.BookmarkItem> bookmarks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        db = AppDatabase.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookmarkAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.empty_view).setVisibility(bookmarks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarks();
    }

    private void loadBookmarks() {
        bookmarks = db.getAllBookmarks();
        adapter.setItems(bookmarks);
        findViewById(R.id.empty_view).setVisibility(bookmarks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openBookmark(AppDatabase.BookmarkItem item) {
        Intent intent = new Intent();
        intent.putExtra("url", item.url);
        setResult(RESULT_OK, intent);
        finish();
    }

    class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {

        private List<AppDatabase.BookmarkItem> items = new ArrayList<>();

        void setItems(List<AppDatabase.BookmarkItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppDatabase.BookmarkItem item = items.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvUrl.setText(item.url);
            holder.itemView.setOnClickListener(v -> openBookmark(item));
            holder.btnDelete.setOnClickListener(v -> {
                db.removeBookmark(item.id);
                items.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, items.size());
                if (items.isEmpty()) {
                    findViewById(R.id.empty_view).setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvUrl;
            View btnDelete;

            ViewHolder(View view) {
                super(view);
                tvTitle = view.findViewById(R.id.tv_title);
                tvUrl = view.findViewById(R.id.tv_url);
                btnDelete = view.findViewById(R.id.btn_delete);
            }
        }
    }
}
