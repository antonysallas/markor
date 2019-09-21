package net.gsantner.opoc.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.gsantner.opoc.util.Callback;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;


//
// Callback: Called when successfully recorded
// will contain path to file in cache directory. Must be copied to custom location in callback handler
//
// Add to build.gradle: implementation 'com.kailashdabhi:om-recorder:1.1.5'
// Add to manifest: <uses-permission android:name="android.permission.RECORD_AUDIO" />
//
@SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
public class AudioRecordOmDialog {
    public static void showAudioRecordDialog(final Activity activity, final int themeIdRes, @StringRes final int titleResId, final Callback.a1<File> recordFinishedCallbackWithPathToTemporaryFile) {
        ////////////////////////////////////
        // Request permission in case not granted. Do not show dialog UI in this case
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
            return;
        }

        ////////////////////////////////////
        // Init
        final String EMOJI_MICROPHONE = "\uD83C\uDFA4";
        final String EMOJI_STOP = "\uD83D\uDED1";
        final String EMOJI_RESTART = "\uD83D\uDD04";
        final String EMOJI_SPEAKER = "\uD83D\uDD0A";

        final AtomicBoolean isRecording = new AtomicBoolean();
        final AtomicBoolean isRecordSavedOnce = new AtomicBoolean();
        final AtomicReference<Recorder> recorder = new AtomicReference<>();
        final AtomicReference<MediaPlayer> mediaPlayer = new AtomicReference<>();
        final File TMP_FILE_RECORDING = new File(activity.getCacheDir(), "recording.wav");
        if (TMP_FILE_RECORDING.exists()) {
            TMP_FILE_RECORDING.delete();
        }

        // Record management callbacks
        final Callback.a1<Boolean> recorderManager = (cbArgRestart) -> {
            if (cbArgRestart) {
                final PullableSource SRC_MICROPHONE = new PullableSource.Default(new AudioRecordConfig.Default(MediaRecorder.AudioSource.MIC, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_IN_STEREO, 44100));
                recorder.set(OmRecorder.wav(new PullTransport.Default(SRC_MICROPHONE), TMP_FILE_RECORDING));
                recorder.get().startRecording();
            }
        };

        ////////////////////////////////////
        // Create UI
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity, themeIdRes);
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView playbackButton = new TextView(activity);
        final TextView recordButton = new TextView(activity);
        final View sep1 = new View(activity);
        sep1.setLayoutParams(new LinearLayout.LayoutParams(100, 1));

        // Record button
        recordButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        recordButton.setGravity(Gravity.CENTER_HORIZONTAL);
        recordButton.setText(EMOJI_MICROPHONE);
        recordButton.setOnClickListener(v -> {
            if (isRecording.get()) {
                try {
                    recorder.get().stopRecording();
                    isRecordSavedOnce.set(true);
                } catch (Exception ignored) {
                }
            } else {
                recorderManager.callback(true);
            }

            // Update state
            isRecording.set(!isRecording.get());
            recordButton.setText(isRecording.get() ? EMOJI_STOP : EMOJI_RESTART);
            playbackButton.setEnabled(!isRecording.get());
        });

        final Callback.a0 playbackStoppedCallback = () -> {
            recordButton.setEnabled(true);
            if (mediaPlayer.get() != null) {
                mediaPlayer.getAndSet(null).release();
            }
            playbackButton.setText(EMOJI_SPEAKER);
        };

        // Play button
        playbackButton.setEnabled(false);
        playbackButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        playbackButton.setGravity(Gravity.CENTER_HORIZONTAL);
        playbackButton.setText(EMOJI_SPEAKER);
        playbackButton.setOnClickListener(v -> {
            final boolean startPlaybackNow = mediaPlayer.get() == null;
            recordButton.setEnabled(false);
            playbackButton.setText(startPlaybackNow ? EMOJI_STOP : EMOJI_SPEAKER);
            if (startPlaybackNow) {
                try {
                    MediaPlayer player = new MediaPlayer();
                    mediaPlayer.set(player);
                    player.setDataSource(TMP_FILE_RECORDING.getAbsolutePath());
                    player.prepare();
                    player.start();
                    player.setOnCompletionListener(mp -> playbackStoppedCallback.callback());
                    player.setLooping(false);
                } catch (IOException ignored) {
                }
            } else {
                mediaPlayer.get().stop();
                playbackStoppedCallback.callback();
            }
        });

        ////////////////////////////////////
        // Callback for OK & Cancel dialog button
        DialogInterface.OnClickListener dialogOkAndCancelListener = (dialogInterface, dialogButtonCase) -> {
            final boolean isSavePressed = (dialogButtonCase == DialogInterface.BUTTON_POSITIVE);
            if (isRecordSavedOnce.get()) {
                try {
                    recorder.get().stopRecording();
                } catch (Exception ignored) {
                }
                if (!isSavePressed) {
                    if (TMP_FILE_RECORDING.exists()) {
                        TMP_FILE_RECORDING.delete();
                    }
                } else if (recordFinishedCallbackWithPathToTemporaryFile != null) {
                    recordFinishedCallbackWithPathToTemporaryFile.callback(TMP_FILE_RECORDING);
                }
            }
            dialogInterface.dismiss();
        };

        ////////////////////////////////////
        // Tooltip
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackButton.setTooltipText("Play recording / Stop playback");
            recordButton.setTooltipText("Record Audio (Voice Note)");
        }

        ////////////////////////////////////
        // Add to layout
        layout.addView(playbackButton);
        layout.addView(sep1);
        layout.addView(recordButton);

        ////////////////////////////////////
        // Create & show dialog
        dialogBuilder
                .setTitle(titleResId)
                .setPositiveButton(android.R.string.ok, dialogOkAndCancelListener)
                .setNegativeButton(android.R.string.cancel, dialogOkAndCancelListener)
                .setView(layout);
        final AlertDialog dialog = dialogBuilder.create();
        Window w;
        dialog.show();
        if ((w = dialog.getWindow()) != null) {
            w.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static String generateFilePath(File recordDirectory) {
        try {
            final String prefix = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date()) + "-record-";
            return File.createTempFile(prefix, ".wav", recordDirectory).getAbsolutePath();
        } catch (Exception ignored) {
        }
        return null;
    }
}