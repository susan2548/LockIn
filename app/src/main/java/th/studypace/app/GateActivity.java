package th.studypace.app;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;

/** A separate opaque task makes the interrupted app lose foreground playback. */
public final class GateActivity extends Activity {
    private GuardService service;
    private AudioManager audio;
    private AudioFocusRequest audioRequest;
    private boolean resumed;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        audio = getSystemService(AudioManager.class);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (resumed) connect();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (connect() && audio != null && audioRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs).setOnAudioFocusChangeListener(change -> {}).build();
            if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) audioRequest = request;
        }
    }

    private boolean connect() {
        service = GuardService.active();
        if (service != null && service.attachGate(this, getIntent().getStringExtra("package"))) return true;
        finishAndRemoveTask();
        return false;
    }

    @Override protected void onPause() {
        resumed = false;
        if (audioRequest != null) {
            audio.abandonAudioFocusRequest(audioRequest);
            audioRequest = null;
        }
        if (service != null) service.detachGate(this);
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !isFinishing()) finishAndRemoveTask();
    }

    @Override public void onBackPressed() {
        if (service != null) service.goHome(); else finishAndRemoveTask();
    }
}
