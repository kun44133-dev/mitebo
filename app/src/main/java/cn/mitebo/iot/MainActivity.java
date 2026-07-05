package cn.mitebo.iot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.widget.CompoundButton;
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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AbsListView;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String BASE_URL = "http://iot.mitebo.cn/prod-api";
    private static final String PREFS = "mitebo_iot";
    private static final String PREF_ALARM_SOUND_URI = "alarm_sound_uri";
    private static final String PREF_ALARM_SOUND_ENABLED = "alarm_sound_enabled";
    private static final String PREF_ALARM_VIBRATION_ENABLED = "alarm_vibration_enabled";
    private static final String PREF_OFFLINE_MOULD_ALARM_SOUND = "offline_mould_alarm_sound";
    private static final String PREF_BACKGROUND_ALARM_MONITOR = "background_alarm_monitor";
    private static final String PREF_PRESSURE_UNIT = "pressure_unit";
    private static final String PREF_OFFLINE_ALARM_MOULD_IDS = "offline_alarm_mould_ids";
    private static final String APP_EXPIRE_AT = "2026-08-31 23:59:59";
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
    private static final long STATIC_PRESSURE_STABLE_MS = 3 * 60 * 1000L;
    private static final long DEFAULT_REFRESH_MS = 15000;
    private static final long ALARM_REFRESH_MS = 5000;
    private static final long MOULD_REFRESH_MS = 5000;
    private static final int ALARM_PAGE_SIZE = 100;
    private static final int MAX_ALARM_PAGE_COUNT = 1;
    private static final int MAX_ALARM_DATE_SCAN_PAGES = 300;
    private static final int BOTTOM_NAV_HEIGHT_DP = 58;
    private static final int MOULD_TOGGLE_HEIGHT_DP = 40;
    private static final int MOULD_TOGGLE_NAV_GAP_DP = -3;
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
    private View bottomNavView;
    private View floatingMouldControls;
    private int lastBottomSystemInset = 0;
    private EditText macSearchInput;
    private Button alarmTabButton;
    private TextView alarmAllChipView;
    private int currentTab = 2;
    private int unreadAlarmCount = 0;
    private int audibleAlarmCount = 0;
    private int lastAlarmTotal = -1;
    private int todayAlarmTotal = -1;
    private boolean todayAlarmTotalHasMore = false;
    private String alarmTotalDateKey = "";
    private int historyAlarmTotal = -1;
    private int alarmTotalRequestVersion = 0;
    private String lastSeenAlarmKey = "";
    private String lastAudibleAlarmKey = "";
    private String alarmDateFilter = "";
    private boolean alarmDateManuallySelected = false;
    private boolean alarmUnclearedOnly = false;
    private boolean alarmHistoryAllMode = false;
    private long lastAlarmAllClickAt = 0;
    private boolean offlineMouldMode = false;
    private boolean gatewayManagementMode = false;
    private boolean appInForeground = false;
    private float mouldPullStartY = -1f;
    private boolean mouldPullReady = false;
    private View openedDeviceSwipeCard;
    private long lastAlarmSoundAt = 0;
    private Ringtone activeAlarmRingtone;
    private boolean alarmSoundLooping = false;
    private boolean alarmVibrationLooping = false;
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
    private final Map<String, Boolean> mouldBusyById = new HashMap<>();
    private final Set<String> activeAlarmMouldIds = new HashSet<>();
    private final Set<String> offlineAlarmMouldIds = new HashSet<>();
    private final Map<String, TextView> visiblePressureViews = new HashMap<>();
    private final Map<String, TextView> visibleStandardViews = new HashMap<>();
    private final Map<String, TextView> visibleUpdateViews = new HashMap<>();
    private final Map<String, ImageView> visibleMouldAlarmIcons = new HashMap<>();
    private final Map<String, JSONArray> mouldDropdownDeviceCache = new HashMap<>();
    private TextView fixedMouldGatewayCountView;
    private TextView fixedAlarmTitleView;
    private TextView fixedAlarmHintView;
    private int listRequestVersion = 0;
    private int loadingListRequestVersion = 0;
    private int alarmCountRequestVersion = 0;
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
        if (isAppExpired()) {
            showExpiredScreen();
            return;
        }
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
        if (isAppExpired()) {
            showExpiredScreen();
            return;
        }
        appInForeground = true;
        stopAlarmMonitorService();
        if (token != null && token.length() > 0 && content != null) {
            fetchAlarmCount(true);
            if (currentTab == 2) {
                loadList(false);
            }
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
        if (!isAppExpired() && token != null && token.length() > 0 && backgroundAlarmMonitorEnabled()) {
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

    private boolean isAppExpired() {
        try {
            Date expireAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).parse(APP_EXPIRE_AT);
            return expireAt != null && System.currentTimeMillis() > expireAt.getTime();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showExpiredScreen() {
        appInForeground = false;
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.removeCallbacks(alarmRefresh);
        stopAlarmSoundLoop();
        stopAlarmMonitorService();
        if (root == null) {
            return;
        }
        applyAdaptiveSystemBars(true);
        root.removeAllViews();
        root.setBackgroundColor(PAGE_BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView mark = new TextView(this);
        mark.setText("!");
        mark.setTextSize(30);
        mark.setTypeface(null, 1);
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(RED);
        mark.setBackground(roundedStroke(0xfffff1f2, 26, 0xffffcdd2));
        page.addView(mark, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView title = new TextView(this);
        title.setText("版本过期请更新版本！");
        title.setTextSize(22);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        page.addView(title, topMargin(dp(18)));

        TextView desc = new TextView(this);
        desc.setText("当前版本使用期限已到期，请安装新版本后继续使用。");
        desc.setTextSize(14);
        desc.setTextColor(MUTED);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(3), 1f);
        page.addView(desc, topMargin(dp(8)));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
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
        if (isAppExpired()) {
            showExpiredScreen();
            return;
        }
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
        subtitle.setText("氮气弹簧时实压力、设备状态、告警联动");
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
        accountPicker.setTextSize(20);
        accountPicker.setTypeface(null, 1);
        accountPicker.setTextColor(0xff64748b);
        accountPicker.setGravity(Gravity.CENTER);
        accountPicker.setBackground(rounded(0x00ffffff, 0));
        accountPicker.setOnClickListener(v -> showSavedAccountChooser());
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(dp(44), dp(50));
        accountRow.addView(accountPicker, pickerParams);
        panel.addView(accountRow, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), dp(18)));
        LinearLayout passwordRow = new LinearLayout(this);
        passwordRow.setOrientation(LinearLayout.HORIZONTAL);
        passwordRow.setGravity(Gravity.CENTER_VERTICAL);
        passwordRow.setPadding(0, 0, dp(4), 0);
        passwordRow.setBackground(roundedStroke(0xfffbfdff, 14, LINE));
        passwordInput = input("密码", true);
        passwordInput.setBackground(rounded(0x00ffffff, 0));
        boolean remembered = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("remember_password", false);
        if (remembered) {
            passwordInput.setText(savedPasswordFor(latestSavedUsername()));
        }
        passwordRow.addView(passwordInput, new LinearLayout.LayoutParams(0, dp(50), 1));
        View passwordDivider = new View(this);
        passwordDivider.setBackgroundColor(0xffdbe3ef);
        passwordRow.addView(passwordDivider, new LinearLayout.LayoutParams(dp(1), dp(26)));
        TextView passwordToggle = new TextView(this);
        final boolean[] passwordVisible = {false};
        passwordToggle.setText("显");
        passwordToggle.setTextSize(14);
        passwordToggle.setTypeface(null, 1);
        passwordToggle.setTextColor(0xff64748b);
        passwordToggle.setGravity(Gravity.CENTER);
        passwordToggle.setBackground(rounded(0x00ffffff, 0));
        passwordToggle.setOnClickListener(v -> {
            passwordVisible[0] = !passwordVisible[0];
            setPasswordVisible(passwordInput, passwordVisible[0]);
            passwordToggle.setText(passwordVisible[0] ? "隐" : "显");
        });
        passwordRow.addView(passwordToggle, new LinearLayout.LayoutParams(dp(44), dp(50)));
        panel.addView(passwordRow, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), dp(12)));

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
        version.setText("作者 kunkun  版本号 1.0.52");
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

    private void setPasswordVisible(EditText input, boolean visible) {
        if (input == null) {
            return;
        }
        int selection = Math.max(0, input.getSelectionStart());
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (visible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        int length = input.getText() == null ? 0 : input.getText().length();
        input.setSelection(Math.min(selection, length));
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
                        String previousUser = currentAccountName();
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
                        if (!username.equals(previousUser)) {
                            resetAlarmSessionState();
                            macSearchInput = null;
                            expandedMouldIds.clear();
                            expandedAlarmIds.clear();
                            expandedGatewayIds.clear();
                            resetPressureStateCaches();
                        }
                        migrateLegacyMonitoredMacs(username);
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
        if (isAppExpired()) {
            showExpiredScreen();
            return;
        }
        applyAdaptiveSystemBars(true);
        refreshHandler.removeCallbacks(pressureRefresh);
        refreshHandler.removeCallbacks(alarmRefresh);
        normalizeAlarmDateForCurrentTab();
        root.removeAllViews();
        loading.setVisibility(View.GONE);
        fixedMouldGatewayCountView = null;
        fixedAlarmTitleView = null;
        fixedAlarmHintView = null;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(PAGE_BG);
        page.setPadding(0, dp(32), 0, dp(20));
        applyHomeInsets(page);

        addContextPanel(page);
        addFixedAlarmSectionHeader(page);
        addFixedMouldGatewaySectionHeader(page);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), currentTab == 0 ? dp(2) : currentTab == 1 || currentTab == 2 ? dp(2) : dp(8), dp(12), contentBottomPadding());
        scroll.addView(content);
        attachPullRefresh(scroll);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        addBottomNavigation(page);

        root.addView(page);
        addFloatingMouldControls();
        root.addView(loading, centeredLoading());
        fetchAlarmCount(true);
        loadList(true);
        schedulePressureRefresh();
        scheduleAlarmRefresh();
    }

    private int contentBottomPadding() {
        if (currentTab != 2) {
            return dp(24);
        }
        return dp(MOULD_TOGGLE_HEIGHT_DP + BOTTOM_NAV_HEIGHT_DP + 28);
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
                    toast(currentTab == 0 ? "正在刷新设备数据" : gatewayManagementMode ? "正在刷新网关数据" : "正在刷新模具数据");
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
            lastBottomSystemInset = bottomInset;
            view.setPadding(0, topInset + dp(22), 0, bottomInset + dp(10));
            updateFloatingMouldControlsPosition();
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
        final int requestVersion = ++listRequestVersion;
        boolean showGlobalLoading = showProgress && currentTab != 2;
        if (showGlobalLoading) {
            loadingListRequestVersion = requestVersion;
            setLoading(true);
        }
        String endpoint = buildListEndpoint();
        new ApiTask("GET", endpoint, null, true, result -> {
            if (requestVersion != listRequestVersion) {
                if (showGlobalLoading && loadingListRequestVersion == requestVersion) {
                    setLoading(false);
                }
                return;
            }
            if (showGlobalLoading) {
                setLoading(false);
            }
            clearVisibleMouldValueViews();
            content.removeAllViews();
            updateFixedAlarmSectionHeader();
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
                if (currentTab == 1) {
                    int total = json.optInt("total", rows == null ? 0 : rows.length());
                    lastAlarmTotal = total;
                    if (alarmHistoryAllMode) {
                        historyAlarmTotal = total;
                    } else if (!alarmUnclearedOnly) {
                        String date = selectedAlarmDate();
                        int totalVersion = ++alarmTotalRequestVersion;
                        updateTodayAlarmTotal(date, countSelectedDateAlarms(rows), false);
                        if (rows != null && rows.length() >= ALARM_PAGE_SIZE) {
                            fetchAlarmDateTotalPage(date, 1, 0, totalVersion);
                        }
                    }
                    updateAlarmAllChipText();
                    syncActiveAlarmState(rows, false, true);
                }
                if (rows == null || rows.length() == 0) {
                    if (isAlarmDateMode()) {
                        scanAlarmDateRows(selectedAlarmDate(), requestVersion);
                        return;
                    }
                    showEmpty("暂无数据");
                    return;
                }
                if (currentTab == 2 && gatewayManagementMode) {
                    renderGatewayManagement(rows);
                    return;
                }
                if (currentTab == 2) {
                    renderPressureMoulds(rows, requestVersion);
                    return;
                }
                if (currentTab != 1) {
                    addSectionTitle(tabTitles[currentTab] + "列表");
                }
                int added = renderListRows(rows);
                if (isAlarmDateMode() && (added == 0 || renderedAlarmRowsAllActive(rows))) {
                    scanAlarmDateRows(selectedAlarmDate(), requestVersion, rows);
                    return;
                }
                if (added == 0) {
                    if (isAlarmDateMode()) {
                        scanAlarmDateRows(selectedAlarmDate(), requestVersion, rows);
                        return;
                    }
                    showEmpty(currentTab == 0 ? "未找到匹配的设备" : currentTab == 1 ? alarmListTitle() + " 暂无告警" : "暂无数据");
                }
            } catch (Exception e) {
                showEmpty("数据解析失败");
            }
        }).execute();
    }

    private int renderListRows(JSONArray rows) {
        if (currentTab == 1 && alarmHistoryAllMode) {
            int added = 0;
            added += renderAlarmRows(rows, true);
            added += renderAlarmRows(rows, false);
            return added;
        }
        int added = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && matchesDeviceQuery(item) && matchesSelectedAlarmDate(item) && matchesAlarmFilter(item)) {
                content.addView(cardFor(item), topMargin(dp(10)));
                added++;
            }
        }
        return added;
    }

    private int renderAlarmRows(JSONArray rows, boolean activeFirst) {
        int added = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null || !matchesDeviceQuery(item) || !matchesSelectedAlarmDate(item) || !matchesAlarmFilter(item)) {
                continue;
            }
            if (isActiveAlarm(item) != activeFirst) {
                continue;
            }
            content.addView(cardFor(item), topMargin(dp(10)));
            added++;
        }
        return added;
    }

    private int countSelectedDateAlarms(JSONArray rows) {
        return countAlarmDate(rows, selectedAlarmDate());
    }

    private int countAlarmDate(JSONArray rows, String date) {
        if (rows == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && matchesAlarmDate(item, date)) {
                count++;
            }
        }
        return count;
    }

    private void updateTodayAlarmTotal(String date, int count, boolean allowDecrease) {
        updateTodayAlarmTotal(date, count, allowDecrease, false);
    }

    private void updateTodayAlarmTotal(String date, int count, boolean allowDecrease, boolean hasMore) {
        if (date == null) {
            date = "";
        }
        if (!date.equals(alarmTotalDateKey)) {
            alarmTotalDateKey = date;
            todayAlarmTotal = Math.max(0, count);
            todayAlarmTotalHasMore = hasMore;
            updateAlarmAllChipText();
            return;
        }
        if (allowDecrease
                || count > todayAlarmTotal
                || (count == todayAlarmTotal && hasMore != todayAlarmTotalHasMore)
                || (hasMore && count >= todayAlarmTotal)) {
            todayAlarmTotal = Math.max(0, count);
            todayAlarmTotalHasMore = hasMore;
            updateAlarmAllChipText();
        }
    }

    private boolean renderedAlarmRowsAllActive(JSONArray rows) {
        if (currentTab != 1 || rows == null || rows.length() == 0) {
            return false;
        }
        int matched = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null || !matchesDeviceQuery(item) || !matchesSelectedAlarmDate(item) || !matchesAlarmFilter(item)) {
                continue;
            }
            matched++;
            if (!isActiveAlarm(item)) {
                return false;
            }
        }
        return matched > 0;
    }

    private void scanAlarmDateRows(String date, int requestVersion) {
        scanAlarmDateRows(date, requestVersion, null);
    }

    private void scanAlarmDateRows(String date, int requestVersion, JSONArray fallbackRows) {
        scanAlarmDateRows(date, requestVersion, fallbackRows, 1, new JSONArray(), 0);
    }

    private void scanAlarmDateRows(String date, int requestVersion, JSONArray fallbackRows, int pageNum, JSONArray matchedRows, int matchedCount) {
        if (token == null || date == null || date.length() == 0 || requestVersion != listRequestVersion || !isAlarmDateMode()) {
            return;
        }
        String endpoint = tabEndpoints[1] + "?pageNum=" + pageNum + "&pageSize=" + ALARM_PAGE_SIZE;
        new ApiTask("GET", endpoint, null, true, result -> {
            if (requestVersion != listRequestVersion || !isAlarmDateMode() || !date.equals(selectedAlarmDate())) {
                return;
            }
            if (!result.ok) {
                showEmpty(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                int nextCount = matchedCount;
                boolean sawOlder = false;
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        int compare = compareAlarmDate(item, date);
                        if (compare == 0) {
                            nextCount++;
                            if (matchedRows.length() < ALARM_PAGE_SIZE) {
                                matchedRows.put(item);
                            }
                        } else if (compare < 0) {
                            sawOlder = true;
                        }
                    }
                }
                boolean reachedEnd = rows == null
                        || rows.length() < ALARM_PAGE_SIZE
                        || pageNum >= MAX_ALARM_DATE_SCAN_PAGES
                        || sawOlder;
                boolean reachedLimit = rows != null
                        && rows.length() >= ALARM_PAGE_SIZE
                        && pageNum >= MAX_ALARM_DATE_SCAN_PAGES
                        && !sawOlder;
                if (nextCount > 0 || reachedEnd) {
                    updateTodayAlarmTotal(date, nextCount, reachedEnd && nextCount == 0, reachedLimit);
                }
                if (rows != null
                        && rows.length() >= ALARM_PAGE_SIZE
                        && pageNum < MAX_ALARM_DATE_SCAN_PAGES
                        && !sawOlder) {
                    scanAlarmDateRows(date, requestVersion, fallbackRows, pageNum + 1, matchedRows, nextCount);
                    return;
                }
                content.removeAllViews();
                int added = renderListRows(matchedRows);
                if (added == 0 && fallbackRows != null && fallbackRows.length() > 0) {
                    added = renderListRows(fallbackRows);
                    updateTodayAlarmTotal(date, added, false);
                }
                if (added == 0) {
                    showEmpty(alarmListTitle() + " 暂无告警");
                }
            } catch (Exception e) {
                showEmpty("数据解析失败");
            }
        }).execute();
    }

    private void fetchAlarmDateTotalPage(String date, int pageNum, int accumulated, int version) {
        if (token == null || date == null || date.length() == 0) {
            return;
        }
        String endpoint = tabEndpoints[1] + "?pageNum=" + pageNum + "&pageSize=" + ALARM_PAGE_SIZE;
        new ApiTask("GET", endpoint, null, true, result -> {
            if (version != alarmTotalRequestVersion || currentTab != 1 || alarmHistoryAllMode || alarmUnclearedOnly || !date.equals(selectedAlarmDate())) {
                return;
            }
            if (!result.ok) {
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                int pageCount = 0;
                boolean sawOlder = false;
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        int compare = compareAlarmDate(item, date);
                        if (compare == 0) {
                            pageCount++;
                        } else if (compare < 0) {
                            sawOlder = true;
                        }
                    }
                }
                int total = accumulated + pageCount;
                boolean reachedEnd = rows == null
                        || rows.length() < ALARM_PAGE_SIZE
                        || pageNum >= MAX_ALARM_DATE_SCAN_PAGES
                        || sawOlder;
                boolean reachedLimit = rows != null
                        && rows.length() >= ALARM_PAGE_SIZE
                        && pageNum >= MAX_ALARM_DATE_SCAN_PAGES
                        && !sawOlder;
                if (total > 0 || reachedEnd) {
                    updateTodayAlarmTotal(date, total, reachedEnd && total == 0, reachedLimit);
                }
                if (rows != null
                        && rows.length() >= ALARM_PAGE_SIZE
                        && pageNum < MAX_ALARM_DATE_SCAN_PAGES
                        && !sawOlder) {
                    fetchAlarmDateTotalPage(date, pageNum + 1, total, version);
                }
            } catch (Exception ignored) {
            }
        }).execute();
    }

    private void fetchAlarmCount(boolean redrawTabs) {
        if (token == null) {
            return;
        }
        String tokenSnapshot = token;
        int version = ++alarmCountRequestVersion;
        fetchAlarmCountPage(1, new JSONArray(), redrawTabs, version, tokenSnapshot);
    }

    private void fetchAlarmCountPage(int pageNum, JSONArray accumulatedRows, boolean redrawTabs, int version, String tokenSnapshot) {
        new ApiTask("GET", tabEndpoints[1] + "?pageNum=" + pageNum + "&pageSize=" + ALARM_PAGE_SIZE, null, true, result -> {
            if (version != alarmCountRequestVersion || token == null || tokenSnapshot == null || !tokenSnapshot.equals(token)) {
                return;
            }
            if (!result.ok) {
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                appendRows(accumulatedRows, rows);
                historyAlarmTotal = json.optInt("total", accumulatedRows.length());
                if (alarmHistoryAllMode) {
                    lastAlarmTotal = historyAlarmTotal;
                }
                if (rows != null && rows.length() >= ALARM_PAGE_SIZE && pageNum < MAX_ALARM_PAGE_COUNT) {
                    fetchAlarmCountPage(pageNum + 1, accumulatedRows, redrawTabs, version, tokenSnapshot);
                    return;
                }
                updateAlarmAllChipText();
                syncActiveAlarmState(accumulatedRows, true, redrawTabs);
            } catch (Exception ignored) {
            }
        }).execute();
    }

    private void resetAlarmSessionState() {
        alarmCountRequestVersion++;
        alarmTotalRequestVersion++;
        listRequestVersion++;
        loadingListRequestVersion++;
        unreadAlarmCount = 0;
        audibleAlarmCount = 0;
        lastAlarmTotal = -1;
        todayAlarmTotal = -1;
        historyAlarmTotal = -1;
        lastSeenAlarmKey = "";
        lastAudibleAlarmKey = "";
        activeAlarmMouldIds.clear();
        offlineAlarmMouldIds.clear();
        visibleMouldAlarmIcons.clear();
        expandedAlarmIds.clear();
        stopAlarmSoundLoop();
        stopAlarmVibrationLoop();
        updateLauncherAlarmBadge();
        updateAlarmTabIconTint();
    }

    private void appendRows(JSONArray target, JSONArray source) {
        if (target == null || source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            Object item = source.opt(i);
            if (item != null) {
                target.put(item);
            }
        }
    }

    private void syncActiveAlarmState(JSONArray rows, boolean allowSound, boolean redrawTabs) {
        int oldCount = unreadAlarmCount;
        int oldAudibleCount = audibleAlarmCount;
        int activeCount = 0;
        int soundableCount = 0;
        String latestActiveKey = "";
        String latestAudibleKey = "";
        Set<String> latestAlarmMouldIds = new HashSet<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject alarm = rows.optJSONObject(i);
                if (alarm == null || !isActiveAlarm(alarm)) {
                    continue;
                }
                activeCount++;
                String mouldId = alarmMouldId(alarm);
                if (mouldId.length() > 0) {
                    latestAlarmMouldIds.add(mouldId);
                }
                if (shouldAlarmMakeSound(alarm)) {
                    soundableCount++;
                    if (latestAudibleKey.length() == 0) {
                        latestAudibleKey = alarmKey(alarm);
                    }
                }
                if (latestActiveKey.length() == 0) {
                    latestActiveKey = alarmKey(alarm);
                }
            }
        }
        // 角标按全部未消除告警统计，响铃只统计允许发声的告警，避免离线模具静音开关影响角标显示。
        boolean newAudibleAlarm = soundableCount > oldAudibleCount
                || (soundableCount >= oldAudibleCount && oldAudibleCount > 0 && latestAudibleKey.length() > 0 && !latestAudibleKey.equals(lastAudibleAlarmKey));
        boolean alarmMouldOrderChanged = !activeAlarmMouldIds.equals(latestAlarmMouldIds);
        unreadAlarmCount = activeCount;
        audibleAlarmCount = soundableCount;
        activeAlarmMouldIds.clear();
        activeAlarmMouldIds.addAll(latestAlarmMouldIds);
        lastSeenAlarmKey = latestActiveKey;
        lastAudibleAlarmKey = latestAudibleKey;
        updateLauncherAlarmBadge();
        updateVisibleMouldAlarmBadges();
        if (alarmMouldOrderChanged && currentTab == 2 && !gatewayManagementMode && content != null) {
            loadList(false);
        }
        if (activeCount > 0) {
            if (soundableCount > 0 && allowSound && newAudibleAlarm) {
                startAlarmSoundLoop();
            } else if (soundableCount == 0) {
                stopAlarmSoundLoop();
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

    private boolean shouldAlarmMakeSound(JSONObject alarm) {
        return isActiveAlarm(alarm) && (!isOfflineSensorAlarm(alarm) || offlineMouldAlarmSoundEnabled());
    }

    private String alarmMouldId(JSONObject alarm) {
        if (alarm == null) {
            return "";
        }
        String mouldId = firstValue(alarm, "mouldId", "mould_id");
        JSONObject mould = alarm.optJSONObject("mould");
        if (mouldId.length() == 0 && mould != null) {
            mouldId = mould.optString("id");
        }
        JSONArray details = alarmDetails(alarm);
        if (mouldId.length() == 0 && details != null) {
            for (int i = 0; i < details.length(); i++) {
                JSONObject detail = details.optJSONObject(i);
                if (detail == null) {
                    continue;
                }
                mouldId = firstValue(detail, "mouldId", "mould_id");
                JSONObject detailMould = detail.optJSONObject("mould");
                if (mouldId.length() == 0 && detailMould != null) {
                    mouldId = detailMould.optString("id");
                }
                if (mouldId.length() > 0) {
                    break;
                }
            }
        }
        return mouldId;
    }

    private boolean matchesAlarmFilter(JSONObject alarm) {
        return currentTab != 1 || !alarmUnclearedOnly || isActiveAlarm(alarm);
    }

    private boolean isOfflineSensorAlarm(JSONObject alarm) {
        if (alarm == null) {
            return false;
        }
        String text = (alarm.optString("title") + " "
                + alarm.optString("name") + " "
                + alarm.optString("type") + " "
                + alarm.optString("state") + " "
                + alarm.optString("status") + " "
                + alarm.optString("msg") + " "
                + alarm.optString("message") + " "
                + alarm.optString("remark") + " "
                + alarm.optString("detail")).toLowerCase();
        if (text.contains("离线")
                || text.contains("掉线")
                || text.contains("断线")
                || text.contains("offline")) {
            return true;
        }
        String mouldId = alarmMouldId(alarm);
        return mouldId.length() > 0 && offlineAlarmMouldIds.contains(mouldId);
    }

    private String alarmMouldStateLabel(JSONObject alarm) {
        return isOfflineSensorAlarm(alarm) ? "离线模具" : "在线模具";
    }

    private TextView alarmMouldStateChip(JSONObject alarm) {
        boolean offline = isOfflineSensorAlarm(alarm);
        TextView chip = new TextView(this);
        chip.setText(alarmMouldStateLabel(alarm));
        chip.setTextSize(10);
        chip.setTypeface(null, 1);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setTextColor(offline ? 0xffb45309 : 0xff047857);
        chip.setPadding(dp(6), 0, dp(6), 0);
        chip.setBackground(roundedStroke(offline ? 0xfffffbeb : 0xffecfdf5, 10,
                offline ? 0xfffbbf24 : 0xff86efac));
        return chip;
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
            AlarmAlertController.vibrateOnce(getApplicationContext(), alarmVibrationEnabled());
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
            alarmSoundLooping = true;
            AlarmAlertController.start(getApplicationContext(), uri, alarmVibrationEnabled());
        } catch (Exception ignored) {
            alarmSoundLooping = false;
            stopAlarmVibrationLoop();
        }
    }

    private final Runnable alarmSoundRepeater = new Runnable() {
        @Override
        public void run() {
            if (!alarmSoundLooping || audibleAlarmCount <= 0) {
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
        AlarmAlertController.stop(getApplicationContext());
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

    private void vibrateOnce() {
        AlarmAlertController.vibrateOnce(getApplicationContext(), alarmVibrationEnabled());
    }

    private void startAlarmVibrationLoop() {
        if (!alarmVibrationEnabled() || alarmVibrationLooping) {
            return;
        }
        alarmVibrationLooping = true;
        AlarmAlertController.startVibrationLoop(getApplicationContext());
    }

    private void stopAlarmVibrationLoop() {
        alarmVibrationLooping = false;
        AlarmAlertController.stopVibrationLoop(getApplicationContext());
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

    private boolean alarmVibrationEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ALARM_VIBRATION_ENABLED, true);
    }

    private boolean offlineMouldAlarmSoundEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_OFFLINE_MOULD_ALARM_SOUND, false);
    }

    private boolean backgroundAlarmMonitorEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_BACKGROUND_ALARM_MONITOR, false);
    }

    private String pressureUnitLabel() {
        String unit = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_PRESSURE_UNIT, "bar");
        if ("MPa".equals(unit) || "psi".equals(unit)) {
            return unit;
        }
        return "bar";
    }

    private String pressureWithUnit(String value) {
        String cleaned = clean(value);
        if ("-".equals(cleaned)) {
            return cleaned;
        }
        Double barValue = pressureToBar(cleaned);
        if (barValue == null) {
            return cleaned;
        }
        return trimNumber(convertBarToPressureUnit(barValue, pressureUnitLabel())) + " " + pressureUnitLabel();
    }

    private String pressureInputValue(String value) {
        Double barValue = pressureToBar(value);
        if (barValue == null) {
            return "";
        }
        return trimNumber(convertBarToPressureUnit(barValue, pressureUnitLabel()));
    }

    private String pressureInputToStorageValue(String value) {
        if (value == null || value.trim().length() == 0) {
            return "";
        }
        Double barValue = pressureInputToBar(value);
        if (barValue == null) {
            return value.trim();
        }
        return trimNumber(barValue);
    }

    private Double pressureInputToBar(String value) {
        String cleaned = clean(value);
        if ("-".equals(cleaned)) {
            return null;
        }
        Double raw = numberValue(cleaned);
        if (raw == null) {
            return null;
        }
        String sourceUnit = pressureUnitInText(cleaned);
        if (!cleaned.toLowerCase().contains("bar")
                && !cleaned.toLowerCase().contains("mpa")
                && !cleaned.toLowerCase().contains("psi")) {
            sourceUnit = pressureUnitLabel();
        }
        if ("MPa".equals(sourceUnit)) {
            return raw / 0.1d;
        }
        if ("psi".equals(sourceUnit)) {
            return raw / 14.5038d;
        }
        return raw;
    }

    private Double pressureToBar(String value) {
        String cleaned = clean(value);
        if ("-".equals(cleaned)) {
            return null;
        }
        Double raw = numberValue(cleaned);
        if (raw == null) {
            return null;
        }
        String sourceUnit = pressureUnitInText(cleaned);
        if ("MPa".equals(sourceUnit)) {
            return raw / 0.1d;
        }
        if ("psi".equals(sourceUnit)) {
            return raw / 14.5038d;
        }
        return raw;
    }

    private double convertBarToPressureUnit(double barValue, String targetUnit) {
        double displayValue = barValue;
        if ("MPa".equals(targetUnit)) {
            displayValue = barValue * 0.1d;
        } else if ("psi".equals(targetUnit)) {
            displayValue = barValue * 14.5038d;
        }
        return displayValue;
    }

    private String pressureUnitInText(String value) {
        String lower = value == null ? "" : value.toLowerCase();
        if (lower.contains("mpa")) {
            return "MPa";
        }
        if (lower.contains("psi")) {
            return "psi";
        }
        return "bar";
    }

    private boolean isPressureField(String key) {
        return "pressure".equals(key) || "standard".equals(key) || "lower".equals(key) || "upper".equals(key);
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
        if (!canPostNotifications()) {
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
        try {
            manager.notify(ALARM_NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
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
        if (alarmHistoryAllMode) {
            return true;
        }
        if (alarmUnclearedOnly) {
            return true;
        }
        return matchesAlarmDate(item, selectedAlarmDate());
    }

    private boolean matchesAlarmDate(JSONObject item, String date) {
        String time = alarmTimeForDateFilter(item);
        if (time.length() == 0) {
            return true;
        }
        return date != null && date.length() > 0 && time.startsWith(date);
    }

    private int compareAlarmDate(JSONObject item, String date) {
        String time = alarmTimeForDateFilter(item);
        if (time.length() < 10 || date == null || date.length() == 0) {
            return 1;
        }
        return time.substring(0, 10).compareTo(date);
    }

    private String alarmCreatedTime(JSONObject item) {
        return firstValue(item, "createTime", "create_time", "alarmTime");
    }

    private String alarmTimeForDateFilter(JSONObject item) {
        String created = alarmCreatedTime(item);
        if (created.length() > 0) {
            return created;
        }
        return firstValue(item, "updateTime", "update_time");
    }

    private boolean isAlarmDateMode() {
        return currentTab == 1 && !alarmHistoryAllMode && !alarmUnclearedOnly;
    }

    private String alarmListTitle() {
        if (alarmHistoryAllMode) {
            return "历史全部告警";
        }
        return alarmUnclearedOnly ? "未消除告警" : selectedAlarmDate() + " 告警";
    }

    private String buildListEndpoint() {
        String pageSize = currentTab == 1 ? String.valueOf(ALARM_PAGE_SIZE) : (currentTab == 0 || currentTab == 2 ? "200" : "20");
        String baseEndpoint = currentTab == 2 && gatewayManagementMode ? "/yujing/gateway/list" : tabEndpoints[currentTab];
        String endpoint = baseEndpoint + "?pageNum=1&pageSize=" + pageSize;
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

    private String buildAlarmDateEndpoint(String date, int pageNum, int pageSize) {
        String endpoint = tabEndpoints[1] + "?pageNum=" + pageNum + "&pageSize=" + pageSize;
        try {
            String encodedDate = URLEncoder.encode(date, "UTF-8");
            endpoint += "&createTime=" + encodedDate;
            endpoint += "&params%5BbeginTime%5D=" + URLEncoder.encode(date + " 00:00:00", "UTF-8");
            endpoint += "&params%5BendTime%5D=" + URLEncoder.encode(date + " 23:59:59", "UTF-8");
        } catch (Exception ignored) {
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

            TextView clearSearch = new TextView(this);
            clearSearch.setText("×");
            clearSearch.setTextSize(18);
            clearSearch.setTypeface(null, 1);
            clearSearch.setTextColor(0xff64748b);
            clearSearch.setGravity(Gravity.CENTER);
            clearSearch.setBackground(rounded(0xfff1f5f9, 12));
            clearSearch.setVisibility(View.GONE);
            clearSearch.setOnClickListener(v -> {
                macSearchInput.setText("");
                loadList();
            });
            macSearchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    clearSearch.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            clearParams.leftMargin = dp(4);
            clearParams.rightMargin = dp(4);
            search.addView(clearSearch, clearParams);

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
            alarmAllChipView = addChip(chips, alarmAllChipText(), !alarmUnclearedOnly, v -> {
                long now = System.currentTimeMillis();
                if (now - lastAlarmAllClickAt < 650) {
                    alarmHistoryAllMode = !alarmHistoryAllMode;
                    alarmUnclearedOnly = false;
                    if (alarmHistoryAllMode) {
                        alarmDateManuallySelected = true;
                    } else {
                        alarmDateFilter = todayPrefix();
                        alarmDateManuallySelected = false;
                    }
                    updateAlarmAllChipText();
                    loadList(false);
                } else {
                    alarmUnclearedOnly = false;
                    updateAlarmAllChipText();
                    loadList(false);
                }
                lastAlarmAllClickAt = now;
            });
            addChip(chips, "未消除 " + unreadAlarmCount, alarmUnclearedOnly && !alarmHistoryAllMode, v -> {
                alarmHistoryAllMode = false;
                alarmUnclearedOnly = true;
                loadList(false);
            });
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
            text.setText(gatewayManagementMode ? "当前显示网关信息与在线状态" : offlineMouldMode ? "当前显示压力不波动的离线模具" : "只显示在线模具（实时压力有波动）");
            text.setTextSize(13);
            text.setTextColor(INK);
            tip.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            panel.addView(tip);
        }
        page.addView(panel);
    }

    private void addFixedMouldGatewaySectionHeader(LinearLayout page) {
        if (currentTab != 2) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(4), dp(14), dp(2));
        row.setBackgroundColor(PAGE_BG);

        TextView bar = new TextView(this);
        bar.setText("");
        bar.setBackground(rounded(tabAccent(currentTab), 3));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(4), dp(18));
        barParams.rightMargin = dp(8);
        row.addView(bar, barParams);

        TextView title = new TextView(this);
        title.setText(gatewayManagementMode ? "网关管理" : offlineMouldMode ? "离线模具" : "在线生产模具");
        title.setTextSize(15);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        fixedMouldGatewayCountView = new TextView(this);
        fixedMouldGatewayCountView.setText("统计中");
        fixedMouldGatewayCountView.setTextSize(12);
        fixedMouldGatewayCountView.setTextColor(MUTED);
        fixedMouldGatewayCountView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        fixedMouldGatewayCountView.setSingleLine(true);
        row.addView(fixedMouldGatewayCountView);
        page.addView(row);
    }

    private void addFixedAlarmSectionHeader(LinearLayout page) {
        if (currentTab != 1) {
            return;
        }
        LinearLayout row = createSectionTitleRow(alarmListTitle(), "实时刷新", true);
        row.setBackgroundColor(PAGE_BG);
        page.addView(row);
    }

    private void updateFixedAlarmSectionHeader() {
        if (fixedAlarmTitleView != null) {
            fixedAlarmTitleView.setText(alarmListTitle());
        }
        if (fixedAlarmHintView != null) {
            fixedAlarmHintView.setText("实时刷新");
        }
    }

    private void updateFixedMouldGatewayCount(String text) {
        if (fixedMouldGatewayCountView != null) {
            fixedMouldGatewayCountView.setText(text);
        }
    }

    private TextView addChip(LinearLayout parent, String text, boolean selected) {
        return addChip(parent, text, selected, null);
    }

    private String alarmAllChipText() {
        if (alarmHistoryAllMode) {
            return "全部报警 " + Math.max(historyAlarmTotal, 0);
        }
        String prefix = selectedAlarmDate().equals(todayPrefix()) ? "今日全部 " : "日期全部 ";
        return prefix + Math.max(todayAlarmTotal, 0) + (todayAlarmTotalHasMore ? "+" : "");
    }

    private void updateAlarmAllChipText() {
        if (alarmAllChipView != null) {
            alarmAllChipView.setText(alarmAllChipText());
        }
    }

    private TextView addChip(LinearLayout parent, String text, boolean selected, View.OnClickListener listener) {
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
        return chip;
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
                    alarmHistoryAllMode = false;
                    alarmUnclearedOnly = false;
                    showHome();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        picker.setButton(DialogInterface.BUTTON_NEUTRAL, "今天", (d, which) -> {
            alarmDateFilter = todayPrefix();
            alarmDateManuallySelected = false;
            alarmHistoryAllMode = false;
            alarmUnclearedOnly = false;
            showHome();
        });
        picker.show();
        styleDialogWindow(picker);
        styleDialogButtons(picker);
    }

    private void renderSettingsPage() {
        addSettingsHeader();

        LinearLayout preferenceCard = settingsCard("偏好设置", "报警、后台轮询与压力单位");
        preferenceCard.addView(settingsSwitchRow(
                "报警声音",
                "有新告警时播放提示音",
                alarmSoundEnabled(),
                (buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_ALARM_SOUND_ENABLED, isChecked)
                    .apply();
            if (!isChecked) {
                stopAlarmSoundLoop();
            } else if (audibleAlarmCount > 0) {
                startAlarmSoundLoop();
            }
        }), topMargin(dp(8)));
        preferenceCard.addView(settingsDivider());
        preferenceCard.addView(settingsSwitchRow(
                "报警震动",
                "有告警提醒时同步震动",
                alarmVibrationEnabled(),
                (buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_ALARM_VIBRATION_ENABLED, isChecked)
                    .apply();
            if (!isChecked) {
                stopAlarmVibrationLoop();
            } else if (alarmSoundLooping && audibleAlarmCount > 0) {
                startAlarmVibrationLoop();
            }
        }));
        preferenceCard.addView(settingsDivider());
        preferenceCard.addView(settingsSwitchRow(
                "离线模具报警声音",
                "开启后离线模具告警也会响铃",
                offlineMouldAlarmSoundEnabled(),
                (buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_OFFLINE_MOULD_ALARM_SOUND, isChecked)
                    .apply();
            if (!isChecked) {
                audibleAlarmCount = 0;
                lastAudibleAlarmKey = "";
                stopAlarmSoundLoop();
            }
            fetchAlarmCount(true);
        }));
        preferenceCard.addView(settingsDivider());
        preferenceCard.addView(settingsSwitchRow(
                "后台告警监控",
                "退到后台后只轮询告警信息",
                backgroundAlarmMonitorEnabled(),
                (buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_BACKGROUND_ALARM_MONITOR, isChecked)
                    .apply();
            if (!isChecked) {
                stopAlarmMonitorService();
            }
            toast(isChecked ? "退到后台后会继续监控告警" : "已关闭后台轮询，退后台后不再刷新告警");
        }));
        preferenceCard.addView(settingsDivider());
        TextView pickTone = settingsAction("选择");
        pickTone.setOnClickListener(v -> showAlarmSoundPicker());
        preferenceCard.addView(settingsActionRow("报警提示音", "选择系统提示音", pickTone));

        preferenceCard.addView(settingsDivider());
        List<OptionItem> units = new ArrayList<>();
        units.add(new OptionItem("bar", "bar"));
        units.add(new OptionItem("MPa", "MPa"));
        units.add(new OptionItem("psi", "psi"));
        Spinner unitSpinner = spinner(units, pressureUnitLabel());
        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selected = parent.getItemAtPosition(position);
                if (!(selected instanceof OptionItem)) {
                    return;
                }
                String unit = ((OptionItem) selected).value;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_PRESSURE_UNIT, unit)
                        .apply();
                refreshVisibleMouldPressureValues();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        preferenceCard.addView(settingsActionRow("压力单位", "切换压力单位", unitSpinner));
        content.addView(preferenceCard, topMargin(dp(8)));

        LinearLayout appCard = settingsCard("账号", "当前登录账号与退出");
        appCard.addView(meta("当前账号：" + getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_username", "-")), topMargin(dp(6)));
        TextView exit = settingsAction("退出登录");
        exit.setTextColor(RED);
        exit.setOnClickListener(v -> {
            stopAlarmMonitorService();
            resetAlarmSessionState();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").apply();
            token = null;
            showLogin();
        });
        appCard.addView(exit, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(38), dp(8)));
        content.addView(appCard, topMargin(dp(8)));
    }

    private void addSettingsHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(2), dp(6), dp(2), dp(2));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(18);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("管理告警、单位和账号");
        subtitle.setTextSize(12);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        header.addView(subtitle);
        content.addView(header);
    }

    private LinearLayout settingsCard(String titleText, String subtitleText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 2);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(11);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        card.addView(subtitle);
        return card;
    }

    private View settingsDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(0xffeef3f8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(0, dp(6), 0, dp(6));
        divider.setLayoutParams(params);
        return divider;
    }

    private LinearLayout settingsSwitchRow(String titleText, String subtitleText, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        CheckBox toggle = new CheckBox(this);
        toggle.setText("");
        toggle.setGravity(Gravity.CENTER);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        return settingsActionRow(titleText, subtitleText, toggle);
    }

    private LinearLayout settingsActionRow(String titleText, String subtitleText, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(14);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        copy.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(11);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        copy.addView(subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout.LayoutParams controlParams;
        if (control instanceof Spinner) {
            controlParams = new LinearLayout.LayoutParams(dp(112), dp(38));
        } else if (control instanceof CheckBox) {
            controlParams = new LinearLayout.LayoutParams(dp(48), dp(38));
        } else {
            controlParams = new LinearLayout.LayoutParams(dp(72), dp(34));
        }
        controlParams.leftMargin = dp(10);
        row.addView(control, controlParams);
        return row;
    }

    private TextView settingsAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextSize(13);
        action.setTypeface(null, 1);
        action.setTextColor(BLUE);
        action.setGravity(Gravity.CENTER);
        action.setBackground(roundedStroke(0xfff8fbff, 10, 0xffdbeafe));
        return action;
    }

    private void addBottomNavigation(LinearLayout page) {
        LinearLayout nav = new LinearLayout(this);
        bottomNavView = nav;
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(roundedStroke(SURFACE, 0, 0xffe2e8f0));
        nav.setElevation(dp(8));

        int[] navOrder = {2, 1, 0, 3};
        for (int i = 0; i < navOrder.length; i++) {
            final int index = navOrder[i];
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
                    alarmHistoryAllMode = false;
                } else {
                    alarmUnclearedOnly = false;
                    alarmHistoryAllMode = false;
                }
                if (currentTab != 2) {
                    offlineMouldMode = false;
                    gatewayManagementMode = false;
                } else {
                    gatewayManagementMode = false;
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

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(BOTTOM_NAV_HEIGHT_DP), 1);
            params.leftMargin = dp(3);
            params.rightMargin = dp(3);
            nav.addView(slot, params);
        }
        page.addView(nav);
    }

    private void addFloatingMouldControls() {
        floatingMouldControls = null;
        if (currentTab != 2) {
            return;
        }
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setBackgroundColor(Color.TRANSPARENT);
        TextView gatewayButton = gatewayModeButton();
        LinearLayout.LayoutParams gatewayParams = new LinearLayout.LayoutParams(0, dp(MOULD_TOGGLE_HEIGHT_DP), 1);
        modeRow.addView(gatewayButton, gatewayParams);
        TextView rightButton = gatewayManagementMode ? gatewayRefreshButton() : mouldModeToggleButton();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(MOULD_TOGGLE_HEIGHT_DP), 1);
        rightParams.leftMargin = dp(10);
        modeRow.addView(rightButton, rightParams);

        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(dp(228), dp(MOULD_TOGGLE_HEIGHT_DP));
        toggleParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        floatingMouldControls = modeRow;
        root.addView(modeRow, toggleParams);
        updateFloatingMouldControlsPosition();
        root.post(this::updateFloatingMouldControlsPosition);
    }

    private void updateFloatingMouldControlsPosition() {
        if (floatingMouldControls == null || bottomNavView == null || root == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = floatingMouldControls.getLayoutParams();
        if (!(rawParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        params.bottomMargin = lastBottomSystemInset + dp(BOTTOM_NAV_HEIGHT_DP + MOULD_TOGGLE_HEIGHT_DP + MOULD_TOGGLE_NAV_GAP_DP);
        floatingMouldControls.setLayoutParams(params);
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
        if (currentTab == 2) return gatewayManagementMode ? "网关管理" : offlineMouldMode ? "离线模具池" : "在线生产模具";
        return offlineMouldMode ? "离线模具池" : "在线生产模具";
    }

    private String overviewSubtitle() {
        if (currentTab == 0) return "查询或添加 MAC 后显示传感器实时压力";
        if (currentTab == 1) return "只展示当天告警，未消除会同步铃声和角标";
        if (currentTab == 2) return gatewayManagementMode ? "查看网关在线状态、组织和更新时间" : offlineMouldMode ? "有压力但波动不足的模具会归入这里" : "压力持续波动的模具显示在这里";
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
        addSectionTitle(titleText, "实时刷新");
    }

    private void addSectionTitle(String titleText, String hintText) {
        content.addView(createSectionTitleRow(titleText, hintText, false));
    }

    private LinearLayout createSectionTitleRow(String titleText, String hintText, boolean fixed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(fixed ? dp(14) : dp(2),
                fixed || currentTab == 1 || currentTab == 2 ? dp(6) : dp(14),
                fixed ? dp(14) : dp(2),
                dp(2));

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
        if (fixed && currentTab == 1) {
            fixedAlarmTitleView = title;
        }
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView hint = new TextView(this);
        hint.setText(hintText);
        hint.setTextSize(12);
        hint.setTextColor(MUTED);
        if (fixed && currentTab == 1) {
            fixedAlarmHintView = hint;
        }
        row.addView(hint);
        return row;
    }

    private void renderGatewayManagement(JSONArray rows) {
        updateFixedMouldGatewayCount(gatewayCountText(rows));
        if (rows == null || rows.length() == 0) {
            showEmpty("暂无网关数据");
            return;
        }
        List<JSONObject> sortedGateways = sortedGateways(rows);
        for (JSONObject gateway : sortedGateways) {
            content.addView(gatewayDisplayCard(gateway), topMargin(dp(10)));
        }
    }

    private List<JSONObject> sortedGateways(JSONArray rows) {
        List<JSONObject> online = new ArrayList<>();
        List<JSONObject> offline = new ArrayList<>();
        if (rows == null) {
            return online;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject gateway = rows.optJSONObject(i);
            if (gateway == null) {
                continue;
            }
            if ("在线".equals(gatewayOnlineStatusText(gateway))) {
                online.add(gateway);
            } else {
                offline.add(gateway);
            }
        }
        online.addAll(offline);
        return online;
    }

    private String gatewayCountText(JSONArray rows) {
        int total = rows == null ? 0 : rows.length();
        int online = 0;
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject gateway = rows.optJSONObject(i);
                if (gateway != null && "在线".equals(gatewayOnlineStatusText(gateway))) {
                    online++;
                }
            }
        }
        return "在线 " + online + " / 离线 " + Math.max(0, total - online);
    }

    private void renderPressureMoulds(JSONArray mouldRows, int requestVersion) {
        new ApiTask("GET", "/yujing/device/list?pageNum=1&pageSize=1000", null, true, result -> {
            if (requestVersion != listRequestVersion || currentTab != 2 || gatewayManagementMode) {
                return;
            }
            if (!result.ok) {
                updateFixedMouldGatewayCount("在线 - / 离线 -");
                showEmpty(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray devices = json.optJSONArray("rows");
                Map<String, Boolean> liveMouldIds = new HashMap<>();
                Map<String, Integer> sensorCountByMouldId = new HashMap<>();
                long now = System.currentTimeMillis();
                Map<String, Boolean> busyMouldIds = new HashMap<>();
                Map<String, Boolean> offlineMouldIds = new HashMap<>();
                for (int i = 0; i < mouldRows.length(); i++) {
                    JSONObject mould = mouldRows.optJSONObject(i);
                    if (mould == null) {
                        continue;
                    }
                    String mouldId = mould.optString("id");
                    String stateMouldId = mouldStateKey(mouldId);
                    Boolean previousBusy = mouldBusyById.get(stateMouldId);
                    Boolean busyState = mouldBusyStateByWebRule(mould);
                    if (busyState == null) {
                        busyState = previousBusy;
                    }
                    if (Boolean.TRUE.equals(busyState)) {
                        busyMouldIds.put(mouldId, true);
                        mouldBusyById.put(stateMouldId, true);
                    } else if (Boolean.FALSE.equals(busyState)) {
                        offlineMouldIds.put(mouldId, true);
                        if (Boolean.TRUE.equals(previousBusy)) {
                            activeMouldUntil.put(stateMouldId, now - 1);
                        }
                        mouldBusyById.put(stateMouldId, false);
                    }
                }
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
                            if (Boolean.TRUE.equals(busyMouldIds.get(mouldId))) {
                                updateStaticPressure(device, false);
                            } else if (Boolean.TRUE.equals(offlineMouldIds.get(mouldId)) || canCaptureStaticAfterMouldOffline(mouldId, now)) {
                                updateStaticPressure(device, true);
                            }
                        } else if (Boolean.TRUE.equals(offlineMouldIds.get(mouldId)) || canCaptureStaticAfterMouldOffline(mouldId, now)) {
                            updateStaticPressure(device, true);
                        }
                    }
                }
                int count = 0;
                Set<String> latestOfflineAlarmMouldIds = new HashSet<>();
                int onlineTotal = 0;
                int offlineTotal = 0;
                for (int i = 0; i < mouldRows.length(); i++) {
                    JSONObject mould = mouldRows.optJSONObject(i);
                    if (mould == null) {
                        continue;
                    }
                    String mouldId = mould.optString("id");
                    if (Boolean.TRUE.equals(busyMouldIds.get(mouldId))) {
                        onlineTotal++;
                    } else if (Boolean.TRUE.equals(offlineMouldIds.get(mouldId)) && liveMouldIds.containsKey(mouldId)) {
                        offlineTotal++;
                    }
                }
                updateFixedMouldGatewayCount("在线 " + onlineTotal + " / 离线 " + offlineTotal);
                List<JSONObject> displayMoulds = alarmFirstMoulds(mouldRows);
                for (int i = 0; i < displayMoulds.size(); i++) {
                    JSONObject mould = displayMoulds.get(i);
                    String mouldId = mould.optString("id");
                    boolean online = Boolean.TRUE.equals(busyMouldIds.get(mouldId));
                    boolean offline = Boolean.TRUE.equals(offlineMouldIds.get(mouldId)) && liveMouldIds.containsKey(mouldId);
                    if (offline && mouldId.length() > 0) {
                        latestOfflineAlarmMouldIds.add(mouldId);
                    }
                    if ((!offlineMouldMode && online) || (offlineMouldMode && offline)) {
                        mould.put("_dynamicOnline", online);
                        mould.put("_offlinePressure", offline);
                        mould.put("_sensorCount", sensorCountByMouldId.containsKey(mouldId) ? sensorCountByMouldId.get(mouldId) : 0);
                        content.addView(cardFor(mould), topMargin(dp(10)));
                        count++;
                    }
                }
                offlineAlarmMouldIds.clear();
                offlineAlarmMouldIds.addAll(latestOfflineAlarmMouldIds);
                saveOfflineAlarmMouldIds();
                if (count == 0) {
                    showEmpty(offlineMouldMode ? "暂无离线模具" : "暂无压力动态波动的模具");
                } else {
                    refreshHandler.post(this::refreshVisibleMouldPressureValues);
                }
            } catch (Exception e) {
                updateFixedMouldGatewayCount("在线 - / 离线 -");
                showEmpty("模具压力筛选失败");
            }
        }).execute();
    }

    private List<JSONObject> alarmFirstMoulds(JSONArray mouldRows) {
        List<JSONObject> list = new ArrayList<>();
        if (mouldRows == null) {
            return list;
        }
        for (int i = 0; i < mouldRows.length(); i++) {
            JSONObject mould = mouldRows.optJSONObject(i);
            if (mould != null) {
                list.add(mould);
            }
        }
        java.util.Collections.sort(list, (left, right) -> {
            boolean leftAlarm = mouldHasActiveAlarm(left);
            boolean rightAlarm = mouldHasActiveAlarm(right);
            if (leftAlarm == rightAlarm) {
                return 0;
            }
            return leftAlarm ? -1 : 1;
        });
        return list;
    }

    private boolean mouldHasActiveAlarm(JSONObject mould) {
        if (mould == null) {
            return false;
        }
        String mouldId = mould.optString("id");
        return mouldId.length() > 0 && activeAlarmMouldIds.contains(mouldId);
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

    private Boolean mouldBusyStateByWebRule(JSONObject mould) {
        if (mould == null) {
            return null;
        }
        // 模具中心可在专用生产状态缺失时回退到列表状态字段。
        Boolean state = parseMouldBusyState(firstValue(mould, "state", "mouldState", "mould_state"));
        return state != null ? state : parseMouldBusyState(firstValue(mould, "status"));
    }

    private Boolean nestedDeviceMouldBusyState(JSONObject mould) {
        if (mould == null) {
            return null;
        }
        // 设备内嵌的 status 常表示记录是否启用，不能用来判断模具是否生产。
        return parseMouldBusyState(firstValue(mould, "state", "mouldState", "mould_state"));
    }

    private Boolean parseMouldBusyState(String state) {
        if ("2".equals(state)) {
            return Boolean.TRUE;
        }
        if ("0".equals(state) || "1".equals(state)) {
            return Boolean.FALSE;
        }
        String text = state.toLowerCase();
        if (text.contains("busy")
                || text.contains("online")
                || text.contains("生产")
                || text.contains("忙")
                || text.contains("在线")) {
            return Boolean.TRUE;
        }
        if (text.contains("offline")
                || text.contains("idle")
                || text.contains("离线")
                || text.contains("停机")
                || text.contains("空闲")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean hasPressureFluctuation(JSONObject device) {
        Double pressure = numberValue(device.optString("pressure"));
        if (pressure == null) {
            return false;
        }
        String key = pressureStateKey(device);
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
        Long activeUntil = activeMouldUntil.get(mouldStateKey(mouldId));
        return activeUntil != null && activeUntil < now;
    }

    private void saveOfflineAlarmMouldIds() {
        StringBuilder builder = new StringBuilder();
        for (String id : offlineAlarmMouldIds) {
            if (id == null || id.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(id);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_OFFLINE_ALARM_MOULD_IDS, builder.toString())
                .apply();
    }

    private void updateStaticPressure(JSONObject device) {
        updateStaticPressure(device, true);
    }

    private void updateStaticPressure(JSONObject device, boolean allowOfflineCapture) {
        Double pressure = numberValue(device.optString("pressure"));
        String rawKey = deviceKey(device);
        String key = pressureStateKey(device);
        if (pressure == null || rawKey.length() == 0 || key.length() == 0) {
            return;
        }
        restoreStaticPressureLock(key);
        // 模具在线生产时只记录“见过动态压力”，不采集静止压力，避免动态压力把已锁定值顶掉。
        if (!allowOfflineCapture) {
            dynamicSeenByDevice.put(key, true);
            setStaticPressurePending(key, true);
            stableCandidatePressureByDevice.remove(key);
            stableSinceByDevice.remove(key);
            return;
        }
        // 已采集的静止压力保持锁定；只有经历过生产动态后再次下线，才开启下一轮静止采集。
        boolean pendingCapture = Boolean.TRUE.equals(dynamicSeenByDevice.get(key)) || staticPressurePending(key);
        if (Boolean.TRUE.equals(staticPressureCapturedByDevice.get(key))) {
            if (pendingCapture) {
                staticPressureCapturedByDevice.put(key, false);
                stableCandidatePressureByDevice.put(key, pressure);
                stableSinceByDevice.put(key, System.currentTimeMillis());
            }
            return;
        }
        // 已有静止压力且没有经历在线生产时保持锁定，禁止 App 重启后的首次刷新直接覆盖。
        if (existingStaticPressure(device) != null && !pendingCapture) {
            staticPressureCapturedByDevice.put(key, true);
            return;
        }
        // 下线后压力连续稳定达到阈值时间，才同步为新的静止压力。
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
            TextView standardView = visibleStandardViews.get(rawKey);
            if (standardView != null) {
                setStaticPressureViewText(standardView, pressureWithUnit(trimNumber(pressure)));
            }
        }
    }

    private void restoreStaticPressureLock(String key) {
        if (key == null || key.length() == 0 || staticPressureCapturedByDevice.containsKey(key)) {
            return;
        }
        Double value = staticPressureByDevice.get(key);
        if (value == null) {
            value = storedStaticPressure(key);
            if (value != null) {
                staticPressureByDevice.put(key, value);
            }
        }
        staticPressureCapturedByDevice.put(key, value != null);
    }

    private void setStaticPressureViewText(TextView view, String value) {
        String current = view.getText() == null ? "" : view.getText().toString();
        view.setText(current.startsWith("静止压力：") ? "静止压力：" + value : value);
    }

    private String staticPressureText(JSONObject device) {
        Double value = existingStaticPressure(device);
        if (value != null) {
            return trimNumber(value);
        }
        return "-";
    }

    private Double existingStaticPressure(JSONObject device) {
        String key = pressureStateKey(device);
        Double value = key.length() == 0 ? null : staticPressureByDevice.get(key);
        if (value != null) {
            return value;
        }
        value = key.length() == 0 ? null : storedStaticPressure(key);
        if (value != null) {
            staticPressureByDevice.put(key, value);
            return value;
        }
        return null;
    }

    private void saveStaticPressure(String key, Double pressure) {
        if (key == null || key.length() == 0 || pressure == null) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("static_pressure_" + key, trimNumber(pressure))
                .putBoolean("static_pressure_pending_" + key, false)
                .apply();
    }

    private boolean staticPressurePending(String key) {
        return key != null
                && key.length() > 0
                && getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean("static_pressure_pending_" + key, false);
    }

    private void setStaticPressurePending(String key, boolean pending) {
        if (key == null || key.length() == 0 || staticPressurePending(key) == pending) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("static_pressure_pending_" + key, pending)
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

    private String pressureStateKey(JSONObject device) {
        String key = deviceKey(device);
        if (key.length() == 0) {
            return "";
        }
        return accountScopePrefix() + key;
    }

    private String mouldStateKey(String mouldId) {
        return accountScopePrefix() + mouldId;
    }

    private String accountScopePrefix() {
        String account = currentAccountName();
        if (account.length() == 0) {
            account = "default";
        }
        return account.replaceAll("[^A-Za-z0-9_@.-]", "_") + "_";
    }

    private void resetPressureStateCaches() {
        lastPressureByDevice.clear();
        stableCandidatePressureByDevice.clear();
        stableSinceByDevice.clear();
        staticPressureByDevice.clear();
        staticPressureCapturedByDevice.clear();
        dynamicSeenByDevice.clear();
        activeMouldUntil.clear();
        mouldBusyById.clear();
        visiblePressureViews.clear();
        visibleStandardViews.clear();
        visibleUpdateViews.clear();
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
            addMetric(metrics, "实时压力", pressureWithUnit(item.optString("pressure")), 0xffeefcf5, 0xff07804d);
            addMetric(metrics, "静止压力", pressureWithUnit(staticPressureText(item)), 0xffedf5ff, BLUE);
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
                    if (isPressureField(key)) {
                        value = pressureWithUnit(value);
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
        card.setPadding(dp(10), dp(11), dp(8), dp(11));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 3);

        boolean offline = isSensorOffline(item);
        ImageView deviceIcon = new ImageView(this);
        deviceIcon.setImageResource(R.drawable.ic_sensor_device);
        deviceIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        deviceIcon.setAlpha(offline ? 0.48f : 1f);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(52));
        iconParams.rightMargin = dp(10);
        card.addView(deviceIcon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        String deviceNumber = firstNonEmpty(firstValue(item, "mac", "macAddress", "number"), primaryTitle(item));
        title.setText("MAC：" + deviceNumber);
        title.setTextSize(14);
        title.setTypeface(null, 1);
        title.setTextColor(INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView status = sensorStatusChip(item);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(dp(40), dp(21));
        statusParams.leftMargin = dp(6);
        titleRow.addView(status, statusParams);
        copy.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String mould = "-";
        JSONObject mouldObj = item.optJSONObject("mould");
        if (mouldObj != null) {
            mould = firstNonEmpty((mouldObj.optString("number") + " " + mouldObj.optString("name")).trim(), "-");
        }
        TextView sub = new TextView(this);
        String sensorName = firstNonEmpty(firstValue(item, "name", "deviceName"), primaryTitle(item));
        sub.setText("模具：" + mould + " · " + clean(sensorName));
        sub.setTextSize(11);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(3), 0, 0);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(sub);
        TextView meta = new TextView(this);
        meta.setText("电量：" + batteryText(item) + " · 信号：" + clean(firstValue(item, "rssi", "signal", "signalStrength")));
        meta.setTextSize(10);
        meta.setTextColor(0xff94a3b8);
        meta.setPadding(0, dp(3), 0, 0);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(meta);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout pressureBox = new LinearLayout(this);
        pressureBox.setOrientation(LinearLayout.VERTICAL);
        pressureBox.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText("实时压力");
        label.setTextSize(10);
        label.setTextColor(MUTED);
        label.setGravity(Gravity.RIGHT);
        pressureBox.addView(label);

        TextView pressure = new TextView(this);
        pressure.setText(pressureWithUnit(item.optString("pressure")));
        pressure.setTextSize(13);
        pressure.setTypeface(null, 1);
        pressure.setTextColor(offline ? 0xff94a3b8 : BLUE);
        pressure.setGravity(Gravity.RIGHT);
        pressure.setSingleLine(true);
        pressureBox.addView(pressure, topMargin(dp(2)));
        card.addView(pressureBox, new LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(0xff94a3b8);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(48)));
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
        card.setPadding(dp(12), dp(10), dp(12), expanded ? dp(10) : dp(9));
        card.setBackground(roundedStroke(SURFACE, 14, 0xffdfe8f2));
        smoothElevation(card, 2);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        boolean active = isActiveAlarm(item);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_alarm);
        icon.setColorFilter(active ? RED : GREEN);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(rounded(active ? 0xfffff1f2 : 0xffecfdf5, 11));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.rightMargin = dp(8);
        topRow.addView(icon, iconParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(alarmTitle(item));
        title.setTextSize(15);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        title.setSingleLine(false);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView mouldState = alarmMouldStateChip(item);
        LinearLayout.LayoutParams mouldStateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(21));
        mouldStateParams.leftMargin = dp(6);
        mouldStateParams.rightMargin = dp(6);
        head.addView(mouldState, mouldStateParams);

        TextView badge = badgeText(item);
        if (badge != null) {
            head.addView(badge);
        }
        TextView arrow = foldArrow(expanded);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        arrowParams.leftMargin = dp(6);
        head.addView(arrow, arrowParams);
        body.addView(head);

        LinearLayout detailContainer = new LinearLayout(this);
        detailContainer.setOrientation(LinearLayout.VERTICAL);
        detailContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        detailContainer.setLayoutTransition(null);
        final boolean[] detailLoaded = {expanded};
        final TextView[] summaryView = {null};
        if (expanded) {
            populateAlarmExpandedContent(detailContainer, item);
        } else {
            TextView summary = new TextView(this);
            summary.setText(alarmSummary(item));
            summary.setTextSize(11);
            summary.setTextColor(MUTED);
            summary.setPadding(0, dp(2), 0, 0);
            summaryView[0] = summary;
            body.addView(summary);
        }
        topRow.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(topRow);
        card.addView(detailContainer);

        card.setOnClickListener(v -> {
            boolean nextExpanded = detailContainer.getVisibility() != View.VISIBLE;
            detailContainer.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
            if (summaryView[0] != null) {
                summaryView[0].setVisibility(nextExpanded ? View.GONE : View.VISIBLE);
            }
            arrow.setText(nextExpanded ? "▲" : "▼");
            card.setPadding(dp(12), dp(10), dp(12), nextExpanded ? dp(10) : dp(9));
            if (nextExpanded) {
                if (!expandedAlarmIds.contains(key)) {
                    expandedAlarmIds.add(key);
                }
                if (!detailLoaded[0]) {
                    detailLoaded[0] = true;
                    populateAlarmExpandedContent(detailContainer, item);
                }
            } else {
                expandedAlarmIds.remove(key);
            }
        });
        return swipeDeleteAlarmRow(item, card);
    }

    private void populateAlarmExpandedContent(LinearLayout container, JSONObject item) {
        container.removeAllViews();
        addAlarmPressureDetails(container, item);

        LinearLayout info = infoPanel();
        addInfoItem(info, "模具状态", alarmMouldStateLabel(item));
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
            container.addView(info, topMargin(dp(6)));
        }

        TextView limit = compactAction("上下限修改");
        limit.setOnClickListener(v -> showAlarmLimitDialog(item));
        container.addView(limit, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(32), dp(6)));
    }

    private View swipeDeleteAlarmRow(JSONObject item, View card) {
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

        delete.setOnClickListener(v -> deleteAlarm(item, row));
        attachDeviceSwipe(card);
        return row;
    }

    private void deleteAlarm(JSONObject item) {
        deleteAlarm(item, null);
    }

    private void deleteAlarm(JSONObject item, View rowView) {
        String id = firstValue(item, "id", "alarmId", "warnId", "recordId");
        if (id.length() == 0) {
            toast("缺少告警ID，无法删除");
            if (openedDeviceSwipeCard != null) {
                openedDeviceSwipeCard.animate().translationX(0).setDuration(120).start();
                openedDeviceSwipeCard = null;
            }
            return;
        }
        setLoading(true);
        new ApiTask("DELETE", "/yujing/alarm/" + id, null, true, result -> {
            setLoading(false);
            openedDeviceSwipeCard = null;
            if (!result.ok) {
                toast(result.message);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                int code = json.optInt("code", 200);
                toast(code == 200 ? "告警已删除" : json.optString("msg", "删除失败"));
                if (code == 200) {
                    refreshAfterAlarmDeleted(rowView);
                }
            } catch (Exception e) {
                toast("告警已删除");
                refreshAfterAlarmDeleted(rowView);
            }
        }).execute();
    }

    private void refreshAfterAlarmDeleted(View rowView) {
        if (rowView != null && rowView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rowView.getParent()).removeView(rowView);
        }
        loadList(false);
        fetchAlarmCount(true);
    }

    private String alarmSummary(JSONObject item) {
        JSONArray details = alarmDetails(item);
        JSONObject sensor = details == null || details.length() == 0 ? null : details.optJSONObject(0);
        if (sensor != null) {
            return "传感器：" + sensorName(sensor) + "  压力：" + pressureWithUnit(sensor.optString("pressure"));
        }
        return "时间：" + clean(firstValue(item, "createTime", "create_time", "alarmTime"));
    }

    private View gatewayDisplayCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(roundedStroke(SURFACE, 16, 0xffdbe7f4));
        smoothElevation(card, 2);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_gateway_router);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(firstNonEmpty(item.optString("number"), primaryTitle(item)));
        title.setTextSize(15);
        title.setTextColor(INK);
        title.setTypeface(null, 1);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);
        head.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = gatewayStatusChip(item);
        head.addView(badge);
        body.addView(head);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(0, dp(4), 0, 0);
        addGatewayLine(info, "名称：", clean(firstValue(item, "name", "remark")));
        addGatewayLine(info, "组织：", gatewayDeptName(item));
        addGatewayLine(info, "当前产线：", gatewayMouldName(item), false);
        addGatewayLine(info, "更新时间：", clean(item.optString("updateTime")));
        body.addView(info);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(32), 0.075f));
        TextView changeMould = gatewayActionButton("更换模具");
        changeMould.setOnClickListener(v -> showGatewayMouldDialog(item));
        actions.addView(changeMould, new LinearLayout.LayoutParams(0, dp(32), 0.425f));
        TextView detail = gatewayActionButton("网关信息修改");
        detail.setOnClickListener(v -> showGatewayInfoEdit(item));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, dp(32), 0.425f);
        detailParams.leftMargin = dp(10);
        actions.addView(detail, detailParams);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(32), 0.075f));

        row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.addView(actions, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(32), dp(8)));
        return card;
    }

    private void addGatewayLine(LinearLayout parent, String label, String value) {
        addGatewayLine(parent, label, value, true);
    }

    private void addGatewayLine(LinearLayout parent, String label, String value, boolean singleLine) {
        TextView line = new TextView(this);
        line.setText(label + clean(value));
        line.setTextSize(12);
        line.setTextColor(0xff475569);
        if (singleLine) {
            line.setSingleLine(true);
            line.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            line.setSingleLine(false);
            line.setMaxLines(2);
            line.setEllipsize(null);
            line.setLineSpacing(dp(1), 1f);
        }
        parent.addView(line, topMargin(dp(1)));
    }

    private TextView gatewayActionButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(null, 1);
        button.setTextColor(BLUE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundedStroke(0xfff8fbff, 6, 0xff93c5fd));
        return button;
    }

    private TextView gatewayStatusChip(JSONObject item) {
        String status = gatewayOnlineStatusText(item);
        boolean online = "在线".equals(status);
        TextView badge = new TextView(this);
        badge.setText(status);
        badge.setTextSize(11);
        badge.setTypeface(null, 1);
        badge.setTextColor(online ? GREEN : 0xff64748b);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundedStroke(online ? 0xffecfdf5 : 0xfff1f5f9, 11, online ? 0xff86efac : 0xffd7dee8));
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        return badge;
    }

    private String gatewayOnlineStatusText(JSONObject gateway) {
        String raw = firstValue(gateway,
                "status", "onlineStatus", "online_status", "onlineState", "online_state",
                "netStatus", "net_status", "networkStatus", "network_status",
                "linkStatus", "link_status", "connectStatus", "connect_status",
                "isOnline", "online");
        if ("1".equals(raw) || "online".equalsIgnoreCase(raw) || "true".equalsIgnoreCase(raw) || "在线".equals(raw)) {
            return "在线";
        }
        return "离线";
    }

    private void addGatewaySummaryItem(LinearLayout row, String label, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(9);
        labelView.setTextColor(0xff94a3b8);
        item.addView(labelView);
        TextView valueView = new TextView(this);
        valueView.setText(clean(value));
        valueView.setTextSize(11);
        valueView.setTextColor(INK);
        valueView.setTypeface(null, 1);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(valueView, topMargin(dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.rightMargin = dp(8);
        row.addView(item, params);
    }

    private String gatewayDeptName(JSONObject item) {
        JSONObject dept = item.optJSONObject("dept");
        if (dept != null) {
            return firstNonEmpty(dept.optString("deptName"), "-");
        }
        return clean(firstValue(item, "deptName", "dept"));
    }

    private String gatewayMouldName(JSONObject item) {
        JSONObject mould = item.optJSONObject("mould");
        if (mould != null) {
            String name = firstValue(mould, "name", "mouldName");
            String number = firstValue(mould, "number", "mouldNumber");
            String title = (number + " " + name).trim();
            return clean(firstNonEmpty(title, firstNonEmpty(name, number)));
        }
        return clean(firstValue(item, "mouldName", "mould_name", "mouldNumber", "mould_number"));
    }

    private String compactTime(String value) {
        String text = clean(value);
        if (text.length() > 10 && text.contains(" ")) {
            return text.substring(5, Math.min(text.length(), 16));
        }
        return text;
    }

    private TextView foldArrow(boolean expanded) {
        TextView arrow = new TextView(this);
        arrow.setText(expanded ? "▲" : "▼");
        arrow.setTextSize(9);
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
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);

        FrameLayout topLine = new FrameLayout(this);

        ImageView icon = new ImageView(this);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        applyMouldAlarmIcon(icon, mouldId);
        if (mouldId.length() > 0) {
            visibleMouldAlarmIcons.put(mouldId, icon);
        }
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(34), dp(34));
        iconParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        topLine.addView(icon, iconParams);

        String number = clean(item.optString("number"));
        String name = clean(item.optString("name"));
        if (number.length() == 0 && name.length() == 0) {
            number = primaryTitle(item);
        }

        LinearLayout numberRow = new LinearLayout(this);
        numberRow.setOrientation(LinearLayout.HORIZONTAL);
        numberRow.setGravity(Gravity.CENTER_VERTICAL);
        numberRow.setPadding(dp(42), 0, dp(144), 0);

        TextView numberLabel = new TextView(this);
        numberLabel.setText("编号：");
        numberLabel.setTextSize(13);
        numberLabel.setTextColor(0xff64748b);
        numberLabel.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        numberRow.addView(numberLabel, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView numberValue = new TextView(this);
        numberValue.setText(number);
        numberValue.setTextSize(14);
        numberValue.setTextColor(INK);
        numberValue.setTypeface(null, 1);
        numberValue.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        numberValue.setSingleLine(true);
        numberValue.setEllipsize(TextUtils.TruncateAt.END);
        numberRow.addView(numberValue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        numberParams.gravity = Gravity.TOP | Gravity.LEFT;
        topLine.addView(numberRow, numberParams);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setPadding(dp(42), 0, dp(4), 0);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("名称：");
        nameLabel.setTextSize(13);
        nameLabel.setTextColor(0xff64748b);
        nameLabel.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameRow.addView(nameLabel, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView nameValue = new TextView(this);
        nameValue.setText(name);
        nameValue.setTextSize(14);
        nameValue.setTextColor(INK);
        nameValue.setTypeface(null, 1);
        nameValue.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameValue.setSingleLine(true);
        nameValue.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(nameValue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        nameParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        topLine.addView(nameRow, nameParams);

        LinearLayout rightMeta = new LinearLayout(this);
        rightMeta.setOrientation(LinearLayout.HORIZONTAL);
        rightMeta.setGravity(Gravity.CENTER_VERTICAL);

        TextView status = mouldStatusChip(item);
        LinearLayout statusSlot = new LinearLayout(this);
        statusSlot.setGravity(Gravity.CENTER);
        statusSlot.addView(status, new LinearLayout.LayoutParams(dp(40), dp(21)));
        rightMeta.addView(statusSlot, new LinearLayout.LayoutParams(dp(42), dp(28)));

        TextView count = new TextView(this);
        int sensorCount = item.optInt("_sensorCount", 0);
        count.setText(sensorCount + "个传感器");
        count.setTextSize(11);
        count.setTextColor(0xff475569);
        count.setGravity(Gravity.CENTER);
        count.setSingleLine(false);
        rightMeta.addView(count, new LinearLayout.LayoutParams(dp(62), dp(28)));

        TextView arrow = foldArrow(expanded);
        LinearLayout arrowSlot = new LinearLayout(this);
        arrowSlot.setGravity(Gravity.CENTER);
        arrowSlot.addView(arrow, new LinearLayout.LayoutParams(dp(26), dp(26)));
        rightMeta.addView(arrowSlot, new LinearLayout.LayoutParams(dp(28), dp(28)));

        FrameLayout.LayoutParams metaParams = new FrameLayout.LayoutParams(dp(132), dp(28));
        metaParams.gravity = Gravity.RIGHT | Gravity.TOP;
        topLine.addView(rightMeta, metaParams);

        head.addView(topLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        card.addView(head);

        LinearLayout detailContainer = new LinearLayout(this);
        detailContainer.setOrientation(LinearLayout.VERTICAL);
        detailContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        detailContainer.setLayoutTransition(null);
        card.addView(detailContainer);

        final boolean[] detailLoaded = {expanded};
        if (expanded) {
            addMouldSensorTable(detailContainer, item);
            addMouldExpandedActions(detailContainer, item, mouldId);
        }
        card.setOnClickListener(v -> {
            boolean nextExpanded = detailContainer.getVisibility() != View.VISIBLE;
            detailContainer.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
            arrow.setText(nextExpanded ? "▲" : "▼");
            card.setPadding(dp(12), dp(12), dp(12), nextExpanded ? dp(12) : dp(10));
            if (nextExpanded) {
                if (!expandedMouldIds.contains(mouldId)) {
                    expandedMouldIds.add(mouldId);
                }
                if (!detailLoaded[0]) {
                    detailLoaded[0] = true;
                    addMouldSensorTable(detailContainer, item);
                    addMouldExpandedActions(detailContainer, item, mouldId);
                }
            } else {
                expandedMouldIds.remove(mouldId);
            }
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
    }

    private TextView mouldStatusChip(JSONObject item) {
        TextView status = new TextView(this);
        boolean offline = item.optBoolean("_offlinePressure");
        status.setText(offline ? "离线" : "在线");
        status.setTextSize(10);
        status.setTypeface(null, 1);
        status.setTextColor(offline ? 0xffb45309 : 0xff059669);
        status.setGravity(Gravity.CENTER);
        status.setBackground(roundedStroke(offline ? 0xfffff7ed : 0xffecfdf5, 10, offline ? 0xfffdba74 : 0xff86efac));
        return status;
    }

    private void applyMouldAlarmIcon(ImageView icon, String mouldId) {
        boolean alarm = mouldId != null && activeAlarmMouldIds.contains(mouldId);
        icon.setImageResource(R.drawable.ic_alarm);
        icon.setColorFilter(alarm ? RED : GREEN);
        icon.setBackground(rounded(alarm ? 0xfffff1f2 : 0xffecfdf5, 11));
    }

    private void updateVisibleMouldAlarmBadges() {
        for (Map.Entry<String, ImageView> entry : visibleMouldAlarmIcons.entrySet()) {
            ImageView icon = entry.getValue();
            if (icon != null) {
                applyMouldAlarmIcon(icon, entry.getKey());
            }
        }
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
        addTableCell(header, "传感器", 2.25f, true, MUTED, Gravity.CENTER);
        addTableCell(header, "实时压力", 1.2f, true, MUTED, Gravity.CENTER);
        addTableCell(header, "静止压力", 1.2f, true, MUTED, Gravity.CENTER);
        addTableCell(header, "状态", 0.85f, true, MUTED, Gravity.CENTER);
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
                    addTableCell(row, name, 2.25f, false, INK, Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    TextView pressureView = addTableCell(row, pressureWithUnit(device.optString("pressure")), 1.2f, false, BLUE, Gravity.CENTER);
                    TextView standardView = addTableCell(row, pressureWithUnit(staticPressureText(device)), 1.2f, false, 0xff334155, Gravity.CENTER);
                    String stateText = sensorPressureState(device);
                    addTableCell(row, stateText, 0.85f, false, sensorStateColor(stateText), Gravity.CENTER);
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

    private TextView addTableCell(LinearLayout row, String text, float weight, boolean header, int color, int gravity) {
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

    private TextView mouldModeToggleButton() {
        TextView button = new TextView(this);
        button.setText(offlineMouldMode ? "在线模具" : "离线模具");
        button.setTextSize(13);
        button.setTypeface(null, 1);
        button.setTextColor(0xffffffff);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(gradient(BLUE, CYAN, 20));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setElevation(0);
            button.setStateListAnimator(null);
        }
        button.setOnClickListener(v -> {
            offlineMouldMode = !offlineMouldMode;
            expandedMouldIds.clear();
            showHome();
        });
        return button;
    }

    private TextView gatewayModeButton() {
        TextView button = new TextView(this);
        button.setText(gatewayManagementMode ? "模具监控" : "网关管理");
        button.setTextSize(13);
        button.setTypeface(null, 1);
        button.setTextColor(gatewayManagementMode ? BLUE : 0xffffffff);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(gatewayManagementMode ? roundedStroke(0xfff8fbff, 20, 0xffb9d7ff) : gradient(CYAN, BLUE, 20));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setElevation(0);
            button.setStateListAnimator(null);
        }
        button.setOnClickListener(v -> {
            gatewayManagementMode = !gatewayManagementMode;
            offlineMouldMode = false;
            expandedGatewayIds.clear();
            expandedMouldIds.clear();
            showHome();
        });
        return button;
    }

    private TextView gatewayRefreshButton() {
        TextView button = new TextView(this);
        button.setText("刷新网关");
        button.setTextSize(13);
        button.setTypeface(null, 1);
        button.setTextColor(0xffffffff);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(gradient(BLUE, CYAN, 20));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setElevation(0);
            button.setStateListAnimator(null);
        }
        button.setOnClickListener(v -> loadList(false));
        return button;
    }

    private void addMouldDropdown(LinearLayout card, JSONObject mould) {
        String mouldId = mould.optString("id");
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutTransition(null);
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
            JSONArray cachedRows = mouldDropdownDeviceCache.get(mouldId);
            if (cachedRows != null) {
                renderMouldDeviceRows(panel, cachedRows);
            } else {
                loadMouldDropdownDevices(mould, panel);
            }
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
        String id = mould.optString("id");
        if (id.length() == 0) {
            panel.removeAllViews();
            panel.addView(meta("缺少模具 ID"));
            return;
        }
        JSONArray cachedRows = mouldDropdownDeviceCache.get(id);
        if (cachedRows != null) {
            panel.removeAllViews();
            renderMouldDeviceRows(panel, cachedRows);
            return;
        }
        panel.removeAllViews();
        panel.addView(meta("正在加载绑定设备和压力..."));
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
                if (rows != null) {
                    mouldDropdownDeviceCache.put(id, rows);
                }
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
            TextView pressureValue = addMetric(metrics, "实时", pressureWithUnit(device.optString("pressure")), 0xffeefcf5, 0xff07804d);
            addMetric(metrics, "上限", pressureWithUnit(device.optString("upper")), 0xfffff1f2, 0xffdc2626);
            addMetric(metrics, "下限", pressureWithUnit(device.optString("lower")), 0xfffff8e5, 0xffa15c07);
            if (metrics.getChildCount() > 0) {
                row.addView(metrics);
            }

            TextView standardView = meta("静止压力：" + pressureWithUnit(staticPressureText(device)));
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
        form.addView(label("报警下限 (" + pressureUnitLabel() + ")"));
        form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("报警上限 (" + pressureUnitLabel() + ")"));
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
        lower.setText(pressureInputValue(device.optString("lower")));
        upper.setText(pressureInputValue(device.optString("upper")));
        pressure.setText("实时压力：" + pressureWithUnit(device.optString("pressure")));
        standard.setText("静止压力：" + pressureWithUnit(staticPressureText(device)));
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
        String pressure = pressureWithUnit(device.optString("pressure"));
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
            body.put("lower", pressureInputToStorageValue(lower));
            body.put("upper", pressureInputToStorageValue(upper));
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
                    JSONObject mouldObj = device.optJSONObject("mould");
                    String mouldKey = mouldStateKey(mouldId);
                    Boolean previousBusy = mouldId.length() == 0 ? null : mouldBusyById.get(mouldKey);
                    Boolean refreshedBusy = nestedDeviceMouldBusyState(mouldObj);
                    Boolean currentBusy = refreshedBusy == null ? previousBusy : refreshedBusy;
                    if (mouldId.length() > 0 && refreshedBusy != null) {
                        if (Boolean.FALSE.equals(refreshedBusy) && Boolean.TRUE.equals(previousBusy)) {
                            activeMouldUntil.put(mouldKey, now - 1);
                        }
                        mouldBusyById.put(mouldKey, refreshedBusy);
                    }
                    boolean busyMould = Boolean.TRUE.equals(currentBusy);
                    boolean offlineMould = Boolean.FALSE.equals(currentBusy);
                    if (mouldId.length() > 0 && hasLivePressure(device)) {
                        if (busyMould) {
                            updateStaticPressure(device, false);
                        } else if (offlineMould || canCaptureStaticAfterMouldOffline(mouldId, now)) {
                            updateStaticPressure(device, true);
                        }
                    } else if (offlineMould || canCaptureStaticAfterMouldOffline(mouldId, now)) {
                        updateStaticPressure(device, true);
                    }
                    String key = deviceKey(device);
                    if (key.length() == 0) {
                        continue;
                    }
                    TextView pressureView = visiblePressureViews.get(key);
                    if (pressureView != null) {
                        pressureView.setText(pressureWithUnit(device.optString("pressure")));
                    }
                    TextView standardView = visibleStandardViews.get(key);
                    if (standardView != null) {
                        setStaticPressureViewText(standardView, pressureWithUnit(staticPressureText(device)));
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
        visibleMouldAlarmIcons.clear();
    }

    private void addAlarmPressureDetails(LinearLayout card, JSONObject alarm) {
        JSONArray details = alarmDetails(alarm);
        if (details == null || details.length() == 0) {
            String detailText = alarm.optString("detail");
            if (detailText.length() > 0 && !"null".equals(detailText)) {
                TextView detail = meta("报警详情：" + detailText);
                detail.setBackground(roundedStroke(0xfffbfdff, 12, 0xffe3ebf5));
                detail.setPadding(dp(10), dp(7), dp(10), dp(7));
                card.addView(detail, topMargin(dp(6)));
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
            row.setBackground(roundedStroke(0xfffbfdff, 12, 0xffe3ebf5));

            TextView title = new TextView(this);
            title.setText(sensorName(sensor));
            title.setTextSize(12);
            title.setTextColor(INK);
            title.setTypeface(null, 1);
            row.addView(title);

            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.setPadding(0, dp(6), 0, 0);
            addCompactMetric(metrics, "压力", pressureWithUnit(sensor.optString("pressure")), 0xfffff1f2, RED);
            addCompactMetric(metrics, "下限", pressureWithUnit(sensor.optString("lower")), 0xfffffbeb, 0xffb45309);
            addCompactMetric(metrics, "上限", pressureWithUnit(sensor.optString("upper")), 0xfffff1f2, 0xffdc2626);
            if (metrics.getChildCount() > 0) {
                row.addView(metrics);
            }
            String time = clean(firstValue(sensor, "create_time", "createTime"));
            if (!"-".equals(time)) {
                TextView timeView = meta("告警时间 " + time);
                timeView.setTextSize(11);
                row.addView(timeView, topMargin(dp(1)));
            }

            LinearLayout.LayoutParams params = topMargin(dp(6));
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

    private void showAlarmLimitDialog(JSONObject alarm) {
        List<JSONObject> sensors = alarmSensors(alarm);
        if (sensors.isEmpty()) {
            toast("未找到报警传感器");
            return;
        }
        // 多传感器告警先按模具拉取完整设备信息，再与告警详情匹配，保证保存上下限时带上设备 ID。
        String mouldId = alarmMouldId(alarm);
        if (mouldId.length() == 0) {
            showAlarmLimitSelector(alarm, sensors);
            return;
        }
        setLoading(true);
        String endpoint = "/yujing/device/list?pageNum=1&pageSize=200&mouldId=" + mouldId;
        new ApiTask("GET", endpoint, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                showAlarmLimitSelector(alarm, sensors);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONArray rows = json.optJSONArray("rows");
                showAlarmLimitSelector(alarm, resolveAlarmSensors(sensors, rows));
            } catch (Exception e) {
                showAlarmLimitSelector(alarm, sensors);
            }
        }).execute();
    }

    private List<JSONObject> alarmSensors(JSONObject alarm) {
        List<JSONObject> sensors = new ArrayList<>();
        JSONArray details = alarmDetails(alarm);
        if (details == null || details.length() == 0) {
            return sensors;
        }
        for (int i = 0; i < details.length(); i++) {
            JSONObject sensor = details.optJSONObject(i);
            if (sensor != null) {
                sensors.add(sensor);
            }
        }
        return sensors;
    }

    private List<JSONObject> resolveAlarmSensors(List<JSONObject> sensors, JSONArray rows) {
        List<JSONObject> resolved = new ArrayList<>();
        for (JSONObject sensor : sensors) {
            JSONObject matched = matchingAlarmDevice(rows, sensor);
            resolved.add(matched == null ? sensor : mergeAlarmSensor(sensor, matched));
        }
        return resolved;
    }

    private JSONObject mergeAlarmSensor(JSONObject alarmSensor, JSONObject device) {
        try {
            JSONObject merged = new JSONObject(device.toString());
            // 告警详情里的压力/上下限是触发时刻数据，优先展示；设备对象负责提供保存接口需要的完整字段。
            String pressure = firstValue(alarmSensor, "pressure", "realPressure", "value");
            if (pressure.length() > 0) {
                merged.put("pressure", pressure);
            }
            String lower = firstValue(alarmSensor, "lower", "lowerLimit", "downLimit");
            if (lower.length() > 0) {
                merged.put("lower", lower);
            }
            String upper = firstValue(alarmSensor, "upper", "upperLimit", "upLimit");
            if (upper.length() > 0) {
                merged.put("upper", upper);
            }
            return merged;
        } catch (Exception ignored) {
            return device;
        }
    }

    private void showAlarmLimitSelector(JSONObject alarm, List<JSONObject> sensors) {
        if (sensors.size() == 1) {
            openAlarmSensorLimit(sensors.get(0));
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), dp(8));

        List<OptionItem> options = new ArrayList<>();
        for (int i = 0; i < sensors.size(); i++) {
            JSONObject sensor = sensors.get(i);
            String value = firstNonEmpty(deviceKey(sensor), "alarm_sensor_" + i);
            options.add(new OptionItem(value, sensorLabel(sensor)));
        }

        Spinner sensorSpinner = spinner(options, options.get(0).value);
        EditText lower = input("报警下限", false);
        EditText upper = input("报警上限", false);
        TextView pressure = meta("实时压力：-");
        TextView standard = meta("静止压力：-");
        TextView battery = meta("电池电量：-");

        form.addView(label("报警传感器"));
        form.addView(sensorSpinner, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        form.addView(pressure, topMargin(dp(8)));
        form.addView(standard, topMargin(dp(2)));
        form.addView(battery, topMargin(dp(2)));
        form.addView(label("报警下限 (" + pressureUnitLabel() + ")"));
        form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("报警上限 (" + pressureUnitLabel() + ")"));
        form.addView(upper, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));

        sensorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                JSONObject sensor = position >= 0 && position < sensors.size() ? sensors.get(position) : null;
                fillMouldLimitSensorFields(sensor, lower, upper, pressure, standard, battery);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        fillMouldLimitSensorFields(sensors.get(0), lower, upper, pressure, standard, battery);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("告警传感器 - 上下限修改")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialogInterface, which) -> {
                    int position = sensorSpinner.getSelectedItemPosition();
                    JSONObject sensor = position >= 0 && position < sensors.size() ? sensors.get(position) : null;
                    if (sensor == null) {
                        toast("请选择要修改的传感器");
                        return;
                    }
                    saveDeviceLimits(sensor, lower.getText().toString().trim(), upper.getText().toString().trim());
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

    private void openAlarmSensorLimit(JSONObject sensor) {
        if (firstValue(sensor, "id", "deviceId", "sensorId").length() > 0) {
            showDeviceLimitDialog(sensor);
            return;
        }
        String keyword = firstValue(sensor, "number", "deviceNumber", "mac", "macAddress", "name", "deviceName");
        if (keyword.length() == 0) {
            showDeviceLimitDialog(sensor);
            return;
        }
        setLoading(true);
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            new ApiTask("GET", "/yujing/device/list?pageNum=1&pageSize=100&number=" + encoded + "&mac=" + encoded + "&macAddress=" + encoded, null, true, result -> {
                setLoading(false);
                if (!result.ok) {
                    showDeviceLimitDialog(sensor);
                    return;
                }
                try {
                    JSONObject json = new JSONObject(result.body);
                    JSONArray rows = json.optJSONArray("rows");
                    JSONObject matched = matchingAlarmDevice(rows, sensor);
                    showDeviceLimitDialog(matched == null ? sensor : mergeAlarmSensor(sensor, matched));
                } catch (Exception e) {
                    showDeviceLimitDialog(sensor);
                }
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            showDeviceLimitDialog(sensor);
        }
    }

    private JSONObject firstMatchingAlarmDevice(JSONArray rows, String keyword) {
        if (rows == null || rows.length() == 0) {
            return null;
        }
        String needle = keyword == null ? "" : keyword.toLowerCase();
        JSONObject fallback = rows.optJSONObject(0);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject device = rows.optJSONObject(i);
            if (device == null) {
                continue;
            }
            String identity = (device.optString("id") + " "
                    + device.optString("number") + " "
                    + device.optString("mac") + " "
                    + device.optString("macAddress") + " "
                    + device.optString("name")).toLowerCase();
            if (needle.length() == 0 || identity.contains(needle)) {
                return device;
            }
        }
        return fallback;
    }

    private JSONObject matchingAlarmDevice(JSONArray rows, JSONObject sensor) {
        if (rows == null || rows.length() == 0 || sensor == null) {
            return null;
        }
        String id = firstValue(sensor, "id", "deviceId", "sensorId");
        String number = firstValue(sensor, "number", "deviceNumber", "mac", "macAddress");
        String name = firstValue(sensor, "name", "deviceName");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject device = rows.optJSONObject(i);
            if (device == null) {
                continue;
            }
            if (id.length() > 0 && id.equals(firstValue(device, "id", "deviceId", "sensorId"))) {
                return device;
            }
            String deviceNumber = firstValue(device, "number", "deviceNumber", "mac", "macAddress");
            if (number.length() > 0 && number.equalsIgnoreCase(deviceNumber)) {
                return device;
            }
            String deviceName = firstValue(device, "name", "deviceName");
            if (name.length() > 0 && name.equalsIgnoreCase(deviceName)) {
                return device;
            }
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

    private void showGatewayDetail(JSONObject item) {
        String id = item.optString("id");
        if (id.length() == 0) {
            showGatewayDetailDialog(item);
            return;
        }
        setLoading(true);
        new ApiTask("GET", "/yujing/gateway/" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                showGatewayDetailDialog(item);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONObject data = json.optJSONObject("data");
                showGatewayDetailDialog(data != null ? data : item);
            } catch (Exception e) {
                showGatewayDetailDialog(item);
            }
        }).execute();
    }

    private void showGatewayMouldDialog(JSONObject gateway) {
        String id = gateway.optString("id");
        if (id.length() == 0) {
            loadGatewayMouldOptions(gateway);
            return;
        }
        setLoading(true);
        new ApiTask("GET", "/yujing/gateway/" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                loadGatewayMouldOptions(gateway);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONObject data = json.optJSONObject("data");
                loadGatewayMouldOptions(data != null ? data : gateway);
            } catch (Exception e) {
                loadGatewayMouldOptions(gateway);
            }
        }).execute();
    }

    private void showGatewayInfoEdit(JSONObject gateway) {
        String id = gateway.optString("id");
        if (id.length() == 0) {
            loadGatewayInfoOptions(gateway);
            return;
        }
        setLoading(true);
        new ApiTask("GET", "/yujing/gateway/" + id, null, true, result -> {
            setLoading(false);
            if (!result.ok) {
                loadGatewayInfoOptions(gateway);
                return;
            }
            try {
                JSONObject json = new JSONObject(result.body);
                JSONObject data = json.optJSONObject("data");
                loadGatewayInfoOptions(data != null ? data : gateway);
            } catch (Exception e) {
                loadGatewayInfoOptions(gateway);
            }
        }).execute();
    }

    private void loadGatewayInfoOptions(JSONObject gateway) {
        setLoading(true);
        new OptionsTask(gateway, options -> {
            setLoading(false);
            showGatewayInfoEditor(gateway, options);
        }).execute();
    }

    private void showGatewayInfoEditor(JSONObject gateway, OptionsState options) {
        applyGatewayMouldPlaceholder(options);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(4), dp(14), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Spinner dept = spinner(options.depts, gatewayDeptId(gateway));
        form.addView(label("组织"));
        form.addView(dept, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(2)));
        Spinner mould = spinner(options.moulds, gatewayMouldId(gateway));
        form.addView(label("注意：模具需要绑定才能通过网关发出声光报警！"));
        form.addView(mould, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        EditText number = input("网关编号", false);
        number.setText(gateway.optString("number"));
        form.addView(label("网关编号"));
        form.addView(number, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        EditText name = input("名称", false);
        name.setText(gateway.optString("name"));
        form.addView(label("名称"));
        form.addView(name, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        EditText longitude = input("经度", false);
        longitude.setText(gateway.optString("longitude"));
        form.addView(label("经度"));
        form.addView(longitude, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        EditText latitude = input("纬度", false);
        latitude.setText(gateway.optString("latitude"));
        form.addView(label("纬度"));
        form.addView(latitude, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        Spinner pointState = gatewayPointStateSpinner(gateway);
        form.addView(label("定位开关"));
        form.addView(pointState, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        EditText remark = input("备注", false);
        remark.setText(gateway.optString("remark"));
        form.addView(label("备注"));
        form.addView(remark, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("网关信息修改")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> saveGatewayInfo(gateway, dept, mould, number, name, longitude, latitude, pointState, remark))
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

    private void saveGatewayInfo(JSONObject gateway, Spinner dept, Spinner mould, EditText number, EditText name, EditText longitude, EditText latitude, Spinner pointState, EditText remark) {
        setLoading(true);
        try {
            JSONObject body = new JSONObject(gateway.toString());
            body.put("number", number.getText().toString().trim());
            body.put("name", name.getText().toString().trim());
            body.put("remark", remark.getText().toString().trim());
            putSelected(body, "deptId", dept);
            putSelected(body, "mouldId", mould);
            putText(body, "longitude", longitude);
            putText(body, "latitude", latitude);
            putSelectedInt(body, "pointState", pointState);
            new ApiTask("PUT", "/yujing/gateway", body.toString(), true, result -> {
                setLoading(false);
                if (!result.ok) {
                    toast(result.message);
                    return;
                }
                toast("网关信息已保存");
                loadList(false);
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            toast("保存内容创建失败");
        }
    }

    private void loadGatewayMouldOptions(JSONObject gateway) {
        setLoading(true);
        new OptionsTask(gateway, options -> {
            setLoading(false);
            showGatewayMouldEditor(gateway, options);
        }).execute();
    }

    private void showGatewayMouldEditor(JSONObject gateway, OptionsState options) {
        applyGatewayMouldPlaceholder(options);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), dp(8));

        form.addView(meta("网关：" + primaryTitle(gateway)));
        Spinner mould = spinner(options.moulds, gatewayMouldId(gateway));
        form.addView(label("注意：模具需要绑定才能通过网关发出声光报警！"));
        form.addView(mould, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("更换网关绑定模具")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> saveGatewayMould(gateway, mould))
                .create();
        showStyledDialog(dialog);
    }

    private void saveGatewayMould(JSONObject gateway, Spinner mould) {
        setLoading(true);
        try {
            JSONObject body = new JSONObject(gateway.toString());
            putSelected(body, "mouldId", mould);
            new ApiTask("PUT", "/yujing/gateway", body.toString(), true, result -> {
                setLoading(false);
                if (!result.ok) {
                    toast(result.message);
                    return;
                }
                try {
                    JSONObject json = new JSONObject(result.body);
                    int code = json.optInt("code", 200);
                    if (code == 200) {
                        toast("网关绑定模具已更新");
                        loadList(false);
                    } else {
                        toast(json.optString("msg", "保存失败"));
                    }
                } catch (Exception e) {
                    toast("网关绑定模具已更新");
                    loadList(false);
                }
            }).execute();
        } catch (Exception e) {
            setLoading(false);
            toast("保存内容创建失败");
        }
    }

    private String gatewayMouldId(JSONObject gateway) {
        String mouldId = valueOf(gateway, "mouldId");
        if (mouldId.length() > 0) {
            return mouldId;
        }
        JSONObject mould = gateway == null ? null : gateway.optJSONObject("mould");
        return mould == null ? "" : mould.optString("id");
    }

    private void applyGatewayMouldPlaceholder(OptionsState options) {
        if (options != null && options.moulds.size() > 0 && options.moulds.get(0).value.length() == 0) {
            options.moulds.get(0).label = "请绑定要上线的模具";
        }
    }

    private String gatewayDeptId(JSONObject gateway) {
        String deptId = valueOf(gateway, "deptId");
        if (deptId.length() > 0) {
            return deptId;
        }
        JSONObject dept = gateway == null ? null : gateway.optJSONObject("dept");
        return dept == null ? "" : dept.optString("id");
    }

    private void showGatewayDetailDialog(JSONObject item) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("网关详情")
                .setMessage(prettyObject(item))
                .setPositiveButton("关闭", null)
                .create();
        showStyledDialog(dialog);
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

        TextView pressure = meta("实时压力：" + pressureWithUnit(device.optString("pressure")));
        TextView standard = meta("静止压力：" + pressureWithUnit(staticPressureText(device)));
        EditText lower = input("报警下限", false);
        lower.setText(pressureInputValue(device.optString("lower")));
        EditText upper = input("报警上限", false);
        upper.setText(pressureInputValue(device.optString("upper")));

        form.addView(pressure);
        form.addView(standard, topMargin(dp(2)));
        form.addView(label("报警下限 (" + pressureUnitLabel() + ")"));
        form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(4)));
        form.addView(label("报警上限 (" + pressureUnitLabel() + ")"));
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
            body.put("lower", pressureInputToStorageValue(lower));
            body.put("upper", pressureInputToStorageValue(upper));
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
            standard.setText(source == null ? "" : pressureInputValue(source.optString("standard")));
            form.addView(label("标准压力 (" + pressureUnitLabel() + ")"));
            form.addView(standard, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            lower = input("下限值", false);
            lower.setText(source == null ? "" : pressureInputValue(source.optString("lower")));
            form.addView(label("报警下限 (" + pressureUnitLabel() + ")"));
            form.addView(lower, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            upper = input("上限值", false);
            upper.setText(source == null ? "" : pressureInputValue(source.optString("upper")));
            form.addView(label("报警上限 (" + pressureUnitLabel() + ")"));
            form.addView(upper, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
        }

        EditText longitude = null;
        EditText latitude = null;
        Spinner pointState = null;
        if (currentTab == 2) {
            longitude = input("经度", false);
            longitude.setText(source == null ? "" : source.optString("longitude"));
            form.addView(label("经度"));
            form.addView(longitude, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            latitude = input("纬度", false);
            latitude.setText(source == null ? "" : source.optString("latitude"));
            form.addView(label("纬度"));
            form.addView(latitude, fixedTop(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), dp(6)));
            pointState = gatewayPointStateSpinner(source);
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
        final Spinner pointStateInput = pointState;
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
                        putPressureText(body, "standard", standardInput);
                        putPressureText(body, "lower", lowerInput);
                        putPressureText(body, "upper", upperInput);
                        putText(body, "longitude", longitudeInput);
                        putText(body, "latitude", latitudeInput);
                        putSelectedInt(body, "pointState", pointStateInput);
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
                if (isPressureField(key)) {
                    value = pressureWithUnit(value);
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
        panel.setPadding(dp(10), dp(7), dp(10), dp(7));
        panel.setBackground(roundedStroke(0xfff8fbff, 12, 0xffe3ebf5));
        return panel;
    }

    private void addInfoItem(LinearLayout panel, String label, String value) {
        if (value == null || value.length() == 0 || "null".equals(value)) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(0xff7b8795);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(12);
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

    private TextView addCompactMetric(LinearLayout parent, String label, String value, int bgColor, int textColor) {
        if (value == null || value.length() == 0 || "null".equals(value)) {
            return null;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(7), dp(6), dp(7), dp(6));
        box.setBackground(roundedStroke(bgColor, 10, 0x12000000));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(textColor);
        valueView.setTextSize(12);
        valueView.setTypeface(null, 1);
        valueView.setGravity(Gravity.CENTER);
        valueView.setSingleLine(false);
        box.addView(valueView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xff69778a);
        labelView.setTextSize(9);
        labelView.setGravity(Gravity.CENTER);
        box.addView(labelView, topMargin(dp(1)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.rightMargin = dp(6);
        parent.addView(box, params);
        return valueView;
    }

    private TextView badgeText(JSONObject item) {
        if (currentTab == 3 && (item.optBoolean("_dynamicOnline") || item.optBoolean("_offlinePressure"))) {
            TextView badge = new TextView(this);
            boolean offline = item.optBoolean("_offlinePressure");
            badge.setText(offline ? "离线" : "在线");
            badge.setTextSize(11);
            badge.setTextColor(offline ? 0xff8a5b00 : 0xff047857);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            badge.setBackground(roundedStroke(offline ? 0xfffff8e5 : 0xffecfdf5, 10, offline ? 0xfff3d77a : 0xffa7f3d0));
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
                badge.setTextSize(11);
                badge.setTextColor(cleared ? 0xff0f766e : 0xffdc2626);
                badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                badge.setBackground(roundedStroke(cleared ? 0xffecfdf5 : 0xfffff1f2, 10, cleared ? 0xffa7f3d0 : 0xffffb4bf));
                return badge;
            }
        }
        if (currentTab == 0) {
            String text = sensorOnlineStatusText(item);
            boolean offline = isSensorOffline(item);
            TextView badge = new TextView(this);
            badge.setText(text);
            badge.setTextSize(11);
            badge.setTextColor(offline ? 0xff92400e : 0xff0f766e);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            badge.setBackground(roundedStroke(offline ? 0xfffff8e5 : 0xffecfdf5, 10, offline ? 0xfff3d77a : 0xffa7f3d0));
            return badge;
        }
        if (currentTab == 2 && status.length() > 0 && !"null".equals(status)) {
            String text = onlineStatusText(status);
            boolean offline = isOfflineStatus(status);
            TextView badge = new TextView(this);
            badge.setText(text);
            badge.setTextSize(11);
            badge.setTextColor(offline ? 0xff92400e : 0xff0f766e);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            badge.setBackground(roundedStroke(offline ? 0xfffff8e5 : 0xffecfdf5, 10, offline ? 0xfff3d77a : 0xffa7f3d0));
            return badge;
        }
        String text = status.length() > 0 ? "在线 " + status : state.length() > 0 ? "状态 " + state : "";
        if (text.length() == 0 || "null".equals(text)) {
            return null;
        }
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(11);
        badge.setTextColor(0xff0f766e);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        badge.setBackground(roundedStroke(0xffecfdf5, 10, 0xffa7f3d0));
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
        migrateLegacyMonitoredMacs(currentAccountName());
        List<String> result = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(monitoredMacsKey(), "");
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
                .putString(monitoredMacsKey(), joinList(macs))
                .apply();
    }

    private void migrateLegacyMonitoredMacs(String username) {
        if (username == null || username.trim().length() == 0) {
            return;
        }
        String legacy = getSharedPreferences(PREFS, MODE_PRIVATE).getString("monitor_macs", "");
        if (legacy == null || legacy.trim().length() == 0) {
            return;
        }
        String accountKey = monitoredMacsKey(username);
        String existing = getSharedPreferences(PREFS, MODE_PRIVATE).getString(accountKey, "");
        if (existing == null || existing.trim().length() == 0) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(accountKey, legacy)
                    .remove("monitor_macs")
                    .apply();
        }
    }

    private String monitoredMacsKey() {
        return monitoredMacsKey(currentAccountName());
    }

    private String monitoredMacsKey(String username) {
        String account = username == null ? "" : username.trim();
        if (account.length() == 0) {
            return "monitor_macs_guest";
        }
        String safe = account.replaceAll("[^A-Za-z0-9_@.-]", "_");
        return "monitor_macs_" + safe + "_" + Integer.toHexString(account.hashCode());
    }

    private String currentAccountName() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString("saved_username", "").trim();
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
        chip.setTextSize(10);
        chip.setTypeface(null, 1);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(offline || unknown ? 0xff64748b : 0xff059669);
        chip.setBackground(roundedStroke(offline || unknown ? 0xfff1f5f9 : 0xffecfdf5, 10, offline || unknown ? 0xffcbd5e1 : 0xff86efac));
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
        spinner.setBackground(roundedStroke(0xfffbfdff, 12, 0xffd8e4f1));
        spinner.setPadding(dp(10), 0, dp(8), 0);
        spinner.setPopupBackgroundDrawable(roundedStroke(0xffffffff, 14, 0xffd8e4f1));
        spinner.setDropDownVerticalOffset(dp(4));
        ArrayAdapter<OptionItem> adapter = new ArrayAdapter<OptionItem>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return spinnerSelectedView(items, position);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return spinnerText(items, position, true);
            }
        };
        spinner.setAdapter(adapter);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).value.equals(selectedValue)) {
                spinner.setSelection(i);
                break;
            }
        }
        return spinner;
    }

    private Spinner gatewayPointStateSpinner(JSONObject gateway) {
        List<OptionItem> states = new ArrayList<>();
        String selectedValue = gatewayPointStateValue(gateway);
        if (selectedValue.length() == 0) {
            states.add(new OptionItem("", "未设置"));
        }
        states.add(new OptionItem("0", "关闭"));
        states.add(new OptionItem("1", "开启"));
        return spinner(states, selectedValue);
    }

    private String gatewayPointStateValue(JSONObject gateway) {
        if (gateway == null || !gateway.has("pointState") || gateway.isNull("pointState")) {
            return "";
        }
        Object value = gateway.opt("pointState");
        if (value instanceof Number) {
            int state = ((Number) value).intValue();
            return state == 1 ? "1" : state == 0 ? "0" : "";
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return "1";
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return "0";
        }
        return "";
    }

    private View spinnerSelectedView(List<OptionItem> items, int position) {
        OptionItem item = position >= 0 && position < items.size() ? items.get(position) : null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), 0, dp(2), 0);
        row.setBackgroundColor(0x00ffffff);

        TextView label = new TextView(this);
        label.setText(item == null ? "" : item.toString());
        label.setTextSize(12);
        label.setTextColor(INK);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextSize(24);
        arrow.setTypeface(null, 1);
        arrow.setTextColor(0xff64748b);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(36)
        ));
        return row;
    }

    private TextView spinnerText(List<OptionItem> items, int position, boolean dropdown) {
        OptionItem item = position >= 0 && position < items.size() ? items.get(position) : null;
        String text = item == null ? "" : item.toString();
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(dropdown ? 13 : 12);
        view.setTextColor(INK);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(dropdown ? 14 : 2), 0, dp(dropdown ? 14 : 20), 0);
        if (dropdown) {
            if (items.size() <= 1) {
                view.setBackground(roundedStroke(0xffffffff, 12, 0x00ffffff));
            } else if (position == 0) {
                view.setBackground(roundedTop(0xffffffff, 12));
            } else if (position == items.size() - 1) {
                view.setBackground(roundedBottom(0xffffffff, 12));
            } else {
                view.setBackgroundColor(0xffffffff);
            }
        } else {
            view.setBackgroundColor(0x00ffffff);
        }
        view.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(dropdown ? 44 : 36)
        ));
        return view;
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

    private void putSelectedInt(JSONObject body, String key, Spinner spinner) throws Exception {
        if (spinner == null) {
            return;
        }
        Object selected = spinner.getSelectedItem();
        if (selected instanceof OptionItem) {
            OptionItem item = (OptionItem) selected;
            if (item.value.length() > 0) {
                body.put(key, Integer.parseInt(item.value));
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

    private void putPressureText(JSONObject body, String key, EditText input) throws Exception {
        if (input == null) {
            return;
        }
        String value = pressureInputToStorageValue(input.getText().toString().trim());
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

    private GradientDrawable roundedTop(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(radiusDp);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable roundedBottom(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(radiusDp);
        drawable.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        return drawable;
    }

    private void setLoading(boolean show) {
        if (loading == null) {
            return;
        }
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showStyledDialog(AlertDialog dialog) {
        if (dialog == null || !isActivityUsable()) {
            return;
        }
        try {
            dialog.show();
        } catch (RuntimeException ignored) {
            return;
        }
        styleDialogWindow(dialog);
        styleDialogButtons(dialog);
    }

    private boolean isActivityUsable() {
        return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
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
        if (!isActivityUsable()) {
            return;
        }
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
        private final boolean gatewayOptions;

        OptionsTask(JSONObject source, OptionsCallback callback) {
            this.source = source;
            this.callback = callback;
            this.gatewayOptions = currentTab == 2;
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
                if (gatewayOptions) {
                    try {
                        removeMouldsBoundToOtherGateways(state.moulds, source);
                    } catch (Exception ignored) {
                        // Keep the unfiltered list when binding data cannot be loaded.
                    }
                }
                try {
                    JSONObject deviceJson = requestJson("GET", "/yujing/device/list?pageNum=1&pageSize=500", null, true);
                    appendDeviceInfoToMouldOptions(deviceJson.optJSONArray("rows"), state.moulds);
                } catch (Exception ignored) {
                }
                JSONObject mould = source == null ? null : source.optJSONObject("mould");
                String mouldId = valueOf(source, "mouldId");
                if (mouldId.length() == 0 && mould != null) {
                    mouldId = mould.optString("id");
                }
                if (mouldId.length() > 0 && !containsOption(state.moulds, mouldId)) {
                    String label = mouldOptionLabel(mould);
                    if (label.length() == 0) {
                        try {
                            JSONObject mouldDetailJson = requestJson("GET", "/yujing/mould/" + mouldId, null, true);
                            label = mouldOptionLabel(mouldDetailJson.optJSONObject("data"));
                        } catch (Exception ignored) {
                        }
                    }
                    if (label.length() == 0) {
                        label = "当前绑定模具";
                    }
                    if (label.length() > 0) {
                        state.moulds.add(Math.min(1, state.moulds.size()), new OptionItem(mouldId, label));
                    }
                }
            }
            return state;
        }

        @Override
        protected void onPostExecute(OptionsState optionsState) {
            if (!isActivityUsable()) {
                return;
            }
            callback.done(optionsState);
        }
    }

    private void removeMouldsBoundToOtherGateways(List<OptionItem> moulds, JSONObject currentGateway) throws Exception {
        Set<String> occupiedMouldIds = new HashSet<>();
        String currentGatewayId = valueOf(currentGateway, "id");
        String currentMouldId = gatewayMouldId(currentGateway);
        int pageSize = 200;
        int pageNum = 1;
        while (pageNum <= 100) {
            JSONObject json = requestJson(
                    "GET",
                    "/yujing/gateway/list?pageNum=" + pageNum + "&pageSize=" + pageSize,
                    null,
                    true
            );
            JSONArray rows = json.optJSONArray("rows");
            if (rows == null || rows.length() == 0) {
                break;
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject gateway = rows.optJSONObject(i);
                if (gateway == null || currentGatewayId.equals(valueOf(gateway, "id"))) {
                    continue;
                }
                String mouldId = gatewayMouldId(gateway);
                if (mouldId.length() > 0) {
                    occupiedMouldIds.add(mouldId);
                }
            }
            int total = json.optInt("total", -1);
            if (rows.length() < pageSize || (total >= 0 && pageNum * pageSize >= total)) {
                break;
            }
            pageNum++;
        }
        for (int i = moulds.size() - 1; i >= 0; i--) {
            String mouldId = moulds.get(i).value;
            if (mouldId.length() > 0
                    && !mouldId.equals(currentMouldId)
                    && occupiedMouldIds.contains(mouldId)) {
                moulds.remove(i);
            }
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
            if (!isActivityUsable()) {
                return;
            }
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
            String label = mouldOptionLabel(item);
            if (id.length() > 0 && label.length() > 0) {
                output.add(new OptionItem(id, label));
            }
        }
    }

    private String mouldOptionLabel(JSONObject mould) {
        if (mould == null) {
            return "";
        }
        String number = cleanOptionPart(mould.optString("number"));
        String name = cleanOptionPart(mould.optString("name"));
        return (number + " " + name).trim();
    }

    private String cleanOptionPart(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return "null".equalsIgnoreCase(text) || "-".equals(text) ? "" : text;
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
                line.append(" 实时").append(pressureWithUnit(pressure));
            }
            String standard = device.optString("standard");
            if (standard.length() > 0 && !"null".equals(standard)) {
                line.append(" 标准").append(pressureWithUnit(standard));
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
