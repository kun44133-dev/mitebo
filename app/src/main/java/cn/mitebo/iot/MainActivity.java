package cn.mitebo.iot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String BASE_URL = "http://iot.mitebo.cn/prod-api";
    private static final String PREFS = "mitebo_iot";
    private static final int BLUE = 0xff1677ff;
    private static final int NAVY = 0xff10233f;
    private static final int CYAN = 0xff00a6d6;
    private static final int GREEN = 0xff10b981;
    private static final int RED = 0xffef4444;
    private static final int AMBER = 0xfff59e0b;
    private static final int INK = 0xff111827;
    private static final int MUTED = 0xff6b7280;
    private static final int LINE = 0xffe5e7eb;
    private static final int PAGE_BG = 0xfff3f6fb;
    private static final String ALARM_CHANNEL_ID = "alarm_badge";
    private static final int ALARM_NOTIFICATION_ID = 1024;
    private static final long STATIC_PRESSURE_STABLE_MS = 5000;
    private static final long DEFAULT_REFRESH_MS = 15000;
    private static final long MOULD_REFRESH_MS = 5000;
    private static final long MOULD_ACTIVE_WINDOW_MS = 5000;
    private static final double MOULD_ONLINE_PRESSURE_DELTA = 10.0;
    private static final double STATIC_PRESSURE_DELTA = 10.0;

    private FrameLayout root;
    private ProgressBar loading;
    private String token;
    private String captchaUuid;
    private ImageView captchaView;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText captchaInput;
    private CheckBox rememberPasswordCheck;
    private LinearLayout content;
    private EditText macSearchInput;
    private Button alarmTabButton;
    private int currentTab = 0;
    private int unreadAlarmCount = 0;
    private int lastAlarmTotal = -1;
    private String lastSeenAlarmKey = "";
    private boolean offlineMouldMode = false;
    private long lastAlarmSoundAt = 0;
    private final List<String> expandedMouldIds = new ArrayList<>();
    private final Map<String, Double> lastPressureByDevice = new HashMap<>();
    private final Map<String, Double> stableCandidatePressureByDevice = new HashMap<>();
    private final Map<String, Long> stableSinceByDevice = new HashMap<>();
    private final Map<String, Double> staticPressureByDevice = new HashMap<>();
    private final Map<String, Boolean> staticPressureCapturedByDevice = new HashMap<>();
    private final Map<String, Long> activeMouldUntil = new HashMap<>();
    private final Map<String, TextView> visiblePressureViews = new HashMap<>();
    private final Map<String, TextView> visibleStandardViews = new HashMap<>();
    private final Map<String, TextView> visibleUpdateViews = new HashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable pressureRefresh = new Runnable() {
        @Override
        public void run() {
            if (token != null && content != null) {
                fetchAlarmCount(true);
                if (currentTab == 3) {
                    refreshVisibleMouldPressureValues();
                } else {
                    loadList(false);
                }
                refreshHandler.postDelayed(this, currentTab == 3 ? MOULD_REFRESH_MS : DEFAULT_REFRESH_MS);
            }
        }
    };

    private final String[] tabTitles = {"设备", "告警", "网关", "模具"};
    private final String[] tabEndpoints = {
            "/yujing/device/list",
            "/yujing/alarm/list",
            "/yujing/gateway/list",
            "/yujing/mould/list"
    };
    private final String[] entityEndpoints = {
            "/yujing/device",
            "/yujing/alarm",
            "/yujing/gateway",
            "/yujing/mould"
    };
    private final int[] tabIcons = {
            R.drawable.ic_device,
            R.drawable.ic_alarm,
            R.drawable.ic_gateway,
            R.drawable.ic_mould
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
        root = new FrameLayout(this);
        getWindow().setStatusBarColor(0xff0f5fd0);
        getWindow().setNavigationBarColor(0xfff5f7fb);
        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        loadingParams.gravity = Gravity.CENTER;
        setContentView(root);
        root.addView(loading, loadingParams);
        ensureAlarmChannel();
        requestNotificationPermissionIfNeeded();
        applyDebugBadgeIntent(getIntent());

        if (token == null || token.length() == 0) {
            showLogin();
        } else {
            showHome();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyDebugBadgeIntent(intent);
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(pressureRefresh);
        super.onDestroy();
    }

    private void showLogin() {
        refreshHandler.removeCallbacks(pressureRefresh);
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAGE_BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(54), dp(22), dp(24));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("密特堡压力监测");
        title.setTextSize(28);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        page.addView(title, topMargin(dp(8)));

        TextView subtitle = new TextView(this);
        subtitle.setText("工业压力监测与告警管理");
        subtitle.setTextSize(15);
        subtitle.setTextColor(MUTED);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, topMargin(dp(8)));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(22), dp(18), dp(18));
        panel.setBackground(roundedStroke(0xffffffff, 20, 0xffe6edf6));
        panel.setElevation(dp(6));
        page.addView(panel, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        usernameInput = input("账号", false);
        usernameInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_username", ""));
        panel.addView(usernameInput, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0));
        passwordInput = input("密码", true);
        boolean remembered = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("remember_password", false);
        if (remembered) {
            passwordInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_password", ""));
        }
        panel.addView(passwordInput, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), dp(12)));
        captchaInput = input("验证码", false);
        panel.addView(captchaInput, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), dp(12)));

        captchaView = new ImageView(this);
        captchaView.setBackground(roundedStroke(0xfff8fafc, 12, 0xffdbeafe));
        captchaView.setScaleType(ImageView.ScaleType.FIT_XY);
        captchaView.setOnClickListener(v -> loadCaptcha());
        LinearLayout.LayoutParams captchaParams = fixedTop(dp(220), dp(82), dp(14));
        captchaParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(captchaView, captchaParams);

        rememberPasswordCheck = new CheckBox(this);
        rememberPasswordCheck.setText("记住密码");
        rememberPasswordCheck.setTextSize(14);
        rememberPasswordCheck.setTextColor(0xff475569);
        rememberPasswordCheck.setChecked(remembered);
        panel.addView(rememberPasswordCheck, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), dp(10)));

        Button loginButton = primaryButton("登录");
        loginButton.setOnClickListener(v -> login());
        panel.addView(loginButton, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), dp(12)));

        TextView tip = new TextView(this);
        tip.setText("点击验证码图片可刷新");
        tip.setTextSize(13);
        tip.setTextColor(0xff9ca3af);
        tip.setGravity(Gravity.CENTER);
        panel.addView(tip, topMargin(dp(14)));

        TextView version = new TextView(this);
        version.setText("作者 kunkun  版本号 0.0.3");
        version.setTextSize(13);
        version.setTextColor(0xff94a3b8);
        version.setGravity(Gravity.CENTER);
        page.addView(version, topMargin(dp(22)));

        root.addView(scroll);
        root.addView(loading, centeredLoading());
        loadCaptcha();
    }

    private EditText input(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setTextColor(INK);
        input.setHintTextColor(0xff9ca3af);
        input.setBackground(roundedStroke(0xffffffff, 12, LINE));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return input;
    }

    private void loadCaptcha() {
        setLoading(true);
        new ApiTask("GET", "/captchaImage", null, false, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                captchaUuid = json.optString("uuid");
                String img = json.optString("img");
                byte[] bytes = Base64.decode(img, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, dp(220), dp(82), true);
                captchaView.setImageBitmap(scaled);
            } catch (Exception e) {
                toast("验证码解析失败");
            }
        }).execute();
    }

    private void login() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String code = captchaInput.getText().toString().trim();
        if (username.length() == 0 || password.length() == 0 || code.length() == 0) {
            toast("请填写账号、密码和验证码");
            return;
        }
        setLoading(true);
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);
            body.put("code", code);
            body.put("uuid", captchaUuid);
            new ApiTask("POST", "/login", body.toString(), false, result -> {
                setLoading(false);
                if (!result.ok) {
                    toast(result.message);
                    loadCaptcha();
                    return;
                }
                try {
                    JSONObject json = new JSONObject(result.body);
                    if (json.optInt("code") == 200) {
                        token = json.optString("token");
                        boolean remember = rememberPasswordCheck != null && rememberPasswordCheck.isChecked();
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString("token", token)
                                .putString("saved_username", username)
                                .putBoolean("remember_password", remember)
                                .putString("saved_password", remember ? password : "")
                                .apply();
                        showHome();
                    } else {
                        toast(json.optString("msg", "登录失败"));
                        loadCaptcha();
                    }
                } catch (Exception e) {
                    toast("登录响应解析失败");
                    loadCaptcha();
                }
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            toast("登录请求创建失败");
        }
    }

    private void showHome() {
        refreshHandler.removeCallbacks(pressureRefresh);
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(PAGE_BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(18), dp(16), dp(16));
        header.setBackground(gradient(NAVY, 0xff173b66, 0));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(tabTitles[currentTab] + "监控");
        title.setTextSize(24);
        title.setTextColor(0xffffffff);
        title.setTypeface(null, 1);
        copy.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(tabSubtitle());
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xffc7d8ea);
        subtitle.setPadding(0, dp(6), 0, 0);
        copy.addView(subtitle);
        titleRow.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button logout = smallButton("退出");
        styleButton(logout, 0x1affffff, 0xffffffff, 0x33ffffff);
        setIcon(logout, R.drawable.ic_logout);
        logout.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").apply();
            token = null;
            showLogin();
        });
        titleRow.addView(logout, fixedTop(dp(82), dp(38), 0));
        header.addView(titleRow);
        page.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(10), dp(12), dp(12));
        tabs.setBackgroundColor(0xffffffff);
        for (int i = 0; i < tabTitles.length; i++) {
            final int index = i;
            FrameLayout tabSlot = new FrameLayout(this);
            Button tab = smallButton(tabTitles[i]);
            setTabIcon(tab, i);
            if (i == 1) {
                alarmTabButton = tab;
            }
            tab.setTextColor(i == currentTab ? 0xffffffff : 0xff475569);
            int tabBg = i == currentTab ? tabAccent(i) : 0xfff8fafc;
            int tabLine = i == currentTab ? tabAccent(i) : 0xffe2e8f0;
            tab.setBackground(roundedStroke(tabBg, 16, tabLine));
            tab.setOnClickListener(v -> {
                currentTab = index;
                if (currentTab != 3) {
                    offlineMouldMode = false;
                }
                if (currentTab == 1) {
                    unreadAlarmCount = 0;
                    updateLauncherAlarmBadge();
                    updateAlarmTabIconTint();
                }
                expandedMouldIds.clear();
                showHome();
            });
            tabSlot.addView(tab, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
            params.leftMargin = dp(4);
            params.rightMargin = dp(4);
            tabs.addView(tabSlot, params);
        }
        page.addView(tabs);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), dp(12), dp(12), dp(2));
        actions.setBackgroundColor(PAGE_BG);
        Button refresh = smallButton("刷新");
        styleButton(refresh, 0xffffffff, 0xff1f2937, 0xffdbe3ef);
        setIcon(refresh, R.drawable.ic_refresh);
        refresh.setOnClickListener(v -> loadList());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1));
        if (currentTab == 0) {
            Button search = smallButton("MAC查询");
            styleButton(search, 0xffedf7ff, BLUE, 0xffb8dcff);
            setIcon(search, R.drawable.ic_info);
            search.setOnClickListener(v -> showMacSearchDialog());
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(44), 1);
            searchParams.leftMargin = dp(8);
            actions.addView(search, searchParams);
        }
        if (currentTab != 1) {
            String actionText = currentTab == 0 ? "添加监控" : currentTab == 3 ? (offlineMouldMode ? "在线模具" : "离线模具") : "新增" + tabTitles[currentTab];
            Button add = smallButton(actionText);
            styleButton(add, tabAccent(currentTab), 0xffffffff, tabAccent(currentTab));
            setIcon(add, currentTab == 3 ? R.drawable.ic_mould : R.drawable.ic_add);
            if (currentTab == 0) {
                add.setOnClickListener(v -> showAddMonitorDeviceDialog());
            } else if (currentTab == 3) {
                add.setOnClickListener(v -> {
                    offlineMouldMode = !offlineMouldMode;
                    expandedMouldIds.clear();
                    showHome();
                });
            } else {
                add.setOnClickListener(v -> loadOptionsForEdit(null));
            }
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(0, dp(44), 1);
            addParams.leftMargin = dp(8);
            actions.addView(add, addParams);
        }
        page.addView(actions);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        root.addView(page);
        root.addView(loading, centeredLoading());
        fetchAlarmCount(true);
        loadList(true);
        schedulePressureRefresh();
    }

    private void loadList() {
        loadList(true);
    }

    private void loadList(boolean showProgress) {
        if (currentTab == 0 && !hasDeviceDisplayFilter()) {
            if (showProgress) {
                setLoading(false);
            }
            clearVisibleMouldValueViews();
            content.removeAllViews();
            showEmpty("设备页默认不显示全部设备，请使用 MAC 查询或添加监控");
            return;
        }
        if (showProgress) {
            setLoading(true);
        }
        String endpoint = buildListEndpoint();
        new ApiTask("GET", endpoint, null, true, result -> {
            if (showProgress) {
                setLoading(false);
            }
            clearVisibleMouldValueViews();
            content.removeAllViews();
            if (!result.ok) {
                showEmpty(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                if (json.optInt("code", 200) == 401) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").apply();
                    token = null;
                    toast("登录已过期，请重新登录");
                    showLogin();
                    return;
                }
                JSONArray rows = json.optJSONArray("rows");
                if (rows == null || rows.length() == 0) {
                    if (currentTab == 1) {
                        unreadAlarmCount = 0;
                        lastAlarmTotal = 0;
                        lastSeenAlarmKey = "";
                        updateLauncherAlarmBadge();
                    }
                    showEmpty("暂无数据");
                    return;
                }
                if (currentTab == 1) {
                    unreadAlarmCount = 0;
                    lastAlarmTotal = json.optInt("total", rows.length());
                    JSONObject latest = rows.optJSONObject(0);
                    if (latest != null) {
                        lastSeenAlarmKey = alarmKey(latest);
                    }
                    updateLauncherAlarmBadge();
                }
                if (currentTab == 3) {
                    renderPressureMoulds(rows);
                    return;
                }
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject item = rows.optJSONObject(i);
                    if (item != null && matchesDeviceQuery(item) && matchesTodayAlarm(item)) {
                        content.addView(cardFor(item), topMargin(dp(10)));
                    }
                }
                if (content.getChildCount() == 0) {
                    showEmpty(currentTab == 0 ? "未找到匹配的设备" : currentTab == 1 ? "今日暂无告警" : "暂无数据");
                }
            } catch (Exception e) {
                showEmpty("数据解析失败");
            }
        }).execute();
    }

    private void fetchAlarmCount(boolean redrawTabs) {
        if (token == null) {
            return;
        }
        new ApiTask("GET", "/yujing/alarm/list?pageNum=1&pageSize=10", null, true, result -> {
            if (!result.ok) {
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                int oldUnread = unreadAlarmCount;
                String latestKey = rows == null || rows.length() == 0 ? "" : alarmKey(rows.optJSONObject(0));
                if (latestKey.length() == 0) {
                    lastSeenAlarmKey = "";
                    lastAlarmTotal = 0;
                    unreadAlarmCount = 0;
                } else if (currentTab == 1) {
                    unreadAlarmCount = 0;
                    lastSeenAlarmKey = latestKey;
                } else {
                    if (lastSeenAlarmKey.length() == 0) {
                        lastSeenAlarmKey = latestKey;
                    } else if (!latestKey.equals(lastSeenAlarmKey)) {
                        unreadAlarmCount += countNewAlarms(rows, lastSeenAlarmKey);
                        lastSeenAlarmKey = latestKey;
                    }
                }
                lastAlarmTotal = json.optInt("total", rows == null ? 0 : rows.length());
                updateLauncherAlarmBadge();
                if (unreadAlarmCount > oldUnread) {
                    playAlarmSound();
                }
                if (oldUnread != unreadAlarmCount || redrawTabs) {
                    updateAlarmTabIconTint();
                }
            } catch (Exception ignored) {
            }
        }).execute();
    }

    private void playAlarmSound() {
        long now = System.currentTimeMillis();
        if (now - lastAlarmSoundAt < 5000) {
            return;
        }
        lastAlarmSoundAt = now;
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (uri == null) {
                return;
            }
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (ringtone == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                ringtone.setLooping(false);
            }
            ringtone.play();
            Ringtone finalRingtone = ringtone;
            refreshHandler.postDelayed(() -> {
                try {
                    if (finalRingtone.isPlaying()) {
                        finalRingtone.stop();
                    }
                } catch (Exception ignored) {
                }
            }, 3000);
        } catch (Exception ignored) {
        }
    }

    private void ensureAlarmChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(ALARM_CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                ALARM_CHANNEL_ID,
                "告警角标",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(true);
        channel.setDescription("用于在桌面图标上显示未查看告警数量");
        manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        }
    }

    private void updateLauncherAlarmBadge() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (unreadAlarmCount <= 0) {
            manager.cancel(ALARM_NOTIFICATION_ID);
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
                .setContentText("有 " + unreadAlarmCount + " 条未查看告警")
                .setNumber(unreadAlarmCount)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(false)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setBadgeIconType(Notification.BADGE_ICON_SMALL);
        }
        manager.notify(ALARM_NOTIFICATION_ID, builder.build());
    }

    private void applyDebugBadgeIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("test_alarm_badge")) {
            return;
        }
        unreadAlarmCount = Math.max(0, intent.getIntExtra("test_alarm_badge", 0));
        updateLauncherAlarmBadge();
    }

    private int countNewAlarms(JSONArray rows, String previousLatestKey) {
        if (rows == null || previousLatestKey == null || previousLatestKey.length() == 0) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            String key = alarmKey(item);
            if (key.length() == 0) {
                continue;
            }
            if (key.equals(previousLatestKey)) {
                return count;
            }
            count++;
        }
        return count;
    }

    private String alarmKey(JSONObject alarm) {
        if (alarm == null) {
            return "";
        }
        String id = firstValue(alarm, "id", "alarmId", "warnId", "recordId");
        if (id.length() > 0) {
            return "id:" + id;
        }
        String time = firstValue(alarm, "createTime", "create_time", "alarmTime", "updateTime");
        String device = firstValue(alarm, "deviceId", "deviceNumber", "number", "mac", "macAddress");
        String state = firstValue(alarm, "state", "status", "type");
        if (time.length() > 0 || device.length() > 0 || state.length() > 0) {
            return "row:" + time + "|" + device + "|" + state;
        }
        return alarm.toString();
    }

    private boolean matchesDeviceQuery(JSONObject item) {
        if (currentTab != 0 || macSearchInput == null) {
            return currentTab != 0 || matchesMonitoredDevice(item);
        }
        String query = macSearchInput.getText().toString().trim().toLowerCase();
        if (query.length() == 0) {
            return matchesMonitoredDevice(item);
        }
        String[] keys = {"mac", "macAddress", "deviceMac", "number", "name"};
        for (String key : keys) {
            String value = item.optString(key).toLowerCase();
            if (value.contains(query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMonitoredDevice(JSONObject item) {
        List<String> macs = monitoredMacs();
        if (macs.size() == 0) {
            return false;
        }
        String identity = (item.optString("mac") + " " + item.optString("macAddress") + " " + item.optString("deviceMac") + " " + item.optString("number")).toLowerCase();
        for (String mac : macs) {
            if (identity.contains(mac.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDeviceDisplayFilter() {
        if (currentTab != 0) {
            return true;
        }
        if (monitoredMacs().size() > 0) {
            return true;
        }
        if (macSearchInput == null) {
            return false;
        }
        String query = macSearchInput.getText().toString().trim();
        return query.length() > 0;
    }

    private boolean matchesTodayAlarm(JSONObject item) {
        if (currentTab != 1) {
            return true;
        }
        String time = firstValue(item, "createTime", "create_time", "alarmTime", "updateTime");
        if (time.length() == 0) {
            return true;
        }
        return time.startsWith(todayPrefix());
    }

    private String buildListEndpoint() {
        String endpoint = tabEndpoints[currentTab] + "?pageNum=1&pageSize=" + (currentTab == 0 || currentTab == 1 || currentTab == 3 ? "200" : "20");
        if (currentTab == 1) {
            try {
                String today = todayPrefix();
                endpoint += "&params%5BbeginTime%5D=" + URLEncoder.encode(today + " 00:00:00", "UTF-8");
                endpoint += "&params%5BendTime%5D=" + URLEncoder.encode(today + " 23:59:59", "UTF-8");
            } catch (Exception ignored) {
            }
        }
        String mac = macSearchInput == null ? "" : macSearchInput.getText().toString().trim();
        if (currentTab == 0 && mac.length() > 0) {
            try {
                String encoded = URLEncoder.encode(mac, "UTF-8");
                endpoint += "&mac=" + encoded + "&macAddress=" + encoded + "&number=" + encoded;
            } catch (Exception ignored) {
            }
        }
        return endpoint;
    }

    private String todayPrefix() {
        return new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(new Date());
    }

    private void schedulePressureRefresh() {
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.postDelayed(pressureRefresh, currentTab == 3 ? MOULD_REFRESH_MS : DEFAULT_REFRESH_MS);
    }

    private void showMacSearchDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);
        EditText input = input("输入 MAC 地址 / 设备编号", false);
        if (macSearchInput != null) {
            input.setText(macSearchInput.getText().toString());
        }
        form.addView(input, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0));
        new AlertDialog.Builder(this)
                .setTitle("查询单个设备")
                .setView(form)
                .setNegativeButton("重置", (dialog, which) -> {
                    macSearchInput = null;
                    loadList();
                })
                .setPositiveButton("查询", (dialog, which) -> {
                    macSearchInput = input;
                    loadList();
                })
                .show();
    }

    private void showAddMonitorDeviceDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);
        EditText input = input("输入要监控的 MAC 地址", false);
        form.addView(input, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0));
        List<String> macs = monitoredMacs();
        TextView current = meta(macs.size() == 0 ? "当前监控：暂无，设备页不会默认显示全部设备" : "当前监控：" + joinList(macs));
        form.addView(current, topMargin(dp(10)));
        new AlertDialog.Builder(this)
                .setTitle("添加监控")
                .setView(form)
                .setNegativeButton("清空监控", (dialog, which) -> {
                    saveMonitoredMacs(new ArrayList<>());
                    loadList();
                })
                .setPositiveButton("添加", (dialog, which) -> {
                    String mac = input.getText().toString().trim();
                    if (mac.length() == 0) {
                        toast("请输入 MAC 地址");
                        return;
                    }
                    List<String> next = monitoredMacs();
                    boolean exists = false;
                    for (String item : next) {
                        if (item.equalsIgnoreCase(mac)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        next.add(mac);
                        saveMonitoredMacs(next);
                    }
                    macSearchInput = null;
                    loadList();
                })
                .show();
    }

    private void renderPressureMoulds(JSONArray mouldRows) {
        new ApiTask("GET", "/yujing/device/list?pageNum=1&pageSize=1000", null, true, result -> {
            if (!result.ok) {
                showEmpty(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray devices = json.optJSONArray("rows");
                Map<String, Boolean> pressureMouldIds = new HashMap<>();
                Map<String, Boolean> liveMouldIds = new HashMap<>();
                long now = System.currentTimeMillis();
                if (devices != null) {
                    for (int i = 0; i < devices.length(); i++) {
                        JSONObject device = devices.optJSONObject(i);
                        if (device == null) {
                            continue;
                        }
                        updateStaticPressure(device);
                        String mouldId = device.optString("mouldId");
                        if (mouldId.length() == 0) {
                            JSONObject mould = device.optJSONObject("mould");
                            mouldId = mould == null ? "" : mould.optString("id");
                        }
                        if (mouldId.length() > 0 && hasLivePressure(device)) {
                            liveMouldIds.put(mouldId, true);
                            if (hasPressureFluctuation(device)) {
                                activeMouldUntil.put(mouldId, now + MOULD_ACTIVE_WINDOW_MS);
                            }
                        }
                    }
                }
                for (Map.Entry<String, Long> entry : activeMouldUntil.entrySet()) {
                    if (entry.getValue() >= now) {
                        pressureMouldIds.put(entry.getKey(), true);
                    }
                }
                int count = 0;
                for (int i = 0; i < mouldRows.length(); i++) {
                    JSONObject mould = mouldRows.optJSONObject(i);
                    if (mould == null) {
                        continue;
                    }
                    String mouldId = mould.optString("id");
                    boolean online = pressureMouldIds.containsKey(mouldId);
                    boolean offline = liveMouldIds.containsKey(mouldId) && !online;
                    if ((!offlineMouldMode && online) || (offlineMouldMode && offline)) {
                        mould.put("_dynamicOnline", online);
                        mould.put("_offlinePressure", offline);
                        content.addView(cardFor(mould), topMargin(dp(10)));
                        count++;
                    }
                }
                if (count == 0) {
                    showEmpty(offlineMouldMode ? "暂无离线模具" : "暂无压力动态波动的模具");
                }
            } catch (Exception e) {
                showEmpty("模具压力筛选失败");
            }
        }).execute();
    }

    private boolean hasLivePressure(JSONObject device) {
        String pressure = device.optString("pressure");
        return pressure.length() > 0 && !"null".equals(pressure) && !"-".equals(pressure);
    }

    private boolean hasDynamicPressure(JSONObject device) {
        if (!hasLivePressure(device)) {
            return false;
        }
        Double pressure = numberValue(device.optString("pressure"));
        if (pressure == null) {
            return false;
        }
        Double lower = numberValue(device.optString("lower"));
        Double upper = numberValue(device.optString("upper"));
        Double standard = numberValue(device.optString("standard"));
        if (lower != null && pressure < lower) {
            return true;
        }
        if (upper != null && pressure > upper) {
            return true;
        }
        if (standard != null && Math.abs(pressure - standard) >= MOULD_ONLINE_PRESSURE_DELTA) {
            return true;
        }
        return false;
    }

    private boolean hasPressureFluctuation(JSONObject device) {
        Double pressure = numberValue(device.optString("pressure"));
        if (pressure == null) {
            return false;
        }
        String key = deviceKey(device);
        if (key.length() == 0) {
            return false;
        }
        Double last = lastPressureByDevice.get(key);
        lastPressureByDevice.put(key, pressure);
        return last != null && Math.abs(pressure - last) >= MOULD_ONLINE_PRESSURE_DELTA;
    }

    private void updateStaticPressure(JSONObject device) {
        Double pressure = numberValue(device.optString("pressure"));
        String key = deviceKey(device);
        if (pressure == null || key.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Double candidate = stableCandidatePressureByDevice.get(key);
        if (candidate == null || Math.abs(pressure - candidate) >= STATIC_PRESSURE_DELTA) {
            stableCandidatePressureByDevice.put(key, pressure);
            stableSinceByDevice.put(key, now);
            staticPressureCapturedByDevice.put(key, false);
            return;
        }
        Long since = stableSinceByDevice.get(key);
        if (since != null && now - since >= STATIC_PRESSURE_STABLE_MS) {
            boolean captured = Boolean.TRUE.equals(staticPressureCapturedByDevice.get(key));
            if (!captured) {
                staticPressureByDevice.put(key, pressure);
                staticPressureCapturedByDevice.put(key, true);
                TextView standardView = visibleStandardViews.get(key);
                if (standardView != null) {
                    standardView.setText("静止压力：" + trimNumber(pressure));
                }
            }
        }
    }

    private String staticPressureText(JSONObject device) {
        String key = deviceKey(device);
        Double value = key.length() == 0 ? null : staticPressureByDevice.get(key);
        if (value != null) {
            return trimNumber(value);
        }
        return clean(device.optString("standard"));
    }

    private String deviceKey(JSONObject device) {
        return firstValue(device, "id", "number", "mac", "macAddress");
    }

    private View cardFor(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 0, 0, dp(14));
        card.setBackground(roundedStroke(0xffffffff, 16, 0xffe6edf6));
        card.setElevation(dp(4));

        View strip = new View(this);
        strip.setBackground(rounded(tabAccent(currentTab), 16));
        card.addView(strip, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(5), 0));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), dp(13), dp(14), dp(4));

        TextView icon = new TextView(this);
        icon.setGravity(Gravity.CENTER);
        icon.setText(iconLetter());
        icon.setTextSize(14);
        icon.setTypeface(null, 1);
        icon.setTextColor(0xffffffff);
        icon.setBackground(rounded(tabAccent(currentTab), 11));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.rightMargin = dp(10);
        heading.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText(primaryTitle(item));
        title.setTextSize(16);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        heading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = badgeText(item);
        if (badge != null) {
            heading.addView(badge);
        }
        card.addView(heading);

        if (currentTab == 1) {
            addAlarmPressureDetails(card, item);
        }

        if (hasAny(item, "pressure", "standard", "battery")) {
            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.setPadding(dp(14), dp(10), dp(6), dp(4));
            addMetric(metrics, "实时压力", item.optString("pressure"), 0xffeafaf1, 0xff118a44);
            addMetric(metrics, "标准压力", item.optString("standard"), 0xffedf7ff, BLUE);
            addMetric(metrics, "电池", item.optString("battery"), 0xfffff7e6, 0xffb45309);
            if (metrics.getChildCount() > 0) {
                card.addView(metrics);
            }
        }

        String[] keys = {
                "name", "number", "mac", "macAddress", "state", "status", "customer",
                "lower", "upper", "rssi",
                "remark", "createTime", "updateTime"
        };
        for (String key : keys) {
            if (currentTab == 3 && "state".equals(key)) {
                continue;
            }
            if (item.has(key) && !item.isNull(key)) {
                String value = item.optString(key);
                if (value.length() > 0) {
                    if (currentTab == 3 && "status".equals(key) && (item.optBoolean("_dynamicOnline") || item.optBoolean("_offlinePressure"))) {
                        value = item.optBoolean("_offlinePressure") ? "离线" : "在线";
                    }
                    if (currentTab == 1 && ("state".equals(key) || "status".equals(key))) {
                        value = alarmStatusText(value);
                    }
                    if ((currentTab == 0 || currentTab == 2) && "status".equals(key)) {
                        value = onlineStatusText(value);
                    }
                    card.addView(meta(labelFor(key) + "：" + value), sideMargin(dp(14), dp(14), 0));
                }
            }
        }

        JSONObject dept = item.optJSONObject("dept");
        if (dept != null) {
            card.addView(meta("组织：" + dept.optString("deptName")), sideMargin(dp(14), dp(14), 0));
        }
        JSONObject gateway = item.optJSONObject("gateway");
        if (gateway != null) {
            card.addView(meta("网关：" + gateway.optString("number") + " " + gateway.optString("name")), sideMargin(dp(14), dp(14), 0));
        }
        JSONObject mould = item.optJSONObject("mould");
        if (mould != null) {
            card.addView(meta("模具：" + mould.optString("number") + " " + mould.optString("name")), sideMargin(dp(14), dp(14), 0));
        }

        if (currentTab == 3) {
            addMouldDropdown(card, item);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(14), dp(12), dp(14), 0);
        Button detail = smallButton("详情");
        styleButton(detail, 0xfff8fafc, 0xff334155, 0xffdbe3ef);
        setIcon(detail, R.drawable.ic_info);
        detail.setOnClickListener(v -> showDetail(item));
        actions.addView(detail, new LinearLayout.LayoutParams(0, dp(40), 1));

        if (currentTab != 1) {
            Button edit = smallButton(currentTab == 3 ? "上下限" : "修改");
            styleButton(edit, 0xffecfdf5, 0xff047857, 0xffa7f3d0);
            setIcon(edit, currentTab == 3 ? R.drawable.ic_pressure : R.drawable.ic_edit);
            edit.setOnClickListener(v -> {
                if (currentTab == 3) {
                    showMouldLimitDialog(item);
                } else {
                    loadDetailForEdit(item);
                }
            });
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(40), 1);
            editParams.leftMargin = dp(8);
            actions.addView(edit, editParams);
        }
        card.addView(actions);
        return card;
    }

    private void addMouldDropdown(LinearLayout card, JSONObject mould) {
        String mouldId = mould.optString("id");
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(roundedStroke(0xfff8fbff, 12, 0xffdbeafe));
        boolean expanded = expandedMouldIds.contains(mouldId);
        panel.setVisibility(expanded ? View.VISIBLE : View.GONE);

        Button toggle = smallButton("展开模具详情");
        toggle.setText(expanded ? "收起模具详情" : "展开模具详情");
        styleButton(toggle, 0xfff8fafc, 0xff334155, 0xffdbe3ef);
        setIcon(toggle, R.drawable.ic_info);
        LinearLayout.LayoutParams toggleParams = fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(38), dp(10));
        toggleParams.leftMargin = dp(14);
        toggleParams.rightMargin = dp(14);
        card.addView(toggle, toggleParams);
        card.addView(panel, sideMargin(dp(14), dp(14), dp(8)));

        final boolean[] loaded = {expanded};
        if (expanded) {
            loadMouldDropdownDevices(mould, panel);
        }
        toggle.setOnClickListener(v -> {
            if (panel.getVisibility() == View.VISIBLE) {
                panel.setVisibility(View.GONE);
                toggle.setText("展开模具详情");
                expandedMouldIds.remove(mouldId);
                return;
            }
            panel.setVisibility(View.VISIBLE);
            toggle.setText("收起模具详情");
            if (!expandedMouldIds.contains(mouldId)) {
                expandedMouldIds.add(mouldId);
            }
            if (!loaded[0]) {
                loaded[0] = true;
                loadMouldDropdownDevices(mould, panel);
            }
        });
    }

    private void loadMouldDropdownDevices(JSONObject mould, LinearLayout panel) {
        panel.removeAllViews();
        panel.addView(meta("正在加载绑定设备和压力..."));
        String id = mould.optString("id");
        if (id.length() == 0) {
            panel.removeAllViews();
            panel.addView(meta("缺少模具 ID"));
            return;
        }
        String endpoint = "/yujing/device/list?pageNum=1&pageSize=100&mouldId=" + id;
        new ApiTask("GET", endpoint, null, true, result -> {
            panel.removeAllViews();
            if (!result.ok) {
                panel.addView(meta(result.message));
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                renderMouldDeviceRows(panel, rows);
            } catch (Exception e) {
                panel.addView(meta("设备压力解析失败"));
            }
        }).execute();
    }

    private void renderMouldDeviceRows(LinearLayout panel, JSONArray rows) {
        if (rows == null || rows.length() == 0) {
            panel.addView(meta("暂无绑定设备"));
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject device = rows.optJSONObject(i);
            if (device == null) {
                continue;
            }
            updateStaticPressure(device);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(rounded(0xffffffff, 10));

            TextView title = new TextView(this);
            title.setText(primaryTitle(device));
            title.setTextColor(INK);
            title.setTextSize(15);
            title.setTypeface(null, 1);
            row.addView(title);

            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.setPadding(0, dp(8), 0, 0);
            TextView pressureValue = addMetric(metrics, "实时", device.optString("pressure"), 0xffeafaf1, 0xff118a44);
            addMetric(metrics, "上限", device.optString("upper"), 0xfffff1f2, 0xffdc2626);
            addMetric(metrics, "下限", device.optString("lower"), 0xfffff7e6, 0xffb45309);
            if (metrics.getChildCount() > 0) {
                row.addView(metrics);
            }

            TextView standardView = meta("静止压力：" + staticPressureText(device));
            TextView updateView = meta("更新时间：" + clean(device.optString("updateTime")));
            row.addView(standardView);
            row.addView(updateView);
            String key = deviceKey(device);
            if (key.length() > 0) {
                if (pressureValue != null) {
                    visiblePressureViews.put(key, pressureValue);
                }
                visibleStandardViews.put(key, standardView);
                visibleUpdateViews.put(key, updateView);
            }
            LinearLayout.LayoutParams params = topMargin(i == 0 ? 0 : dp(8));
            panel.addView(row, params);
        }
    }

    private void showMouldLimitDialog(JSONObject mould) {
        String id = mould.optString("id");
        if (id.length() == 0) {
            toast("缺少模具 ID，无法加载传感器");
            return;
        }
        setLoading(true);
        new ApiTask("GET", "/yujing/device/list?pageNum=1&pageSize=100&mouldId=" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                if (rows == null || rows.length() == 0) {
                    toast("该模具暂无绑定传感器");
                    return;
                }
                showMouldLimitEditor(mould, rows);
            } catch (Exception e) {
                toast("传感器数据解析失败");
            }
        }).execute();
    }

    private void showMouldLimitEditor(JSONObject mould, JSONArray rows) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), dp(8));

        List<OptionItem> sensors = new ArrayList<>();
        List<JSONObject> devices = new ArrayList<>();
        sensors.add(new OptionItem("", "请选择传感器"));
        devices.add(null);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject device = rows.optJSONObject(i);
            if (device == null) {
                continue;
            }
            devices.add(device);
            sensors.add(new OptionItem(deviceKey(device), sensorLabel(device)));
        }

        Spinner sensorSpinner = spinner(sensors, "");
        EditText lower = input("报警下限", false);
        EditText upper = input("报警上限", false);
        TextView pressure = meta("实时压力：-");
        TextView standard = meta("静止压力：-");

        form.addView(label("传感器"));
        form.addView(sensorSpinner, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        form.addView(pressure, topMargin(dp(8)));
        form.addView(standard, topMargin(dp(2)));
        form.addView(label("报警下限"));
        form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("报警上限"));
        form.addView(upper, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));

        sensorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                JSONObject device = position >= 0 && position < devices.size() ? devices.get(position) : null;
                if (device == null) {
                    lower.setText("");
                    upper.setText("");
                    pressure.setText("实时压力：-");
                    standard.setText("静止压力：-");
                    return;
                }
                lower.setText(cleanInput(device.optString("lower")));
                upper.setText(cleanInput(device.optString("upper")));
                pressure.setText("实时压力：" + clean(device.optString("pressure")));
                standard.setText("静止压力：" + staticPressureText(device));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(primaryTitle(mould) + " - 压力上下限")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialogInterface, which) -> {
                    int position = sensorSpinner.getSelectedItemPosition();
                    JSONObject device = position >= 0 && position < devices.size() ? devices.get(position) : null;
                    if (device == null) {
                        toast("请选择要修改的传感器");
                        return;
                    }
                    saveMouldSensorLimits(device, lower.getText().toString().trim(), upper.getText().toString().trim());
                })
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private String sensorLabel(JSONObject device) {
        String number = firstValue(device, "number", "deviceNumber", "mac", "macAddress");
        String name = firstValue(device, "name", "deviceName");
        String pressure = clean(device.optString("pressure"));
        String title = (number + " " + name).trim();
        return (title.length() == 0 ? "传感器" : title) + "  实时 " + pressure;
    }

    private String cleanInput(String value) {
        if (value == null || value.length() == 0 || "null".equals(value) || "-".equals(value)) {
            return "";
        }
        return value;
    }

    private void saveMouldSensorLimits(JSONObject device, String lower, String upper) {
        setLoading(true);
        try {
            JSONObject body = new JSONObject(device.toString());
            body.put("lower", lower);
            body.put("upper", upper);
            new ApiTask("PUT", "/yujing/device", body.toString(), true, result -> {
                setLoading(false);
                if (!result.ok) {
                    toast(result.message);
                    return;
                }
                try {
                    JSONObject json = new JSONObject(result.body);
                    if (json.optInt("code", 200) == 200) {
                        toast("上下限已保存");
                        loadList(false);
                    } else {
                        toast(json.optString("msg", "保存失败"));
                    }
                } catch (Exception e) {
                    toast("上下限已保存");
                    loadList(false);
                }
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            toast("保存内容创建失败");
        }
    }

    private void refreshVisibleMouldPressureValues() {
        if (visiblePressureViews.size() == 0 && visibleStandardViews.size() == 0 && visibleUpdateViews.size() == 0) {
            return;
        }
        new ApiTask("GET", "/yujing/device/list?pageNum=1&pageSize=1000", null, true, result -> {
            if (!result.ok) {
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                if (rows == null) {
                    return;
                }
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject device = rows.optJSONObject(i);
                    if (device == null) {
                        continue;
                    }
                    updateStaticPressure(device);
                    String mouldId = device.optString("mouldId");
                    if (mouldId.length() == 0) {
                        JSONObject mould = device.optJSONObject("mould");
                        mouldId = mould == null ? "" : mould.optString("id");
                    }
                    if (mouldId.length() > 0 && hasLivePressure(device) && hasPressureFluctuation(device)) {
                        activeMouldUntil.put(mouldId, System.currentTimeMillis() + MOULD_ACTIVE_WINDOW_MS);
                    }
                    String key = deviceKey(device);
                    if (key.length() == 0) {
                        continue;
                    }
                    TextView pressureView = visiblePressureViews.get(key);
                    if (pressureView != null) {
                        pressureView.setText(clean(device.optString("pressure")));
                    }
                    TextView standardView = visibleStandardViews.get(key);
                    if (standardView != null) {
                        standardView.setText("静止压力：" + staticPressureText(device));
                    }
                    TextView updateView = visibleUpdateViews.get(key);
                    if (updateView != null) {
                        updateView.setText("更新时间：" + clean(device.optString("updateTime")));
                    }
                }
            } catch (Exception ignored) {
            }
        }).execute();
    }

    private void clearVisibleMouldValueViews() {
        visiblePressureViews.clear();
        visibleStandardViews.clear();
        visibleUpdateViews.clear();
    }

    private void addAlarmPressureDetails(LinearLayout card, JSONObject alarm) {
        JSONArray details = alarmDetails(alarm);
        if (details == null || details.length() == 0) {
            String detailText = alarm.optString("detail");
            if (detailText.length() > 0 && !"null".equals(detailText)) {
                card.addView(meta("报警详情：" + detailText), sideMargin(dp(14), dp(14), 0));
            }
            return;
        }
        for (int i = 0; i < details.length(); i++) {
            JSONObject sensor = details.optJSONObject(i);
            if (sensor == null) {
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(roundedStroke(0xfffffbeb, 12, 0xfffde68a));

            TextView title = new TextView(this);
            title.setText("传感器：" + sensorName(sensor));
            title.setTextSize(15);
            title.setTextColor(0xff92400e);
            title.setTypeface(null, 1);
            row.addView(title);

            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.setPadding(0, dp(8), 0, 0);
            addMetric(metrics, "报警压力", sensor.optString("pressure"), 0xfffff1f2, 0xffdc2626);
            addMetric(metrics, "下限", sensor.optString("lower"), 0xfffff7e6, 0xffb45309);
            addMetric(metrics, "上限", sensor.optString("upper"), 0xfffff1f2, 0xffdc2626);
            if (metrics.getChildCount() > 0) {
                row.addView(metrics);
            }
            row.addView(meta("告警时间：" + clean(firstValue(sensor, "create_time", "createTime"))));

            LinearLayout.LayoutParams params = sideMargin(dp(14), dp(14), dp(10));
            card.addView(row, params);
        }
    }

    private JSONArray alarmDetails(JSONObject alarm) {
        Object value = alarm.opt("detail");
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        if (value instanceof JSONObject) {
            JSONArray array = new JSONArray();
            array.put(value);
            return array;
        }
        String text = alarm.optString("detail");
        if (text.length() == 0 || "null".equals(text)) {
            return null;
        }
        try {
            Object parsed = new org.json.JSONTokener(text).nextValue();
            if (parsed instanceof JSONArray) {
                return (JSONArray) parsed;
            }
            if (parsed instanceof JSONObject) {
                JSONArray array = new JSONArray();
                array.put(parsed);
                return array;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String sensorName(JSONObject sensor) {
        String number = firstValue(sensor, "number", "deviceNumber", "mac", "macAddress");
        String name = firstValue(sensor, "name", "deviceName");
        String result = (number + " " + name).trim();
        return result.length() == 0 ? "未知传感器" : result;
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

    private void showMouldDevices(JSONObject mould) {
        String id = mould.optString("id");
        if (id.length() == 0) {
            toast("缺少模具 ID，无法查询设备");
            return;
        }
        setLoading(true);
        String endpoint = "/yujing/device/list?pageNum=1&pageSize=100&mouldId=" + id;
        new ApiTask("GET", endpoint, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                showMouldDevicesDialog(mould, rows);
            } catch (Exception e) {
                toast("设备压力解析失败");
            }
        }).execute();
    }

    private void showMouldDevicesDialog(JSONObject mould, JSONArray rows) {
        StringBuilder builder = new StringBuilder();
        if (rows == null || rows.length() == 0) {
            builder.append("暂无绑定设备");
        } else {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                builder.append(i + 1).append(". ").append(primaryTitle(item)).append("\n");
                appendIfPresent(builder, item, "pressure");
                appendIfPresent(builder, item, "standard");
                appendIfPresent(builder, item, "lower");
                appendIfPresent(builder, item, "upper");
                appendIfPresent(builder, item, "battery");
                appendIfPresent(builder, item, "rssi");
                appendIfPresent(builder, item, "state");
                appendIfPresent(builder, item, "status");
                appendIfPresent(builder, item, "updateTime");
                if (i < rows.length() - 1) {
                    builder.append("\n");
                }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(primaryTitle(mould) + " - 设备压力")
                .setMessage(builder.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showDetail(JSONObject item) {
        String id = item.optString("id");
        if (id.length() == 0) {
            showDetailDialog(item);
            return;
        }
        setLoading(true);
        new ApiTask("GET", entityEndpoints[currentTab] + "/" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONObject data = json.optJSONObject("data");
                showDetailDialog(data != null ? data : item);
            } catch (Exception e) {
                showDetailDialog(item);
            }
        }).execute();
    }

    private void showDetailDialog(JSONObject item) {
        new AlertDialog.Builder(this)
                .setTitle(tabTitles[currentTab] + "详情")
                .setMessage(prettyObject(item))
                .setPositiveButton("关闭", null)
                .show();
    }

    private void loadDetailForEdit(JSONObject item) {
        String id = item.optString("id");
        if (id.length() == 0) {
            loadOptionsForEdit(item);
            return;
        }
        setLoading(true);
        new ApiTask("GET", entityEndpoints[currentTab] + "/" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONObject data = json.optJSONObject("data");
                loadOptionsForEdit(data != null ? data : item);
            } catch (Exception e) {
                loadOptionsForEdit(item);
            }
        }).execute();
    }

    private void loadOptionsForEdit(JSONObject source) {
        setLoading(true);
        new OptionsTask(source, result -> {
            setLoading(false);
            showEditDialog(source, result);
        }).execute();
    }

    private void showEditDialog(JSONObject source, OptionsState options) {
        boolean editing = source != null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(4), dp(14), dp(8));
        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Spinner dept = null;
        Spinner mould = null;
        if (currentTab != 1) {
            dept = spinner(options.depts, valueOf(source, "deptId"));
            form.addView(label("组织"));
            form.addView(dept, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        }
        if (currentTab == 0 || currentTab == 2) {
            mould = spinner(options.moulds, valueOf(source, "mouldId"));
            form.addView(label("绑定模具"));
            form.addView(mould, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        }

        EditText number = input("编号", false);
        number.setText(source == null ? "" : source.optString("number"));
        form.addView(label(currentTab == 0 ? "设备编号" : currentTab == 2 ? "网关编号" : "编号"));
        form.addView(number, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), 0));

        EditText name = input("名称", false);
        name.setText(source == null ? "" : source.optString("name"));
        form.addView(label("名称"));
        form.addView(name, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        EditText standard = null;
        EditText lower = null;
        EditText upper = null;
        if (currentTab == 0) {
            standard = input("标准压力", false);
            standard.setText(source == null ? "" : source.optString("standard"));
            form.addView(label("标准压力"));
            form.addView(standard, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            lower = input("下限值", false);
            lower.setText(source == null ? "" : source.optString("lower"));
            form.addView(label("报警下限"));
            form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            upper = input("上限值", false);
            upper.setText(source == null ? "" : source.optString("upper"));
            form.addView(label("报警上限"));
            form.addView(upper, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
        }

        EditText longitude = null;
        EditText latitude = null;
        EditText pointState = null;
        if (currentTab == 2) {
            longitude = input("经度", false);
            longitude.setText(source == null ? "" : source.optString("longitude"));
            form.addView(label("经度"));
            form.addView(longitude, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            latitude = input("纬度", false);
            latitude.setText(source == null ? "" : source.optString("latitude"));
            form.addView(label("纬度"));
            form.addView(latitude, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            pointState = input("定位开关：0关 / 1开", false);
            pointState.setText(source == null ? "" : source.optString("pointState"));
            form.addView(label("定位开关"));
            form.addView(pointState, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
        }

        EditText customer = null;
        if (currentTab == 3) {
            customer = input("客户", false);
            customer.setText(source == null ? "" : source.optString("customer"));
            form.addView(label("客户"));
            form.addView(customer, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
        }

        EditText remark = input("备注", false);
        remark.setText(source == null ? "" : source.optString("remark"));
        form.addView(label("备注"));
        form.addView(remark, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        final Spinner deptInput = dept;
        final Spinner mouldInput = mould;
        final EditText standardInput = standard;
        final EditText lowerInput = lower;
        final EditText upperInput = upper;
        final EditText longitudeInput = longitude;
        final EditText latitudeInput = latitude;
        final EditText pointStateInput = pointState;
        final EditText customerInput = customer;

        AlertDialog editDialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "修改" + tabTitles[currentTab] : "新增" + tabTitles[currentTab])
                .setView(formScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialogInterface, which) -> {
                    try {
                        JSONObject body = source == null ? new JSONObject() : new JSONObject(source.toString());
                        body.put("number", number.getText().toString().trim());
                        body.put("name", name.getText().toString().trim());
                        body.put("remark", remark.getText().toString().trim());
                        putSelected(body, "deptId", deptInput);
                        putSelected(body, "mouldId", mouldInput);
                        putText(body, "standard", standardInput);
                        putText(body, "lower", lowerInput);
                        putText(body, "upper", upperInput);
                        putText(body, "longitude", longitudeInput);
                        putText(body, "latitude", latitudeInput);
                        putText(body, "pointState", pointStateInput);
                        if (customerInput != null) {
                            body.put("customer", customerInput.getText().toString().trim());
                        }
                        saveEntity(editing, body);
                    } catch (Exception e) {
                        toast("保存内容创建失败");
                    }
                })
                .create();
        editDialog.setOnShowListener(d -> {
            if (editDialog.getWindow() != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
                editDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        editDialog.show();
    }

    private void saveEntity(boolean editing, JSONObject body) {
        setLoading(true);
        new ApiTask(editing ? "PUT" : "POST", entityEndpoints[currentTab], body.toString(), true, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                int code = json.optInt("code", 200);
                if (code == 200) {
                    toast(editing ? "修改成功" : "新增成功");
                    loadList();
                } else {
                    toast(json.optString("msg", "保存失败"));
                }
            } catch (Exception e) {
                toast("保存完成");
                loadList();
            }
        }).execute();
    }

    private void confirmDelete(JSONObject item) {
        String id = item.optString("id");
        if (id.length() == 0) {
            toast("缺少记录 ID，无法删除");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定删除「" + primaryTitle(item) + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteEntity(id))
                .show();
    }

    private void deleteEntity(String id) {
        setLoading(true);
        new ApiTask("DELETE", entityEndpoints[currentTab] + "/" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                int code = json.optInt("code", 200);
                if (code == 200) {
                    toast("删除成功");
                    loadList();
                } else {
                    toast(json.optString("msg", "删除失败"));
                }
            } catch (Exception e) {
                toast("删除完成");
                loadList();
            }
        }).execute();
    }

    private String prettyObject(JSONObject object) {
        StringBuilder builder = new StringBuilder();
        String[] preferred = {
                "id", "number", "name", "state", "status", "customer",
                "pressure", "standard", "lower", "upper", "battery", "rssi",
                "remark", "createTime", "updateTime"
        };
        for (String key : preferred) {
            appendIfPresent(builder, object, key);
        }
        JSONObject dept = object.optJSONObject("dept");
        if (dept != null) {
            builder.append("组织：").append(dept.optString("deptName")).append("\n");
        }
        JSONObject gateway = object.optJSONObject("gateway");
        if (gateway != null) {
            builder.append("网关：").append(gateway.optString("number")).append(" ").append(gateway.optString("name")).append("\n");
        }
        JSONObject mould = object.optJSONObject("mould");
        if (mould != null) {
            builder.append("模具：").append(mould.optString("number")).append(" ").append(mould.optString("name")).append("\n");
        }
        return builder.length() == 0 ? object.toString() : builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, JSONObject object, String key) {
        if (object.has(key) && !object.isNull(key)) {
            String value = object.optString(key);
            if (value.length() > 0) {
                builder.append(labelFor(key)).append("：").append(value).append("\n");
            }
        }
    }

    private String primaryTitle(JSONObject item) {
        if (currentTab == 1) {
            JSONArray details = alarmDetails(item);
            if (details != null && details.length() > 0) {
                JSONObject first = details.optJSONObject(0);
                if (first != null) {
                    return "报警 - " + sensorName(first);
                }
            }
            JSONObject mouldObj = item.optJSONObject("mould");
            if (mouldObj != null) {
                return "报警 - " + mouldObj.optString("number") + " " + mouldObj.optString("name");
            }
        }
        String number = item.optString("number");
        String name = item.optString("name");
        if (number.length() > 0 && name.length() > 0) {
            return number + "  " + name;
        }
        if (number.length() > 0) {
            return number;
        }
        if (name.length() > 0) {
            return name;
        }
        JSONObject gateway = item.optJSONObject("gateway");
        JSONObject mould = item.optJSONObject("mould");
        if (gateway != null || mould != null) {
            return (gateway != null ? gateway.optString("number") : "") + " / " + (mould != null ? mould.optString("number") : "");
        }
        return "记录 #" + item.optString("id");
    }

    private TextView meta(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(0xff526174);
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private TextView addMetric(LinearLayout parent, String label, String value, int bgColor, int textColor) {
        if (value == null || value.length() == 0 || "null".equals(value)) {
            return null;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(9), dp(10), dp(9));
        box.setBackground(roundedStroke(bgColor, 12, 0x11000000));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(textColor);
        valueView.setTextSize(17);
        valueView.setTypeface(null, 1);
        box.addView(valueView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xff718096);
        labelView.setTextSize(12);
        box.addView(labelView, topMargin(dp(2)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.rightMargin = dp(8);
        parent.addView(box, params);
        return valueView;
    }

    private TextView badgeText(JSONObject item) {
        if (currentTab == 3 && (item.optBoolean("_dynamicOnline") || item.optBoolean("_offlinePressure"))) {
            TextView badge = new TextView(this);
            boolean offline = item.optBoolean("_offlinePressure");
            badge.setText(offline ? "离线" : "在线");
            badge.setTextSize(12);
            badge.setTextColor(offline ? 0xff92400e : 0xff0f766e);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(rounded(offline ? 0xfffffbeb : 0xffecfdf5, 10));
            return badge;
        }
        String status = item.optString("status");
        String state = item.optString("state");
        if (currentTab == 1) {
            String raw = state.length() > 0 && !"null".equals(state) ? state : status;
            if (raw.length() > 0 && !"null".equals(raw)) {
                boolean cleared = isAlarmCleared(raw);
                TextView badge = new TextView(this);
                badge.setText(cleared ? "已消除" : "未消除");
                badge.setTextSize(12);
                badge.setTextColor(cleared ? 0xff0f766e : 0xffdc2626);
                badge.setPadding(dp(8), dp(4), dp(8), dp(4));
                badge.setBackground(rounded(cleared ? 0xffecfdf5 : 0xfffff1f2, 10));
                return badge;
            }
        }
        if ((currentTab == 0 || currentTab == 2) && status.length() > 0 && !"null".equals(status)) {
            String text = onlineStatusText(status);
            boolean offline = isOfflineStatus(status);
            TextView badge = new TextView(this);
            badge.setText(text);
            badge.setTextSize(12);
            badge.setTextColor(offline ? 0xff92400e : 0xff0f766e);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(rounded(offline ? 0xfffffbeb : 0xffecfdf5, 10));
            return badge;
        }
        String text = status.length() > 0 ? "在线 " + status : state.length() > 0 ? "状态 " + state : "";
        if (text.length() == 0 || "null".equals(text)) {
            return null;
        }
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(12);
        badge.setTextColor(0xff0f766e);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(rounded(0xffecfdf5, 10));
        return badge;
    }

    private boolean hasAny(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key);
            if (value.length() > 0 && !"null".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private List<String> monitoredMacs() {
        List<String> result = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("monitor_macs", "");
        String[] parts = saved.split("\\n");
        for (String part : parts) {
            String value = part.trim();
            if (value.length() > 0) {
                result.add(value);
            }
        }
        return result;
    }

    private void saveMonitoredMacs(List<String> macs) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("monitor_macs", joinList(macs))
                .apply();
    }

    private String joinList(List<String> items) {
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private String clean(String value) {
        if (value == null || value.length() == 0 || "null".equals(value)) {
            return "-";
        }
        return value;
    }

    private String onlineStatusText(String status) {
        if (status == null) {
            return "-";
        }
        String value = status.trim();
        if ("1".equals(value) || "online".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "在线".equals(value)) {
            return "在线";
        }
        if ("0".equals(value) || "offline".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "离线".equals(value)) {
            return "离线";
        }
        return value;
    }

    private boolean isOfflineStatus(String status) {
        String text = onlineStatusText(status);
        String raw = String.valueOf(status).trim();
        return "离线".equals(text) || "0".equals(raw) || "offline".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw);
    }

    private String alarmStatusText(String status) {
        if (status == null || status.trim().length() == 0 || "null".equals(status)) {
            return "-";
        }
        return isAlarmCleared(status) ? "已消除" : "未消除";
    }

    private boolean isAlarmCleared(String status) {
        String value = String.valueOf(status).trim();
        return "1".equals(value)
                || "已消除".equals(value)
                || "已消警".equals(value)
                || "cleared".equalsIgnoreCase(value)
                || "resolved".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value);
    }

    private Double numberValue(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9+\\-.]", "");
        if (cleaned.length() == 0 || "-".equals(cleaned) || ".".equals(cleaned)) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private String trimNumber(Double value) {
        if (value == null) {
            return "-";
        }
        if (Math.abs(value - Math.round(value)) < 0.0001) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String iconLetter() {
        if (currentTab == 0) return "设";
        if (currentTab == 1) return "警";
        if (currentTab == 2) return "关";
        return "模";
    }

    private String tabSubtitle() {
        if (currentTab == 0) return "按 MAC 查询或监控关键传感器的实时压力";
        if (currentTab == 1) return "今日告警、压力详情和消除状态";
        if (currentTab == 2) return "网关在线状态、绑定模具和设备概览";
        return offlineMouldMode ? "查看有压力但波动不足的离线模具" : "仅显示压力动态波动的在线生产模具";
    }

    private int tabAccent(int tab) {
        if (tab == 0) return BLUE;
        if (tab == 1) return RED;
        if (tab == 2) return CYAN;
        return GREEN;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(0xff374151);
        view.setPadding(0, dp(6), 0, 0);
        return view;
    }

    private Spinner spinner(List<OptionItem> items, String selectedValue) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<OptionItem> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                items
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).value.equals(selectedValue)) {
                spinner.setSelection(i);
                break;
            }
        }
        return spinner;
    }

    private String valueOf(JSONObject source, String key) {
        if (source == null || !source.has(key) || source.isNull(key)) {
            return "";
        }
        return source.optString(key);
    }

    private void putSelected(JSONObject body, String key, Spinner spinner) throws Exception {
        if (spinner == null) {
            return;
        }
        Object selected = spinner.getSelectedItem();
        if (selected instanceof OptionItem) {
            OptionItem item = (OptionItem) selected;
            if (item.value.length() > 0) {
                body.put(key, item.value);
            } else {
                body.remove(key);
            }
        }
    }

    private void putText(JSONObject body, String key, EditText input) throws Exception {
        if (input == null) {
            return;
        }
        String value = input.getText().toString().trim();
        if (value.length() > 0) {
            body.put(key, value);
        } else {
            body.remove(key);
        }
    }

    private String labelFor(String key) {
        if ("name".equals(key)) return "名称";
        if ("number".equals(key)) return "编号";
        if ("mac".equals(key)) return "MAC";
        if ("macAddress".equals(key)) return "MAC";
        if ("state".equals(key)) return "报警状态";
        if ("status".equals(key)) return "在线状态";
        if ("customer".equals(key)) return "客户";
        if ("pressure".equals(key)) return "实时压力";
        if ("standard".equals(key)) return "标准压力";
        if ("lower".equals(key)) return "报警下限";
        if ("upper".equals(key)) return "报警上限";
        if ("battery".equals(key)) return "电池电量";
        if ("rssi".equals(key)) return "信号强度";
        if ("remark".equals(key)) return "备注";
        if ("createTime".equals(key)) return "创建时间";
        if ("updateTime".equals(key)) return "更新时间";
        if ("id".equals(key)) return "ID";
        return key;
    }

    private void showEmpty(String text) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(18), dp(20), dp(18), dp(20));
        panel.setBackground(roundedStroke(0xffffffff, 18, 0xffe6edf6));
        panel.setElevation(dp(4));

        TextView icon = new TextView(this);
        icon.setText("·");
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(40);
        icon.setTextColor(tabAccent(currentTab));
        panel.addView(icon, fixedTop(dp(46), dp(30), 0));

        TextView empty = new TextView(this);
        empty.setText("暂无数据");
        empty.setTextSize(17);
        empty.setTypeface(null, 1);
        empty.setTextColor(INK);
        empty.setGravity(Gravity.CENTER);
        panel.addView(empty);

        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(14);
        message.setTextColor(MUTED);
        message.setGravity(Gravity.CENTER);
        panel.addView(message, topMargin(dp(8)));

        content.addView(panel, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), dp(34)));
    }

    private void showEmptyOld(String text) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextSize(15);
        empty.setTextColor(0xff6b7280);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(160), dp(40)));
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(0xffffffff);
        button.setTypeface(null, 1);
        button.setAllCaps(false);
        button.setBackground(gradient(BLUE, CYAN, 14));
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(null, 1);
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        return button;
    }

    private void styleButton(Button button, int bgColor, int textColor, int strokeColor) {
        button.setTextColor(textColor);
        button.setBackground(roundedStroke(bgColor, 14, strokeColor));
    }

    private void setIcon(Button button, int resId) {
        button.setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(5));
    }

    private void setTabIcon(Button button, int index) {
        if (index == 1) {
            setTintedIcon(button, tabIcons[index], unreadAlarmCount > 0 ? 0xffef4444 : 0xff5b6472);
        } else {
            setIcon(button, tabIcons[index]);
        }
    }

    private void updateAlarmTabIconTint() {
        if (alarmTabButton != null) {
            setTintedIcon(alarmTabButton, R.drawable.ic_alarm, unreadAlarmCount > 0 ? 0xffef4444 : 0xff5b6472);
        }
    }

    private void setTintedIcon(Button button, int resId, int color) {
        Drawable icon = Build.VERSION.SDK_INT >= 21 ? getDrawable(resId) : getResources().getDrawable(resId);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color);
        }
        button.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(5));
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable gradient(int startColor, int endColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{startColor, endColor}
        );
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedStroke(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void setLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = top;
        return params;
    }

    private LinearLayout.LayoutParams sideMargin(int left, int right, int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.leftMargin = left;
        params.rightMargin = right;
        params.topMargin = top;
        return params;
    }

    private LinearLayout.LayoutParams fixedTop(int width, int height, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = top;
        return params;
    }

    private FrameLayout.LayoutParams centeredLoading() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44), dp(44));
        params.gravity = Gravity.CENTER;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ApiCallback {
        void done(ApiResult result);
    }

    private static class ApiResult {
        boolean ok;
        String body;
        String message;
    }

    private interface OptionsCallback {
        void done(OptionsState result);
    }

    private static class OptionItem {
        final String value;
        String label;

        OptionItem(String value, String label) {
            this.value = value == null ? "" : value;
            this.label = label == null ? "" : label;
        }

        @Override
        public String toString() {
            return label.length() == 0 ? value : label;
        }
    }

    private static class OptionsState {
        final List<OptionItem> depts = new ArrayList<>();
        final List<OptionItem> moulds = new ArrayList<>();
    }

    private class OptionsTask extends AsyncTask<Void, Void, OptionsState> {
        private final JSONObject source;
        private final OptionsCallback callback;

        OptionsTask(JSONObject source, OptionsCallback callback) {
            this.source = source;
            this.callback = callback;
        }

        @Override
        protected OptionsState doInBackground(Void... voids) {
            OptionsState state = new OptionsState();
            state.depts.add(new OptionItem("", "请选择组织"));
            state.moulds.add(new OptionItem("", "请选择绑定模具"));
            if (currentTab != 1) {
                try {
                    JSONObject deptJson = requestJson("GET", "/system/user/deptTree", null, true);
                    flattenDeptOptions(deptJson.optJSONArray("data"), state.depts, "");
                } catch (Exception ignored) {
                }
            }
            if (currentTab == 0 || currentTab == 2) {
                try {
                    JSONObject mouldJson = requestJson("GET", "/yujing/mould/onList?pageNum=1&pageSize=200", null, true);
                    appendMouldOptions(mouldJson.optJSONArray("rows"), state.moulds);
                } catch (Exception ignored) {
                }
                try {
                    JSONObject deviceJson = requestJson("GET", "/yujing/device/list?pageNum=1&pageSize=500", null, true);
                    appendDeviceInfoToMouldOptions(deviceJson.optJSONArray("rows"), state.moulds);
                } catch (Exception ignored) {
                }
                JSONObject mould = source == null ? null : source.optJSONObject("mould");
                String mouldId = valueOf(source, "mouldId");
                if (mould != null && mouldId.length() > 0 && !containsOption(state.moulds, mouldId)) {
                    state.moulds.add(new OptionItem(mouldId, mould.optString("number") + " " + mould.optString("name")));
                }
            }
            return state;
        }

        @Override
        protected void onPostExecute(OptionsState optionsState) {
            callback.done(optionsState);
        }
    }

    private class ApiTask extends AsyncTask<Void, Void, ApiResult> {
        private final String method;
        private final String endpoint;
        private final String body;
        private final boolean auth;
        private final ApiCallback callback;

        ApiTask(String method, String endpoint, String body, boolean auth, ApiCallback callback) {
            this.method = method;
            this.endpoint = endpoint;
            this.body = body;
            this.auth = auth;
            this.callback = callback;
        }

        @Override
        protected ApiResult doInBackground(Void... voids) {
            ApiResult result = new ApiResult();
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL + endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                if (auth && token != null) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    OutputStream os = connection.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.close();
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                result.body = readAll(stream);
                result.ok = code >= 200 && code < 400;
                result.message = result.ok ? "" : "请求失败：" + code;
            } catch (Exception e) {
                result.ok = false;
                result.message = "网络请求失败";
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(ApiResult apiResult) {
            callback.done(apiResult);
        }
    }

    private JSONObject requestJson(String method, String endpoint, String body, boolean auth) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            if (auth && token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                OutputStream os = connection.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new JSONObject(readAll(stream));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void flattenDeptOptions(JSONArray array, List<OptionItem> output, String prefix) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id");
            String label = item.optString("label", item.optString("deptName"));
            if (id.length() > 0 && label.length() > 0) {
                output.add(new OptionItem(id, prefix + label));
            }
            flattenDeptOptions(item.optJSONArray("children"), output, prefix + "  ");
        }
    }

    private void appendMouldOptions(JSONArray array, List<OptionItem> output) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id");
            String number = item.optString("number");
            String name = item.optString("name");
            if (id.length() > 0) {
                output.add(new OptionItem(id, (number + " " + name).trim()));
            }
        }
    }

    private void appendDeviceInfoToMouldOptions(JSONArray devices, List<OptionItem> moulds) {
        if (devices == null || moulds.size() <= 1) {
            return;
        }
        Map<String, StringBuilder> deviceInfoByMould = new HashMap<>();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) {
                continue;
            }
            String mouldId = device.optString("mouldId");
            if (mouldId.length() == 0) {
                JSONObject mould = device.optJSONObject("mould");
                mouldId = mould == null ? "" : mould.optString("id");
            }
            if (mouldId.length() == 0) {
                continue;
            }
            StringBuilder line = deviceInfoByMould.get(mouldId);
            if (line == null) {
                line = new StringBuilder();
                deviceInfoByMould.put(mouldId, line);
            } else {
                line.append("；");
            }
            line.append(device.optString("number"));
            String name = device.optString("name");
            if (name.length() > 0) {
                line.append(" ").append(name);
            }
            String pressure = device.optString("pressure");
            if (pressure.length() > 0 && !"null".equals(pressure)) {
                line.append(" 实时").append(pressure);
            }
            String standard = device.optString("standard");
            if (standard.length() > 0 && !"null".equals(standard)) {
                line.append(" 标准").append(standard);
            }
        }
        for (OptionItem mould : moulds) {
            StringBuilder deviceInfo = deviceInfoByMould.get(mould.value);
            if (deviceInfo != null && deviceInfo.length() > 0) {
                mould.label = mould.label + " | 设备 " + deviceInfo;
            }
        }
    }

    private boolean containsOption(List<OptionItem> items, String value) {
        for (OptionItem item : items) {
            if (item.value.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String readAll(InputStream stream) throws Exception {
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
