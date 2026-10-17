package vn.mmfplayer;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** The player page (assets/index.html) in a WebView; the only native part is the file chooser behind <input type="file">. */
public class MainActivity extends Activity {
    private static final int PICK = 1;
    private WebView web;
    ValueCallback<Uri[]> pending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new Chrome(this));
        setContentView(web);
        if (state == null) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            web.restoreState(state);
        }
    }

    /**
     * Opens the system picker for &lt;input type="file"&gt;. A static class with an explicit owner: javac 21 --release 8 writes a
     * MethodParameters entry without a name for an inner class's implicit outer parameter, which d8 8.2 cannot read.
     */
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
                    a.startActivityForResult(Intent.createChooser(i, "Chọn file"), PICK);
                } catch (Exception e) {
                    a.pending = null;
                    return false;
                }
                return true;
            }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != PICK || pending == null) {
            super.onActivityResult(req, res, data);
            return;
        }
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
        pending.onReceiveValue(out);
        pending = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
