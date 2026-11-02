package com.alramz.utils;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class DataValidationUtilTest {

    @Test
    void isDateCurrentDateComparesCalendarDay() {
        assertThat(DataValidationUtil.isDateCurrentDate(new Date())).isTrue();
        assertThat(DataValidationUtil.isDateCurrentDate(new Date(0))).isFalse();
        assertThat(DataValidationUtil.isDateCurrentDate(null)).isFalse();
    }

    @Test
    void nullOrEmptyChecks() {
        assertThat(DataValidationUtil.nullOrEmpty((String) null)).isTrue();
        assertThat(DataValidationUtil.nullOrEmpty("  ")).isTrue();
        assertThat(DataValidationUtil.nullOrEmpty("x")).isFalse();
        assertThat(DataValidationUtil.nullOrEmpty((Date) null)).isTrue();
        assertThat(DataValidationUtil.nullOrEmpty(new Date())).isFalse();
    }

    @Test
    void nonZeroValueCheck() {
        assertThat(DataValidationUtil.isNotValidNonZeroValue(null)).isTrue();
        assertThat(DataValidationUtil.isNotValidNonZeroValue(Double.NaN)).isTrue();
        assertThat(DataValidationUtil.isNotValidNonZeroValue(0.0)).isTrue();
        assertThat(DataValidationUtil.isNotValidNonZeroValue(1.5)).isFalse();
    }

    @Test
    void strictDateFormatValidation() {
        assertThat(DataValidationUtil.isDateValidWithFormat("2026-02-28", "yyyy-MM-dd")).isTrue();
        assertThat(DataValidationUtil.isDateValidWithFormat("2026-02-30", "yyyy-MM-dd")).isFalse();
        assertThat(DataValidationUtil.isDateValidWithFormat("", "yyyy-MM-dd")).isFalse();
        assertThat(DataValidationUtil.isDateValidWithFormat("2026-02-28", null)).isFalse();
    }

    @Test
    void numericCheck() {
        assertThat(DataValidationUtil.isNumericValue("12.5")).isTrue();
        assertThat(DataValidationUtil.isNumericValue("abc")).isFalse();
    }
}
