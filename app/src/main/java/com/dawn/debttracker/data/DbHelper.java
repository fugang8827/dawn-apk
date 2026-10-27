package com.dawn.debttracker.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "dawn_debt.db";
    private static final int DB_VERSION = 1;

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 债务表
        db.execSQL("CREATE TABLE debts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "principal REAL NOT NULL," +
                "original_principal REAL NOT NULL," +
                "annual_rate REAL NOT NULL," +
                "last_calc_date TEXT NOT NULL" +
                ")");

        // 交易流水表 (收入/支出/还债)
        db.execSQL("CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," + // income, expense, repay
                "amount REAL NOT NULL," +
                "category TEXT NOT NULL," +
                "is_necessary INTEGER DEFAULT 1," + // 1:必要, 0:非必要
                "debt_id INTEGER DEFAULT 0," +
                "date TEXT NOT NULL," +
                "note TEXT" +
                ")");

        // 分类标签表 (用户可自定义持久化)
        db.execSQL("CREATE TABLE categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," + // income, expense
                "name TEXT NOT NULL UNIQUE" +
                ")");

        // 初始化默认分类
        String[] defaultIncome = {"主业薪资", "周末副业", "闲置转卖", "奖金补贴"};
        for (String inc : defaultIncome) {
            db.execSQL("INSERT INTO categories (type, name) VALUES ('income', ?)", new Object[]{inc});
        }
        String[] defaultExpense = {"餐饮伙食", "房租水电", "交通出行", "日用百货", "娱乐社交"};
        for (String exp : defaultExpense) {
            db.execSQL("INSERT INTO categories (type, name) VALUES ('expense', ?)", new Object[]{exp});
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS debts");
        db.execSQL("DROP TABLE IF EXISTS transactions");
        db.execSQL("DROP TABLE IF EXISTS categories");
        onCreate(db);
    }
}
