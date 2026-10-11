package com.robotarm.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * แอปควบคุมแขนกล 4 แกนผ่านบลูทูธ BLE
 *
 * ส่งคำสั่งรูปแบบ "S<เลขเซอร์โว>:<องศา>\n" (เช่น "S1:120\n") ไปที่โมดูลบลูทูธ
 * ซึ่งส่งต่อให้ Arduino Nano ทางขา TX/RX
 *
 * - รายการค้นหาแสดงเฉพาะโมดูลบลูทูธแบบ HC-06 / BT05 / HM-10 (กดแสดงทั้งหมดได้)
 * - จำโมดูลที่เชื่อมต่อล่าสุดไว้แสดงเป็นอันดับแรก
 * - หลังเชื่อมต่อ แอปค้นหา "ช่องส่งข้อมูล" (characteristic) ของโมดูลเอง ไม่ต้องรู้ UUID ล่วงหน้า
 */
@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    // ช่องส่งข้อมูลมาตรฐานของโมดูล HM-10 / BT05 (ถ้าโมดูลมีช่องนี้จะเลือกก่อน)
    private static final UUID PREFERRED_CHAR = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    // บริการรับส่งข้อมูลแบบอนุกรมของโมดูลส่วนใหญ่ ใช้คัดกรองรายการค้นหา
    private static final ParcelUuid SERIAL_SERVICE = ParcelUuid.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    // ชื่อโมดูลบลูทูธที่พบบ่อย เช่น HC-06, H06, HC-05, BT05, HM-10, HMSoft, JDY-xx, MLT-BT05, AT-09
    private static final Pattern MODULE_NAME =
            Pattern.compile("(?i).*(hc-?0[5-9]|h-?06|bt-?0[45]|hm-?1[0-9]|hmsoft|jdy|mlt|at-?09|cc41).*");

    private static final String[] JOINT_NAMES = {"ฐานหมุน", "ไหล่", "ข้อศอก", "มือจับ"};
    private static final String[] JOINT_PINS = {"D3", "D5", "D6", "D9"};
    // องศาเริ่มต้นตอนเปิดแอป และองศาที่ปุ่ม "กลับท่าเริ่มต้น" สั่งไป
    private static final int HOME_ANGLE = 0;
    // มือจับ (เซอร์โว 4) ขยับได้จริงแค่ช่วงแคบ: 0° = หุบ, ~45° = เปิด, เกิน 50° จะดันชนตัวกั้น
    private static final int GRIP_CLOSED = 0;
    private static final int GRIP_OPEN = 45;
    // องศาสูงสุดที่ข้อต่อแต่ละตัวขยับได้จริง (เกินนี้จะชนโครง): ฐานหมุน, ไหล่, ข้อศอก, มือจับ
    private static final int[] JOINT_MAX = {180, 100, 100, 50};
    private static final int REQUEST_PERMISSIONS = 1;
    private static final long SCAN_TIME_MS = 8000;
    private static final long CONNECT_TIMEOUT_MS = 15000;

    // สีของแอป
    private static final int PRIMARY = Color.rgb(21, 101, 192);
    private static final int PRIMARY_DARK = Color.rgb(13, 71, 161);
    private static final int BG = Color.rgb(238, 242, 247);
    private static final int TEXT = Color.rgb(38, 50, 56);
    private static final int MUTED = Color.rgb(120, 144, 156);
    private static final int LINE = Color.rgb(222, 228, 236);
    private static final int GREEN = Color.rgb(46, 125, 50);
    private static final int ORANGE = Color.rgb(239, 108, 0);
    private static final int RED = Color.rgb(198, 40, 40);
    private static final int GRAY = Color.rgb(158, 158, 158);
    private static final int[] JOINT_COLORS = {
            Color.rgb(21, 101, 192), Color.rgb(0, 137, 123), Color.rgb(123, 31, 162), Color.rgb(239, 108, 0)};

    /** อุปกรณ์ที่พบระหว่างค้นหา */
    private static class Found {
        BluetoothDevice device;
        String name;
        int rssi;
        boolean module;  // ดูเหมือนโมดูลบลูทูธของแขนกล
        boolean last;    // เป็นโมดูลที่เชื่อมต่อครั้งล่าสุด
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic dataChar;  // ช่องที่ใช้ส่งคำสั่ง
    private boolean scanning, connecting, showAll;
    private volatile boolean ready;                // เชื่อมต่อและพบช่องส่งข้อมูลแล้ว
    private volatile boolean writeBusy;            // BLE ส่งได้ทีละข้อความ ต้องรอให้ข้อความก่อนหน้าเสร็จ
    private long writeStartedAt;

    // องศาที่ต้องการ (จากแถบเลื่อน) และองศาที่ส่งไปแล้ว (-1 = ยังไม่ได้ส่ง)
    private final int[] desired = {HOME_ANGLE, HOME_ANGLE, HOME_ANGLE, HOME_ANGLE};
    private final int[] sent = {-1, -1, -1, -1};

    private final List<Found> found = new ArrayList<>();

    // ส่วนประกอบบนหน้าจอ
    private View statusDot;
    private TextView statusText, statusDetail, emptyText, showAllToggle, logText, logToggle;
    private ProgressBar busySpinner;
    private Button scanButton, disconnectButton;
    private LinearLayout deviceList;
    private View logCard;
    private final SeekBar[] bars = new SeekBar[4];
    private final TextView[] angleValues = new TextView[4];

    // ==================================================================
    // สร้างหน้าจอ
    // ==================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("robotarm", MODE_PRIVATE);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        getWindow().setStatusBarColor(PRIMARY_DARK);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = vertical();
        scroll.addView(root);

        LinearLayout header = buildHeader();
        root.addView(header);

        LinearLayout content = vertical();
        root.addView(content);
        content.addView(buildConnectionCard());
        for (int i = 0; i < 4; i++) content.addView(buildJointCard(i));

        Button reset = filledButton("กลับท่าเริ่มต้น (ทุกข้อ " + HOME_ANGLE + "°)", Color.rgb(69, 90, 100), v -> {
            for (SeekBar b : bars) b.setProgress(HOME_ANGLE);
        });
        content.addView(reset, matchWidth(dp(52), dp(14)));
        content.addView(buildLogSection());

        // Android 15+ วาดหน้าจอเต็มขอบ: เว้นที่ให้แถบสถานะด้านบนและแถบนำทางด้านล่าง
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                top = sys.top;
                bottom = sys.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(dp(20), dp(18) + top, dp(20), dp(20));
            content.setPadding(dp(14), dp(2), dp(14), dp(24) + bottom);
            return insets;
        });
        header.setPadding(dp(20), dp(18), dp(20), dp(20));
        content.setPadding(dp(14), dp(2), dp(14), dp(24));

        setContentView(scroll);
        showIdle();
        ui.post(sendLoop);

        // ถ้าเคยอนุญาตสิทธิ์แล้ว เริ่มค้นหาทันทีตอนเปิดแอป
        if (adapter != null && adapter.isEnabled() && hasPermissions()) startScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        stopScan();
        closeGatt();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = vertical();
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRIMARY_DARK, PRIMARY}));
        header.addView(text("แขนกล BLE", 26, Color.WHITE, true));
        TextView sub = text("ควบคุมแขนกล 4 แกนผ่านบลูทูธ", 14, Color.WHITE, false);
        sub.setAlpha(0.8f);
        header.addView(sub);
        return header;
    }

    private View buildConnectionCard() {
        LinearLayout card = card();

        LinearLayout statusRow = horizontal();
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotLp.rightMargin = dp(10);
        statusRow.addView(statusDot, dotLp);
        statusText = text("", 17, TEXT, true);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        busySpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        busySpinner.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY));
        statusRow.addView(busySpinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
        card.addView(statusRow);

        statusDetail = text("", 13, MUTED, false);
        statusDetail.setPadding(dp(22), dp(2), 0, 0);
        card.addView(statusDetail);

        LinearLayout buttons = horizontal();
        buttons.setPadding(0, dp(14), 0, 0);
        scanButton = filledButton("ค้นหา HC-06", PRIMARY, v -> onScanClicked());
        disconnectButton = outlineButton("ตัดการเชื่อมต่อ", RED, v -> {
            closeGatt();
            showIdle();
        });
        buttons.addView(scanButton, weighted(dp(48), 0, dp(5)));
        buttons.addView(disconnectButton, weighted(dp(48), dp(5), 0));
        card.addView(buttons);

        deviceList = vertical();
        deviceList.setPadding(0, dp(6), 0, 0);
        card.addView(deviceList);

        emptyText = text("", 13, MUTED, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(8), dp(12), dp(8), dp(4));
        card.addView(emptyText);

        showAllToggle = text("", 14, PRIMARY, true);
        showAllToggle.setGravity(Gravity.CENTER);
        showAllToggle.setPadding(0, dp(10), 0, dp(2));
        showAllToggle.setOnClickListener(v -> {
            showAll = !showAll;
            renderDevices();
        });
        card.addView(showAllToggle);
        return card;
    }

    private View buildJointCard(int i) {
        int color = JOINT_COLORS[i];
        LinearLayout card = card();

        // แถวบน: หมายเลข · ชื่อข้อต่อ · องศา
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(String.valueOf(i + 1), 16, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(oval(color));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        badgeLp.rightMargin = dp(12);
        top.addView(badge, badgeLp);

        LinearLayout titles = vertical();
        titles.addView(text(JOINT_NAMES[i], 18, TEXT, true));
        titles.addView(text("เซอร์โว " + (i + 1) + " · ขา " + JOINT_PINS[i] + " · 0–" + JOINT_MAX[i] + "°", 13, MUTED, false));
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        angleValues[i] = text(HOME_ANGLE + "°", 30, color, true);
        top.addView(angleValues[i]);
        card.addView(top);

        // แถวล่าง: ปุ่ม − · แถบเลื่อน · ปุ่ม +
        LinearLayout control = horizontal();
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(0, dp(10), 0, 0);
        int maxAngle = JOINT_MAX[i];
        SeekBar bar = new SeekBar(this);
        bar.setMax(maxAngle);
        bar.setProgress(HOME_ANGLE);
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setThumbTintList(ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        bar.setPadding(dp(14), dp(12), dp(14), dp(12));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                desired[i] = progress;
                angleValues[i].setText(progress + "°");
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        bars[i] = bar;
        control.addView(stepButton("−", color, v -> bar.setProgress(Math.max(0, bar.getProgress() - 5))));
        control.addView(bar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        control.addView(stepButton("+", color, v -> bar.setProgress(Math.min(maxAngle, bar.getProgress() + 5))));
        card.addView(control);

        // มือจับ: ปุ่มเปิด/หุบ
        if (i == 3) {
            LinearLayout grip = horizontal();
            grip.setPadding(0, dp(10), 0, 0);
            grip.addView(filledButton("เปิดมือจับ (" + GRIP_OPEN + "°)", GREEN, v -> bar.setProgress(GRIP_OPEN)), weighted(dp(46), 0, dp(5)));
            grip.addView(filledButton("หุบมือจับ (" + GRIP_CLOSED + "°)", ORANGE, v -> bar.setProgress(GRIP_CLOSED)), weighted(dp(46), dp(5), 0));
            card.addView(grip);
        }
        return card;
    }

    private View buildLogSection() {
        LinearLayout box = vertical();
        logToggle = text("รายละเอียดการเชื่อมต่อ ▾", 13, MUTED, false);
        logToggle.setGravity(Gravity.CENTER);
        logToggle.setPadding(0, dp(18), 0, dp(6));
        logToggle.setOnClickListener(v -> {
            boolean show = logCard.getVisibility() != View.VISIBLE;
            logCard.setVisibility(show ? View.VISIBLE : View.GONE);
            logToggle.setText(show ? "รายละเอียดการเชื่อมต่อ ▴" : "รายละเอียดการเชื่อมต่อ ▾");
        });
        box.addView(logToggle);

        LinearLayout card = card();
        logText = text("ยังไม่มีข้อมูล", 12, Color.rgb(84, 110, 122), false);
        logText.setTypeface(Typeface.MONOSPACE);
        card.addView(logText);
        card.setVisibility(View.GONE);
        logCard = card;
        box.addView(card);
        return box;
    }

    // ==================================================================
    // สถานะการเชื่อมต่อ
    // ==================================================================

    private void setStatus(int dotColor, String status, String detail, boolean busy) {
        ((GradientDrawable) statusDot.getBackground()).setColor(dotColor);
        statusText.setText(status);
        statusDetail.setText(detail);
        statusDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        busySpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
        boolean connected = ready || connecting;
        disconnectButton.setEnabled(connected);
        disconnectButton.setAlpha(connected ? 1f : 0.4f);
        scanButton.setText(scanning ? "กำลังค้นหา..." : "ค้นหา HC-06");
        renderDevices();
    }

    private void showIdle() {
        statusDot.setBackground(oval(GRAY));
        setStatus(GRAY, "ยังไม่เชื่อมต่อ", "กด \"ค้นหา HC-06\" แล้วแตะชื่อโมดูล", false);
    }

    // ==================================================================
    // สิทธิ์การใช้บลูทูธ
    // ==================================================================

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String p : requiredPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_PERMISSIONS) return;
        if (hasPermissions()) startScan();
        else toast("ต้องอนุญาตสิทธิ์ \"อุปกรณ์ใกล้เคียง\" ก่อน จึงจะค้นหาบลูทูธได้");
    }

    // ==================================================================
    // ค้นหาอุปกรณ์ BLE
    // ==================================================================

    private void onScanClicked() {
        if (adapter == null) { toast("มือถือนี้ไม่รองรับบลูทูธ"); return; }
        if (!hasPermissions()) {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS);
            return;
        }
        startScan();
    }

    private void startScan() {
        if (!adapter.isEnabled()) { toast("กรุณาเปิดบลูทูธของมือถือก่อน"); return; }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { toast("เปิดการค้นหาบลูทูธไม่ได้"); return; }
        stopScan();
        if (ready || connecting) closeGatt();

        found.clear();
        // แสดงโมดูลที่เชื่อมต่อครั้งล่าสุดไว้ก่อน (เชื่อมต่อได้ทันทีโดยไม่ต้องรอค้นหาเจอ)
        String lastAddress = prefs.getString("last_address", null);
        if (lastAddress != null && BluetoothAdapter.checkBluetoothAddress(lastAddress)) {
            Found f = new Found();
            f.device = adapter.getRemoteDevice(lastAddress);
            f.name = prefs.getString("last_name", "โมดูลล่าสุด");
            f.rssi = Integer.MIN_VALUE;
            f.module = true;
            f.last = true;
            found.add(f);
        }

        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(null, settings, scanCallback);
        scanning = true;
        ui.removeCallbacks(scanTimeout);
        ui.postDelayed(scanTimeout, SCAN_TIME_MS);
        setStatus(PRIMARY, "กำลังค้นหา HC-06...", "แตะชื่อโมดูลเพื่อเชื่อมต่อ", true);
    }

    private final Runnable scanTimeout = () -> {
        stopScan();
        if (ready || connecting) return;
        int count = 0;
        for (Found f : found) if (f.module) count++;
        setStatus(GRAY, "ยังไม่เชื่อมต่อ",
                count > 0 ? "แตะชื่อโมดูลด้านล่างเพื่อเชื่อมต่อ" : "ไม่พบโมดูล กด \"ค้นหา HC-06\" อีกครั้ง", false);
    };

    private void stopScan() {
        if (scanning && scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) { }
        }
        scanning = false;
        if (scanButton != null) scanButton.setText("ค้นหา HC-06");
    }

    private static boolean looksLikeModule(String name, ScanRecord record) {
        if (name != null && MODULE_NAME.matcher(name).matches()) return true;
        if (record != null && record.getServiceUuids() != null && record.getServiceUuids().contains(SERIAL_SERVICE)) return true;
        return false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            ScanRecord record = result.getScanRecord();
            String name = device.getName();
            if (name == null && record != null) name = record.getDeviceName();
            final String finalName = name;
            final boolean module = looksLikeModule(name, record);
            final int rssi = result.getRssi();
            ui.post(() -> onDeviceFound(device, finalName, rssi, module));
        }

        @Override
        public void onScanFailed(int errorCode) {
            ui.post(() -> {
                scanning = false;
                setStatus(RED, "ค้นหาไม่สำเร็จ", "รหัสข้อผิดพลาด " + errorCode + " ลองปิด-เปิดบลูทูธแล้วค้นหาใหม่", false);
            });
        }
    };

    private void onDeviceFound(BluetoothDevice device, String name, int rssi, boolean module) {
        if (!scanning) return;
        for (Found f : found) {
            if (f.device.getAddress().equals(device.getAddress())) {
                boolean changed = f.rssi == Integer.MIN_VALUE || (name != null && !name.equals(f.name));
                f.rssi = rssi;
                if (name != null) f.name = name;
                f.module |= module;
                if (changed) renderDevices();
                return;
            }
        }
        Found f = new Found();
        f.device = device;
        f.name = name;
        f.rssi = rssi;
        f.module = module;
        found.add(f);
        renderDevices();
    }

    private void renderDevices() {
        if (deviceList == null) return;
        deviceList.removeAllViews();
        boolean active = ready || connecting;

        List<Found> visible = new ArrayList<>();
        int hidden = 0;
        for (Found f : found) {
            if (showAll || f.module) visible.add(f);
            else hidden++;
        }
        // เรียง: โมดูลล่าสุด → โมดูล HC-06 → สัญญาณแรงก่อน
        Collections.sort(visible, (a, b) -> {
            if (a.last != b.last) return a.last ? -1 : 1;
            if (a.module != b.module) return a.module ? -1 : 1;
            return Integer.compare(b.rssi, a.rssi);
        });
        if (!active) for (Found f : visible) deviceList.addView(deviceRow(f));

        if (active) {
            emptyText.setVisibility(View.GONE);
        } else if (visible.isEmpty() && (scanning || !found.isEmpty())) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(scanning
                    ? "กำลังค้นหาโมดูล HC-06..."
                    : "ไม่พบโมดูล HC-06\nตรวจว่าโมดูลมีไฟ (LED กะพริบ) และไม่ได้เชื่อมต่ออยู่กับเครื่องอื่น");
        } else {
            emptyText.setVisibility(View.GONE);
        }

        boolean toggleVisible = !active && (showAll || hidden > 0);
        showAllToggle.setVisibility(toggleVisible ? View.VISIBLE : View.GONE);
        showAllToggle.setText(showAll ? "แสดงเฉพาะ HC-06" : "แสดงอุปกรณ์บลูทูธทั้งหมด (" + hidden + ")");
    }

    private View deviceRow(Found f) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(ripple(shape(Color.rgb(248, 250, 252), LINE, 12), Color.argb(40, 21, 101, 192)));
        row.setClickable(true);
        row.setOnClickListener(v -> connect(f));

        TextView icon = text("BT", 13, f.module ? Color.WHITE : MUTED, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(oval(f.module ? PRIMARY : LINE));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconLp.rightMargin = dp(12);
        row.addView(icon, iconLp);

        LinearLayout info = vertical();
        String name = f.name != null ? f.name : "(ไม่มีชื่อ)";
        info.addView(text(f.last ? name + "  · ใช้ล่าสุด" : name, 16, TEXT, true));
        String signal = f.rssi == Integer.MIN_VALUE ? "ยังไม่พบสัญญาณ" : "สัญญาณ" + signalWord(f.rssi);
        info.addView(text(f.device.getAddress() + " · " + signal, 12, MUTED, false));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(text("เชื่อมต่อ ›", 14, PRIMARY, true));

        LinearLayout.LayoutParams lp = matchWidth(LinearLayout.LayoutParams.WRAP_CONTENT, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private static String signalWord(int rssi) {
        if (rssi > -60) return "ดีมาก";
        if (rssi > -75) return "ดี";
        return "อ่อน";
    }

    // ==================================================================
    // เชื่อมต่อ และหาช่องส่งข้อมูล
    // ==================================================================

    private void connect(Found f) {
        stopScan();
        ui.removeCallbacks(scanTimeout);
        closeGatt();
        connecting = true;
        logText.setText("");
        String name = f.name != null ? f.name : f.device.getAddress();
        setStatus(ORANGE, "กำลังเชื่อมต่อ...", name, true);
        prefs.edit().putString("pending_name", name).apply();
        if (Build.VERSION.SDK_INT >= 23) {
            gatt = f.device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = f.device.connectGatt(this, false, gattCallback);
        }
        ui.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS);
    }

    private final Runnable connectTimeout = () -> {
        if (ready || !connecting) return;
        closeGatt();
        setStatus(RED, "เชื่อมต่อไม่สำเร็จ", "โมดูลไม่ตอบ ลองถอดไฟ Nano 5 วินาที แล้วค้นหาใหม่", false);
    };

    private void closeGatt() {
        ui.removeCallbacks(connectTimeout);
        ready = false;
        connecting = false;
        dataChar = null;
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) { }
            gatt = null;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                ui.post(() -> {
                    if (g != gatt) return;
                    setStatus(ORANGE, "กำลังเตรียมช่องส่งข้อมูล...", statusDetail.getText().toString(), true);
                });
                // หน่วงเล็กน้อยก่อนค้นหา service (ช่วยให้โมดูลราคาถูกตอบได้เสถียรขึ้น)
                ui.postDelayed(() -> { if (g == gatt) g.discoverServices(); }, 400);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ui.post(() -> {
                    if (g != gatt) return;
                    boolean wasReady = ready;
                    closeGatt();
                    String code = status != 0 ? " (รหัส " + status + ")" : "";
                    setStatus(RED, wasReady ? "การเชื่อมต่อหลุด" : "เชื่อมต่อไม่สำเร็จ",
                            "กด \"ค้นหา HC-06\" เพื่อเชื่อมต่อใหม่" + code, false);
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            final StringBuilder info = new StringBuilder("บริการที่พบในโมดูล:\n");
            BluetoothGattCharacteristic preferred = null, fallback = null;

            for (BluetoothGattService service : g.getServices()) {
                String su = service.getUuid().toString();
                // บริการมาตรฐานของ BLE (ชื่ออุปกรณ์, ข้อมูลอุปกรณ์) ไม่ใช่ช่องรับส่งข้อมูล
                boolean standard = su.startsWith("00001800") || su.startsWith("00001801") || su.startsWith("0000180a");
                info.append("• ").append(shortUuid(service.getUuid())).append(standard ? " (มาตรฐาน)" : "").append("\n");
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    int p = c.getProperties();
                    boolean writable = (p & (BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
                    info.append("    - ").append(shortUuid(c.getUuid())).append(" ").append(describe(p)).append("\n");
                    if (!writable || standard) continue;
                    if (c.getUuid().equals(PREFERRED_CHAR)) preferred = c;
                    else if (fallback == null) fallback = c;
                }
            }

            final BluetoothGattCharacteristic chosen = preferred != null ? preferred : fallback;
            if (chosen != null) {
                boolean noResponse = (chosen.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
                chosen.setWriteType(noResponse
                        ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                info.append("ใช้ช่องส่งข้อมูล: ").append(shortUuid(chosen.getUuid()))
                        .append(noResponse ? " (write no response)" : " (write)");
            } else {
                info.append("ไม่พบช่องที่ส่งข้อมูลได้");
            }

            ui.post(() -> {
                if (g != gatt) return;
                logText.setText(info.toString());
                if (chosen == null) {
                    closeGatt();
                    setStatus(RED, "โมดูลนี้ส่งข้อมูลไม่ได้", "ไม่พบช่องส่งข้อมูล ดูรายละเอียดด้านล่าง", false);
                    return;
                }
                ui.removeCallbacks(connectTimeout);
                dataChar = chosen;
                for (int i = 0; i < 4; i++) sent[i] = -1;  // ส่งตำแหน่งแถบทั้งหมดให้แขนกลขยับตามจอ
                writeBusy = false;
                connecting = false;
                ready = true;

                String address = g.getDevice().getAddress();
                String name = g.getDevice().getName();
                if (name == null) name = prefs.getString("pending_name", address);
                prefs.edit().putString("last_address", address).putString("last_name", name).apply();
                setStatus(GREEN, "เชื่อมต่อแล้ว", name + " · " + address, false);
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            writeBusy = false;  // ส่งข้อความก่อนหน้าเสร็จแล้ว ส่งข้อความถัดไปได้
        }
    };

    // ==================================================================
    // ส่งคำสั่งให้ Arduino
    // ==================================================================

    // วนทุก 40 ms: ส่งเฉพาะเซอร์โวที่องศาเปลี่ยน ทีละข้อความ (กันส่งรัวจนโมดูลรับไม่ทัน)
    private final Runnable sendLoop = new Runnable() {
        @Override
        public void run() {
            if (ready && dataChar != null && gatt != null) {
                // กันค้าง: บางโมดูลไม่แจ้งว่าส่งเสร็จ ถ้าเกิน 300 ms ถือว่าเสร็จแล้ว
                if (writeBusy && SystemClock.uptimeMillis() - writeStartedAt > 300) writeBusy = false;
                if (!writeBusy) {
                    for (int i = 0; i < 4; i++) {
                        if (desired[i] == sent[i]) continue;
                        int angle = desired[i];
                        if (write("S" + (i + 1) + ":" + angle + "\n")) sent[i] = angle;
                        break;
                    }
                }
            }
            ui.postDelayed(this, 40);
        }
    };

    @SuppressWarnings("deprecation")
    private boolean write(String message) {
        byte[] data = message.getBytes(StandardCharsets.US_ASCII);
        boolean ok;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ok = gatt.writeCharacteristic(dataChar, data, dataChar.getWriteType()) == BluetoothStatusCodes.SUCCESS;
            } else {
                dataChar.setValue(data);
                ok = gatt.writeCharacteristic(dataChar);
            }
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            writeBusy = true;
            writeStartedAt = SystemClock.uptimeMillis();
        }
        return ok;
    }

    // ==================================================================
    // ตัวช่วยสร้างหน้าจอ
    // ==================================================================

    private static String shortUuid(UUID uuid) {
        String s = uuid.toString();
        // UUID มาตรฐาน 0000xxxx-0000-1000-8000-00805f9b34fb แสดงแค่ xxxx
        if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb")) return s.substring(4, 8).toUpperCase();
        return s;
    }

    private static String describe(int p) {
        StringBuilder sb = new StringBuilder("[");
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) sb.append("read ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) sb.append("write ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) sb.append("write-no-resp ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) sb.append("notify ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) sb.append("indicate ");
        return sb.toString().trim() + "]";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = vertical();
        c.setBackground(shape(Color.WHITE, Color.WHITE, 16));
        c.setElevation(dp(2));
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        c.setLayoutParams(matchWidth(LinearLayout.LayoutParams.WRAP_CONTENT, dp(12)));
        return c;
    }

    private TextView text(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button filledButton(String label, int color, View.OnClickListener onClick) {
        Button b = baseButton(label, Color.WHITE, onClick);
        b.setBackground(ripple(shape(color, color, 12), Color.argb(70, 255, 255, 255)));
        return b;
    }

    private Button outlineButton(String label, int color, View.OnClickListener onClick) {
        Button b = baseButton(label, color, onClick);
        b.setBackground(ripple(shape(Color.WHITE, color, 12), Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))));
        return b;
    }

    private Button baseButton(String label, int textColor, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setOnClickListener(onClick);
        return b;
    }

    private TextView stepButton(String label, int color, View.OnClickListener onClick) {
        TextView t = text(label, 22, color, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(2), LINE);
        t.setBackground(ripple(bg, Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))));
        t.setClickable(true);
        t.setOnClickListener(onClick);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return t;
    }

    private GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private static Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private static LinearLayout.LayoutParams matchWidth(int height, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = topMargin;
        return lp;
    }

    private static LinearLayout.LayoutParams weighted(int height, int leftMargin, int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, 1f);
        lp.leftMargin = leftMargin;
        lp.rightMargin = rightMargin;
        return lp;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
