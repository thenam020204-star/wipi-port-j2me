package vn.mmfplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Playback controls outside the app: a media-style notification (status bar, lock screen, Android 13+ media panel) and a
 * MediaSession (headset / Bluetooth buttons). The audio itself plays in the page's Web Audio; this service only mirrors the
 * page's state (Android.mediaState) and sends the buttons back as window.onNative('media', {cmd, ms}).
 *
 * Started as a foreground service (type mediaPlayback) when playback starts while the activity is in front, and stays in the
 * foreground while paused, so the notification buttons keep working from the background (Android 12+ forbids starting a
 * foreground service from the background). It stops on the notification's close button, when the task is swiped away, or
 * when the activity (and with it the WebView) is destroyed. While the page plays or renders the next song it holds a partial
 * wake lock: songs are rendered in JavaScript before they play, and with the screen off the CPU could otherwise sleep there.
 *
 * Everything runs on the main thread. No anonymous or inner classes (see MainActivity).
 */
public class PlayerService extends Service {
    static final String CHANNEL = "playback";
    static final int NOTE_ID = 1;
    static final String ACT_TOGGLE = "vn.mmfplayer.TOGGLE";
    static final String ACT_NEXT = "vn.mmfplayer.NEXT";
    static final String ACT_PREV = "vn.mmfplayer.PREV";
    static final String ACT_STOP = "vn.mmfplayer.STOP";
    static final String EXTRA_STATE = "state";

    static PlayerService instance;

    MediaSession session;
    AudioManager am;
    AudioFocusRequest focusReq;
    final AudioManager.OnAudioFocusChangeListener focusListener = this::onFocusChange;
    boolean hasFocus, pausedByFocus;
    PowerManager.WakeLock wake;
    Noisy noisy;
    boolean noisyOn, foreground;
    Bitmap art;

    // the page's state
    String title = "", sub = "";
    boolean playing, busy;
    long pos, dur;
    int idx, count;

