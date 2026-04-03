package com.zenith.browser.ui;

import android.content.Intent;
import android.net.Uri;
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
import com.zenith.browser.extensions.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DownloadAdapter adapter;
    private AppDatabase db;
    private List<AppDatabase.DownloadItem> downloadItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        db = AppDatabase.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDownloads();
    }

    private void loadDownloads() {
        downloadItems = db.getAllDownloads();
        adapter.setItems(downloadItems);
        findViewById(R.id.empty_view).setVisibility(downloadItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openFile(AppDatabase.DownloadItem item) {
        if (item.filePath != null) {
            try {
                File file = new File(item.filePath);
                if (file.exists()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(file), item.mimeType);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                }
            } catch (Exception e) {
                // Handle with FileProvider
            }
        }
    }

    class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

        private List<AppDatabase.DownloadItem> items = new ArrayList<>();

        void setItems(List<AppDatabase.DownloadItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppDatabase.DownloadItem item = items.get(position);
            holder.tvFilename.setText(item.fileName);
            holder.tvInfo.setText(FileUtils.formatFileSize(item.fileSize) +
                (item.isCompleted() ? " - Completed" : item.isFailed() ? " - Failed" : " - Downloading"));
            holder.progressBar.setVisibility(item.isDownloading() ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> openFile(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvFilename, tvInfo;
            android.widget.ProgressBar progressBar;

            ViewHolder(View view) {
                super(view);
                tvFilename = view.findViewById(R.id.tv_filename);
                tvInfo = view.findViewById(R.id.tv_info);
                progressBar = view.findViewById(R.id.progress_bar);
            }
        }
    }

}
