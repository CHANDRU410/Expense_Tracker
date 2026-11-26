package dev.spendsense;

import java.time.LocalDate;

final class Period {
    final LocalDate from, to;
    Period(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("End date must be on or after start date.");
        if (to.isAfter(LocalDate.now())) throw new IllegalArgumentException("Choose an end date up to today.");
        if (!to.isBefore(from.plusMonths(6))) throw new IllegalArgumentException("Choose a range of up to six calendar months.");
        this.from = from;
        this.to = to;
    }
    static Period current() {
        LocalDate today = LocalDate.now();
        return new Period(today.withDayOfMonth(1), today);
    }
}
