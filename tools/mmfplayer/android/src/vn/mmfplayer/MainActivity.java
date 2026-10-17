package vn.mmfplayer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * The player page (assets/index.html) in a WebView. Native parts: the file chooser behind &lt;input type="file"&gt; and the
 * "Android" bridge (source folders through the Storage Access Framework, recursive scan for MMF / MIDI files, reading a file,
 * writing an exported WAV into a chosen folder). Folders, the scanned library and the export folder are kept in SharedPreferences.
 *
 * No anonymous or inner classes: javac 21 --release 8 writes a MethodParameters entry without a name for an inner class's
 * implicit outer parameter, which d8 8.2 cannot read. Static nested classes with an explicit owner and lambdas are used instead.
 */
public class MainActivity extends Activity {
    static final int PICK_FILE = 1;
    static final int PICK_FOLDER = 2;
    static final int PICK_EXPORT = 3;

    WebView web;
    ValueCallback<Uri[]> pending;
    SharedPreferences prefs;
    volatile boolean scanning;
    final HashMap<String, OutputStream> exports = new HashMap<String, OutputStream>();
    int exportSeq;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("library", MODE_PRIVATE);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new Chrome(this));
        web.addJavascriptInterface(new Bridge(this), "Android");
        setContentView(web);
        if (state == null) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            web.restoreState(state);
        }
    }

    /** window.onNative(type, json) in the page */
    void emit(String type, String json) {
        final String js = "window.onNative&&window.onNative(" + JSONObject.quote(type) + "," + json + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    // ------------------------------------------------------------------ state

    JSONArray folders() {
        try {
            return new JSONArray(prefs.getString("folders", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    String stateJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("folders", folders());
            o.put("library", new JSONArray(prefs.getString("lib", "[]")));
            String ex = prefs.getString("export", null);
            if (ex != null) {
                o.put("exportFolder", new JSONObject(ex));
            }
            o.put("scanning", scanning);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    static String treeName(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);    // "primary:Download/SND"
        int c = id.indexOf(':');
        String p = c >= 0 ? id.substring(c + 1) : id;
        if (p.length() == 0) {
            return c >= 0 ? id.substring(0, c) : id;
        }
        int sl = p.lastIndexOf('/');
        return sl >= 0 ? p.substring(sl + 1) : p;
    }

    static String treePath(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        int c = id.indexOf(':');
        String vol = c >= 0 ? id.substring(0, c) : "";
        String p = c >= 0 ? id.substring(c + 1) : id;
        return ("primary".equals(vol) ? "Bộ nhớ trong" : vol) + (p.length() > 0 ? " / " + p.replace("/", " / ") : "");
    }

    void addFolder(Uri tree) {
        try {
            JSONArray f = folders();
            String u = tree.toString();
            for (int i = 0; i < f.length(); i++) {
                if (u.equals(f.getJSONObject(i).getString("uri"))) {
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("uri", u);
            o.put("name", treeName(tree));
            o.put("path", treePath(tree));
            f.put(o);
            prefs.edit().putString("folders", f.toString()).apply();
        } catch (JSONException e) {
            // ignore
        }
    }

    // ------------------------------------------------------------------ scan

    void scanAll() {
        if (scanning) {
            return;
        }
        scanning = true;
        new Thread(() -> {
            JSONArray lib = new JSONArray();
            int[] seen = new int[1];
            JSONArray f = folders();
            for (int i = 0; i < f.length(); i++) {
                try {
                    JSONObject fo = f.getJSONObject(i);
                    Uri tree = Uri.parse(fo.getString("uri"));
                    String rootId = DocumentsContract.getTreeDocumentId(tree);
                    walk(tree, rootId, fo.getString("name"), fo.getString("uri"), lib, seen, 0);
                } catch (Exception e) {
                    // a folder whose permission was revoked: skipped, stays in the list so the page can show it
                }
            }
            prefs.edit().putString("lib", lib.toString()).apply();
            scanning = false;
            emit("state", stateJson());
        }).start();
    }

    void walk(Uri tree, String docId, String rel, String root, JSONArray out, int[] seen, int depth) {
        if (depth > 16) {
            return;
        }
        ContentResolver cr = getContentResolver();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        Cursor c = null;
        try {
            c = cr.query(kids, cols, null, null, null);
            if (c == null) {
                return;
            }
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? 0 : c.getLong(3);
                if (name == null) {
                    continue;
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(tree, id, rel + "/" + name, root, out, seen, depth + 1);
                    continue;
                }
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                String type = classify(cr, doc, name, size);
                if (type != null) {
                    JSONObject o = new JSONObject();
                    o.put("uri", doc.toString());
                    o.put("name", name);
                    o.put("folder", rel);
                    o.put("root", root);
                    o.put("type", type);
                    o.put("size", size);
                    out.put(o);
                }
                if (++seen[0] % 50 == 0) {
                    emit("scan", "{\"seen\":" + seen[0] + ",\"found\":" + out.length() + "}");
                }
            }
        } catch (Exception e) {
            // unreadable folder: skipped
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** "mmf" / "midi" by extension; files without an extension (game resources such as BGM_07_MMF) by their first 4 bytes */
    static String classify(ContentResolver cr, Uri doc, String name, long size) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mid") || n.endsWith(".midi") || n.endsWith(".kar")) {
            return "midi";
        }
        if (n.endsWith(".mmf")) {
            return "mmf";
        }
        int dot = n.lastIndexOf('.');
        boolean noExt = dot < 0 || n.length() - dot > 6;
        if (!noExt || size < 8 || size > 4 * 1024 * 1024) {
            return null;
        }
        InputStream in = null;
        try {
            in = cr.openInputStream(doc);
            byte[] b = new byte[4];
            int r = 0;
            while (r < 4) {
                int k = in.read(b, r, 4 - r);
                if (k < 0) {
                    break;
                }
                r += k;
            }
            if (r == 4 && b[0] == 'M' && b[1] == 'M' && b[2] == 'M' && b[3] == 'D') {
                return "mmf";
            }
            if (r == 4 && b[0] == 'M' && b[1] == 'T' && b[2] == 'h' && b[3] == 'd') {
                return "midi";
            }
        } catch (Exception e) {
            // unreadable
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ activity results

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILE) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    out = new Uri[clip.getItemCount()];
                    for (int k = 0; k < out.length; k++) {
                        out[k] = clip.getItemAt(k).getUri();
                    }
                } else if (data.getData() != null) {
                    out = new Uri[] {data.getData()};
                }
            }
            if (pending != null) {
                pending.onReceiveValue(out);
            }
            pending = null;
            return;
        }
        if ((req == PICK_FOLDER || req == PICK_EXPORT) && res == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                // some providers only grant a session permission
            }
            if (req == PICK_FOLDER) {
                addFolder(tree);
                emit("state", stateJson());
                scanAll();
            } else {
                try {
                    JSONObject o = new JSONObject();
                    o.put("uri", tree.toString());
                    o.put("name", treeName(tree));
                    o.put("path", treePath(tree));
                    prefs.edit().putString("export", o.toString()).apply();
                } catch (JSONException e) {
                    // ignore
                }
                emit("state", stateJson());
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    void openTreePicker(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            emit("error", JSONObject.quote("Máy không có trình chọn thư mục"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack?window.onBack():false", v -> {
            if (!"true".equals(v)) {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ bridge

    /** window.Android in the page; methods run on the WebView's bridge thread */
    static final class Bridge {
        private final MainActivity a;

        Bridge(MainActivity owner) {
            a = owner;
        }

        @JavascriptInterface
        public String getState() {
            return a.stateJson();
        }

        @JavascriptInterface
        public void addFolder() {
            a.runOnUiThread(() -> a.openTreePicker(PICK_FOLDER));
        }

        @JavascriptInterface
        public void pickExportFolder() {
            a.runOnUiThread(() -> a.openTreePicker(PICK_EXPORT));
        }

        @JavascriptInterface
        public String removeFolder(String uri) {
            try {
                JSONArray f = a.folders(), keep = new JSONArray();
                for (int i = 0; i < f.length(); i++) {
                    if (!uri.equals(f.getJSONObject(i).getString("uri"))) {
                        keep.put(f.getJSONObject(i));
                    }
                }
                JSONArray lib = new JSONArray(a.prefs.getString("lib", "[]")), keepLib = new JSONArray();
                for (int i = 0; i < lib.length(); i++) {
                    if (!uri.equals(lib.getJSONObject(i).optString("root"))) {
                        keepLib.put(lib.getJSONObject(i));
                    }
                }
                a.prefs.edit().putString("folders", keep.toString()).putString("lib", keepLib.toString()).apply();
                try {
                    a.getContentResolver().releasePersistableUriPermission(Uri.parse(uri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    // not persisted
                }
            } catch (JSONException e) {
                // ignore
            }
            return a.stateJson();
        }

        @JavascriptInterface
        public void scan() {
            a.scanAll();
        }

        /** the whole file as base64 ("" when it cannot be read); MMF / MIDI files are small */
        @JavascriptInterface
        public String readFile(String uri) {
            InputStream in = null;
            try {
                in = a.getContentResolver().openInputStream(Uri.parse(uri));
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[65536];
                int k;
                while ((k = in.read(b)) > 0) {
                    o.write(b, 0, k);
                    if (o.size() > 32 * 1024 * 1024) {
                        return "";
                    }
                }
                return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        /** creates name in the export folder; returns a handle, "" when no export folder is set, "ERR:..." on failure */
        @JavascriptInterface
        public String exportBegin(String name) {
            String ex = a.prefs.getString("export", null);
            if (ex == null) {
                return "";
            }
            try {
                Uri tree = Uri.parse(new JSONObject(ex).getString("uri"));
                Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                Uri doc = DocumentsContract.createDocument(a.getContentResolver(), dir, "audio/x-wav", name);
                if (doc == null) {
                    return "ERR:không tạo được file trong thư mục xuất";
                }
                OutputStream o = a.getContentResolver().openOutputStream(doc);
                synchronized (a.exports) {
                    String h = "x" + (++a.exportSeq);
                    a.exports.put(h, o);
                    return h;
                }
            } catch (Exception e) {
                return "ERR:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public boolean exportChunk(String h, String b64) {
            OutputStream o;
            synchronized (a.exports) {
                o = a.exports.get(h);
            }
            if (o == null) {
                return false;
            }
            try {
                o.write(Base64.decode(b64, Base64.NO_WRAP));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean exportEnd(String h) {
            OutputStream o;
            synchronized (a.exports) {
                o = a.exports.remove(h);
            }
            if (o == null) {
                return false;
            }
            try {
                o.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** opens the system picker for &lt;input type="file"&gt; */
    static final class Chrome extends WebChromeClient {
        private final MainActivity a;

        Chrome(MainActivity owner) {
            a = owner;
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (a.pending != null) {
                a.pending.onReceiveValue(null);
            }
            a.pending = callback;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");                           // game files have no extension (BGM_07_MMF)
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
            try {
                a.startActivityForResult(Intent.createChooser(i, "Chọn file"), PICK_FILE);
            } catch (Exception e) {
                a.pending = null;
                return false;
            }
            return true;
        }
    }
}
