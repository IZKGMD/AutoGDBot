package com.izkgmd.vitatextbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 25);
    private static final int SURFACE = Color.rgb(17, 26, 44);
    private static final int LINE = Color.rgb(47, 66, 92);
    private static final int PRIMARY = Color.rgb(112, 183, 255);
    private static final int WHITE = Color.rgb(231, 239, 251);
    private static final int MUTED = Color.rgb(147, 168, 194);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private EditText host, port, user, password, command, textToPaste;
    private TextView status, terminal;
    private Button testButton, runButton, pasteButton;
    private boolean running;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        buildUi();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        if (stroke != 0) result.setStroke(dp(1), stroke);
        return result;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        parent.addView(view, p);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText field(String value, String hint, boolean secure, boolean multiline) {
        EditText e = new EditText(this);
        e.setSingleLine(!multiline);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setHint(hint);
        if (secure) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else if (multiline) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        e.setBackground(shape(BG, 11, LINE));
        e.setText(value);
        if (multiline) { e.setGravity(Gravity.TOP | Gravity.START); e.setMinLines(4); }
        return e;
    }

    private Button action(String title, boolean major) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextColor(major ? BG : WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(shape(major ? PRIMARY : Color.rgb(38, 57, 82), 11, 0));
        b.setMinHeight(dp(50));
        return b;
    }

    private LinearLayout panel() {
        LinearLayout box = column();
        box.setPadding(dp(15), dp(15), dp(15), dp(16));
        box.setBackground(shape(SURFACE, 17, LINE));
        return box;
    }

    private void buildUi() {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        scroller.addView(root);

        TextView heading = label("VITA  /  BRIDGE", 25, WHITE, true);
        heading.setLetterSpacing(.075f);
        root.addView(heading);
        add(root, label("Linux on PS Vita  •  SSH remote control", 12, MUTED, false), 5);

        LinearLayout connection = panel();
        add(root, connection, 22);
        status = label("●  НЕ ПОДКЛЮЧЕНО", 12, MUTED, true);
        connection.addView(status);
        add(connection, label("АДРЕС PS VITA", 11, MUTED, true), 17);
        host = field(prefs.getString("host", "192.168.1.141"), "IP или hostname", false, false);
        add(connection, host, 7);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout userColumn = column();
        userColumn.addView(label("ПОЛЬЗОВАТЕЛЬ", 11, MUTED, true));
        user = field(prefs.getString("user", "root"), "root", false, false);
        add(userColumn, user, 7);
        LinearLayout portColumn = column();
        portColumn.addView(label("ПОРТ", 11, MUTED, true));
        port = field(prefs.getString("port", "22"), "22", false, false);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        add(portColumn, port, 7);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 2);
        left.rightMargin = dp(10);
        row.addView(userColumn, left);
        row.addView(portColumn, new LinearLayout.LayoutParams(0, -2, 1));
        add(connection, row, 15);
        add(connection, label("SSH-ПАРОЛЬ  ·  НЕ СОХРАНЯЕТСЯ", 11, MUTED, true), 15);
        password = field("", "Пароль от Linux", true, false);
        add(connection, password, 7);
        testButton = action("Проверить SSH", true);
        add(connection, testButton, 15);
        testButton.setOnClickListener(v -> submit("Проверка подключения", "uname -a && whoami"));

        LinearLayout commands = panel();
        add(root, commands, 16);
        commands.addView(label("ТЕРМИНАЛ", 16, WHITE, true));
        add(commands, label("Команда выполняется в Alpine Linux на Вите", 12, MUTED, false), 5);
        command = field("", "Например: ls -la", false, false);
        add(commands, command, 13);
        runButton = action("▶  Выполнить команду", false);
        add(commands, runButton, 11);
        runButton.setOnClickListener(v -> {
            String c = command.getText().toString().trim();
            if (c.isEmpty()) { command.setError("Введи команду"); return; }
            submit("$ " + c, c);
        });

        LinearLayout pastePanel = panel();
        add(root, pastePanel, 16);
        pastePanel.addView(label("ВСТАВИТЬ В SAKURA", 16, WHITE, true));
        add(pastePanel, label("Текст появится в активном терминале на экране PS Vita.\nНужны xclip и xdotool; окно Sakura должно быть активным.", 12, MUTED, false), 5);
        textToPaste = field("", "Вставь большой текст или команды сюда…", false, true);
        add(pastePanel, textToPaste, 13);
        pasteButton = action("↗  Отправить текст на Виту", true);
        add(pastePanel, pasteButton, 11);
        pasteButton.setOnClickListener(v -> {
            String payload = textToPaste.getText().toString();
            if (payload.isEmpty()) { textToPaste.setError("Вставь текст"); return; }
            submit("Вставка в Sakura (" + payload.length() + " символов)", pasteCommand(payload));
        });

        LinearLayout log = panel();
        add(root, log, 16);
        TextView title = label("ВЫВОД", 16, WHITE, true);
        log.addView(title);
        terminal = label("Готово к подключению.\n", 12, Color.rgb(186, 216, 246), false);
        terminal.setTypeface(Typeface.MONOSPACE);
        terminal.setTextIsSelectable(true);
        terminal.setMovementMethod(new ScrollingMovementMethod());
        terminal.setMinHeight(dp(80));
        terminal.setPadding(dp(12), dp(12), dp(12), dp(12));
        terminal.setBackground(shape(BG, 11, 0));
        add(log, terminal, 12);
        Button clear = action("Очистить вывод", false);
        add(log, clear, 10);
        clear.setOnClickListener(v -> terminal.setText(""));
        add(root, label("VitaTextBridge 1.0  •  Локальная сеть  •  Ключи SSH проверяются", 11, MUTED, false), 16);
        setContentView(scroller);
    }

    private static String pasteCommand(String payload) {
        String data = Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        // base64 avoids executing content as shell code; Sakura pastes UTF-8 from X clipboard.
        return "if ! command -v xclip >/dev/null 2>&1 || ! command -v xdotool >/dev/null 2>&1; then " +
                "echo 'ОШИБКА: установи пакеты: apk add xclip xdotool' >&2; exit 127; fi; " +
                "printf '%s' '" + data + "' | base64 -d | DISPLAY=:0 xclip -selection clipboard -in " +
                "&& sleep 0.25 && DISPLAY=:0 xdotool key --clearmodifiers ctrl+shift+v " +
                "&& echo 'Текст отправлен в активное окно Sakura'";
    }

    private void setBusy(boolean busy) {
        running = busy;
        testButton.setEnabled(!busy);
        runButton.setEnabled(!busy);
        pasteButton.setEnabled(!busy);
        status.setText(busy ? "●  ПОДКЛЮЧЕНИЕ…" : "●  ГОТОВО");
        status.setTextColor(busy ? PRIMARY : Color.rgb(133, 223, 176));
    }

    private void append(String message) {
        String current = terminal.getText().toString();
        if (current.length() > 50000) current = current.substring(current.length() - 35000);
        terminal.setText(current + message + "\n");
    }

    private boolean confirmHostKey(String message) throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        AtomicBoolean accept = new AtomicBoolean(false);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) { gate.countDown(); return; }
            new AlertDialog.Builder(this)
                    .setTitle("Новый SSH-ключ PS Vita")
                    .setMessage("Это первое подключение к этому устройству. Сверь отпечаток ключа с Vita, если возможно.\n\n" + message)
                    .setPositiveButton("Доверять ключу", (d, w) -> { accept.set(true); gate.countDown(); })
                    .setNegativeButton("Отмена", (d, w) -> gate.countDown())
                    .setOnCancelListener(d -> gate.countDown())
                    .show();
        });
        return gate.await(90, TimeUnit.SECONDS) && accept.get();
    }

    private void submit(String action, String cmd) {
        if (running) return;
        String h = host.getText().toString().trim();
        String u = user.getText().toString().trim();
        String p = password.getText().toString();
        int sshPort;
        try { sshPort = Integer.parseInt(port.getText().toString().trim()); }
        catch (NumberFormatException e) { port.setError("Нужен номер порта"); return; }
        if (h.isEmpty() || h.contains(" ") || u.isEmpty() || sshPort < 1 || sshPort > 65535) {
            new AlertDialog.Builder(this).setMessage("Проверь IP, пользователя и порт.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (p.isEmpty()) { password.setError("Введи пароль SSH"); return; }
        prefs.edit().putString("host", h).putString("user", u)
                .putString("port", String.valueOf(sshPort)).apply();
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(password.getWindowToken(), 0);
        append("▶ " + action);
        setBusy(true);
        executor.execute(() -> {
            String result;
            try {
                SshClient client = new SshClient(h, sshPort, u, p,
                        new File(getFilesDir(), "known_hosts"), this::confirmHostKey);
                result = client.execute(cmd);
            } catch (Exception e) {
                result = "Ошибка SSH: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            String finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                append(finalResult.isEmpty() ? "Команда завершена без вывода." : finalResult);
                setBusy(false);
            });
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
