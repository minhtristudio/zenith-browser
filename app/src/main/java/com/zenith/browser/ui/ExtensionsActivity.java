package com.zenith.browser.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zenith.browser.R;
import com.zenith.browser.extensions.ChromeExtension;
import com.zenith.browser.extensions.ExtensionManager;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExtensionsActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1001;
    private RecyclerView recyclerView;
    private ExtensionAdapter adapter;
    private ExtensionManager extensionManager;
    private List<ChromeExtension> extensions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        extensionManager = ExtensionManager.getInstance();
        extensionManager.setListener(new ExtensionManager.ExtensionListener() {
            @Override
            public void onExtensionLoaded(ChromeExtension extension) {
                runOnUiThread(() -> {
                    loadExtensions();
                    Toast.makeText(ExtensionsActivity.this, R.string.extension_installed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onExtensionError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ExtensionsActivity.this, R.string.extension_failed + ": " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onExtensionRemoved(ChromeExtension extension) {
                runOnUiThread(() -> loadExtensions());
            }
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_load_from_file) {
                openFileChooser();
                return true;
            }
            return false;
        });

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExtensionAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_add_extension).setOnClickListener(v -> showAddExtensionDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadExtensions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    File tempFile = new File(getCacheDir(), "temp_ext_" + System.currentTimeMillis() + ".zip");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();
                    extensionManager.installFromFile(tempFile);
                    tempFile.delete();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void loadExtensions() {
        extensions = extensionManager.getExtensions();
        adapter.setItems(extensions);
        findViewById(R.id.empty_view).setVisibility(extensions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddExtensionDialog() {
        String[] options = {"From storage (.crx / .zip)", "From URL", "Userscript manager"};
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_extension)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    openFileChooser();
                } else if (which == 1) {
                    showUrlInputDialog();
                } else {
                    startActivity(new Intent(this, UserscriptsActivity.class));
                }
            })
            .show();
    }

    private void showUrlInputDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.extension_url_hint);
        new MaterialAlertDialogBuilder(this)
            .setTitle("Install from URL")
            .setView(input)
            .setPositiveButton(R.string.install, (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    Toast.makeText(this, "Downloading extension...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        File tempFile = null;
                        try {
                            // Ensure cache dir exists
                            File cacheDir = getCacheDir();
                            if (!cacheDir.exists()) cacheDir.mkdirs();

                            // Determine file extension from URL
                            String fileUrl = url;
                            String ext = ".zip";
                            if (fileUrl.endsWith(".crx")) ext = ".crx";
                            else if (fileUrl.endsWith(".zip")) ext = ".zip";
                            else if (fileUrl.endsWith(".user.js")) ext = ".user.js";
                            else if (fileUrl.endsWith(".js")) ext = ".js";

                            // Use unique temp file to avoid ENOENT race conditions
                            tempFile = new File(cacheDir, "temp_ext_" + System.currentTimeMillis() + ext);

                            java.net.URL extUrl = new java.net.URL(fileUrl);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) extUrl.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                            conn.setRequestProperty("Accept", "*/*");
                            conn.setInstanceFollowRedirects(true);
                            conn.setConnectTimeout(30000);
                            conn.setReadTimeout(60000);
                            // Handle GitHub redirects manually
                            final int[] responseCodeHolder = {conn.getResponseCode()};
                            if (responseCodeHolder[0] == 301 || responseCodeHolder[0] == 302 || responseCodeHolder[0] == 303 || responseCodeHolder[0] == 307) {
                                String newUrl = conn.getHeaderField("Location");
                                if (newUrl != null) {
                                    conn.disconnect();
                                    extUrl = new java.net.URL(newUrl);
                                    conn = (java.net.HttpURLConnection) extUrl.openConnection();
                                    conn.setRequestMethod("GET");
                                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                                    conn.setRequestProperty("Accept", "*/*");
                                    conn.setConnectTimeout(30000);
                                    conn.setReadTimeout(60000);
                                    responseCodeHolder[0] = conn.getResponseCode();
                                }
                            }

                            if (responseCodeHolder[0] != 200) {
                                final int code = responseCodeHolder[0];
                                runOnUiThread(() -> Toast.makeText(this, "Download failed: HTTP " + code, Toast.LENGTH_SHORT).show());
                                return;
                            }

                            int fileSize = conn.getContentLength();
                            InputStream is = conn.getInputStream();
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                            byte[] buffer = new byte[8192];
                            int len;
                            long total = 0;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                                total += len;
                            }
                            fos.flush();
                            fos.close();
                            is.close();
                            conn.disconnect();

                            if (tempFile == null || !tempFile.exists() || tempFile.length() == 0) {
                                if (tempFile != null) tempFile.delete();
                                runOnUiThread(() -> Toast.makeText(this, "Downloaded file is empty", Toast.LENGTH_SHORT).show());
                                return;
                            }

                            // Install extension
                            if (ext.equals(".user.js") || ext.equals(".js")) {
                                extensionManager.installUserscript(tempFile, url);
                            } else {
                                extensionManager.installFromFile(tempFile);
                            }
                            tempFile.delete();
                        } catch (final Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        } finally {
                            // Clean up temp file in case of error
                            if (tempFile != null && tempFile.exists()) {
                                tempFile.delete();
                            }
                        }
                    }).start();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"application/zip", "application/x-chrome-extension", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "Select extension file"), PICK_FILE_REQUEST);
    }

    class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.ViewHolder> {

        private List<ChromeExtension> items = new ArrayList<>();

        void setItems(List<ChromeExtension> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_extension, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChromeExtension ext = items.get(position);
            holder.tvName.setText(ext.getName());
            holder.tvVersion.setText("v" + ext.getVersion());
            holder.tvDescription.setText(ext.getDescription());
            holder.switchEnable.setChecked(ext.isEnabled());

            // Load icon
            File iconFile = ext.getIconFile();
            if (iconFile != null && iconFile.exists()) {
                Bitmap icon = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                if (icon != null) holder.ivIcon.setImageBitmap(icon);
            }

            holder.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ext.setEnabled(isChecked);
            });

            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(ExtensionsActivity.this)
                    .setTitle(ext.getName())
                    .setMessage("Version: " + ext.getVersion() + "\n" +
                        "Manifest V" + ext.getManifestVersion() + "\n" +
                        "Permissions: " + ext.getPermissions())
                    .setPositiveButton("Uninstall", (dialog, which) -> {
                        extensionManager.uninstallExtension(ext);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvVersion, tvDescription;
            com.google.android.material.switchmaterial.SwitchMaterial switchEnable;

            ViewHolder(View view) {
                super(view);
                ivIcon = view.findViewById(R.id.iv_icon);
                tvName = view.findViewById(R.id.tv_name);
                tvVersion = view.findViewById(R.id.tv_version);
                tvDescription = view.findViewById(R.id.tv_description);
                switchEnable = view.findViewById(R.id.switch_enable);
            }
        }
    }
}
