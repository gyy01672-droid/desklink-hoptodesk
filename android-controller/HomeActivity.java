// SPDX-License-Identifier: AGPL-3.0-only
package cn.desklink.hopcontroller;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Outgoing connection home. All network traffic and session authorization are handled by HopToDesk.
 */
public final class HomeActivity extends Activity {
  private static final int PAPER = 0xfff6f8f6,
      INK = 0xff172d27,
      MUTED = 0xff486057,
      GREEN = 0xff155a46;
  private static final String UPSTREAM = "com.hoptodesk.app.MainActivity";
  private LinearLayout body;
  private SharedPreferences prefs;
  private EditText field;
  private boolean edit;
  private boolean rememberDevice = true;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    if (getActionBar() != null) getActionBar().hide();
    configureSystemBars();
    prefs = getSharedPreferences("viewer-home", MODE_PRIVATE);
    edit = state == null ? !prefs.contains("device") : state.getBoolean("editing");
    if (state != null) rememberDevice = state.getBoolean("rememberDevice", true);
    render(state == null ? "" : state.getString("draft", ""));
  }

  private void configureSystemBars() {
    Window window = getWindow();
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    window.setStatusBarColor(Build.VERSION.SDK_INT >= 23 ? PAPER : GREEN);
    window.setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? PAPER : INK);
    if (Build.VERSION.SDK_INT >= 30) {
      window.setDecorFitsSystemWindows(false);
      int appearance =
          WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
      WindowInsetsController controller = window.getInsetsController();
      if (controller != null) controller.setSystemBarsAppearance(appearance, appearance);
    } else {
      View decor = window.getDecorView();
      int flags = decor.getSystemUiVisibility();
      if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      decor.setSystemUiVisibility(flags);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putBoolean("editing", edit);
    state.putBoolean("rememberDevice", rememberDevice);
    if (field != null) {
      String draft = field.getText().toString();
      // Never turn a long, invalid paste into a valid but unintended device ID.
      state.putString("draft", draft.length() <= 128 ? draft : "输入内容过长");
    }
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
  }

  private GradientDrawable background(int color, int border, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    if (border != 0) d.setStroke(dp(1), border);
    return d;
  }

  private void add(LinearLayout parent, View view, int top) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(top);
    parent.addView(view, lp);
  }

  private TextView text(
      LinearLayout parent, String value, int sp, int color, boolean bold, int top) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextColor(color);
    v.setTextSize(sp);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    add(parent, v, top);
    return v;
  }

  private void button(
      LinearLayout parent, String title, boolean primary, int top, View.OnClickListener click) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setTextColor(primary ? Color.WHITE : GREEN);
    b.setMinHeight(dp(48));
    b.setBackground(background(primary ? GREEN : Color.WHITE, primary ? 0 : 0xffbed2c8, 10));
    b.setOnClickListener(click);
    add(parent, b, top);
  }

  private void render(String draft) {
    field = null;
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(PAPER);
    scroll.setFillViewport(true);
    if (Build.VERSION.SDK_INT >= 30) {
      scroll.setOnApplyWindowInsetsListener(
          (view, insets) -> {
            int mask =
                WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout()
                    | WindowInsets.Type.ime();
            Insets safe = insets.getInsets(mask);
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
          });
    }
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(24), dp(32), dp(24), dp(24));
    body.setFocusableInTouchMode(true);
    scroll.addView(body);
    setContentView(scroll);
    scroll.requestApplyInsets();
    text(body, "DeskLink · 个人分支", 16, GREEN, true, 0);
    text(body, "远程电脑", 32, INK, true, 18);
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(18), dp(18), dp(18), dp(18));
    card.setBackground(background(Color.WHITE, 0xffdbe4de, 16));
    add(body, card, 24);
    String saved = prefs.getString("device", "");
    if (!edit && !saved.isEmpty()) {
      text(card, "已保存的电脑", 17, MUTED, false, 0);
      text(card, grouped(saved), 26, INK, true, 8);
      button(card, "连接这台电脑", true, 20, v -> open(saved));
      button(
          card,
          "输入其他设备号",
          false,
          10,
          v -> {
            edit = true;
            render("");
          });
    } else {
      text(card, "电脑设备号", 19, INK, true, 0);
      text(card, "在 Windows 被控端查看 HopToDesk 设备号。", 15, MUTED, false, 6);
      field = new EditText(this);
      field.setSingleLine();
      field.setTextColor(INK);
      field.setTextSize(21);
      field.setHint("输入设备号");
      field.setHintTextColor(MUTED);
      // Advertise a number pad to the IME. The text key listener preserves pasted
      // characters, so an invalid ID cannot silently become another device's ID.
      field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      field.setRawInputType(InputType.TYPE_CLASS_NUMBER);
      field.setBackground(background(PAPER, 0xffb8cec3, 8));
      field.setPadding(dp(12), dp(12), dp(12), dp(12));
      field.setText(draft);
      add(card, field, 16);
      CheckBox remember = new CheckBox(this);
      remember.setText("保存这台电脑的设备号");
      remember.setTextColor(INK);
      remember.setButtonTintList(ColorStateList.valueOf(GREEN));
      remember.setChecked(rememberDevice);
      remember.setOnCheckedChangeListener((button, checked) -> rememberDevice = checked);
      add(card, remember, 8);
      Runnable go =
          () -> {
            String id = canonicalId(field.getText().toString());
            if (id == null) {
              field.setError("请输入电脑上显示的数字设备号");
              return;
            }
            if (remember.isChecked()) prefs.edit().putString("device", id).apply();
            edit = !prefs.contains("device");
            render("");
            open(id);
          };
      field.setImeOptions(EditorInfo.IME_ACTION_GO);
      field.setOnEditorActionListener(
          (v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            go.run();
            return true;
          });
      button(card, "连接电脑", true, 8, v -> go.run());
      if (!saved.isEmpty())
        button(
            card,
            "返回已保存的电脑",
            false,
            10,
            v -> {
              edit = false;
              render("");
            });
    }
    button(body, "打开 HopToDesk", false, 22, v -> open(null));
    text(body, "连接、密码验证和远程画面由 HopToDesk 提供。", 13, MUTED, false, 12);
  }

  static String canonicalId(String input) {
    if (input.length() > 128) return null;
    String id = input.replaceAll("\\s+", "");
    return id.matches("[0-9]{1,20}") ? id : null;
  }

  private String grouped(String id) {
    return id.replaceAll("(\\d)(?=(\\d{3})+$)", "$1 ");
  }

  private void open(String id) {
    Intent i = new Intent(id == null ? Intent.ACTION_MAIN : Intent.ACTION_VIEW);
    i.setClassName(getPackageName(), UPSTREAM);
    if (id != null)
      i.setData(new Uri.Builder().scheme("hoptodesk").authority("connect").appendPath(id).build());
    startActivity(i);
  }
}
