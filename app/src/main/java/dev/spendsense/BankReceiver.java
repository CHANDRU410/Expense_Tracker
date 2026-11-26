package dev.spendsense;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class BankReceiver extends BroadcastReceiver {
    static final String CHANGED = "dev.spendsense.LEDGER_CHANGED";
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0 || parts[0] == null) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) {
            if (part == null || part.getMessageBody() == null) return;
            body.append(part.getMessageBody());
        }
        long time = parts[0].getTimestampMillis();
        Parser parsed = Parser.parse(parts[0].getOriginatingAddress(), body.toString(), time);
        if (parsed == null) return;
        try (Ledger ledger = new Ledger(context)) { ledger.record(parsed, time); }
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }
}