    /** from the page (main thread): updates the running service, or starts it when playback starts in front */
    static void push(MainActivity a, String json) {
        if (instance != null) {
            instance.apply(json);
            return;
        }
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("has") || !(o.optBoolean("playing") || o.optBoolean("busy")) || !a.resumed) {
                return;
            }
        } catch (JSONException e) {
            return;
        }
        Intent i = new Intent(a, PlayerService.class).putExtra(EXTRA_STATE, json);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                a.startForegroundService(i);
            } else {
                a.startService(i);
            }
        } catch (Exception e) {
            return;                                    // not allowed now: the next state change tries again
        }
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED && !a.askedNotify) {
            a.askedNotify = true;
            a.requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, MainActivity.ASK_NOTIFY);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mmfplayer:playback");
        wake.setReferenceCounted(false);
        noisy = new Noisy(this);
        art = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Đang phát", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        session = new MediaSession(this, "MayNgheMMF");
        session.setCallback(new SessionCallback(this));
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(openApp());
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? null : intent.getAction();
        if (intent != null && intent.hasExtra(EXTRA_STATE)) {
            apply(intent.getStringExtra(EXTRA_STATE));
        }
        if (!foreground || (intent != null && intent.hasExtra(EXTRA_STATE))) {
            goForeground();                            // every startForegroundService needs a startForeground
        }
        if (ACT_TOGGLE.equals(act)) {
            send(playing || busy ? "pause" : "play", 0);
        } else if (ACT_NEXT.equals(act)) {
            send("next", 0);
        } else if (ACT_PREV.equals(act)) {
            send("prev", 0);
        } else if (ACT_STOP.equals(act)) {
            send("pause", 0);
            shutdown();
        }
        return START_NOT_STICKY;
    }

    void goForeground() {
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, n);
        }
        foreground = true;
    }

    /** removes the notification and stops; the page keeps its queue */
    void shutdown() {
        if (foreground) {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            foreground = false;
        }
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent root) {
        send("pause", 0);
        shutdown();
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        if (wake.isHeld()) {
            wake.release();
        }
        setNoisy(false);
        abandonFocus();
        session.setActive(false);
        session.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** a button pressed outside the page -> window.onNative('media', {cmd, ms}) */
    void send(String cmd, long ms) {
        MainActivity a = MainActivity.instance;
        if (a != null) {
            a.emit("media", "{\"cmd\":" + JSONObject.quote(cmd) + ",\"ms\":" + ms + "}");
        }
    }

    // ------------------------------------------------------------------ state from the page

    /** {has, title, sub, playing, busy, pos, dur (ms), idx, count} */
    void apply(String json) {
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            return;
        }
        if (!o.optBoolean("has")) {                    // queue cleared
            shutdown();
            return;
        }
        String t = o.optString("title"), s = o.optString("sub");
        long d = o.optLong("dur");
        boolean meta = !t.equals(title) || !s.equals(sub) || d != dur;
        title = t;
        sub = s;
        dur = d;
        playing = o.optBoolean("playing");
        busy = o.optBoolean("busy");
        pos = o.optLong("pos");
        idx = o.optInt("idx");
        count = o.optInt("count");
        if (meta) {
            session.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, sub)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, sub)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, dur)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .build());
        }
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(playing ? PlaybackState.STATE_PLAYING : busy ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED,
                        pos, playing ? 1f : 0f, SystemClock.elapsedRealtime())
                .build());
        if (playing || busy) {
            if (!wake.isHeld()) {
                wake.acquire();
            }
        } else if (wake.isHeld()) {
            wake.release();
        }
        if (playing) {
            pausedByFocus = false;
            requestFocus();
        }
        setNoisy(playing);
        if (foreground) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTE_ID, build());
        }
    }

    // ------------------------------------------------------------------ notification

    PendingIntent action(String act, int req) {
        Intent i = new Intent(this, PlayerService.class).setAction(act);
        return PendingIntent.getService(this, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    @SuppressWarnings("deprecation")
    Notification build() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        boolean on = playing || busy;
        b.setSmallIcon(R.drawable.ic_stat_music)
                .setLargeIcon(art)
                .setContentTitle(title)
                .setContentText(busy && !playing ? "Đang dựng âm thanh…" : sub)
                .setSubText(count > 0 ? (idx + 1) + " / " + count : null)
                .setContentIntent(openApp())
                .setDeleteIntent(action(ACT_STOP, 4))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(on)
                .addAction(new Notification.Action(android.R.drawable.ic_media_previous, "Bài trước", action(ACT_PREV, 1)))
                .addAction(new Notification.Action(on ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        on ? "Tạm dừng" : "Phát", action(ACT_TOGGLE, 2)))
                .addAction(new Notification.Action(android.R.drawable.ic_media_next, "Bài sau", action(ACT_NEXT, 3)))
                .addAction(new Notification.Action(android.R.drawable.ic_menu_close_clear_cancel, "Đóng", action(ACT_STOP, 5)))
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_LOW);
        }
        return b.build();
    }

    // ------------------------------------------------------------------ audio focus, headphones unplugged

    @SuppressWarnings("deprecation")
    void requestFocus() {
        if (hasFocus) {
            return;
        }
        int r;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusReq == null) {
                focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(focusListener)
                        .build();
            }
            r = am.requestAudioFocus(focusReq);
        } else {
            r = am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        hasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @SuppressWarnings("deprecation")
    void abandonFocus() {
        if (!hasFocus) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            am.abandonAudioFocusRequest(focusReq);
        } else {
            am.abandonAudioFocus(focusListener);
        }
        hasFocus = false;
    }

    /** another app took the audio: pause; a short interruption (call, navigation prompt) resumes afterwards */
    void onFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            hasFocus = false;
            pausedByFocus = false;
            if (playing || busy) {
                send("pause", 0);
            }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (playing || busy) {
                pausedByFocus = true;
                send("pause", 0);
            }
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            hasFocus = true;
            if (pausedByFocus) {
                pausedByFocus = false;
                send("play", 0);
            }
        }
        // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Web Audio cannot be ducked from here; keeps playing
    }

    void setNoisy(boolean on) {
        if (on == noisyOn) {
            return;
        }
        if (on) {
            registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));   // system broadcast
        } else {
            unregisterReceiver(noisy);
        }
        noisyOn = on;
    }

    /** headphones unplugged / Bluetooth disconnected */
    static final class Noisy extends BroadcastReceiver {
        private final PlayerService s;

        Noisy(PlayerService owner) {
            s = owner;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) {
                s.send("pause", 0);
            }
        }
    }

    /** headset / Bluetooth buttons, lock screen and the Android 13+ media panel */
    static final class SessionCallback extends MediaSession.Callback {
        private final PlayerService s;

        SessionCallback(PlayerService owner) {
            s = owner;
        }

        @Override
        public void onPlay() {
            s.send("play", 0);
        }

        @Override
        public void onPause() {
            s.send("pause", 0);
        }

        @Override
        public void onSkipToNext() {
            s.send("next", 0);
        }

        @Override
        public void onSkipToPrevious() {
            s.send("prev", 0);
        }

        @Override
        public void onSeekTo(long ms) {
            s.send("seek", ms);
        }

        @Override
        public void onStop() {
            s.send("pause", 0);
            s.shutdown();
        }
    }
}
