package dev.spendsense;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Dashboard extends Activity {
    private final Locale india = new Locale("en","IN");
    private final int ink = Color.rgb(20,48,44), accent = Color.rgb(16,101,79);
    private final ZoneId zone = ZoneId.systemDefault();
    private YearMonth month = YearMonth.now();
    private Ledger ledger;
    private LinearLayout page, summary, categories;
    private TextView period;
    private Button permission;
    private EditText search;
    private ListView list;
    private List<Ledger.Payment> payments = new ArrayList<>(), visible = new ArrayList<>();
    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) { reload(); }
    };
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(ink);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private Button button(String text, View.OnClickListener action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(action); return b;
    }
    private void heading(LinearLayout parent, String text) {
        TextView h = label(text, 19, true); h.setPadding(0,dp(14),0,dp(5)); parent.addView(h);
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) month = YearMonth.parse(saved.getString("month", month.toString()));
        ledger = new Ledger(this);
        list = new ListView(this);
        list.setBackgroundColor(Color.rgb(245,249,246));
        list.setPadding(dp(16),0,dp(16),0);
        list.setOnApplyWindowInsetsListener((v,i) -> {
            v.setPadding(dp(16),i.getSystemWindowInsetTop(),dp(16),i.getSystemWindowInsetBottom());
            return i;
        });
        page = column(); page.setPadding(0,dp(12),0,0);
        page.addView(label("SpendSense", 30, true));
        permission = button("", v -> enableSms()); page.addView(permission);
        LinearLayout navigation = new LinearLayout(this);
        navigation.addView(button("‹", v -> changeMonth(-1)));
        navigation.getChildAt(0).setContentDescription("Previous month");
        period = label("",18,true); period.setGravity(Gravity.CENTER);
        navigation.addView(period,new LinearLayout.LayoutParams(0,-2,1));
        navigation.addView(button("›", v -> changeMonth(1)));
        navigation.getChildAt(2).setContentDescription("Next month");
        navigation.setGravity(Gravity.CENTER_VERTICAL); page.addView(navigation);
        summary = column(); summary.setPadding(dp(15),dp(12),dp(15),dp(12)); summary.setBackgroundColor(accent);
        page.addView(summary); heading(page,"Spending by category");
        categories = column(); page.addView(categories); heading(page,"Transactions");
        search = new EditText(this); search.setSingleLine(); search.setHint("Search this month"); page.addView(search);
        list.addHeaderView(page,null,false); setContentView(list);
        page.setFocusableInTouchMode(true); page.requestFocus();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { filter(); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - list.getHeaderViewsCount();
            if (index >= 0 && index < visible.size()) edit(visible.get(index));
        });
    }
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refresh,new IntentFilter(BankReceiver.CHANGED),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(refresh,new IntentFilter(BankReceiver.CHANGED));
    }
    @Override protected void onStop() { unregisterReceiver(refresh); super.onStop(); }
    @Override protected void onResume() { super.onResume(); reload(); }
    @Override protected void onDestroy() { ledger.close(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("month",month.toString());
    }
    private void enableSms() {
        boolean asked = getPreferences(0).getBoolean("asked",false);
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())));
        } else {
            getPreferences(0).edit().putBoolean("asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},1);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] grants) {
        super.onRequestPermissionsResult(code,p,grants); reload();
    }
    private void changeMonth(int delta) { month = month.plusMonths(delta); reload(); }
    private String money(long amount) {
        return NumberFormat.getCurrencyInstance(india).format(BigDecimal.valueOf(amount,2));
    }
    private long start(YearMonth m) { return m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(); }
    private void reload() {
        boolean enabled = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        permission.setText(enabled ? "SMS tracking enabled" : "Enable bank SMS tracking"); permission.setEnabled(!enabled);
        period.setText(month.format(DateTimeFormatter.ofPattern("MMM yyyy",india)));
        payments = ledger.month(start(month),start(month.plusMonths(1)));
        long income=0, expense=0;
        Map<String,Long> totals = new TreeMap<>();
        for (Ledger.Payment p : payments) {
            if (p.credit) income += p.amount;
            else { expense += p.amount; totals.merge(p.category,p.amount,Long::sum); }
        }
        summary.removeAllViews();
        for (String s : new String[]{"Net cash flow   " + money(income-expense),"Income   "+money(income),"Expenses   "+money(expense)}) {
            TextView item = label(s,summary.getChildCount()==0 ? 20 : 16,true); item.setTextColor(Color.WHITE); summary.addView(item);
        }
        categories.removeAllViews();
        if (totals.isEmpty()) categories.addView(label("No recorded expenses this month.",15,false));
        totals.forEach((name,amount) -> categories.addView(label(name+"   "+money(amount),16,false)));
        filter();
    }
    private final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() { return Math.max(1,visible.size()); }
        public Object getItem(int i) { return visible.isEmpty() ? null : visible.get(i); }
        public long getItemId(int i) { return visible.isEmpty() ? -1 : visible.get(i).id; }
        public boolean isEnabled(int i) { return !visible.isEmpty(); }
        public View getView(int index, View recycled, ViewGroup parent) {
            LinearLayout row = column(); row.setPadding(0,dp(13),0,dp(13));
            if (visible.isEmpty()) {
                row.addView(label(payments.isEmpty() ? "No transactions captured this month." : "No matching transactions.",15,false));
            } else {
                Ledger.Payment p = visible.get(index);
                row.addView(label(p.merchant+"   "+(p.credit ? "+" : "−")+money(p.amount),17,true));
                row.addView(label(p.category+"  ·  "+Instant.ofEpochMilli(p.time).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm",india)),14,false));
            }
            return row;
        }
    };
    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(india);
        visible = new ArrayList<>();
        for (Ledger.Payment p : payments) {
            String terms = p.merchant+" "+p.category+" "+(p.credit ? "credit income" : "debit expense")+" "+money(p.amount)+" "+BigDecimal.valueOf(p.amount,2);
            if (terms.toLowerCase(india).contains(query)) visible.add(p);
        }
        if (list.getAdapter()==null) list.setAdapter(adapter); else adapter.notifyDataSetChanged();
    }
    private EditText field(LinearLayout form,String title,String value) {
        form.addView(label(title,14,true));
        EditText e = new EditText(this); e.setSingleLine(); e.setText(value); form.addView(e); return e;
    }
    private void edit(Ledger.Payment p) {
        LinearLayout form = column(); form.setPadding(dp(20),dp(10),dp(20),0);
        EditText merchant=field(form,"Merchant",p.merchant), category=field(form,"Category",p.category);
        EditText amount=field(form,"Amount (₹)",BigDecimal.valueOf(p.amount,2).toPlainString());
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Switch credit = new Switch(this); credit.setText("Income"); credit.setChecked(p.credit); form.addView(credit);
        ZonedDateTime original = Instant.ofEpochMilli(p.time).atZone(zone);
        LocalDate[] date = {original.toLocalDate()};
        Button when = button(date[0].toString(), v -> new DatePickerDialog(this,(picker,y,m,d)->{
            date[0]=LocalDate.of(y,m+1,d); ((Button)v).setText(date[0].toString());
        },date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth()).show()); form.addView(when);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit transaction").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null)
            .setNeutralButton("Delete",(d,w)-> new AlertDialog.Builder(this).setMessage("Delete this transaction?")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete",(a,b)->{ ledger.remove(p.id); reload(); }).show()).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name=merchant.getText().toString().trim(), group=category.getText().toString().trim();
            if (name.isEmpty()) {merchant.setError("Enter a merchant"); return;}
            if (group.isEmpty()) {category.setError("Enter a category"); return;}
            long value;
            try {
                value = new BigDecimal(amount.getText().toString()).movePointRight(2).longValueExact();
                if (value <= 0 || value > 100000000000000L) throw new ArithmeticException();
            } catch (ArithmeticException | NumberFormatException invalid) { amount.setError("Enter a positive amount with up to two decimal places"); return; }
            ledger.edit(p.id,value,credit.isChecked(),name,group,date[0].atTime(original.toLocalTime()).atZone(zone).toInstant().toEpochMilli());
            month = YearMonth.from(date[0]); dialog.dismiss(); reload();
        });
    }
}
