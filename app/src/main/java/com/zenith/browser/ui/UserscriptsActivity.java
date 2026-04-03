package com.zenith.browser.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zenith.browser.R;
import com.zenith.browser.extensions.UserscriptManager;
import com.zenith.browser.extensions.UserscriptManager.Userscript;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class UserscriptsActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 2001;
    private RecyclerView recyclerView;
    private ScriptAdapter adapter;
    private UserscriptManager scriptManager;
    private List<Userscript> scripts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userscripts);

        scriptManager = UserscriptManager.getInstance(this);
        scriptManager.setListener(new UserscriptManager.UserscriptListener() {
            @Override
            public void onScriptInstalled(Userscript script) {
                runOnUiThread(() -> {
                    loadScripts();
                    Toast.makeText(UserscriptsActivity.this, "Userscript installed: " + script.name, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onScriptError(String error) {
                runOnUiThread(() -> Toast.makeText(UserscriptsActivity.this, "Error: " + error, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onScriptRemoved(Userscript script) {
                runOnUiThread(() -> loadScripts());
            }

            @Override
            public void onScriptUpdated(Userscript script) {
                runOnUiThread(() -> loadScripts());
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_scripts);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScriptAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fab_add_script).setOnClickListener(v -> showAddScriptDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadScripts();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    File tempFile = new File(getCacheDir(), "temp_userscript_install.user.js");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                    fos.close();
                    is.close();
                    scriptManager.installUserscriptFromFile(tempFile);
                    tempFile.delete();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void loadScripts() {
        scripts = scriptManager.getScripts();
        adapter.setItems(scripts);
        findViewById(R.id.empty_view).setVisibility(scripts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddScriptDialog() {
        String[] options = {"From file (.user.js)", "From URL"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("Add Userscript")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    openFileChooser();
                } else {
                    showUrlDialog();
                }
            })
            .show();
    }

    private void showUrlDialog() {
        EditText input = new EditText(this);
        input.setHint("https://greasyfork.org/scripts/.../script.user.js");
        new MaterialAlertDialogBuilder(this)
            .setTitle("Install from URL")
            .setView(input)
            .setPositiveButton("Install", (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    Toast.makeText(this, "Downloading userscript...", Toast.LENGTH_SHORT).show();
                    scriptManager.installUserscriptFromUrl(url);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select userscript file"), PICK_FILE_REQUEST);
    }

    class ScriptAdapter extends RecyclerView.Adapter<ScriptAdapter.ViewHolder> {
        private List<Userscript> items = new ArrayList<>();

        void setItems(List<Userscript> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_userscript, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Userscript script = items.get(position);
            holder.tvName.setText(script.name);
            holder.tvDesc.setText(script.description.isEmpty() ? script.getMatchSummary() : script.description);
            holder.tvMeta.setText("v" + script.version + " | " + script.getMatchSummary() + " | @" + script.getGrantsSummary());
            holder.switchEnable.setChecked(script.enabled);

            holder.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                scriptManager.toggleScript(script);
            });

            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(UserscriptsActivity.this)
                    .setTitle(script.name)
                    .setMessage("Version: " + script.version + "\n" +
                        "Author: " + (script.author != null ? script.author : "Unknown") + "\n" +
                        "Match: " + script.getMatchSummary() + "\n" +
                        "Grants: " + script.getGrantsSummary() + "\n" +
                        "Run at: " + script.runAt)
                    .setPositiveButton("Uninstall", (dialog, which) -> {
                        scriptManager.uninstallScript(script);
                    })
                    .setNegativeButton("Close", null)
                    .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDesc, tvMeta;
            com.google.android.material.switchmaterial.SwitchMaterial switchEnable;

            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tv_script_name);
                tvDesc = view.findViewById(R.id.tv_script_desc);
                tvMeta = view.findViewById(R.id.tv_script_meta);
                switchEnable = view.findViewById(R.id.switch_enable);
            }
        }
    }
}
