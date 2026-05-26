package cn.mitebo.iot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String BASE_URL = "http://iot.mitebo.cn/prod-api";
    private static final String PREFS = "mitebo_iot";
    private static final String PREF_ALARM_SOUND_URI = "alarm_sound_uri";
    private static final String PREF_ALARM_SOUND_ENABLED = "alarm_sound_enabled";
    private static final String PREF_BACKGROUND_ALARM_MONITOR = "background_alarm_monitor";
    private static final int REQ_ALARM_SOUND = 310;
    private static final int BLUE = 0xff1f6feb;
    private static final int NAVY = 0xff071827;
    private static final int NAVY_2 = 0xff0e2a42;
    private static final int CYAN = 0xff06a9d7;
    private static final int GREEN = 0xff10b981;
    private static final int RED = 0xffef4444;
    private static final int AMBER = 0xfff6c343;
    private static final int INK = 0xff102033;
    private static final int MUTED = 0xff667085;
    private static final int LINE = 0xffd9e2ee;
    private static final int PAGE_BG = 0xffedf2f8;
    private static final int SURFACE = 0xffffffff;
    private static final int SOFT = 0xfff6f9fd;
    private static final String ALARM_CHANNEL_ID = "alarm_badge";
    private static final int ALARM_NOTIFICATION_ID = 1024;
    private static final long STATIC_PRESSURE_STABLE_MS = 10000;
    private static final long DEFAULT_REFRESH_MS = 15000;
    private static final long ALARM_REFRESH_MS = 5000;
    private static final long MOULD_REFRESH_MS = 5000;
    private static final long MOULD_ACTIVE_WINDOW_MS = 5000;
    private static final double MOULD_ONLINE_PRESSURE_DELTA = 10.0;
    private static final double STATIC_PRESSURE_DELTA = 10.0;
    private static final double STATIC_PRESSURE_STABLE_DELTA = 2.0;
    private static final double STATIC_PRESSURE_OVERWRITE_DELTA = 2.0;

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
    private String alarmDateFilter = "";
    private boolean alarmDateManuallySelected = false;
    private boolean offlineMouldMode = false;
    private boolean appInForeground = false;
    private float mouldPullStartY = -1f;
    private boolean mouldPullReady = false;
    private View openedDeviceSwipeCard;
    private long lastAlarmSoundAt = 0;
    private Ringtone activeAlarmRingtone;
    private boolean alarmSoundLooping = false;
    private final List<String> expandedMouldIds = new ArrayList<>();
    private final List<String> expandedAlarmIds = new ArrayList<>();
    private final List<String> expandedGatewayIds = new ArrayList<>();
    private final Map<String, Double> lastPressureByDevice = new HashMap<>();
    private final Map<String, Double> stableCandidatePressureByDevice = new HashMap<>();
    private final Map<String, Long> stableSinceByDevice = new HashMap<>();
    private final Map<String, Double> staticPressureByDevice = new HashMap<>();
    private final Map<String, Boolean> staticPressureCapturedByDevice = new HashMap<>();
    private final Map<String, Boolean> dynamicSeenByDevice = new HashMap<>();
    private final Map<String, Long> activeMouldUntil = new HashMap<>();
    private final Map<String, TextView> visiblePressureViews = new HashMap<>();
    private final Map<String, TextView> visibleStandardViews = new HashMap<>();
    private final Map<String, TextView> visibleUpdateViews = new HashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable pressureRefresh = new Runnable() {
        @Override
        public void run() {
            if (appInForeground && token != null && content != null) {
                if (currentTab == 2) {
                    refreshVisibleMouldPressureValues();
                } else {
                    loadList(false);
                }
                refreshHandler.postDelayed(this, currentTab == 2 ? MOULD_REFRESH_MS : DEFAULT_REFRESH_MS);
            }
        }
    };
    private final Runnable alarmRefresh = new Runnable() {
        @Override
        public void run() {
            if (appInForeground && token != null) {
                fetchAlarmCount(false);
                refreshHandler.postDelayed(this, ALARM_REFRESH_MS);
            }
        }
    };

    private final String[] tabTitles = {"设备", "告警", "模具", "设置"};
    private final String[] tabEndpoints = {
            "/yujing/device/list",
            "/yujing/alarm/list",
            "/yujing/mould/list",
            ""
    };
    private final String[] entityEndpoints = {
            "/yujing/device",
            "/yujing/alarm",
            "/yujing/mould",
            ""
    };
    private final int[] tabIcons = {
            R.drawable.ic_device,
            R.drawable.ic_alarm,
            R.drawable.ic_mould,
            R.drawable.ic_settings
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
        root = new FrameLayout(this);
        applyAdaptiveSystemBars();
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
        applyAdaptiveSystemBars();
        applyDebugBadgeIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ALARM_SOUND || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_ALARM_SOUND_URI, picked == null ? "" : picked.toString())
                .apply();
        toast(picked == null ? "已关闭报警提示音" : "报警提示音已设置");
        playAlarmSound(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        appInForeground = true;
        stopAlarmMonitorService();
        if (token != null && token.length() > 0 && content != null) {
            fetchAlarmCount(true);
            schedulePressureRefresh();
            scheduleAlarmRefresh();
        }
    }

    @Override
    protected void onStop() {
        appInForeground = false;
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.removeCallbacks(alarmRefresh);
        stopAlarmSoundLoop();
        if (token != null && token.length() > 0 && backgroundAlarmMonitorEnabled()) {
            startAlarmMonitorService();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.removeCallbacks(alarmRefresh);
        stopAlarmSoundLoop();
        super.onDestroy();
    }

    private void applyAdaptiveSystemBars() {
        applyAdaptiveSystemBars(false);
    }

    private void applyAdaptiveSystemBars(boolean darkSystemBarIcons) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(PAGE_BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        int flags = 0;
        if (darkSystemBarIcons && Build.VERSION.SDK_INT >= 23) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (darkSystemBarIcons && Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void showLogin() {
        applyAdaptiveSystemBars();
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.removeCallbacks(alarmRefresh);
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(gradient(NAVY, 0xff0b3a5a, 0));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(48), dp(22), dp(24));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("密特堡压力监测");
        title.setTextSize(30);
        title.setTextColor(0xffffffff);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        page.addView(title, topMargin(dp(2)));

        TextView subtitle = new TextView(this);
        subtitle.setText("设备状态、模具压力、告警联动");
        subtitle.setTextSize(15);
        subtitle.setTextColor(0xffb7c9d9);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, topMargin(dp(2)));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(20), dp(18), dp(18));
        panel.setBackground(roundedStroke(SURFACE, 22, 0xffdce7f3));
        smoothElevation(panel, 10);
        page.addView(panel, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(7)));

        TextView panelTitle = new TextView(this);
        panelTitle.setText("账号登录");
        panelTitle.setTextSize(18);
        panelTitle.setTextColor(INK);
        panelTitle.setTypeface(null, 1);
        panel.addView(panelTitle);

        TextView panelSub = new TextView(this);
        panelSub.setText("请输入验证码后进入监控工作台");
        panelSub.setTextSize(13);
        panelSub.setTextColor(MUTED);
        panel.addView(panelSub, topMargin(dp(4)));

        LinearLayout accountRow = new LinearLayout(this);
        accountRow.setOrientation(LinearLayout.HORIZONTAL);
        accountRow.setGravity(Gravity.CENTER_VERTICAL);
        accountRow.setPadding(0, 0, dp(4), 0);
        accountRow.setBackground(roundedStroke(0xfffbfdff, 14, LINE));
        usernameInput = input("账号", false);
        usernameInput.setBackground(rounded(0x00ffffff, 0));
        usernameInput.setText(latestSavedUsername());
        accountRow.addView(usernameInput, new LinearLayout.LayoutParams(0, dp(50), 1));
        View divider = new View(this);
        divider.setBackgroundColor(0xffdbe3ef);
        accountRow.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(26)));
        TextView accountPicker = new TextView(this);
        accountPicker.setText("▾");
        accountPicker.setTextSize(18);
        accountPicker.setTypeface(null, 1);
        accountPicker.setTextColor(0xff64748b);
        accountPicker.setGravity(Gravity.CENTER);
        accountPicker.setBackground(rounded(0x00ffffff, 0));
        accountPicker.setOnClickListener(v -> showSavedAccountChooser());
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(dp(44), dp(50));
        accountRow.addView(accountPicker, pickerParams);
        panel.addView(accountRow, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), dp(18)));
        passwordInput = input("密码", true);
        boolean remembered = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("remember_password", false);
        if (remembered) {
            passwordInput.setText(savedPasswordFor(latestSavedUsername()));
        }
        panel.addView(passwordInput, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), dp(12)));

        LinearLayout captchaRow = new LinearLayout(this);
        captchaRow.setOrientation(LinearLayout.HORIZONTAL);
        captchaRow.setGravity(Gravity.CENTER_VERTICAL);
        captchaInput = input("验证码", false);
        captchaRow.addView(captchaInput, new LinearLayout.LayoutParams(0, dp(50), 1));
        captchaView = new ImageView(this);
        captchaView.setBackground(roundedStroke(0xfff8fbff, 14, 0xffc7d7ea));
        captchaView.setScaleType(ImageView.ScaleType.FIT_XY);
        captchaView.setOnClickListener(v -> loadCaptcha());
        LinearLayout.LayoutParams captchaParams = new LinearLayout.LayoutParams(dp(126), dp(50));
        captchaParams.leftMargin = dp(10);
        captchaRow.addView(captchaView, captchaParams);
        panel.addView(captchaRow, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), dp(12)));

        rememberPasswordCheck = new CheckBox(this);
        rememberPasswordCheck.setText("记住密码");
        rememberPasswordCheck.setTextSize(14);
        rememberPasswordCheck.setTextColor(0xff334155);
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
        version.setText("作者 kunkun  版本号 1.0.1");
        version.setTextSize(13);
        version.setTextColor(0xffb7c9d9);
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
        input.setBackground(roundedStroke(0xfffbfdff, 14, LINE));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return input;
    }

    private List<OptionItem> savedAccountOptions() {
        List<OptionItem> options = new ArrayList<>();
        options.add(new OptionItem("", "选择已保存账号"));
        JSONArray accounts = savedAccounts();
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account == null) {
                continue;
            }
            String username = account.optString("username");
            if (username.length() > 0) {
                options.add(new OptionItem(username, username));
            }
        }
        return options;
    }

    private JSONArray savedAccounts() {
        JSONArray result = new JSONArray();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_accounts", "");
        try {
            if (raw.length() > 0) {
                result = new JSONArray(raw);
            }
        } catch (Exception ignored) {
            result = new JSONArray();
        }
        String legacyUser = getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_username", "");
        String legacyPass = getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_password", "");
        if (legacyUser.length() > 0 && savedPasswordFor(result, legacyUser).length() == 0) {
            try {
                JSONObject account = new JSONObject();
                account.put("username", legacyUser);
                account.put("password", legacyPass);
                result.put(account);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String latestSavedUsername() {
        String latest = getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_username", "");
        if (latest.length() > 0) {
            return latest;
        }
        JSONArray accounts = savedAccounts();
        JSONObject first = accounts.length() > 0 ? accounts.optJSONObject(0) : null;
        return first == null ? "" : first.optString("username");
    }

    private String savedPasswordFor(String username) {
        return savedPasswordFor(savedAccounts(), username);
    }

    private String savedPasswordFor(JSONArray accounts, String username) {
        if (username == null || username.length() == 0) {
            return "";
        }
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account != null && username.equals(account.optString("username"))) {
                return account.optString("password");
            }
        }
        return "";
    }

    private void saveAccountPassword(String username, String password) {
        if (username == null || username.length() == 0) {
            return;
        }
        JSONArray existing = savedAccounts();
        JSONArray next = new JSONArray();
        try {
            JSONObject current = new JSONObject();
            current.put("username", username);
            current.put("password", password == null ? "" : password);
            next.put(current);
            for (int i = 0; i < existing.length(); i++) {
                JSONObject account = existing.optJSONObject(i);
                if (account == null) {
                    continue;
                }
                String savedUser = account.optString("username");
                if (savedUser.length() == 0 || savedUser.equals(username)) {
                    continue;
                }
                next.put(account);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("saved_accounts", next.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void showSavedAccountChooser() {
        List<OptionItem> accounts = savedAccountOptions();
        if (accounts.size() <= 1) {
            toast("暂无已保存账号");
            return;
        }
        String[] labels = new String[accounts.size() - 1];
        for (int i = 1; i < accounts.size(); i++) {
            labels[i - 1] = accounts.get(i).label;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择账号")
                .setItems(labels, (d, which) -> {
                    OptionItem account = accounts.get(which + 1);
                    usernameInput.setText(account.value);
                    passwordInput.setText(savedPasswordFor(account.value));
                    rememberPasswordCheck.setChecked(true);
                })
                .setNegativeButton("取消", null)
                .create();
        showStyledDialog(dialog);
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
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, dp(126), dp(50), true);
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
                        if (remember) {
                            saveAccountPassword(username, password);
                        }
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
        applyAdaptiveSystemBars(true);
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.removeCallbacks(alarmRefresh);
        normalizeAlarmDateForCurrentTab();
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(PAGE_BG);
        page.setPadding(0, dp(32), 0, dp(20));
        applyHomeInsets(page);

        addContextPanel(page);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), currentTab == 0 ? dp(2) : currentTab == 1 || currentTab == 2 ? dp(2) : dp(8), dp(12), dp(24));
        scroll.addView(content);
        attachPullRefresh(scroll);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        addBottomNavigation(page);

        root.addView(page);
        root.addView(loading, centeredLoading());
        fetchAlarmCount(true);
        loadList(true);
        schedulePressureRefresh();
        scheduleAlarmRefresh();
    }

    private void startAlarmMonitorService() {
        if (token == null || token.length() == 0) {
            return;
        }
        Intent intent = new Intent(this, AlarmMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopAlarmMonitorService() {
        try {
            stopService(new Intent(this, AlarmMonitorService.class));
        } catch (Exception ignored) {
        }
    }

    private void attachPullRefresh(ScrollView scroll) {
        scroll.setOnTouchListener((view, event) -> {
            if (currentTab != 0 && currentTab != 2) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mouldPullStartY = event.getY();
                mouldPullReady = scroll.getScrollY() == 0;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                float distance = mouldPullStartY < 0 ? 0 : event.getY() - mouldPullStartY;
                if (mouldPullReady && scroll.getScrollY() == 0 && distance > dp(76)) {
                    toast(currentTab == 0 ? "正在刷新设备数据" : "正在刷新模具数据");
                    loadList(true);
                }
                mouldPullStartY = -1f;
                mouldPullReady = false;
            }
            return false;
        });
    }

    private void applyHomeInsets(LinearLayout page) {
        if (Build.VERSION.SDK_INT < 20) {
            return;
        }
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = getTopSystemInset(insets);
            int bottomInset = getBottomSystemInset(insets);
            view.setPadding(0, topInset + dp(22), 0, bottomInset + dp(10));
            return insets;
        });
        page.requestApplyInsets();
        page.post(page::requestApplyInsets);
    }

    private int getTopSystemInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            return insets.getInsets(WindowInsets.Type.statusBars()).top;
        }
        return insets.getSystemWindowInsetTop();
    }

    private int getBottomSystemInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }
        return insets.getSystemWindowInsetBottom();
    }

    private void loadList() {
        loadList(true);
    }

    private void loadList(boolean showProgress) {
        if (currentTab == 3) {
            setLoading(false);
            clearVisibleMouldValueViews();
            content.removeAllViews();
            renderSettingsPage();
            return;
        }
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
                        syncActiveAlarmState(null, false, true);
                    }
                    showEmpty("暂无数据");
                    return;
                }
                if (currentTab == 1) {
                    lastAlarmTotal = json.optInt("total", rows.length());
                    syncActiveAlarmState(rows, false, true);
                }
                if (currentTab == 2) {
                    renderPressureMoulds(rows);
                    return;
                }
                addSectionTitle(currentTab == 1 ? selectedAlarmDate() + " 告警" : tabTitles[currentTab] + "列表");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject item = rows.optJSONObject(i);
                    if (item != null && matchesDeviceQuery(item) && matchesSelectedAlarmDate(item)) {
                        content.addView(cardFor(item), topMargin(dp(10)));
                    }
                }
                if (content.getChildCount() == 0) {
                    showEmpty(currentTab == 0 ? "未找到匹配的设备" : currentTab == 1 ? selectedAlarmDate() + " 暂无告警" : "暂无数据");
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
        new ApiTask("GET", "/yujing/alarm/list?pageNum=1&pageSize=50", null, true, result -> {
            if (!result.ok) {
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                lastAlarmTotal = json.optInt("total", rows == null ? 0 : rows.length());
                syncActiveAlarmState(rows, true, redrawTabs);
            } catch (Exception ignored) {
            }
        }).execute();
    }

    private void syncActiveAlarmState(JSONArray rows, boolean allowSound, boolean redrawTabs) {
        int oldCount = unreadAlarmCount;
        int activeCount = 0;
        String latestActiveKey = "";
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject alarm = rows.optJSONObject(i);
                if (alarm == null || !isActiveAlarm(alarm)) {
                    continue;
                }
                activeCount++;
                if (latestActiveKey.length() == 0) {
                    latestActiveKey = alarmKey(alarm);
                }
            }
        }
        boolean newActiveAlarm = activeCount > oldCount
                || (activeCount >= oldCount && oldCount > 0 && latestActiveKey.length() > 0 && !latestActiveKey.equals(lastSeenAlarmKey));
        unreadAlarmCount = activeCount;
        lastSeenAlarmKey = latestActiveKey;
        updateLauncherAlarmBadge();
        if (activeCount > 0) {
            if (allowSound && newActiveAlarm) {
                startAlarmSoundLoop();
            }
        } else {
            stopAlarmSoundLoop();
        }
        if (oldCount != unreadAlarmCount || redrawTabs) {
            updateAlarmTabIconTint();
        }
    }

    private boolean isActiveAlarm(JSONObject alarm) {
        String state = firstValue(alarm, "state", "status", "type");
        if (state.length() == 0) {
            return true;
        }
        return !isAlarmCleared(state);
    }

    private void playAlarmSound(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastAlarmSoundAt < 5000) {
            return;
        }
        lastAlarmSoundAt = now;
        try {
            Uri uri = selectedAlarmSoundUri();
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

    private void startAlarmSoundLoop() {
        if (!alarmSoundEnabled()) {
            return;
        }
        if (alarmSoundLooping) {
            return;
        }
        try {
            Uri uri = selectedAlarmSoundUri();
            if (uri == null) {
                return;
            }
            activeAlarmRingtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (activeAlarmRingtone == null) {
                return;
            }
            alarmSoundLooping = true;
            if (Build.VERSION.SDK_INT >= 28) {
                activeAlarmRingtone.setLooping(true);
            }
            activeAlarmRingtone.play();
            if (Build.VERSION.SDK_INT < 28) {
                refreshHandler.postDelayed(alarmSoundRepeater, 3000);
            }
        } catch (Exception ignored) {
            alarmSoundLooping = false;
        }
    }

    private final Runnable alarmSoundRepeater = new Runnable() {
        @Override
        public void run() {
            if (!alarmSoundLooping || unreadAlarmCount <= 0) {
                stopAlarmSoundLoop();
                return;
            }
            try {
                if (activeAlarmRingtone != null && !activeAlarmRingtone.isPlaying()) {
                    activeAlarmRingtone.play();
                }
            } catch (Exception ignored) {
            }
            refreshHandler.postDelayed(this, 3000);
        }
    };

    private void stopAlarmSoundLoop() {
        refreshHandler.removeCallbacks(alarmSoundRepeater);
        alarmSoundLooping = false;
        if (activeAlarmRingtone != null) {
            try {
                if (activeAlarmRingtone.isPlaying()) {
                    activeAlarmRingtone.stop();
                }
            } catch (Exception ignored) {
            }
            activeAlarmRingtone = null;
        }
    }

    private Uri selectedAlarmSoundUri() {
        if (!alarmSoundEnabled()) {
            return null;
        }
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_ALARM_SOUND_URI, null);
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
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ALARM_SOUND_ENABLED, true);
    }

    private boolean backgroundAlarmMonitorEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_BACKGROUND_ALARM_MONITOR, false);
    }

    private void showAlarmSoundPicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择报警提示音");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        Uri current = selectedAlarmSoundUri();
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);
        try {
            startActivityForResult(intent, REQ_ALARM_SOUND);
        } catch (Exception e) {
            toast("当前设备不支持系统铃声选择");
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

    private boolean matchesSelectedAlarmDate(JSONObject item) {
        if (currentTab != 1) {
            return true;
        }
        String time = firstValue(item, "createTime", "create_time", "alarmTime", "updateTime");
        if (time.length() == 0) {
            return true;
        }
        return time.startsWith(selectedAlarmDate());
    }

    private String buildListEndpoint() {
        String endpoint = tabEndpoints[currentTab] + "?pageNum=1&pageSize=" + (currentTab == 0 || currentTab == 1 || currentTab == 2 ? "200" : "20");
        if (currentTab == 1) {
            try {
                String date = selectedAlarmDate();
                endpoint += "&params%5BbeginTime%5D=" + URLEncoder.encode(date + " 00:00:00", "UTF-8");
                endpoint += "&params%5BendTime%5D=" + URLEncoder.encode(date + " 23:59:59", "UTF-8");
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

    private String selectedAlarmDate() {
        if (alarmDateFilter == null || alarmDateFilter.length() == 0 || !alarmDateManuallySelected) {
            alarmDateFilter = todayPrefix();
        }
        return alarmDateFilter;
    }

    private void normalizeAlarmDateForCurrentTab() {
        if (currentTab == 1 && !alarmDateManuallySelected) {
            alarmDateFilter = todayPrefix();
        }
    }

    private void schedulePressureRefresh() {
        refreshHandler.removeCallbacks(pressureRefresh);
        if (currentTab == 3) {
            return;
        }
        refreshHandler.postDelayed(pressureRefresh, currentTab == 2 ? MOULD_REFRESH_MS : DEFAULT_REFRESH_MS);
    }

    private void scheduleAlarmRefresh() {
        refreshHandler.removeCallbacks(alarmRefresh);
        refreshHandler.postDelayed(alarmRefresh, ALARM_REFRESH_MS);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("查询单个设备")
                .setView(form)
                .setNegativeButton("重置", (d, which) -> {
                    macSearchInput = null;
                    loadList();
                })
                .setPositiveButton("查询", (d, which) -> {
                    macSearchInput = input;
                    loadList();
                })
                .create();
        showStyledDialog(dialog);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加监控")
                .setView(form)
                .setNegativeButton("清空监控", (d, which) -> {
                    saveMonitoredMacs(new ArrayList<>());
                    loadList();
                })
                .setPositiveButton("添加", (d, which) -> {
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
                .create();
        showStyledDialog(dialog);
    }

    private String headerTitle() {
        if (currentTab == 1) return "告警中心";
        if (currentTab == 2) return "模具监控";
        if (currentTab == 3) return "设置";
        return "设备监控";
    }

    private String headerLeftAction() {
        if (currentTab == 3) return "";
        return currentTab == 0 ? "" : "刷新";
    }

    private String headerRightAction() {
        if (currentTab == 1) return alarmSoundEnabled() ? "声音开" : "声音关";
        return "";
    }

    private void headerLeftClick() {
        if (currentTab != 0 && currentTab != 3) {
            loadList();
        }
    }

    private void headerRightClick() {
        if (currentTab == 1) {
            boolean next = !alarmSoundEnabled();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_ALARM_SOUND_ENABLED, next)
                    .apply();
            if (!next) {
                stopAlarmSoundLoop();
            } else if (unreadAlarmCount > 0) {
                startAlarmSoundLoop();
            }
            toast(next ? "报警声音已开启" : "报警声音已关闭");
            showHome();
        }
    }

    private void addContextPanel(LinearLayout page) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), currentTab == 1 || currentTab == 2 ? dp(4) : dp(8));
        panel.setBackgroundColor(PAGE_BG);
        if (currentTab == 0) {
            LinearLayout search = new LinearLayout(this);
            search.setOrientation(LinearLayout.HORIZONTAL);
            search.setGravity(Gravity.CENTER_VERTICAL);
            search.setPadding(dp(10), dp(8), dp(8), dp(8));
            search.setBackground(roundedStroke(SURFACE, 14, 0xffe0e7f0));
            smoothElevation(search, 2);

            TextView magnifier = new TextView(this);
            magnifier.setText("⌕");
            magnifier.setTextSize(22);
            magnifier.setTextColor(0xff64748b);
            magnifier.setGravity(Gravity.CENTER);
            search.addView(magnifier, new LinearLayout.LayoutParams(dp(30), dp(40)));

            macSearchInput = input("请输入MAC地址查询", false);
            macSearchInput.setBackground(rounded(0x00ffffff, 0));
            search.addView(macSearchInput, new LinearLayout.LayoutParams(0, dp(40), 1));

            Button button = smallButton("MAC查询");
            styleButton(button, BLUE, 0xffffffff, BLUE);
            button.setOnClickListener(v -> loadList());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(92), dp(40));
            buttonParams.leftMargin = dp(6);
            search.addView(button, buttonParams);
            panel.addView(search);

            Button addMonitor = smallButton("+ 添加监控");
            styleButton(addMonitor, BLUE, 0xffffffff, BLUE);
            addMonitor.setOnClickListener(v -> showAddMonitorDeviceDialog());
            panel.addView(addMonitor, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(10)));
        } else if (currentTab == 1) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            addChip(chips, "全部 " + Math.max(lastAlarmTotal, 0), true);
            addChip(chips, "未消除 " + unreadAlarmCount, false);
            addChip(chips, "日期查询", false, v -> showAlarmDatePicker());
            panel.addView(chips);
        } else if (currentTab == 2) {
            LinearLayout tip = new LinearLayout(this);
            tip.setOrientation(LinearLayout.HORIZONTAL);
            tip.setGravity(Gravity.CENTER_VERTICAL);
            tip.setPadding(dp(12), dp(10), dp(12), dp(10));
            tip.setBackground(roundedStroke(SURFACE, 14, 0xffe0e7f0));
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(14);
            dot.setTextColor(GREEN);
            tip.addView(dot, new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView text = new TextView(this);
            text.setText(offlineMouldMode ? "当前显示压力不波动的离线模具" : "只显示在线模具（实时压力有波动）");
            text.setTextSize(13);
            text.setTextColor(INK);
            tip.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            panel.addView(tip);
        }
        page.addView(panel);
    }

    private void addChip(LinearLayout parent, String text, boolean selected) {
        addChip(parent, text, selected, null);
    }

    private void addChip(LinearLayout parent, String text, boolean selected, View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(null, selected ? 1 : 0);
        chip.setTextColor(selected ? 0xffffffff : 0xff475569);
        chip.setBackground(roundedStroke(selected ? tabAccent(currentTab) : SURFACE, 18, selected ? tabAccent(currentTab) : 0xffe0e7f0));
        if (listener != null) {
            chip.setOnClickListener(listener);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
        params.rightMargin = dp(8);
        parent.addView(chip, params);
    }

    private void showAlarmDatePicker() {
        Calendar calendar = Calendar.getInstance();
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).parse(selectedAlarmDate());
            if (date != null) {
                calendar.setTime(date);
            }
        } catch (Exception ignored) {
        }
        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    alarmDateFilter = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    alarmDateManuallySelected = !alarmDateFilter.equals(todayPrefix());
                    showHome();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        picker.setButton(DialogInterface.BUTTON_NEUTRAL, "今天", (d, which) -> {
            alarmDateFilter = todayPrefix();
            alarmDateManuallySelected = false;
            showHome();
        });
        picker.show();
        styleDialogWindow(picker);
        styleDialogButtons(picker);
    }

    private void renderSettingsPage() {
        addSectionTitle("系统设置");

        LinearLayout alarmCard = settingsCard("报警声音设置", "控制有新告警时是否播放提示音");
        CheckBox soundSwitch = new CheckBox(this);
        soundSwitch.setText(alarmSoundEnabled() ? "报警声音：开启" : "报警声音：关闭");
        soundSwitch.setTextSize(14);
        soundSwitch.setTextColor(INK);
        soundSwitch.setChecked(alarmSoundEnabled());
        soundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_ALARM_SOUND_ENABLED, isChecked)
                    .apply();
            if (!isChecked) {
                stopAlarmSoundLoop();
            } else if (unreadAlarmCount > 0) {
                startAlarmSoundLoop();
            }
            buttonView.setText(isChecked ? "报警声音：开启" : "报警声音：关闭");
        });
        alarmCard.addView(soundSwitch, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), dp(10)));
        CheckBox backgroundSwitch = new CheckBox(this);
        backgroundSwitch.setText(backgroundAlarmMonitorEnabled() ? "后台告警监控：开启" : "后台告警监控：关闭");
        backgroundSwitch.setTextSize(14);
        backgroundSwitch.setTextColor(INK);
        backgroundSwitch.setChecked(backgroundAlarmMonitorEnabled());
        backgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_BACKGROUND_ALARM_MONITOR, isChecked)
                    .apply();
            if (!isChecked) {
                stopAlarmMonitorService();
            }
            buttonView.setText(isChecked ? "后台告警监控：开启" : "后台告警监控：关闭");
            toast(isChecked ? "退到后台后会继续监控告警" : "已关闭后台轮询，退后台后不再刷新告警");
        });
        alarmCard.addView(backgroundSwitch, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), dp(8)));
        TextView pickTone = settingsAction("选择报警提示音");
        pickTone.setOnClickListener(v -> showAlarmSoundPicker());
        alarmCard.addView(pickTone, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), dp(8)));
        content.addView(alarmCard, topMargin(dp(10)));

        LinearLayout appCard = settingsCard("退出登录", "清除当前登录状态并返回登录页面");
        appCard.addView(meta("当前账号：" + getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_username", "-")), topMargin(dp(8)));
        TextView exit = settingsAction("退出登录");
        exit.setTextColor(RED);
        exit.setOnClickListener(v -> {
            stopAlarmMonitorService();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").apply();
            token = null;
            showLogin();
        });
        appCard.addView(exit, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), dp(10)));
        content.addView(appCard, topMargin(dp(10)));
    }

    private LinearLayout settingsCard(String titleText, String subtitleText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 3);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(16);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(12);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(4), 0, 0);
        card.addView(subtitle);
        return card;
    }

    private TextView settingsAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextSize(14);
        action.setTypeface(null, 1);
        action.setTextColor(BLUE);
        action.setGravity(Gravity.CENTER);
        action.setBackground(roundedStroke(0xfff8fbff, 12, 0xffdbeafe));
        return action;
    }

    private void addBottomNavigation(LinearLayout page) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(roundedStroke(SURFACE, 0, 0xffe2e8f0));
        nav.setElevation(dp(8));

        for (int i = 0; i < tabTitles.length; i++) {
            final int index = i;
            FrameLayout slot = new FrameLayout(this);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(3));
            item.setBackground(rounded(currentTab == index ? softAccent(index) : 0x00ffffff, 16));

            ImageView icon = new ImageView(this);
            icon.setImageResource(tabIcons[index]);
            int tint = index == 1 && unreadAlarmCount > 0 ? RED : (currentTab == index ? tabAccent(index) : 0xff667085);
            icon.setColorFilter(tint);
            item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));

            TextView label = new TextView(this);
            label.setText(index == 1 ? "告警中心" : index == 3 ? "设置" : tabTitles[index] + "监控");
            label.setTextSize(11);
            label.setTextColor(currentTab == index ? tabAccent(index) : 0xff667085);
            label.setTypeface(null, currentTab == index ? 1 : 0);
            label.setGravity(Gravity.CENTER);
            item.addView(label, topMargin(dp(3)));

            item.setOnClickListener(v -> {
                currentTab = index;
                if (currentTab == 1) {
                    alarmDateManuallySelected = false;
                    alarmDateFilter = todayPrefix();
                }
                if (currentTab != 2) {
                    offlineMouldMode = false;
                }
                expandedMouldIds.clear();
                expandedAlarmIds.clear();
                expandedGatewayIds.clear();
                showHome();
            });
            slot.addView(item, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            if (index == 1 && unreadAlarmCount > 0) {
                TextView bubble = new TextView(this);
                bubble.setText(unreadAlarmCount > 99 ? "99+" : String.valueOf(unreadAlarmCount));
                bubble.setTextColor(0xffffffff);
                bubble.setTextSize(10);
                bubble.setTypeface(null, 1);
                bubble.setGravity(Gravity.CENTER);
                bubble.setBackground(rounded(RED, 10));
                FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(dp(unreadAlarmCount > 99 ? 30 : 20), dp(20));
                bubbleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                bubbleParams.leftMargin = dp(18);
                slot.addView(bubble, bubbleParams);
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1);
            params.leftMargin = dp(3);
            params.rightMargin = dp(3);
            nav.addView(slot, params);
        }
        page.addView(nav);
    }

    private void addPageOverview(JSONArray rows) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(4), 0, dp(4));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(4), 0, 0);
        int total = rows == null ? 0 : rows.length();
        if (currentTab == 0) {
            addOverviewStat(stats, "监控设备", String.valueOf(total), "在线 " + countOnline(rows), BLUE);
            addOverviewStat(stats, "正常设备", String.valueOf(countOnline(rows)), total == 0 ? "0%" : percent(countOnline(rows), total), GREEN);
            addOverviewStat(stats, "告警设备", String.valueOf(Math.max(0, total - countOnline(rows))), "异常", AMBER);
        } else if (currentTab == 1) {
            addOverviewStat(stats, "未消除告警", String.valueOf(countActiveAlarms(rows)), "待处理", RED);
            addOverviewStat(stats, "已消除告警", String.valueOf(Math.max(0, total - countActiveAlarms(rows))), "已恢复", GREEN);
        } else if (currentTab == 2) {
            addOverviewStat(stats, "在线模具", offlineMouldMode ? "0" : String.valueOf(total), "实时波动", BLUE);
            addOverviewStat(stats, "传感器总数", "-", "动态读取", 0xff64748b);
            addOverviewStat(stats, "告警中", String.valueOf(unreadAlarmCount), "同步告警", RED);
        }
        panel.addView(stats);
        content.addView(panel, topMargin(0));
    }

    private String overviewTitle() {
        if (currentTab == 0) return "设备监控台";
        if (currentTab == 1) return "告警处置中心";
        if (currentTab == 2) return offlineMouldMode ? "离线模具池" : "在线生产模具";
        return offlineMouldMode ? "离线模具池" : "在线生产模具";
    }

    private String overviewSubtitle() {
        if (currentTab == 0) return "查询或添加 MAC 后显示传感器实时压力";
        if (currentTab == 1) return "只展示当天告警，未消除会同步铃声和角标";
        if (currentTab == 2) return offlineMouldMode ? "有压力但波动不足的模具会归入这里" : "压力持续波动的模具显示在这里";
        return offlineMouldMode ? "有压力但波动不足的模具会归入这里" : "压力持续波动的模具显示在这里";
    }

    private String percent(int part, int total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round((part * 100f) / total) + "%";
    }

    private void addOverviewStat(LinearLayout parent, String label, String value, String caption, int valueColor) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(8), dp(10), dp(8), dp(10));
        cell.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(cell, 3);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xff334155);
        labelView.setTextSize(12);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTypeface(null, 1);
        cell.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(value.length() > 4 ? 18 : 24);
        valueView.setTypeface(null, 1);
        valueView.setGravity(Gravity.CENTER);
        cell.addView(valueView, topMargin(dp(6)));

        TextView captionView = new TextView(this);
        captionView.setText(caption);
        captionView.setTextColor(MUTED);
        captionView.setTextSize(11);
        captionView.setGravity(Gravity.CENTER);
        cell.addView(captionView, topMargin(dp(4)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.rightMargin = dp(8);
        parent.addView(cell, params);
    }

    private int countOnline(JSONArray rows) {
        if (rows == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && !isSensorOffline(item)) {
                count++;
            }
        }
        return count;
    }

    private int countActiveAlarms(JSONArray rows) {
        if (rows == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && isActiveAlarm(item)) {
                count++;
            }
        }
        return count;
    }

    private void addSectionTitle(String titleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), currentTab == 1 || currentTab == 2 ? dp(6) : dp(14), dp(2), dp(2));

        TextView bar = new TextView(this);
        bar.setText("");
        bar.setBackground(rounded(tabAccent(currentTab), 3));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(4), dp(18));
        barParams.rightMargin = dp(8);
        row.addView(bar, barParams);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView hint = new TextView(this);
        hint.setText("实时刷新");
        hint.setTextSize(12);
        hint.setTextColor(MUTED);
        row.addView(hint);
        content.addView(row);
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
                Map<String, Integer> sensorCountByMouldId = new HashMap<>();
                long now = System.currentTimeMillis();
                if (devices != null) {
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
                        if (mouldId.length() > 0 && hasLivePressure(device)) {
                            Integer sensorCount = sensorCountByMouldId.get(mouldId);
                            sensorCountByMouldId.put(mouldId, sensorCount == null ? 1 : sensorCount + 1);
                            liveMouldIds.put(mouldId, true);
                            boolean fluctuating = hasPressureFluctuation(device);
                            if (fluctuating) {
                                updateStaticPressure(device, false);
                                activeMouldUntil.put(mouldId, now + MOULD_ACTIVE_WINDOW_MS);
                            } else {
                                updateStaticPressure(device, canCaptureStaticAfterMouldOffline(mouldId, now));
                            }
                        } else {
                            updateStaticPressure(device, canCaptureStaticAfterMouldOffline(mouldId, now));
                        }
                    }
                }
                for (Map.Entry<String, Long> entry : activeMouldUntil.entrySet()) {
                    if (entry.getValue() >= now) {
                        pressureMouldIds.put(entry.getKey(), true);
                    }
                }
                int count = 0;
                addSectionTitle(offlineMouldMode ? "离线模具" : "在线生产模具");
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
                        mould.put("_sensorCount", sensorCountByMouldId.containsKey(mouldId) ? sensorCountByMouldId.get(mouldId) : 0);
                        content.addView(cardFor(mould), topMargin(dp(10)));
                        count++;
                    }
                }
                addMouldModeButton();
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

    private boolean canCaptureStaticAfterMouldOffline(String mouldId, long now) {
        if (mouldId == null || mouldId.length() == 0) {
            return false;
        }
        Long activeUntil = activeMouldUntil.get(mouldId);
        return activeUntil != null && activeUntil < now;
    }

    private void updateStaticPressure(JSONObject device) {
        updateStaticPressure(device, true);
    }

    private void updateStaticPressure(JSONObject device, boolean allowOfflineCapture) {
        Double pressure = numberValue(device.optString("pressure"));
        String key = deviceKey(device);
        if (pressure == null || key.length() == 0) {
            return;
        }
        if (!allowOfflineCapture) {
            dynamicSeenByDevice.put(key, true);
            stableCandidatePressureByDevice.remove(key);
            stableSinceByDevice.remove(key);
            return;
        }
        if (Boolean.TRUE.equals(staticPressureCapturedByDevice.get(key))) {
            if (Boolean.TRUE.equals(dynamicSeenByDevice.get(key))) {
                staticPressureCapturedByDevice.put(key, false);
                stableCandidatePressureByDevice.put(key, pressure);
                stableSinceByDevice.put(key, System.currentTimeMillis());
            }
            return;
        }
        if (existingStaticPressure(device) != null && !Boolean.TRUE.equals(dynamicSeenByDevice.get(key))) {
            staticPressureCapturedByDevice.put(key, true);
            return;
        }
        long now = System.currentTimeMillis();
        Double candidate = stableCandidatePressureByDevice.get(key);
        if (candidate == null || Math.abs(pressure - candidate) >= STATIC_PRESSURE_STABLE_DELTA) {
            stableCandidatePressureByDevice.put(key, pressure);
            stableSinceByDevice.put(key, now);
            return;
        }
        Long since = stableSinceByDevice.get(key);
        if (since != null && now - since >= STATIC_PRESSURE_STABLE_MS) {
            staticPressureByDevice.put(key, pressure);
            saveStaticPressure(key, pressure);
            staticPressureCapturedByDevice.put(key, true);
            dynamicSeenByDevice.put(key, false);
            TextView standardView = visibleStandardViews.get(key);
            if (standardView != null) {
                setStaticPressureViewText(standardView, trimNumber(pressure));
            }
        }
    }

    private void setStaticPressureViewText(TextView view, String value) {
        String current = view.getText() == null ? "" : view.getText().toString();
        view.setText(current.startsWith("静止压力：") ? "静止压力：" + value : value);
    }

    private String staticPressureText(JSONObject device) {
        String key = deviceKey(device);
        Double value = existingStaticPressure(device);
        if (value != null) {
            return trimNumber(value);
        }
        return clean(device.optString("standard"));
    }

    private Double existingStaticPressure(JSONObject device) {
        String key = deviceKey(device);
        Double value = key.length() == 0 ? null : staticPressureByDevice.get(key);
        if (value != null) {
            return value;
        }
        value = key.length() == 0 ? null : storedStaticPressure(key);
        if (value != null) {
            staticPressureByDevice.put(key, value);
            return value;
        }
        return numberValue(device.optString("standard"));
    }

    private void saveStaticPressure(String key, Double pressure) {
        if (key == null || key.length() == 0 || pressure == null) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("static_pressure_" + key, trimNumber(pressure))
                .apply();
    }

    private Double storedStaticPressure(String key) {
        if (key == null || key.length() == 0) {
            return null;
        }
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("static_pressure_" + key, "");
        return numberValue(saved);
    }

    private String deviceKey(JSONObject device) {
        return firstValue(device, "id", "number", "mac", "macAddress");
    }

    private View cardFor(JSONObject item) {
        if (currentTab == 0) {
            return deviceDisplayCard(item);
        }
        if (currentTab == 1) {
            return alarmDisplayCard(item);
        }
        if (currentTab == 2) {
            return mouldDisplayCard(item);
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 0, 0, dp(14));
        card.setBackground(roundedStroke(SURFACE, 18, 0xffdfe8f2));
        smoothElevation(card, 5);

        View strip = new View(this);
        strip.setBackground(gradient(tabAccent(currentTab), 0xff60a5fa, 18));
        card.addView(strip, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), 0));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), dp(14), dp(14), dp(4));

        ImageView icon = new ImageView(this);
        icon.setImageResource(tabIcons[currentTab]);
        icon.setColorFilter(tabAccent(currentTab));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setBackground(rounded(softAccent(currentTab), 12));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.rightMargin = dp(10);
        heading.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText(primaryTitle(item));
        title.setTextSize(16);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        title.setSingleLine(false);
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
            addMetric(metrics, "实时压力", item.optString("pressure"), 0xffeefcf5, 0xff07804d);
            addMetric(metrics, "静止压力", item.optString("standard"), 0xffedf5ff, BLUE);
            addMetric(metrics, "电池", item.optString("battery"), 0xfffff8e5, 0xffa15c07);
            if (metrics.getChildCount() > 0) {
                card.addView(metrics);
            }
        }

        String[] keys = {
                "name", "number", "mac", "macAddress", "state", "status", "customer",
                "lower", "upper", "rssi",
                "remark", "createTime", "updateTime"
        };
        LinearLayout infoPanel = infoPanel();
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
                    addInfoItem(infoPanel, labelFor(key), value);
                }
            }
        }

        JSONObject dept = item.optJSONObject("dept");
        if (dept != null) {
            addInfoItem(infoPanel, "组织", dept.optString("deptName"));
        }
        JSONObject gateway = item.optJSONObject("gateway");
        if (gateway != null) {
            addInfoItem(infoPanel, "网关", gateway.optString("number") + " " + gateway.optString("name"));
        }
        JSONObject mould = item.optJSONObject("mould");
        if (mould != null) {
            addInfoItem(infoPanel, "模具", mould.optString("number") + " " + mould.optString("name"));
        }
        if (infoPanel.getChildCount() > 0) {
            card.addView(infoPanel, sideMargin(dp(14), dp(14), dp(8)));
        }

        if (currentTab == 3) {
            addMouldDropdown(card, item);
        }

        LinearLayout actionLine = new LinearLayout(this);
        actionLine.setOrientation(LinearLayout.HORIZONTAL);
        actionLine.setGravity(Gravity.CENTER_VERTICAL);
        actionLine.setPadding(dp(14), dp(10), dp(14), 0);
        TextView detail = compactAction(currentTab == 3 ? "查看传感器与压力" : "查看详情");
        detail.setOnClickListener(v -> showDetail(item));
        actionLine.addView(detail, new LinearLayout.LayoutParams(0, dp(34), 1));
        if (currentTab != 1) {
            TextView edit = compactAction(currentTab == 3 ? "压力上下限" : "编辑");
            edit.setOnClickListener(v -> {
                if (currentTab == 3) {
                    showMouldLimitDialog(item);
                } else {
                    loadDetailForEdit(item);
                }
            });
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(34), 1);
            editParams.leftMargin = dp(8);
            actionLine.addView(edit, editParams);
        }
        card.addView(actionLine);
        return card;
    }

    private TextView compactAction(String text) {
        TextView view = new TextView(this);
        view.setText(text + "  ›");
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
        view.setTypeface(null, 1);
        view.setTextColor(tabAccent(currentTab));
        view.setBackground(roundedStroke(softAccent(currentTab), 12, 0x22000000));
        return view;
    }

    private View deviceDisplayCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(8), dp(12));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 3);

        TextView dot = new TextView(this);
        boolean offline = isSensorOffline(item);
        dot.setText("●");
        dot.setTextSize(16);
        dot.setTextColor(offline ? 0xff94a3b8 : GREEN);
        dot.setGravity(Gravity.CENTER);
        card.addView(dot, new LinearLayout.LayoutParams(dp(22), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(firstNonEmpty(firstValue(item, "mac", "macAddress"), primaryTitle(item)));
        title.setTextSize(15);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        title.setSingleLine(false);
        copy.addView(title);

        String mould = "-";
        JSONObject mouldObj = item.optJSONObject("mould");
        if (mouldObj != null) {
            mould = firstNonEmpty((mouldObj.optString("number") + " " + mouldObj.optString("name")).trim(), "-");
        }
        TextView sub = new TextView(this);
        sub.setText("模具：" + mould + " · 传感器：" + clean(firstValue(item, "number", "name")));
        sub.setTextSize(12);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(4), 0, 0);
        copy.addView(sub);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView status = sensorStatusChip(item);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(dp(48), dp(26));
        statusParams.leftMargin = dp(6);
        statusParams.rightMargin = dp(6);
        card.addView(status, statusParams);

        LinearLayout pressureBox = new LinearLayout(this);
        pressureBox.setOrientation(LinearLayout.VERTICAL);
        pressureBox.setGravity(Gravity.RIGHT);
        TextView label = new TextView(this);
        label.setText("实时压力");
        label.setTextSize(11);
        label.setTextColor(MUTED);
        label.setGravity(Gravity.RIGHT);
        pressureBox.addView(label);

        TextView pressure = new TextView(this);
        pressure.setText(clean(item.optString("pressure")));
        pressure.setTextSize(15);
        pressure.setTypeface(null, 1);
        pressure.setTextColor(offline ? 0xff94a3b8 : BLUE);
        pressure.setGravity(Gravity.RIGHT);
        pressureBox.addView(pressure, topMargin(dp(4)));
        card.addView(pressureBox, new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(0xff94a3b8);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(42)));
        card.setOnClickListener(v -> showDetail(item));
        return swipeDeleteDeviceRow(item, card);
    }

    private View swipeDeleteDeviceRow(JSONObject item, View card) {
        FrameLayout row = new FrameLayout(this);
        row.setClipToPadding(false);

        TextView delete = new TextView(this);
        delete.setText("删除");
        delete.setTextSize(14);
        delete.setTypeface(null, 1);
        delete.setTextColor(0xffffffff);
        delete.setGravity(Gravity.CENTER);
        delete.setBackground(rounded(RED, 14));
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT);
        deleteParams.gravity = Gravity.RIGHT;
        row.addView(delete, deleteParams);

        row.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        delete.setOnClickListener(v -> removeMonitoredDevice(item));
        attachDeviceSwipe(card);
        return row;
    }

    private void attachDeviceSwipe(View card) {
        final float[] startX = {0f};
        final float[] startY = {0f};
        final boolean[] dragging = {false};
        final int maxSwipe = dp(76);
        card.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    dragging[0] = false;
                    if (openedDeviceSwipeCard != null && openedDeviceSwipeCard != view) {
                        openedDeviceSwipeCard.animate().translationX(0).setDuration(120).start();
                        openedDeviceSwipeCard = null;
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    float dy = event.getRawY() - startY[0];
                    if (Math.abs(dx) > dp(8) && Math.abs(dx) > Math.abs(dy)) {
                        dragging[0] = true;
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (dragging[0]) {
                        float translation = Math.max(-maxSwipe, Math.min(0, dx));
                        view.setTranslationX(translation);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        boolean open = view.getTranslationX() < -maxSwipe * 0.45f;
                        view.animate().translationX(open ? -maxSwipe : 0).setDuration(120).start();
                        openedDeviceSwipeCard = open ? view : null;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        dragging[0] = false;
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    private void removeMonitoredDevice(JSONObject item) {
        String matched = monitoredMacForDevice(item);
        if (matched.length() == 0) {
            toast("该设备不在监控列表中");
            if (openedDeviceSwipeCard != null) {
                openedDeviceSwipeCard.animate().translationX(0).setDuration(120).start();
                openedDeviceSwipeCard = null;
            }
            return;
        }
        List<String> next = monitoredMacs();
        for (int i = next.size() - 1; i >= 0; i--) {
            if (next.get(i).equalsIgnoreCase(matched)) {
                next.remove(i);
            }
        }
        saveMonitoredMacs(next);
        openedDeviceSwipeCard = null;
        toast("已移除监控：" + matched);
        loadList(false);
    }

    private String monitoredMacForDevice(JSONObject item) {
        String identity = (item.optString("mac") + " " + item.optString("macAddress") + " " + item.optString("deviceMac") + " " + item.optString("number")).toLowerCase();
        for (String mac : monitoredMacs()) {
            if (identity.contains(mac.toLowerCase())) {
                return mac;
            }
        }
        return "";
    }

    private View alarmDisplayCard(JSONObject item) {
        String key = alarmKey(item);
        boolean expanded = expandedAlarmIds.contains(key);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), expanded ? dp(12) : dp(10));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 3);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        boolean active = isActiveAlarm(item);
        icon.setText("●");
        icon.setTextSize(22);
        icon.setTextColor(active ? RED : GREEN);
        icon.setGravity(Gravity.CENTER);
        head.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView title = new TextView(this);
        title.setText(alarmTitle(item));
        title.setTextSize(16);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        title.setSingleLine(false);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = badgeText(item);
        if (badge != null) {
            head.addView(badge);
        }
        TextView arrow = foldArrow(expanded);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        arrowParams.leftMargin = dp(6);
        head.addView(arrow, arrowParams);
        card.addView(head);

        if (expanded) {
            addAlarmPressureDetails(card, item);

            LinearLayout info = infoPanel();
            addInfoItem(info, "报警时间", clean(firstValue(item, "createTime", "create_time", "alarmTime")));
            addInfoItem(info, "更新时间", clean(firstValue(item, "updateTime", "update_time")));
            JSONObject mould = item.optJSONObject("mould");
            if (mould != null) {
                addInfoItem(info, "模具", (mould.optString("number") + " " + mould.optString("name")).trim());
            }
            JSONObject gateway = item.optJSONObject("gateway");
            if (gateway != null) {
                addInfoItem(info, "网关", (gateway.optString("number") + " " + gateway.optString("name")).trim());
            }
            if (info.getChildCount() > 0) {
                card.addView(info, topMargin(dp(10)));
            }

            TextView detail = compactAction("查看告警详情");
            detail.setOnClickListener(v -> showDetail(item));
            card.addView(detail, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(34), dp(10)));
        } else {
            TextView summary = new TextView(this);
            summary.setText(alarmSummary(item));
            summary.setTextSize(12);
            summary.setTextColor(MUTED);
            summary.setPadding(dp(34), dp(4), 0, 0);
            card.addView(summary);
        }

        card.setOnClickListener(v -> {
            if (expandedAlarmIds.contains(key)) {
                expandedAlarmIds.remove(key);
            } else {
                expandedAlarmIds.add(key);
            }
            loadList(false);
        });
        return card;
    }

    private String alarmSummary(JSONObject item) {
        JSONArray details = alarmDetails(item);
        JSONObject sensor = details == null || details.length() == 0 ? null : details.optJSONObject(0);
        if (sensor != null) {
            return "传感器：" + sensorName(sensor) + "  压力：" + clean(sensor.optString("pressure"));
        }
        return "时间：" + clean(firstValue(item, "createTime", "create_time", "alarmTime"));
    }

    private View gatewayDisplayCard(JSONObject item) {
        String id = firstNonEmpty(item.optString("id"), primaryTitle(item));
        boolean expanded = expandedGatewayIds.contains(id);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), expanded ? dp(12) : dp(10));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 3);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_gateway);
        icon.setColorFilter(CYAN);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(rounded(0xffe8faff, 11));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.rightMargin = dp(10);
        head.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText(primaryTitle(item));
        title.setTextSize(16);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        title.setSingleLine(false);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = badgeText(item);
        if (badge != null) {
            head.addView(badge);
        }
        TextView arrow = foldArrow(expanded);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        arrowParams.leftMargin = dp(6);
        head.addView(arrow, arrowParams);
        card.addView(head);

        if (expanded) {
            LinearLayout info = infoPanel();
            String[] keys = {"number", "name", "status", "rssi", "remark", "createTime", "updateTime"};
            for (String key : keys) {
                if (item.has(key) && !item.isNull(key)) {
                    String value = item.optString(key);
                    if ("status".equals(key)) {
                        value = onlineStatusText(value);
                    }
                    addInfoItem(info, labelFor(key), value);
                }
            }
            JSONObject dept = item.optJSONObject("dept");
            if (dept != null) {
                addInfoItem(info, "组织", dept.optString("deptName"));
            }
            if (info.getChildCount() > 0) {
                card.addView(info, topMargin(dp(10)));
            }
            TextView detail = compactAction("查看网关详情");
            detail.setOnClickListener(v -> showDetail(item));
            card.addView(detail, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(34), dp(10)));
        } else {
            TextView summary = new TextView(this);
            summary.setText("状态：" + onlineStatusText(item.optString("status")) + "  更新时间：" + clean(item.optString("updateTime")));
            summary.setTextSize(12);
            summary.setTextColor(MUTED);
            summary.setPadding(dp(44), dp(4), 0, 0);
            card.addView(summary);
        }
        card.setOnClickListener(v -> {
            if (expandedGatewayIds.contains(id)) {
                expandedGatewayIds.remove(id);
            } else {
                expandedGatewayIds.add(id);
            }
            loadList(false);
        });
        return card;
    }

    private TextView foldArrow(boolean expanded) {
        TextView arrow = new TextView(this);
        arrow.setText(expanded ? "▲" : "▼");
        arrow.setTextSize(8);
        arrow.setTextColor(0xff64748b);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(rounded(0xffeef2f7, 999));
        return arrow;
    }

    private View mouldDisplayCard(JSONObject item) {
        String mouldId = item.optString("id");
        boolean expanded = expandedMouldIds.contains(mouldId);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), expanded ? dp(12) : dp(10));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 3);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mould);
        icon.setColorFilter(BLUE);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(rounded(0xffeef5ff, 11));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.rightMargin = dp(10);
        head.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText(primaryTitle(item));
        title.setTextSize(16);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        title.setSingleLine(false);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout rightMeta = new LinearLayout(this);
        rightMeta.setOrientation(LinearLayout.HORIZONTAL);
        rightMeta.setGravity(Gravity.CENTER_VERTICAL);

        TextView status = mouldStatusChip(item);
        LinearLayout statusSlot = new LinearLayout(this);
        statusSlot.setGravity(Gravity.CENTER);
        statusSlot.addView(status, new LinearLayout.LayoutParams(dp(52), dp(26)));
        rightMeta.addView(statusSlot, new LinearLayout.LayoutParams(dp(64), dp(30)));

        TextView count = new TextView(this);
        int sensorCount = item.optInt("_sensorCount", 0);
        count.setText(sensorCount + "个传感器");
        count.setTextSize(12);
        count.setTextColor(0xff475569);
        count.setGravity(Gravity.CENTER);
        count.setSingleLine(false);
        rightMeta.addView(count, new LinearLayout.LayoutParams(dp(74), dp(30)));

        TextView arrow = foldArrow(expanded);
        LinearLayout arrowSlot = new LinearLayout(this);
        arrowSlot.setGravity(Gravity.CENTER);
        arrowSlot.addView(arrow, new LinearLayout.LayoutParams(dp(26), dp(26)));
        rightMeta.addView(arrowSlot, new LinearLayout.LayoutParams(dp(34), dp(30)));

        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(dp(172), dp(30));
        metaParams.leftMargin = dp(8);
        head.addView(rightMeta, metaParams);
        card.addView(head);

        if (expanded) {
            addMouldSensorTable(card, item);
            addMouldExpandedActions(card, item, mouldId);
        }
        card.setOnClickListener(v -> {
            toggleMouldDetails(mouldId);
        });
        card.setOnLongClickListener(v -> {
            showMouldLimitDialog(item);
            return true;
        });
        return card;
    }

    private void addMouldExpandedActions(LinearLayout card, JSONObject item, String mouldId) {
        LinearLayout actionLine = new LinearLayout(this);
        actionLine.setOrientation(LinearLayout.HORIZONTAL);
        actionLine.setGravity(Gravity.CENTER_VERTICAL);
        actionLine.setPadding(0, dp(10), 0, 0);

        TextView detail = compactAction("修改信息");
        detail.setOnClickListener(v -> showMouldInfoDialog(item));
        actionLine.addView(detail, new LinearLayout.LayoutParams(0, dp(34), 1));

        TextView limit = compactAction("修改上下限");
        limit.setOnClickListener(v -> showMouldLimitDialog(item));
        LinearLayout.LayoutParams limitParams = new LinearLayout.LayoutParams(0, dp(34), 1);
        limitParams.leftMargin = dp(8);
        actionLine.addView(limit, limitParams);
        card.addView(actionLine);
    }

    private void toggleMouldDetails(String mouldId) {
        if (expandedMouldIds.contains(mouldId)) {
            expandedMouldIds.remove(mouldId);
        } else {
            expandedMouldIds.add(mouldId);
        }
        loadList(false);
    }

    private TextView mouldStatusChip(JSONObject item) {
        TextView status = new TextView(this);
        boolean offline = item.optBoolean("_offlinePressure");
        status.setText(offline ? "离线" : "在线");
        status.setTextSize(11);
        status.setTypeface(null, 1);
        status.setTextColor(offline ? 0xffb45309 : 0xff059669);
        status.setGravity(Gravity.CENTER);
        status.setBackground(roundedStroke(offline ? 0xfffff7ed : 0xffecfdf5, 13, offline ? 0xfffdba74 : 0xff86efac));
        return status;
    }

    private String alarmTitle(JSONObject item) {
        JSONArray details = alarmDetails(item);
        JSONObject sensor = details == null || details.length() == 0 ? null : details.optJSONObject(0);
        if (sensor != null) {
            if (isLowBatteryAlarm(sensor)) {
                return "电量低报警";
            }
            Double pressure = numberValue(sensor.optString("pressure"));
            Double upper = numberValue(sensor.optString("upper"));
            Double lower = numberValue(sensor.optString("lower"));
            if (upper != null && pressure != null && pressure > upper) {
                return "压力上限报警";
            }
            if (lower != null && pressure != null && pressure < lower) {
                return "压力下限报警";
            }
        }
        return "压力报警";
    }

    private boolean isLowBatteryAlarm(JSONObject item) {
        String text = (item.optString("type") + " " + item.optString("name") + " " + item.optString("title") + " " + item.optString("msg") + " " + item.optString("message") + " " + item.optString("remark") + " " + item.optString("detail")).toLowerCase();
        if (text.contains("电量") || text.contains("低电") || text.contains("battery") || text.contains("low power")) {
            return true;
        }
        Double battery = numberValue(firstValue(item, "battery", "electricity", "power"));
        return battery != null && battery > 0 && battery <= 20;
    }

    private String firstNonEmpty(String first, String fallback) {
        return first != null && first.trim().length() > 0 && !"null".equals(first) ? first : fallback;
    }

    private void addMouldSensorTable(LinearLayout card, JSONObject mould) {
        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding(dp(8), dp(8), dp(8), dp(8));
        table.setBackground(roundedStroke(0xfffbfdff, 12, 0xffe5edf6));

        LinearLayout header = tableRow();
        addTableCell(header, "传感器", 2, true, MUTED, Gravity.CENTER);
        addTableCell(header, "实时压力", 1, true, MUTED, Gravity.CENTER);
        addTableCell(header, "静止压力", 1, true, MUTED, Gravity.CENTER);
        addTableCell(header, "状态", 1, true, MUTED, Gravity.CENTER);
        table.addView(header);

        TextView loadingText = meta("正在加载传感器压力...");
        table.addView(loadingText, topMargin(dp(8)));
        card.addView(table);

        String id = mould.optString("id");
        if (id.length() == 0) {
            loadingText.setText("缺少模具 ID");
            return;
        }
        new ApiTask("GET", "/yujing/device/list?pageNum=1&pageSize=1000&mouldId=" + id, null, true, result -> {
            table.removeView(loadingText);
            if (!result.ok) {
                table.addView(meta(result.message), topMargin(dp(8)));
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                if (rows == null || rows.length() == 0) {
                    table.addView(meta("暂无绑定传感器"), topMargin(dp(8)));
                    return;
                }
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject device = rows.optJSONObject(i);
                    if (device == null) {
                        continue;
                    }
                    LinearLayout row = tableRow();
                    row.setBackground(roundedStroke(i % 2 == 0 ? 0xfffbfdff : 0xfff6f9fd, 10, 0xffedf2f7));
                    String name = firstNonEmpty(firstValue(device, "mac", "macAddress"), primaryTitle(device));
                    addTableCell(row, name, 2, false, INK, Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    TextView pressureView = addTableCell(row, clean(device.optString("pressure")), 1, false, BLUE, Gravity.CENTER);
                    TextView standardView = addTableCell(row, staticPressureText(device), 1, false, 0xff334155, Gravity.CENTER);
                    String stateText = sensorPressureState(device);
                    addTableCell(row, stateText, 1, false, sensorStateColor(stateText), Gravity.CENTER);
                    table.addView(row, topMargin(dp(6)));
                    String key = deviceKey(device);
                    if (key.length() > 0) {
                        visiblePressureViews.put(key, pressureView);
                        visibleStandardViews.put(key, standardView);
                    }
                }
            } catch (Exception e) {
                table.addView(meta("传感器压力解析失败"), topMargin(dp(8)));
            }
        }).execute();
    }

    private LinearLayout tableRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        return row;
    }

    private TextView addTableCell(LinearLayout row, String text, int weight, boolean header, int color, int gravity) {
        TextView cell = new TextView(this);
        cell.setText(clean(text));
        cell.setTextSize(header ? 11 : 12);
        cell.setTextColor(color);
        cell.setTypeface(null, header ? 1 : 0);
        cell.setGravity(gravity);
        cell.setSingleLine(false);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return cell;
    }

    private String sensorPressureState(JSONObject device) {
        if (isSensorOffline(device)) {
            return "离线";
        }
        Double pressure = numberValue(device.optString("pressure"));
        Double lower = numberValue(device.optString("lower"));
        Double upper = numberValue(device.optString("upper"));
        if (pressure != null && ((lower != null && pressure < lower) || (upper != null && pressure > upper))) {
            return "告警";
        }
        return "在线";
    }

    private int sensorStateColor(String stateText) {
        if ("告警".equals(stateText)) {
            return RED;
        }
        if ("离线".equals(stateText) || "-".equals(stateText)) {
            return 0xff94a3b8;
        }
        return GREEN;
    }

    private void addMouldModeButton() {
        TextView button = new TextView(this);
        button.setText(offlineMouldMode ? "在线模具" : "离线模具");
        button.setTextSize(15);
        button.setTypeface(null, 1);
        button.setTextColor(BLUE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundedStroke(0x00ffffff, 6, BLUE));
        button.setOnClickListener(v -> {
            offlineMouldMode = !offlineMouldMode;
            expandedMouldIds.clear();
            showHome();
        });
        content.addView(button, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), dp(18)));
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
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(11), dp(10), dp(11), dp(10));
            row.setBackground(roundedStroke(SURFACE, 12, 0xffe3edf8));

            TextView title = new TextView(this);
            title.setText(primaryTitle(device));
            title.setTextColor(INK);
            title.setTextSize(15);
            title.setTypeface(null, 1);
            row.addView(title);

            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.setPadding(0, dp(8), 0, 0);
            TextView pressureValue = addMetric(metrics, "实时", device.optString("pressure"), 0xffeefcf5, 0xff07804d);
            addMetric(metrics, "上限", device.optString("upper"), 0xfffff1f2, 0xffdc2626);
            addMetric(metrics, "下限", device.optString("lower"), 0xfffff8e5, 0xffa15c07);
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

        String defaultSensor = sensors.size() > 1 ? sensors.get(1).value : "";
        Spinner sensorSpinner = spinner(sensors, defaultSensor);
        EditText lower = input("报警下限", false);
        EditText upper = input("报警上限", false);
        TextView pressure = meta("实时压力：-");
        TextView standard = meta("静止压力：-");
        TextView battery = meta("电池电量：-");

        form.addView(label("传感器"));
        form.addView(sensorSpinner, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        form.addView(pressure, topMargin(dp(8)));
        form.addView(standard, topMargin(dp(2)));
        form.addView(battery, topMargin(dp(2)));
        form.addView(label("报警下限"));
        form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("报警上限"));
        form.addView(upper, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));

        sensorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                JSONObject device = position >= 0 && position < devices.size() ? devices.get(position) : null;
                fillMouldLimitSensorFields(device, lower, upper, pressure, standard, battery);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        if (devices.size() > 1) {
            sensorSpinner.setSelection(1);
            fillMouldLimitSensorFields(devices.get(1), lower, upper, pressure, standard, battery);
        }

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
            styleDialogButtons(dialog);
        });
        showStyledDialog(dialog);
    }

    private void fillMouldLimitSensorFields(JSONObject device, EditText lower, EditText upper, TextView pressure, TextView standard, TextView battery) {
        if (device == null) {
            lower.setText("");
            upper.setText("");
            pressure.setText("实时压力：-");
            standard.setText("静止压力：-");
            battery.setText("电池电量：-");
            return;
        }
        lower.setText(cleanInput(device.optString("lower")));
        upper.setText(cleanInput(device.optString("upper")));
        pressure.setText("实时压力：" + clean(device.optString("pressure")));
        standard.setText("静止压力：" + staticPressureText(device));
        battery.setText("电池电量：" + batteryText(device));
    }

    private String batteryText(JSONObject device) {
        String battery = firstValue(device, "battery", "electricity", "power", "voltage", "bat");
        if (battery.length() == 0) {
            return "-";
        }
        String cleaned = clean(battery);
        if ("-".equals(cleaned)) {
            return cleaned;
        }
        return cleaned.contains("%") || cleaned.toLowerCase().contains("v") ? cleaned : cleaned + "%";
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
                long now = System.currentTimeMillis();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject device = rows.optJSONObject(i);
                    if (device == null) {
                        continue;
                    }
                    String mouldId = device.optString("mouldId");
                    if (mouldId.length() == 0) {
                        JSONObject mould = device.optJSONObject("mould");
                        mouldId = mould == null ? "" : mould.optString("id");
                    }
                    if (mouldId.length() > 0 && hasLivePressure(device)) {
                        boolean fluctuating = hasPressureFluctuation(device);
                        if (fluctuating) {
                            updateStaticPressure(device, false);
                            activeMouldUntil.put(mouldId, now + MOULD_ACTIVE_WINDOW_MS);
                        } else {
                            updateStaticPressure(device, canCaptureStaticAfterMouldOffline(mouldId, now));
                        }
                    } else {
                        updateStaticPressure(device, canCaptureStaticAfterMouldOffline(mouldId, now));
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
                        setStaticPressureViewText(standardView, staticPressureText(device));
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
            row.setPadding(dp(11), dp(10), dp(11), dp(10));
            row.setBackground(roundedStroke(0xfffffbeb, 14, 0xfff5d469));

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
            addMetric(metrics, "下限", sensor.optString("lower"), 0xfffff8e5, 0xffa15c07);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(primaryTitle(mould) + " - 设备压力")
                .setMessage(builder.toString())
                .setPositiveButton("关闭", null)
                .create();
        showStyledDialog(dialog);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(tabTitles[currentTab] + "详情")
                .setMessage(prettyObject(item));
        if (currentTab == 0) {
            builder.setNegativeButton("关闭", null)
                    .setPositiveButton("信息修改", (d, which) -> loadDetailForEdit(item));
        } else {
            builder.setPositiveButton("关闭", null);
        }
        showStyledDialog(builder.create());
    }

    private void showDeviceLimitDialog(JSONObject device) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), dp(8));

        TextView pressure = meta("实时压力：" + clean(device.optString("pressure")));
        TextView standard = meta("静止压力：" + staticPressureText(device));
        EditText lower = input("报警下限", false);
        lower.setText(cleanInput(device.optString("lower")));
        EditText upper = input("报警上限", false);
        upper.setText(cleanInput(device.optString("upper")));

        form.addView(pressure);
        form.addView(standard, topMargin(dp(2)));
        form.addView(label("报警下限"));
        form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("报警上限"));
        form.addView(upper, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(primaryTitle(device) + " - 上下限修改")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> saveDeviceLimits(device, lower.getText().toString().trim(), upper.getText().toString().trim()))
                .create();
        showStyledDialog(dialog);
    }

    private void saveDeviceLimits(JSONObject device, String lower, String upper) {
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
                toast("上下限已保存");
                loadList(false);
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            toast("保存上下限失败");
        }
    }

    private void showMouldInfoDialog(JSONObject mould) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), dp(8));

        EditText number = input("模具编号", false);
        number.setText(mould.optString("number"));
        EditText name = input("模具名称", false);
        name.setText(mould.optString("name"));
        EditText customer = input("客户", false);
        customer.setText(mould.optString("customer"));
        EditText remark = input("备注", false);
        remark.setText(mould.optString("remark"));

        form.addView(label("模具编号"));
        form.addView(number, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        form.addView(label("模具名称"));
        form.addView(name, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("客户"));
        form.addView(customer, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("备注"));
        form.addView(remark, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(primaryTitle(mould) + " - 修改信息")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> saveMouldInfo(
                        mould,
                        number.getText().toString().trim(),
                        name.getText().toString().trim(),
                        customer.getText().toString().trim(),
                        remark.getText().toString().trim()
                ))
                .create();
        showStyledDialog(dialog);
    }

    private void saveMouldInfo(JSONObject mould, String number, String name, String customer, String remark) {
        setLoading(true);
        try {
            JSONObject body = new JSONObject(mould.toString());
            body.put("number", number);
            body.put("name", name);
            body.put("customer", customer);
            body.put("remark", remark);
            new ApiTask("PUT", "/yujing/mould", body.toString(), true, result -> {
                setLoading(false);
                if (!result.ok) {
                    toast(result.message);
                    return;
                }
                toast("模具信息已保存");
                loadList(false);
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            toast("保存模具信息失败");
        }
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
            styleDialogButtons(editDialog);
        });
        showStyledDialog(editDialog);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定删除「" + primaryTitle(item) + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> deleteEntity(id))
                .create();
        showStyledDialog(dialog);
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
                if ("status".equals(key) && currentTab == 0) {
                    value = sensorOnlineStatusText(object);
                } else if ("status".equals(key) && currentTab == 2) {
                    value = onlineStatusText(value);
                }
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
        view.setTextColor(0xff58677a);
        view.setPadding(0, dp(5), 0, 0);
        return view;
    }

    private LinearLayout infoPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(roundedStroke(SOFT, 14, 0xffe3ebf5));
        return panel;
    }

    private void addInfoItem(LinearLayout panel, String label, String value) {
        if (value == null || value.length() == 0 || "null".equals(value)) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(0xff7b8795);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(INK);
        valueView.setTypeface(null, 1);
        valueView.setSingleLine(false);
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(row);
    }

    private TextView addMetric(LinearLayout parent, String label, String value, int bgColor, int textColor) {
        if (value == null || value.length() == 0 || "null".equals(value)) {
            return null;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(9));
        box.setBackground(roundedStroke(bgColor, 14, 0x14000000));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(textColor);
        valueView.setTextSize(18);
        valueView.setTypeface(null, 1);
        valueView.setSingleLine(false);
        box.addView(valueView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xff69778a);
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
            badge.setTextColor(offline ? 0xff8a5b00 : 0xff047857);
            badge.setPadding(dp(10), dp(5), dp(10), dp(5));
            badge.setBackground(roundedStroke(offline ? 0xfffff8e5 : 0xffecfdf5, 12, offline ? 0xfff3d77a : 0xffa7f3d0));
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
                badge.setPadding(dp(10), dp(5), dp(10), dp(5));
                badge.setBackground(roundedStroke(cleared ? 0xffecfdf5 : 0xfffff1f2, 12, cleared ? 0xffa7f3d0 : 0xffffb4bf));
                return badge;
            }
        }
        if (currentTab == 0) {
            String text = sensorOnlineStatusText(item);
            boolean offline = isSensorOffline(item);
            TextView badge = new TextView(this);
            badge.setText(text);
            badge.setTextSize(12);
            badge.setTextColor(offline ? 0xff92400e : 0xff0f766e);
            badge.setPadding(dp(10), dp(5), dp(10), dp(5));
            badge.setBackground(roundedStroke(offline ? 0xfffff8e5 : 0xffecfdf5, 12, offline ? 0xfff3d77a : 0xffa7f3d0));
            return badge;
        }
        if (currentTab == 2 && status.length() > 0 && !"null".equals(status)) {
            String text = onlineStatusText(status);
            boolean offline = isOfflineStatus(status);
            TextView badge = new TextView(this);
            badge.setText(text);
            badge.setTextSize(12);
            badge.setTextColor(offline ? 0xff92400e : 0xff0f766e);
            badge.setPadding(dp(10), dp(5), dp(10), dp(5));
            badge.setBackground(roundedStroke(offline ? 0xfffff8e5 : 0xffecfdf5, 12, offline ? 0xfff3d77a : 0xffa7f3d0));
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
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(roundedStroke(0xffecfdf5, 12, 0xffa7f3d0));
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
        if ("1".equals(value)
                || "online".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value)
                || "在线".equals(value)) {
            return "在线";
        }
        if ("0".equals(value)
                || "offline".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value)
                || "离线".equals(value)) {
            return "离线";
        }
        return value;
    }

    private boolean isOfflineStatus(String status) {
        String text = onlineStatusText(status);
        String raw = String.valueOf(status).trim();
        return "离线".equals(text)
                || "0".equals(raw)
                || "offline".equalsIgnoreCase(raw)
                || "false".equalsIgnoreCase(raw)
                || "no".equalsIgnoreCase(raw)
                || "off".equalsIgnoreCase(raw);
    }

    private String sensorOnlineStatusText(JSONObject device) {
        String raw = sensorOnlineStatusRaw(device);
        if (raw.length() > 0) {
            String status = onlineStatusText(raw);
            if ("在线".equals(status) || "离线".equals(status)) {
                return status;
            }
        }
        if (isDeviceUpdateStale(device)) {
            return "离线";
        }
        return raw.length() > 0 ? onlineStatusText(raw) : "-";
    }

    private boolean isSensorOffline(JSONObject device) {
        String raw = sensorOnlineStatusRaw(device);
        if (raw.length() > 0) {
            String status = onlineStatusText(raw);
            if ("离线".equals(status)) {
                return true;
            }
            if ("在线".equals(status)) {
                return false;
            }
        }
        return isDeviceUpdateStale(device);
    }

    private String sensorOnlineStatusRaw(JSONObject device) {
        return firstValue(
                device,
                "onlineStatus", "online_status", "onlineState", "online_state",
                "onlineFlag", "online_flag", "onlineStatusFlag", "online_status_flag",
                "netStatus", "net_status", "networkStatus", "network_status",
                "linkStatus", "link_status", "connectStatus", "connect_status",
                "isOnline", "online", "status"
        );
    }

    private boolean isDeviceUpdateStale(JSONObject device) {
        String time = firstValue(device, "updateTime", "update_time", "lastTime", "last_time", "lastOnlineTime", "lastReportTime");
        if (time.length() == 0) {
            return false;
        }
        try {
            String normalized = time.length() >= 19 ? time.substring(0, 19).replace("T", " ") : time;
            Date updated = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).parse(normalized);
            return updated != null && System.currentTimeMillis() - updated.getTime() > 10 * 60 * 1000;
        } catch (Exception ignored) {
            return false;
        }
    }

    private TextView sensorStatusChip(JSONObject device) {
        String text = sensorOnlineStatusText(device);
        boolean offline = "离线".equals(text);
        boolean unknown = "-".equals(text);
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(11);
        chip.setTypeface(null, 1);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(offline || unknown ? 0xff64748b : 0xff059669);
        chip.setBackground(roundedStroke(offline || unknown ? 0xfff1f5f9 : 0xffecfdf5, 13, offline || unknown ? 0xffcbd5e1 : 0xff86efac));
        return chip;
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
        if (tab == 1) return AMBER;
        if (tab == 2) return CYAN;
        return GREEN;
    }

    private int softAccent(int tab) {
        if (tab == 0) return 0xffeaf4ff;
        if (tab == 1) return 0xfffff8db;
        if (tab == 2) return 0xffe8faff;
        return 0xffecfdf5;
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
        panel.setBackground(roundedStroke(SURFACE, 18, 0xffdfe8f2));
        smoothElevation(panel, 4);

        TextView icon = new TextView(this);
        icon.setText("—");
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(28);
        icon.setTextColor(tabAccent(currentTab));
        panel.addView(icon, fixedTop(dp(46), dp(28), 0));

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

        content.addView(panel, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), currentTab == 0 ? dp(8) : dp(34)));
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
        button.setBackground(gradient(BLUE, CYAN, 16));
        smoothElevation(button, 3);
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
        smoothElevation(button, 1);
    }

    private void smoothElevation(View view, int elevationDp) {
        view.setElevation(0);
        if (Build.VERSION.SDK_INT >= 21) {
            view.setTranslationZ(0);
            view.setStateListAnimator(null);
            view.setClipToOutline(true);
        }
    }

    private void setIcon(Button button, int resId) {
        button.setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(5));
    }

    private void setTabIcon(Button button, int index) {
        if (index == 1) {
            int tint = unreadAlarmCount > 0 ? RED : (index == currentTab ? 0xffffffff : 0xff5b6472);
            setTintedIcon(button, tabIcons[index], tint);
        } else {
            setTintedIcon(button, tabIcons[index], index == currentTab ? 0xffffffff : 0xff5b6472);
        }
    }

    private void updateAlarmTabIconTint() {
        if (alarmTabButton != null) {
            int tint = unreadAlarmCount > 0 ? RED : (currentTab == 1 ? 0xffffffff : 0xff5b6472);
            setTintedIcon(alarmTabButton, R.drawable.ic_alarm, tint);
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

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        styleDialogWindow(dialog);
        styleDialogButtons(dialog);
    }

    private void styleDialogWindow(AlertDialog dialog) {
        if (dialog.getWindow() == null) {
            return;
        }
        dialog.getWindow().setBackgroundDrawable(roundedStroke(SURFACE, 22, 0xffdbe5f1));
        dialog.getWindow().getDecorView().setPadding(dp(2), dp(2), dp(2), dp(2));
    }

    private void styleDialogButtons(AlertDialog dialog) {
        styleDialogButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), true);
        styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), false);
        styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), false);
        layoutDialogButtons(dialog);
    }

    private void styleDialogButton(Button button, boolean primary) {
        if (button == null) {
            return;
        }
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(null, 1);
        button.setTextColor(primary ? 0xffffffff : BLUE);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(primary ? gradient(BLUE, CYAN, 10) : roundedStroke(0xfff8fbff, 10, 0xffcfe0f4));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setElevation(0);
            button.setTranslationZ(0);
            button.setStateListAnimator(null);
        }
    }

    private void layoutDialogButtons(AlertDialog dialog) {
        Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        Button negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        LinearLayout parent = null;
        if (positive != null && positive.getParent() instanceof LinearLayout) {
            parent = (LinearLayout) positive.getParent();
        } else if (negative != null && negative.getParent() instanceof LinearLayout) {
            parent = (LinearLayout) negative.getParent();
        } else if (neutral != null && neutral.getParent() instanceof LinearLayout) {
            parent = (LinearLayout) neutral.getParent();
        }
        if (parent == null) {
            return;
        }
        parent.setGravity(Gravity.CENTER);
        parent.setPadding(dp(18), dp(8), dp(18), dp(14));
        int count = 0;
        if (negative != null) count++;
        if (neutral != null) count++;
        if (positive != null) count++;
        int width = count > 1 ? 0 : dp(132);
        int weight = count > 1 ? 1 : 0;
        applyDialogButtonLayout(negative, width, weight, count > 1);
        applyDialogButtonLayout(neutral, width, weight, count > 1);
        applyDialogButtonLayout(positive, width, weight, count > 1);
    }

    private void applyDialogButtonLayout(Button button, int width, int weight, boolean spaced) {
        if (button == null) {
            return;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(40), weight);
        params.leftMargin = spaced ? dp(5) : 0;
        params.rightMargin = spaced ? dp(5) : 0;
        button.setLayoutParams(params);
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
