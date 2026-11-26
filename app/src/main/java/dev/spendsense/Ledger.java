package dev.spendsense;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class Ledger extends SQLiteOpenHelper {
    Ledger(Context context) { super(context, "ledger.db", null, 1); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY, fingerprint TEXT NOT NULL UNIQUE, time INTEGER NOT NULL, paise INTEGER NOT NULL, credit INTEGER NOT NULL, merchant TEXT NOT NULL, category TEXT NOT NULL)");
        db.execSQL("CREATE INDEX payments_time ON payments(time)");
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported schema migration");
    }
    boolean record(Parser parsed, long time) {
        ContentValues row = values(parsed.paise, parsed.credit, parsed.merchant, parsed.category);
        row.put("time", time);
        row.put("fingerprint", parsed.key);
        return getWritableDatabase().insertWithOnConflict("payments", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }
    private static ContentValues values(long amount, boolean credit, String merchant, String category) {
        ContentValues row = new ContentValues();
        row.put("paise", amount);
        row.put("credit", credit ? 1 : 0);
        row.put("merchant", merchant);
        row.put("category", category);
        return row;
    }
    void add(long amount, boolean credit, String merchant, String category, long time) {
        ContentValues row = values(amount, credit, merchant, category);
        row.put("time", time);
        row.put("fingerprint", java.util.UUID.randomUUID().toString());
        getWritableDatabase().insertOrThrow("payments", null, row);
    }
    void edit(long id, long amount, boolean credit, String merchant, String category, long time) {
        ContentValues row = values(amount, credit, merchant, category);
        row.put("time", time);
        getWritableDatabase().update("payments", row, "id=?", new String[]{Long.toString(id)});
    }
    void remove(long id) {
        getWritableDatabase().delete("payments", "id=?", new String[]{Long.toString(id)});
    }
    static final class Payment {
        final long id, time, amount;
        final boolean credit;
        final String merchant, category;
        Payment(Cursor c) {
            id = c.getLong(0); time = c.getLong(1); amount = c.getLong(2);
            credit = c.getInt(3) != 0; merchant = c.getString(4); category = c.getString(5);
        }
    }
    List<Payment> month(long start, long end) {
        ArrayList<Payment> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,time,paise,credit,merchant,category FROM payments WHERE time>=? AND time<? ORDER BY time DESC,id DESC",
                new String[]{Long.toString(start), Long.toString(end)})) {
            while (c.moveToNext()) rows.add(new Payment(c));
        }
        return rows;
    }
}
