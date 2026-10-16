package org.h4df.evidence;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_EVIDENCE = 41;
    private static final int EXPORT_REGISTER = 42;
    private android.content.SharedPreferences prefs;
    private String currentCase = "Unassigned case";
    private TextView statusLabel;
    private final ArrayList<String> entries = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("h4df_local", MODE_PRIVATE);
        currentCase = prefs.getString("current_case", "Unassigned case");
        loadEntries();
        render();
    }

    private void render() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(20, 22, 20, 18);
        content.setBackgroundColor(0xFFF4F6F8);
        ScrollView scroll = new ScrollView(this);
        TextView title = new TextView(this);
        title.setText("H4DF | Digital Evidence"); title.setTextSize(24); title.setTextColor(0xFF15283D);
        content.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Offline case workspace and file register"); subtitle.setTextSize(14); subtitle.setTextColor(0xFF4D5D6C);
        content.addView(subtitle);
        TextView caseLabel = new TextView(this);
        caseLabel.setText("Current case: " + currentCase); caseLabel.setTextSize(17); caseLabel.setPadding(0, 22, 0, 10);
        content.addView(caseLabel);
        Button caseButton = button("Create / change case"); caseButton.setOnClickListener(v -> askCase()); content.addView(caseButton);
        Button importButton = button("Import evidence file"); importButton.setOnClickListener(v -> chooseEvidence()); content.addView(importButton);
        Button exportButton = button("Export evidence register"); exportButton.setOnClickListener(v -> exportRegister()); content.addView(exportButton);
        statusLabel = new TextView(this);
        statusLabel.setText("Choose a case, then select a file you are authorized to handle. The app calculates a SHA-256 hash.");
        statusLabel.setTextColor(0xFF35475A); statusLabel.setPadding(0, 12, 0, 14); content.addView(statusLabel);
        TextView heading = new TextView(this); heading.setText("Evidence activity log"); heading.setTextSize(19); heading.setTextColor(0xFF15283D); heading.setPadding(0, 10, 0, 8); content.addView(heading);
        TextView entriesView = new TextView(this); entriesView.setTextSize(13); entriesView.setTextColor(0xFF17212B);
        entriesView.setText(joinEntries()); entriesView.setPadding(12, 12, 12, 12); entriesView.setBackgroundColor(0xFFFFFFFF); content.addView(entriesView);
        TextView footer = new TextView(this); footer.setText("Authorized files only. No background collection or covert recording."); footer.setTextSize(12); footer.setTextColor(0xFF6C3B24); footer.setPadding(0, 18, 0, 8); content.addView(footer);
        scroll.addView(content); setContentView(scroll);
    }

    private String joinEntries() {
        if (entries.isEmpty()) return "No evidence imported yet.";
        StringBuilder b = new StringBuilder();
        for (String e : entries) { if (b.length() > 0) b.append("\n\n--------------------\n\n"); b.append(e); }
        return b.toString();
    }

    private Button button(String label) {
        Button b = new Button(this); b.setText(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 6, 0, 6); b.setLayoutParams(p); return b;
    }

    private void askCase() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Case ID or short case name"); input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Case workspace").setMessage("Enter a case label; avoid unnecessary personal details.").setView(input)
            .setPositiveButton("Save", (d, w) -> { String value = input.getText().toString().trim(); if (!value.isEmpty()) { currentCase = value; prefs.edit().putString("current_case", currentCase).apply(); render(); } })
            .setNegativeButton("Cancel", null).show();
    }

    private void chooseEvidence() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_EVIDENCE);
    }

    private void exportRegister() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "H4DF_evidence_register.txt"); startActivityForResult(i, EXPORT_REGISTER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == EXPORT_REGISTER) { writeExport(uri); return; }
        if (requestCode != PICK_EVIDENCE) return;
        try {
            String name = "selected-file"; long size = -1;
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) { try { int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); int si = c.getColumnIndex(OpenableColumns.SIZE); if (c.moveToFirst()) { if (ni >= 0) name = c.getString(ni); if (si >= 0 && !c.isNull(si)) size = c.getLong(si); } } finally { c.close(); } }
            String hash = sha256(uri); String mime = getContentResolver().getType(uri);
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date());
            String record = "Case: " + currentCase + "\nFile: " + name + "\nMIME: " + (mime == null ? "unknown" : mime) + "\nSize: " + (size < 0 ? "unknown" : size + " bytes") + "\nSHA-256: " + hash + "\nImported: " + time;
            entries.add(0, record); saveEntries(); render();
            Toast.makeText(this, "Evidence metadata and hash recorded", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Could not read file: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private String sha256(Uri uri) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("File could not be opened");
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private void saveEntries() {
        StringBuilder b = new StringBuilder();
        for (String e : entries) b.append(e.replace("\n", "\\n")).append("\n---ENTRY---\n");
        prefs.edit().putString("entries", b.toString()).apply();
    }

    private void loadEntries() {
        String raw = prefs.getString("entries", ""); if (raw.isEmpty()) return;
        for (String e : raw.split("\\n---ENTRY---\\n")) if (!e.trim().isEmpty()) entries.add(e.replace("\\n", "\n"));
    }

    private void writeExport(Uri uri) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Export destination could not be opened");
            StringBuilder b = new StringBuilder("H4DF Evidence Register\nExported: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append("\n\n");
            b.append(joinEntries()); out.write(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this, "Register exported", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
}
