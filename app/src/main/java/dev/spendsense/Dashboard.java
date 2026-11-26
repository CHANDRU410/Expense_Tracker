package dev.spendsense;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class Dashboard extends Activity {
    private static final String HOME = "file:///android_asset/index.html";
    private final ZoneId zone = ZoneId.systemDefault();
    private Period period = Period.current();
    private Period importRange;
    private Ledger ledger;
    private WebView web;
    private boolean ready, importing;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Ledger.Payment> payments = Collections.emptyList();
    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { reload(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ledger = new Ledger(this);
        try {
            String start = getPreferences(0).getString("from","");
            String end = getPreferences(0).getString("to","");
            if (!start.isEmpty()) period = new Period(LocalDate.parse(start), LocalDate.parse(end));
        } catch (RuntimeException ignored) { period = Period.current(); }
        if (state != null && state.containsKey("importFrom")) {
            importRange = new Period(LocalDate.parse(state.getString("importFrom")),LocalDate.parse(state.getString("importTo")));
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(245,247,246));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());
            return i;
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBlockNetworkLoads(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return true; }
            @Override public void onPageFinished(WebView v,String url) {
                if (HOME.equals(url)) {ready = true; reload();}
            }
        });
        web.addJavascriptInterface(new Controls(),"SpendSense");
        setContentView(web); web.loadUrl(HOME);
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        if (importRange != null) {
            b.putString("importFrom", importRange.from.toString());
            b.putString("importTo", importRange.to.toString());
        }
    }
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refresh,new IntentFilter(BankReceiver.CHANGED),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(refresh,new IntentFilter(BankReceiver.CHANGED));
    }
    @Override protected void onStop() { unregisterReceiver(refresh); super.onStop(); }
    @Override protected void onResume() { super.onResume(); reload(); }
    @Override protected void onDestroy() { ready=false; worker.shutdown(); ledger.close(); web.destroy(); super.onDestroy(); }

    private long midnight(LocalDate date) { return date.atStartOfDay(zone).toInstant().toEpochMilli(); }
    private boolean granted(String permission) { return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED; }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void reload() {
        if (!ready) return;
        payments = ledger.month(midnight(period.from),midnight(period.to.plusDays(1)));
        JSONObject state = new JSONObject();
        try {
            state.put("from",period.from.toString()).put("to",period.to.toString())
                 .put("today",LocalDate.now().toString()).put("enabled",granted(Manifest.permission.RECEIVE_SMS))
                 .put("importing",importing);
            JSONArray rows = new JSONArray();
            for (Ledger.Payment p : payments) {
                ZonedDateTime local=Instant.ofEpochMilli(p.time).atZone(zone);
                rows.put(new JSONObject().put("id",p.id).put("date",local.toLocalDate().toString())
                    .put("credit",p.credit).put("merchant",p.merchant).put("category",p.category).put("amount",p.amount));
            }
            state.put("rows",rows);
        } catch (JSONException invalid) { throw new IllegalStateException(invalid); }
        web.evaluateJavascript("render("+state+")",null);
    }
    private void request(String permission,int requestCode) {
        if (getPreferences(0).getBoolean(permission,false) && !shouldShowRequestPermissionRationale(permission)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())));
        } else {
            getPreferences(0).edit().putBoolean(permission,true).apply();
            requestPermissions(new String[]{permission},requestCode);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results) {
        super.onRequestPermissionsResult(code,names,results);
        if (code==2 && granted(Manifest.permission.READ_SMS) && importRange!=null) importInbox(importRange);
        else reload();
    }
    public final class Controls {
        @JavascriptInterface public void range(String from,String to) { runOnUiThread(()->{
            try {
                period=new Period(LocalDate.parse(from),LocalDate.parse(to));
                getPreferences(0).edit().putString("from",from).putString("to",to).apply();
                reload();
            } catch (RuntimeException invalid) { toast(invalid.getMessage()); }
        }); }
        @JavascriptInterface public void tracking() { runOnUiThread(()->{
            if (!granted(Manifest.permission.RECEIVE_SMS)) request(Manifest.permission.RECEIVE_SMS,1);
        }); }
        @JavascriptInterface public void updates() { runOnUiThread(() -> {
            Intent release = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CHANDRU410/Expense_Tracker/releases/latest"));
            try { startActivity(release); }
            catch (ActivityNotFoundException e) { toast("Open SpendSense Releases on GitHub to check for updates."); }
        }); }
        @JavascriptInterface public void add() { runOnUiThread(()->edit(null)); }
        @JavascriptInterface public void editPayment(long id) { runOnUiThread(()->{
            for (Ledger.Payment p:payments) if(p.id==id) { edit(p); break; }
        }); }
        @JavascriptInterface public void importHistory() { runOnUiThread(()->{
            if (importing) return;
            new AlertDialog.Builder(Dashboard.this).setTitle("Import bank alerts?")
                .setMessage("Read SMS received from "+period.from+" through "+period.to+" on this phone. Only extracted payment details are saved. Matching alerts are skipped.")
                .setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->{
                    importRange=period;
                    if (granted(Manifest.permission.READ_SMS)) importInbox(importRange);
                    else request(Manifest.permission.READ_SMS,2);
                }).show();
        }); }
    }
    private void importInbox(Period range) {
        if (importing) return;
        importing=true; importRange=null; reload();
        worker.execute(()->{
            int added=0; String result;
            String[] limits={Long.toString(midnight(range.from)),Long.toString(midnight(range.to.plusDays(1)))};
            try (Ledger db=new Ledger(getApplicationContext());
                 Cursor sms=getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{"address","body","date","date_sent"},"date>=? AND date<?",limits,"date ASC")) {
                if (sms==null) throw new IllegalStateException("SMS inbox unavailable");
                db.getWritableDatabase().beginTransaction();
                try {
                    while (sms.moveToNext()) {
                        long received=sms.getLong(2),sent=sms.getLong(3);
                        Parser p=Parser.parse(sms.getString(0),sms.getString(1),sent>0 ? sent : received);
                        if(p!=null && db.record(p,received)) added++;
                    }
                    db.getWritableDatabase().setTransactionSuccessful();
                } finally { db.getWritableDatabase().endTransaction(); }
                result=added+" new transaction"+(added==1?"":"s")+" imported.";
            } catch (RuntimeException unavailable) { result="Could not read the inbox. Check SMS access and try again."; }
            final String message=result;
            runOnUiThread(()->{ if (!isDestroyed()) {importing=false; reload(); toast(message);} });
        });
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(28,47,49));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private Button button(String text, View.OnClickListener action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(action); return b;
    }
    private EditText field(LinearLayout form,String title,String value) {
        form.addView(label(title,14,true));
        EditText e = new EditText(this); e.setSingleLine(); e.setText(value); form.addView(e); return e;
    }
    private void edit(Ledger.Payment p) {
        LinearLayout form = column(); form.setPadding(dp(20),dp(10),dp(20),0);
        EditText merchant=field(form,"Merchant",p==null ? "" : p.merchant), category=field(form,"Category",p==null ? "Other" : p.category);
        EditText amount=field(form,"Amount (₹)",p==null ? "" : BigDecimal.valueOf(p.amount,2).toPlainString());
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Switch credit = new Switch(this); credit.setText("Income"); credit.setChecked(p!=null && p.credit); form.addView(credit);
        ZonedDateTime original = p==null ? period.to.atTime(LocalTime.now()).atZone(zone) : Instant.ofEpochMilli(p.time).atZone(zone);
        LocalDate[] date = {original.toLocalDate()};
        Button when = button(date[0].toString(), v -> new DatePickerDialog(this,(picker,y,m,d)->{
            date[0]=LocalDate.of(y,m+1,d); ((Button)v).setText(date[0].toString());
        },date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth()).show()); form.addView(when);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(p==null ? "Add transaction" : "Edit transaction").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null)
            .setNeutralButton("Delete",(d,w)-> new AlertDialog.Builder(this).setMessage("Delete this transaction?")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete",(a,b)->{ ledger.remove(p.id); reload(); }).show()).create();
        dialog.show();
        if(p==null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name=merchant.getText().toString().trim(), group=category.getText().toString().trim();
            if (name.isEmpty()) {merchant.setError("Enter a merchant"); return;}
            if (group.isEmpty()) {category.setError("Enter a category"); return;}
            long value;
            try {
                value = new BigDecimal(amount.getText().toString()).movePointRight(2).longValueExact();
                if (value <= 0 || value > 100000000000000L) throw new ArithmeticException();
            } catch (ArithmeticException | NumberFormatException invalid) { amount.setError("Enter a positive amount with up to two decimal places"); return; }
            if (date[0].isAfter(LocalDate.now())) {toast("Choose today or an earlier date."); return;}
            long time=date[0].atTime(original.toLocalTime()).atZone(zone).toInstant().toEpochMilli();
            if (p==null) ledger.add(value,credit.isChecked(),name,group,time);
            else ledger.edit(p.id,value,credit.isChecked(),name,group,time);
            if(date[0].isBefore(period.from) || date[0].isAfter(period.to)) {
                period=new Period(date[0].withDayOfMonth(1),date[0]);
                getPreferences(0).edit().putString("from",period.from.toString()).putString("to",period.to.toString()).apply();
            }
            dialog.dismiss(); reload();
        });
    }
}
