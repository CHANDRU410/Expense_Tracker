package dev.spendsense;

import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PeriodTest {
    @Test public void sixMonthsAcrossNewYear() {
        LocalDate from=LocalDate.of(2023,12,1),to=LocalDate.of(2024,5,31);
        assertEquals(to,new Period(from,to).to);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsExtraDay() {
        new Period(LocalDate.of(2023,12,1), LocalDate.of(2024,6,1));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsReversedDates() {
        new Period(LocalDate.of(2024,5,3), LocalDate.of(2024,5,2));
    }
}
