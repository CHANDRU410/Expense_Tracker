package dev.spendsense;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParserTest {
    private Parser parse(String body) { return Parser.parse("VM-HDFCBK-S",body,12345); }
    @Test public void diningAlert() {
        Parser p = parse("Your A/c XX1234 has been debited by INR 250.00 towards SWIGGY via UPI");
        assertEquals(25000L,p.paise);
        assertEquals("SWIGGY",p.merchant);
        assertEquals("Food and Dining",p.category);
        assertFalse(p.credit);
    }
    @Test public void recognizesMerchantAndSpendType() {
        Parser grocery = parse("INR 390 debited to VPA: swiggy.instamart@hdfcbank on 02-01-25");
        assertEquals("swiggy.instamart@hdfcbank",grocery.merchant);
        assertEquals("Groceries",grocery.category);
        assertEquals("Food and Dining",parse("INR 180 spent; merchant: Campus Canteen; txn complete").category);
        assertEquals("Transport",parse("INR 120 debited UPI/DR/123456789012/RAPIDO/HDFC").category);
        assertEquals("Other",parse("INR 65 paid to ravi@okaxis via UPI").category);
        assertEquals("Other",Parser.expenseCategory("Scholarship Office"));
    }
    @Test public void incomeAndBalance() {
        Parser p = parse("Rs. 12,345.67 credited to your A/c from ACME via IMPS. Avl Bal INR 90,000");
        assertTrue(p.credit); assertEquals(1234567L,p.paise); assertEquals("ACME",p.merchant);
    }
    @Test public void skipsNonPayments() {
        for (String body : new String[]{"OTP to pay INR 250", "INR 100 debited but transaction failed",
                "INR 10 will be debited", "Payment pending: INR 200 paid", "Available balance INR 500",
                "A/c debited. Avl Bal INR 500", "Your A/c was not debited INR 80", "You spent INR 0 at Store"}) {
            assertNull(body,parse(body));
        }
        assertNull(Parser.parse("+919999999999","INR 20 debited at Cafe",12345));
    }
    @Test public void deduplication() {
        String a = "INR 250 debited at Cafe via UPI ref 123456789012";
        assertEquals(parse(a).key,parse(a.replace("Cafe","CAFE STORE")).key);
        assertNotEquals(parse(a).key,parse(a.replace("9012","9013")).key);
        String b = "Rs.10 paid to Cafe";
        assertEquals(parse(b).key,parse(b).key);
        assertNotEquals(parse(b).key,Parser.parse("VM-HDFCBK-S",b,12346).key);
    }
}
