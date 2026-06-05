package cn.mitebo.iot;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class AlarmAlertController {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Ringtone ringtone;
    private static boolean playing = false;
    private static boolean vibrating = false;

    private static final Runnable RINGTONE_REPEATER = new Runnable() {
        @Override
        public void run() {
            if (!playing) {
                return;
            }
            try {
                if (ringtone != null && !ringtone.isPlaying()) {
                    ringtone.play();
                }
            } catch (Exception ignored) {
            }
            HANDLER.postDelayed(this, 3000);
        }
    };

    private AlarmAlertController() {
    }

    static synchronized void start(Context context, Uri uri, boolean vibrationEnabled) {
        // 全局只允许一个告警铃声实例，避免多条告警或前后台同时触发时声音重叠。
        stop(context);
        if (context == null || uri == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        try {
            ringtone = RingtoneManager.getRingtone(appContext, uri);
            if (ringtone == null) {
                return;
            }
            playing = true;
            if (Build.VERSION.SDK_INT >= 28) {
                ringtone.setLooping(true);
            }
            ringtone.play();
            if (Build.VERSION.SDK_INT < 28) {
                HANDLER.postDelayed(RINGTONE_REPEATER, 3000);
            }
            if (vibrationEnabled) {
                startVibrationLoop(appContext);
            }
        } catch (Exception ignored) {
            stop(appContext);
        }
    }

    static synchronized void stop(Context context) {
        HANDLER.removeCallbacks(RINGTONE_REPEATER);
        playing = false;
        if (ringtone != null) {
            try {
                if (ringtone.isPlaying()) {
                    ringtone.stop();
                }
            } catch (Exception ignored) {
            }
            ringtone = null;
        }
        stopVibrationLoop(context);
    }

    static synchronized void vibrateOnce(Context context, boolean vibrationEnabled) {
        if (!vibrationEnabled || context == null) {
            return;
        }
        try {
            Vibrator vibrator = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(350);
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized void startVibrationLoop(Context context) {
        if (vibrating || context == null) {
            return;
        }
        try {
            Vibrator vibrator = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            vibrating = true;
            long[] pattern = {0, 650, 450, 650, 1200};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {
            vibrating = false;
        }
    }

    static synchronized void stopVibrationLoop(Context context) {
        if (!vibrating && context == null) {
            return;
        }
        vibrating = false;
        try {
            if (context == null) {
                return;
            }
            Vibrator vibrator = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {
        }
    }
}
