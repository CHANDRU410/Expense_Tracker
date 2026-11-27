package dev.spendsense;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Parser {
    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
    private static final Pattern SENDER = pattern("(?:[A-Z]{2}-)?[A-Z][A-Z0-9]{2,11}(?:-[A-Z])?");
    private static final Pattern SKIP = pattern("\\b(otp|one[ -]time|verification code|failed|declined|unsuccessful|pending|scheduled|will be|attempted|request(?:ed)?|offer|eligible|due date|could not|not (?:been )?(?:debited|credited))\\b");
    private static final Pattern ACTION = pattern("\\b(debited|spent|paid|withdrawn|sent|credited|received|deposited)\\b");
    private static final Pattern AMOUNT = pattern("(?:₹|\\bINR|\\bRs\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![0-9.,]\\d)");
    private static final Pattern MERCHANT = pattern("\\b(?:towards|at|to|from)\\s+(?!your\\b|a/?c\\b|account\\b|INR\\b|Rs\\b)([A-Za-z0-9][A-Za-z0-9 .@&_-]{0,60}?)(?=\\s+(?:via|on|ref|UPI ref|avl|available|bal|using|txn|for Rs|for INR)\\b|[,;:/]|[.]?(?:$))");
    private static final Pattern VPA = pattern("\\b(?:to|from|at|towards)(?:\\s+(?:vpa|upi\\s*id))?\\s*[:=-]?\\s*([a-z0-9._-]+@[a-z][a-z0-9.-]*[a-z0-9])\\b");
    private static final Pattern LABEL = pattern("\\b(?:merchant(?:\\s+name)?|payee)\\s*[:=]\\s*([\\w .&'-]+?)(?=\\s+(?:on|via|ref|txn|UPI)\\b|[,;/]|$)");
    private static final Pattern NARRATION = pattern("\\bUPI/(?:DR/|CR/)?\\d{6,18}/([^/;]{2,60})(?:/|;|$)");
    private static final String[][] CATEGORY_RULES = {
        {"Groceries","swiggyinstamart|amazonfresh|instamart|blinkit|zepto|bigbasket|big basket|dmart|d mart|grocer(?:y|ies)|supermarket|super market|kirana|freshmart|amazon fresh"},
        {"Food and Dining","swiggy|zomato|restaurant|cafe|bakery|biryani|domino(?:s)?|starbucks|canteen|eatclub|pizza|mcdonald(?:s)?"},
        {"Transport","uber|ola(?: cabs)?|rapido|irctc|metro|redbus|railway|fastag|petrol|fuel"},
        {"Health","pharmacy|pharma|medical store|hospital|medplus|netmeds|1mg"},
        {"Bills and Utilities","electricity|broadband|airtel|jio|bescom|tangedco|water bill"},
        {"Entertainment","netflix|spotify|bookmyshow|cinema|hotstar"},
        {"Shopping","amazon|flipkart|myntra|ajio|meesho|decathlon"}
    };
    private static final Pattern[] CATEGORY_MATCHERS = new Pattern[CATEGORY_RULES.length];
    static {
        for (int i=0;i<CATEGORY_RULES.length;i++) {
            CATEGORY_MATCHERS[i]=pattern("\\b(?:" + CATEGORY_RULES[i][1] + ")(?:\\b|(?=\\d))");
        }
    }
    static String expenseCategory(String merchant) {
        String local = merchant.split("@", 2)[0].toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
        for (int i=0;i<CATEGORY_MATCHERS.length;i++) {
            if (CATEGORY_MATCHERS[i].matcher(local).find()) return CATEGORY_RULES[i][0];
        }
        return "Other";
    }
    private static final Pattern REF = pattern("\\b(?:UPI[ /-]*(?:ref(?:erence)?|txn)|UTR|RRN|ref(?:erence)?|txn id)\\s*(?:no\\.?|id)?[ .:#/-]*([A-Z0-9]{6,32})\\b");

    final long paise;
    final boolean credit;
    final String merchant, category, key;

    private Parser(long paise, boolean credit, String merchant, String category, String key) {
        this.paise = paise;
        this.credit = credit;
        this.merchant = merchant;
        this.category = category;
        this.key = key;
    }

    static Parser parse(String sender, String raw, long time) {
        if (sender == null || raw == null || !SENDER.matcher(sender.trim()).matches()) return null;
        String body = raw.trim().replaceAll("\\s+", " ");
        if (SKIP.matcher(body).find()) return null;
        Matcher action = ACTION.matcher(body);
        if (!action.find()) return null;
        boolean credit = action.group().matches("(?i)credited|received|deposited");
        String transaction = body.split("(?i)\\b(?:avl|avail(?:able)?|closing|total)\\.?\\s*(?:bal(?:ance)?|limit)\\b|\\bbal(?:ance)?\\s*[:=]", 2)[0];
        Matcher amount = AMOUNT.matcher(transaction);
        if (!amount.find()) return null;
        long paise;
        try {
            paise = new BigDecimal(amount.group(1).replace(",", "")).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) { return null; }
        if (paise <= 0 || paise > 100000000000000L) return null;
        Matcher party = MERCHANT.matcher(transaction);
        String merchant = party.find() ? party.group(1).replaceAll("[. ]+$", "") : "Unknown";
        Matcher labelled = LABEL.matcher(transaction);
        Matcher narration = NARRATION.matcher(transaction);
        Matcher vpa = VPA.matcher(transaction);
        if (labelled.find()) merchant = labelled.group(1).trim();
        else if (narration.find()) merchant = narration.group(1).trim();
        else if (vpa.find()) merchant = vpa.group(1);
        String category = credit ? "Income" : expenseCategory(merchant);
        Matcher ref = REF.matcher(body);
        String identity = ref.find() ? "ref|" + ref.group(1) + "|" + credit + "|" + paise
                : "sms|" + sender + "|" + time + "|" + body;
        return new Parser(paise, credit, merchant, category, digest(identity.toLowerCase(Locale.ROOT)));
    }

    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
