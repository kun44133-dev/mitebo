package cn.mitebo.iot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AlarmMonitorService extends Service {
    private static final String BASE_URL = "http://iot.mitebo.cn/prod-api";
    private static final String PREFS = "mitebo_iot";
    private static final String PREF_ALARM_SOUND_URI = "alarm_sound_uri";
    private static final String PREF_ALARM_SOUND_ENABLED = "alarm_sound_enabled";
    private static final String PREF_BACKGROUND_ALARM_MONITOR = "background_alarm_monitor";
    private static final String ALARM_CHANNEL_ID = "alarm_badge";
    private static final String MONITOR_CHANNEL_ID = "alarm_monitor";
    private static final int ALARM_NOTIFICATION_ID = 1024;
    private static final int MONITOR_NOTIFICATION_ID = 1025;
    private static final long POLL_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;
    private int activeAlarmCount = 0;
    private Ringtone activeRingtone;
    private boolean alarmSoundLooping = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollAlarmState();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable soundRepeater = new Runnable() {
        @Override
        public void run() {
            if (!alarmSoundLooping || activeAlarmCount <= 0) {
                stopAlarmSoundLoop();
                return;
            }
            try {
                if (activeRingtone != null && !activeRingtone.isPlaying()) {
                    activeRingtone.play();
                }
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String token = token();
        if (token.length() == 0 || !backgroundAlarmMonitorEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(MONITOR_NOTIFICATION_ID, monitorNotification());
        if (!polling) {
            polling = true;
            pollAlarmState();
            handler.postDelayed(pollRunnable, POLL_MS);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        stopAlarmSoundLoop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void pollAlarmState() {
        String token = token();
        if (token.length() == 0 || !backgroundAlarmMonitorEnabled()) {
            clearAlarmNotification();
            stopAlarmSoundLoop();
            stopSelf();
            return;
        }
        new Thread(() -> {
            int count = activeAlarmCount;
            boolean ok = false;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + "/yujing/alarm/list?pageNum=1&pageSize=50").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                String body = readAll(stream);
                if (code >= 200 && code < 400) {
                    JSONObject json = new JSONObject(body);
                    JSONArray rows = json.optJSONArray("rows");
                    count = countActiveAlarms(rows);
                    ok = true;
                }
                connection.disconnect();
            } catch (Exception ignored) {
            }
            int finalCount = count;
            boolean finalOk = ok;
            handler.post(() -> {
                if (finalOk) {
                    applyAlarmCount(finalCount);
                } else {
                    applyAlarmCount(0);
                }
            });
        }).start();
    }

    private int countActiveAlarms(JSONArray rows) {
        int count = 0;
        if (rows == null) {
            return 0;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject alarm = rows.optJSONObject(i);
            if (alarm == null) {
                continue;
            }
            String state = firstValue(alarm, "state", "status", "type");
            if (state.length() == 0 || !isAlarmCleared(state)) {
                count++;
            }
        }
        return count;
    }

    private void applyAlarmCount(int count) {
        activeAlarmCount = Math.max(0, count);
        if (activeAlarmCount > 0) {
            showAlarmNotification(activeAlarmCount);
            startAlarmSoundLoop();
        } else {
            clearAlarmNotification();
            stopAlarmSoundLoop();
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(MONITOR_NOTIFICATION_ID, monitorNotification());
        }
    }

    private void startAlarmSoundLoop() {
        if (!alarmSoundEnabled() || alarmSoundLooping) {
            return;
        }
        try {
            Uri uri = selectedAlarmSoundUri();
            if (uri == null) {
                return;
            }
            activeRingtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (activeRingtone == null) {
                return;
            }
            alarmSoundLooping = true;
            if (Build.VERSION.SDK_INT >= 28) {
                activeRingtone.setLooping(true);
            }
            activeRingtone.play();
            if (Build.VERSION.SDK_INT < 28) {
                handler.postDelayed(soundRepeater, 3000);
            }
        } catch (Exception ignored) {
            alarmSoundLooping = false;
        }
    }

    private void stopAlarmSoundLoop() {
        handler.removeCallbacks(soundRepeater);
        alarmSoundLooping = false;
        if (activeRingtone != null) {
            try {
                if (activeRingtone.isPlaying()) {
                    activeRingtone.stop();
                }
            } catch (Exception ignored) {
            }
            activeRingtone = null;
        }
    }

    private Notification monitorNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                1,
                intent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, MONITOR_CHANNEL_ID)
                : new Notification.Builder(this);
        String text = activeAlarmCount > 0 ? "未消除告警 " + activeAlarmCount + " 条，正在提醒" : "正在后台监控告警";
        builder.setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("密特堡告警监控")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false);
        return builder.build();
    }

    private void showAlarmNotification(int count) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ALARM_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("密特堡压力监测")
                .setContentText("有 " + count + " 条未消除告警")
                .setNumber(count)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(false)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setBadgeIconType(Notification.BADGE_ICON_SMALL);
        }
        manager.notify(ALARM_NOTIFICATION_ID, builder.build());
    }

    private void clearAlarmNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ALARM_NOTIFICATION_ID);
        }
    }

    private Uri selectedAlarmSoundUri() {
        if (!alarmSoundEnabled()) {
            return null;
        }
        String saved = prefs().getString(PREF_ALARM_SOUND_URI, null);
        if (saved != null) {
            if (saved.length() == 0) {
                return null;
            }
            return Uri.parse(saved);
        }
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        return uri;
    }

    private boolean alarmSoundEnabled() {
        return prefs().getBoolean(PREF_ALARM_SOUND_ENABLED, true);
    }

    private boolean backgroundAlarmMonitorEnabled() {
        return prefs().getBoolean(PREF_BACKGROUND_ALARM_MONITOR, false);
    }

    private String token() {
        return prefs().getString("token", "");
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
            NotificationChannel alarm = new NotificationChannel(ALARM_CHANNEL_ID, "告警角标", NotificationManager.IMPORTANCE_LOW);
            alarm.setShowBadge(true);
            alarm.setDescription("用于在桌面图标上显示未消除告警数量");
            manager.createNotificationChannel(alarm);
        }
        if (manager.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
            NotificationChannel monitor = new NotificationChannel(MONITOR_CHANNEL_ID, "后台告警监控", NotificationManager.IMPORTANCE_LOW);
            monitor.setShowBadge(false);
            monitor.setDescription("用于保持后台告警实时监控");
            manager.createNotificationChannel(monitor);
        }
    }

    private String firstValue(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key);
            if (value.length() > 0 && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isAlarmCleared(String status) {
        if (status == null) {
            return false;
        }
        String value = status.trim();
        return "1".equals(value)
                || "已消除".equals(value)
                || "已消警".equals(value)
                || "cleared".equalsIgnoreCase(value)
                || "resolved".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value);
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
