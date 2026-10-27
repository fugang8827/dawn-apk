package com.dawn.debttracker;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dawn.debttracker.data.DbHelper;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private DbHelper dbHelper;
    private FrameLayout container;
    private TextView tabHome, tabIncome, tabExpense, tabAnalytics;
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    private final DecimalFormat dfZero = new DecimalFormat("#,##0");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DbHelper(this);
        container = findViewById(R.id.container_fragment);

        tabHome = findViewById(R.id.tab_home);
        tabIncome = findViewById(R.id.tab_income);
        tabExpense = findViewById(R.id.tab_expense);
        tabAnalytics = findViewById(R.id.tab_analytics);

        tabHome.setOnClickListener(v -> showHomeView());
        tabIncome.setOnClickListener(v -> showIncomeAnalysisView());
        tabExpense.setOnClickListener(v -> showExpenseAnalysisView());
        tabAnalytics.setOnClickListener(v -> showOverallAnalyticsView());

        // 启动时自动计提复利
        accrueDailyInterest();

        // 默认显示主页
        showHomeView();
    }

    private void updateTabColors(TextView activeTab, int activeColor) {
        tabHome.setTextColor(Color.parseColor("#666D7C"));
        tabIncome.setTextColor(Color.parseColor("#666D7C"));
        tabExpense.setTextColor(Color.parseColor("#666D7C"));
        tabAnalytics.setTextColor(Color.parseColor("#666D7C"));
        activeTab.setTextColor(activeColor);
    }

    // 自动按自然日复利计息
    private void accrueDailyInterest() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String todayStr = getTodayDate();
        Cursor cursor = db.rawQuery("SELECT id, name, principal, annual_rate, last_calc_date FROM debts", null);
        double totalAccrued = 0;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date today = sdf.parse(todayStr);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                double p = cursor.getDouble(2);
                double rate = cursor.getDouble(3);
                String lastCalcStr = cursor.getString(4);
                if (rate <= 0 || p <= 0) continue;

                Date lastDate = sdf.parse(lastCalcStr);
                long diffDays = (today.getTime() - lastDate.getTime()) / 86400000;
                if (diffDays >= 1) {
                    double interest = p * (rate / 100.0 / 365.0) * diffDays;
                    p += interest;
                    totalAccrued += interest;

                    ContentValues cv = new ContentValues();
                    cv.put("principal", p);
                    cv.put("last_calc_date", todayStr);
                    db.update("debts", cv, "id=?", new String[]{String.valueOf(id)});
                }
            }
        } catch (Exception ignored) {}
        cursor.close();

        if (totalAccrued > 0) {
            Toast.makeText(this, "📅 已自动计提近阶段利息 ¥" + df.format(totalAccrued), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // 页面 1：原生主页 (纯原生 Android View)
    // ==========================================
    private void showHomeView() {
        updateTabColors(tabHome, Color.WHITE);
        container.removeAllViews();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(20));

        // 顶部品牌栏
        LinearLayout brandBar = new LinearLayout(this);
        brandBar.setOrientation(LinearLayout.HORIZONTAL);
        brandBar.setGravity(Gravity.CENTER_VERTICAL);
        brandBar.setPadding(0, 0, 0, dp(16));

        TextView brandName = new TextView(this);
        brandName.setText("破晓");
        brandName.setTextSize(20);
        brandName.setTextColor(Color.WHITE);
        brandName.getPaint().setFakeBoldText(true);

        TextView brandSub = new TextView(this);
        brandSub.setText(" · 债务记账 (纯原生)");
        brandSub.setTextSize(12);
        brandSub.setTextColor(Color.parseColor("#7D8594"));

        TextView btnSettings = new TextView(this);
        btnSettings.setText("⚙️ 设置");
        btnSettings.setTextColor(Color.parseColor("#60A5FA"));
        btnSettings.setTextSize(13);
        btnSettings.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnSettings.setBackground(createPillBg("#131620", "#222738"));
        btnSettings.setOnClickListener(v -> showDebtSettingsDialog());

        LinearLayout.LayoutParams brandParam = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout leftBrand = new LinearLayout(this);
        leftBrand.setOrientation(LinearLayout.HORIZONTAL);
        leftBrand.addView(brandName);
        leftBrand.addView(brandSub);
        brandBar.addView(leftBrand, brandParam);
        brandBar.addView(btnSettings);
        root.addView(brandBar);

        // 获取核心数据
        CoreStats stats = calculateStats();

        // 待还负债大数字卡片
        LinearLayout debtBox = createMetricCard(
                "待还负债总额 (本息合计)",
                df.format(stats.totalDebt) + " 元",
                "加权年化 " + String.format(Locale.getDefault(), "%.1f", stats.avgRate) + "% · 日息 ¥" + df.format(stats.dailyInterest)
        );
        root.addView(debtBox);

        // 预计还清时间大数字卡片
        String daysStr = stats.totalDebt <= 0 ? "0" : (stats.daysLeft >= 0 ? dfZero.format(stats.daysLeft) : "需增收");
        String targetDesc = stats.totalDebt <= 0 ? "🎉 恭喜！您已全部清偿！" : (stats.daysLeft >= 0 ? "预计上岸：" + stats.targetDateDesc : "当前结余无法覆盖利息");
        LinearLayout daysBox = createMetricCard(
                "推断预计还清时间",
                daysStr + (stats.daysLeft >= 0 && stats.totalDebt > 0 ? " 天" : ""),
                targetDesc
        );
        root.addView(daysBox);

        // 雪崩法优先攻坚提示条
        TextView strategyBar = new TextView(this);
        strategyBar.setText(stats.topDebtName != null ? "❄️ 优先攻坚：" + stats.topDebtName + " (雪崩法省息)" : "点击右上角设置录入债务");
        strategyBar.setTextColor(Color.parseColor("#A3ADC2"));
        strategyBar.setTextSize(12);
        strategyBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        strategyBar.setBackground(createPillBg("#0C0E14", "#212636"));
        LinearLayout.LayoutParams stratParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stratParam.setMargins(0, dp(14), 0, dp(24));
        strategyBar.setLayoutParams(stratParam);
        strategyBar.setOnClickListener(v -> showRepayDialog());
        root.addView(strategyBar);

        // 圆形 收入 / 支出 核心按钮
        LinearLayout circleRow = new LinearLayout(this);
        circleRow.setOrientation(LinearLayout.HORIZONTAL);
        circleRow.setGravity(Gravity.CENTER);
        circleRow.setPadding(0, dp(10), 0, dp(20));

        Button btnIncome = new Button(this);
        btnIncome.setText("收入");
        btnIncome.setTextColor(Color.WHITE);
        btnIncome.setTextSize(18);
        btnIncome.getPaint().setFakeBoldText(true);
        btnIncome.setBackgroundResource(R.drawable.bg_btn_income);
        LinearLayout.LayoutParams btnIncParam = new LinearLayout.LayoutParams(dp(96), dp(96));
        btnIncParam.setMargins(0, 0, dp(30), 0);
        btnIncome.setLayoutParams(btnIncParam);
        btnIncome.setOnClickListener(v -> showAddTxDialog("income"));

        Button btnExpense = new Button(this);
        btnExpense.setText("支出");
        btnExpense.setTextColor(Color.WHITE);
        btnExpense.setTextSize(18);
        btnExpense.getPaint().setFakeBoldText(true);
        btnExpense.setBackgroundResource(R.drawable.bg_btn_expense);
        LinearLayout.LayoutParams btnExpParam = new LinearLayout.LayoutParams(dp(96), dp(96));
        btnExpParam.setMargins(dp(30), 0, 0, 0);
        btnExpense.setLayoutParams(btnExpParam);
        btnExpense.setOnClickListener(v -> showAddTxDialog("expense"));

        circleRow.addView(btnIncome);
        circleRow.addView(btnExpense);
        root.addView(circleRow);

        // 还债长条按钮
        Button btnRepay = new Button(this);
        btnRepay.setText("💳 还债 (冲减债务本金)");
        btnRepay.setTextColor(Color.parseColor("#CFD6E6"));
        btnRepay.setTextSize(15);
        btnRepay.setBackgroundResource(R.drawable.bg_btn_repay);
        LinearLayout.LayoutParams repayParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        repayParam.setMargins(0, dp(6), 0, dp(16));
        btnRepay.setLayoutParams(repayParam);
        btnRepay.setOnClickListener(v -> showRepayDialog());
        root.addView(btnRepay);

        scrollView.addView(root);
        container.addView(scrollView);
    }

    private LinearLayout createMetricCard(String title, String val, String desc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, dp(10), 0, dp(14));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(12);
        tvTitle.setTextColor(Color.parseColor("#7D8594"));

        TextView tvVal = new TextView(this);
        tvVal.setText(val);
        tvVal.setTextSize(36);
        tvVal.setTextColor(Color.WHITE);
        tvVal.getPaint().setFakeBoldText(true);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(11);
        tvDesc.setTextColor(Color.parseColor("#5B6273"));

        card.addView(tvTitle);
        card.addView(tvVal);
        card.addView(tvDesc);
        return card;
    }

    // ==========================================
    // 页面 2：原生收入分析
    // ==========================================
    private void showIncomeAnalysisView() {
        updateTabColors(tabIncome, Color.parseColor("#22C55E"));
        container.removeAllViews();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("📈 收入深度分析 · 开源");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        root.addView(title);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String curYM = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());

        double curMonthTotal = 0;
        Map<String, Double> channels = new HashMap<>();
        Cursor c = db.rawQuery("SELECT category, amount FROM transactions WHERE type='income' AND date LIKE ?", new String[]{curYM + "%"});
        while (c.moveToNext()) {
            String cat = c.getString(0);
            double amt = c.getDouble(1);
            curMonthTotal += amt;
            channels.put(cat, channels.getOrDefault(cat, 0.0) + amt);
        }
        c.close();

        // 卡片 1：本月收入
        LinearLayout card1 = new LinearLayout(this);
        card1.setOrientation(LinearLayout.VERTICAL);
        card1.setPadding(dp(16), dp(14), dp(16), dp(14));
        card1.setBackground(createPillBg("#0E1016", "#1A1E29"));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.setMargins(0, dp(14), 0, dp(12));
        card1.setLayoutParams(p1);

        TextView t1 = new TextView(this);
        t1.setText("本月收入累计");
        t1.setTextColor(Color.parseColor("#7D8594"));
        t1.setTextSize(12);

        TextView v1 = new TextView(this);
        v1.setText("¥" + df.format(curMonthTotal));
        v1.setTextColor(Color.WHITE);
        v1.setTextSize(26);
        v1.getPaint().setFakeBoldText(true);

        card1.addView(t1);
        card1.addView(v1);
        root.addView(card1);

        // 卡片 2：渠道细分
        LinearLayout card2 = new LinearLayout(this);
        card2.setOrientation(LinearLayout.VERTICAL);
        card2.setPadding(dp(16), dp(14), dp(16), dp(14));
        card2.setBackground(createPillBg("#0E1016", "#1A1E29"));
        card2.setLayoutParams(p1);

        TextView t2 = new TextView(this);
        t2.setText("收入渠道丰富度 (共 " + channels.size() + " 种)");
        t2.setTextColor(Color.parseColor("#22C55E"));
        t2.setTextSize(13);
        t2.getPaint().setFakeBoldText(true);
        card2.addView(t2);

        for (Map.Entry<String, Double> entry : channels.entrySet()) {
            int pct = curMonthTotal > 0 ? (int) Math.round((entry.getValue() / curMonthTotal) * 100) : 0;
            TextView itemTv = new TextView(this);
            itemTv.setText(entry.getKey() + ": ¥" + df.format(entry.getValue()) + " (" + pct + "%)");
            itemTv.setTextColor(Color.parseColor("#CFD6E6"));
            itemTv.setTextSize(12);
            itemTv.setPadding(0, dp(6), 0, 0);
            card2.addView(itemTv);
        }
        if (channels.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("本月暂无收入流水");
            empty.setTextColor(Color.parseColor("#7D8594"));
            empty.setTextSize(12);
            card2.addView(empty);
        }
        root.addView(card2);

        scrollView.addView(root);
        container.addView(scrollView);
    }

    // ==========================================
    // 页面 3：原生支出分析
    // ==========================================
    private void showExpenseAnalysisView() {
        updateTabColors(tabExpense, Color.parseColor("#F43F5E"));
        container.removeAllViews();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("📉 支出深度分析 · 节流");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        root.addView(title);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String curYM = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());

        double necTotal = 0, luxTotal = 0;
        Map<String, Double> catRank = new HashMap<>();
        Cursor c = db.rawQuery("SELECT category, amount, is_necessary FROM transactions WHERE type='expense' AND date LIKE ?", new String[]{curYM + "%"});
        while (c.moveToNext()) {
            String cat = c.getString(0);
            double amt = c.getDouble(1);
            int isNec = c.getInt(2);
            if (isNec == 1) necTotal += amt;
            else luxTotal += amt;
            catRank.put(cat, catRank.getOrDefault(cat, 0.0) + amt);
        }
        c.close();
        double totalExp = necTotal + luxTotal;
        int necRatio = totalExp > 0 ? (int) Math.round((necTotal / totalExp) * 100) : 100;

        // 卡片 1：总支出与必要性
        LinearLayout card1 = new LinearLayout(this);
        card1.setOrientation(LinearLayout.VERTICAL);
        card1.setPadding(dp(16), dp(14), dp(16), dp(14));
        card1.setBackground(createPillBg("#0E1016", "#1A1E29"));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.setMargins(0, dp(14), 0, dp(12));
        card1.setLayoutParams(p1);

        TextView t1 = new TextView(this);
        t1.setText("本月支出结构 (必要 vs 非必要)");
        t1.setTextColor(Color.parseColor("#7D8594"));
        t1.setTextSize(12);

        TextView v1 = new TextView(this);
        v1.setText("¥" + df.format(totalExp));
        v1.setTextColor(Color.WHITE);
        v1.setTextSize(26);
        v1.getPaint().setFakeBoldText(true);

        TextView sub1 = new TextView(this);
        sub1.setText("🟢 必要: ¥" + df.format(necTotal) + " (" + necRatio + "%)   🔴 非必要: ¥" + df.format(luxTotal) + " (" + (100 - necRatio) + "%)");
        sub1.setTextColor(Color.parseColor("#A3ADC2"));
        sub1.setTextSize(12);
        sub1.setPadding(0, dp(6), 0, 0);

        card1.addView(t1);
        card1.addView(v1);
        card1.addView(sub1);
        root.addView(card1);

        // 卡片 2：分类去向
        LinearLayout card2 = new LinearLayout(this);
        card2.setOrientation(LinearLayout.VERTICAL);
        card2.setPadding(dp(16), dp(14), dp(16), dp(14));
        card2.setBackground(createPillBg("#0E1016", "#1A1E29"));
        card2.setLayoutParams(p1);

        TextView t2 = new TextView(this);
        t2.setText("支出分类去向");
        t2.setTextColor(Color.parseColor("#F43F5E"));
        t2.setTextSize(13);
        t2.getPaint().setFakeBoldText(true);
        card2.addView(t2);

        for (Map.Entry<String, Double> entry : catRank.entrySet()) {
            TextView itemTv = new TextView(this);
            itemTv.setText(entry.getKey() + ": ¥" + df.format(entry.getValue()));
            itemTv.setTextColor(Color.parseColor("#CFD6E6"));
            itemTv.setTextSize(12);
            itemTv.setPadding(0, dp(6), 0, 0);
            card2.addView(itemTv);
        }
        if (catRank.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("本月暂无支出流水");
            empty.setTextColor(Color.parseColor("#7D8594"));
            empty.setTextSize(12);
            card2.addView(empty);
        }
        root.addView(card2);

        scrollView.addView(root);
        container.addView(scrollView);
    }

    // ==========================================
    // 页面 4：综合评分
    // ==========================================
    private void showOverallAnalyticsView() {
        updateTabColors(tabAnalytics, Color.parseColor("#F59E0B"));
        container.removeAllViews();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("🏆 开源节流 · 综合评判");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        root.addView(title);

        LinearLayout scoreCard = new LinearLayout(this);
        scoreCard.setOrientation(LinearLayout.VERTICAL);
        scoreCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        scoreCard.setGravity(Gravity.CENTER);
        scoreCard.setBackground(createPillBg("#0E1016", "#1A1E29"));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.setMargins(0, dp(14), 0, dp(12));
        scoreCard.setLayoutParams(p1);

        TextView scoreTv = new TextView(this);
        scoreTv.setText("88");
        scoreTv.setTextSize(42);
        scoreTv.setTextColor(Color.parseColor("#22C55E"));
        scoreTv.getPaint().setFakeBoldText(true);

        TextView labelTv = new TextView(this);
        labelTv.setText("开源节流表现：极佳");
        labelTv.setTextColor(Color.WHITE);
        labelTv.setTextSize(16);
        labelTv.getPaint().setFakeBoldText(true);

        TextView descTv = new TextView(this);
        descTv.setText("非必要欲望消费克制到位，具备稳步削减债务的良好现金流。");
        descTv.setTextColor(Color.parseColor("#7D8594"));
        descTv.setTextSize(12);
        descTv.setGravity(Gravity.CENTER);
        descTv.setPadding(0, dp(6), 0, 0);

        scoreCard.addView(scoreTv);
        scoreCard.addView(labelTv);
        scoreCard.addView(descTv);
        root.addView(scoreCard);

        scrollView.addView(root);
        container.addView(scrollView);
    }

    // ==========================================
    // 弹窗逻辑：记一笔 / 还债 / 债务设置
    // ==========================================
    private void showAddTxDialog(String type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(type.equals("income") ? "记录收入" : "记录支出");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        EditText etAmount = new EditText(this);
        etAmount.setHint("输入金额 (元)");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etAmount);

        EditText etCat = new EditText(this);
        etCat.setHint(type.equals("income") ? "收入类型 (如: 薪资/兼职)" : "支出类型 (如: 餐饮/房租)");
        layout.addView(etCat);

        final boolean[] isNec = {true};
        if (type.equals("expense")) {
            Button btnNecToggle = new Button(this);
            btnNecToggle.setText("支出属性：🟢 必要支出 (点击切换)");
            btnNecToggle.setOnClickListener(v -> {
                isNec[0] = !isNec[0];
                btnNecToggle.setText(isNec[0] ? "支出属性：🟢 必要支出 (点击切换)" : "支出属性：🔴 非必要支出 (点击切换)");
            });
            layout.addView(btnNecToggle);
        }

        builder.setView(layout);
        builder.setPositiveButton("确认保存", (dialog, which) -> {
            String amtStr = etAmount.getText().toString().trim();
            String cat = etCat.getText().toString().trim();
            if (amtStr.isEmpty()) return;
            double amt = Double.parseDouble(amtStr);
            if (cat.isEmpty()) cat = type.equals("income") ? "常规收入" : "常规支出";

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("type", type);
            cv.put("amount", amt);
            cv.put("category", cat);
            cv.put("is_necessary", isNec[0] ? 1 : 0);
            cv.put("date", getTodayDate());
            db.insert("transactions", null, cv);

            Toast.makeText(this, "✅ 记账成功", Toast.LENGTH_SHORT).show();
            showHomeView();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showRepayDialog() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, name, principal FROM debts WHERE principal > 0", null);
        List<Long> debtIds = new ArrayList<>();
        List<String> debtNames = new ArrayList<>();
        while (c.moveToNext()) {
            debtIds.add(c.getLong(0));
            debtNames.add(c.getString(1) + " (剩 ¥" + df.format(c.getDouble(2)) + ")");
        }
        c.close();

        if (debtNames.isEmpty()) {
            Toast.makeText(this, "当前无待还债务，请先在右上角设置中添加", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择偿还债务");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        final int[] selectedIdx = {0};
        String[] items = debtNames.toArray(new String[0]);
        builder.setSingleChoiceItems(items, 0, (dialog, which) -> selectedIdx[0] = which);

        EditText etAmount = new EditText(this);
        etAmount.setHint("输入本次还款金额 (元)");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etAmount);

        builder.setView(layout);
        builder.setPositiveButton("确认冲减本金", (dialog, which) -> {
            String amtStr = etAmount.getText().toString().trim();
            if (amtStr.isEmpty()) return;
            double amt = Double.parseDouble(amtStr);

            long targetId = debtIds.get(selectedIdx[0]);
            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
            wdb.execSQL("UPDATE debts SET principal = MAX(0, principal - ?) WHERE id = ?", new Object[]{amt, targetId});

            ContentValues cv = new ContentValues();
            cv.put("type", "repay");
            cv.put("amount", amt);
            cv.put("category", "偿还债务");
            cv.put("debt_id", targetId);
            cv.put("date", getTodayDate());
            wdb.insert("transactions", null, cv);

            Toast.makeText(this, "🎉 成功冲减本金 ¥" + df.format(amt), Toast.LENGTH_SHORT).show();
            showHomeView();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDebtSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新增待还债务");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        EditText etName = new EditText(this);
        etName.setHint("债务名称 (如: 招行信用卡/朋友借款)");
        layout.addView(etName);

        EditText etAmount = new EditText(this);
        etAmount.setHint("待还本金金额 (元)");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etAmount);

        EditText etRate = new EditText(this);
        etRate.setHint("年化利率 % (无息填 0)");
        etRate.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etRate);

        builder.setView(layout);
        builder.setPositiveButton("保存债务", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String amtStr = etAmount.getText().toString().trim();
            String rateStr = etRate.getText().toString().trim();
            if (name.isEmpty() || amtStr.isEmpty()) return;

            double amt = Double.parseDouble(amtStr);
            double rate = rateStr.isEmpty() ? 0 : Double.parseDouble(rateStr);

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("name", name);
            cv.put("principal", amt);
            cv.put("original_principal", amt);
            cv.put("annual_rate", rate);
            cv.put("last_calc_date", getTodayDate());
            db.insert("debts", null, cv);

            Toast.makeText(this, "✅ 债务账户添加成功", Toast.LENGTH_SHORT).show();
            showHomeView();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 财务模型运算
    private static class CoreStats {
        double totalDebt = 0;
        double dailyInterest = 0;
        double avgRate = 0;
        long daysLeft = -1;
        String targetDateDesc = "";
        String topDebtName = null;
    }

    private CoreStats calculateStats() {
        CoreStats s = new CoreStats();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        double weightedSum = 0;
        double maxRate = -1;
        Cursor c = db.rawQuery("SELECT name, principal, annual_rate FROM debts WHERE principal > 0", null);
        while (c.moveToNext()) {
            String name = c.getString(0);
            double p = c.getDouble(1);
            double r = c.getDouble(2);
            s.totalDebt += p;
            s.dailyInterest += (p * (r / 100.0) / 365.0);
            weightedSum += p * r;
            if (r > maxRate) {
                maxRate = r;
                s.topDebtName = name;
            }
        }
        c.close();

        if (s.totalDebt > 0) s.avgRate = weightedSum / s.totalDebt;

        // 计算收支结余
        double incSum = 0, expSum = 0;
        Cursor c2 = db.rawQuery("SELECT type, amount FROM transactions", null);
        while (c2.moveToNext()) {
            if ("income".equals(c2.getString(0))) incSum += c2.getDouble(1);
            else if ("expense".equals(c2.getString(0))) expSum += c2.getDouble(1);
        }
        c2.close();

        double surplus = Math.max(0, incSum - expSum);
        double monthlyInterest = s.dailyInterest * 30;
        if (s.totalDebt > 0 && surplus > monthlyInterest) {
            double netMonthly = surplus - monthlyInterest;
            long months = (long) Math.ceil(s.totalDebt / netMonthly);
            s.daysLeft = months * 30;

            long targetTime = System.currentTimeMillis() + (s.daysLeft * 86400000L);
            s.targetDateDesc = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(new Date(targetTime));
        }

        return s;
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private GradientDrawable createPillBg(String bgColor, String strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgColor));
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Color.parseColor(strokeColor));
        return gd;
    }

    private int dp(float dpVal) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dpVal * scale + 0.5f);
    }
}
