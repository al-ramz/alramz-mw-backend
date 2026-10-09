package com.alramz.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DataValidationUtil {
    public static Boolean isDateCurrentDate(Date date) {
        if (date == null) {
            return false;
        }
        Calendar today = Calendar.getInstance();
        Calendar inputDate = Calendar.getInstance();
        inputDate.setTime(date);
        return (today.get(Calendar.YEAR) == inputDate.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == inputDate.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_YEAR) == inputDate.get(Calendar.DAY_OF_YEAR));
    }

    public static Boolean nullOrEmpty(String value) {
        return (value == null || value.trim().isEmpty());
    }

    public static Boolean nullOrEmpty(Date date) {
        return (date == null);
    }

    public static Boolean isNotValidNonZeroValue(Double value) {
        return (value == null || value.isNaN() || value == 0.0);
    }

    public static boolean isDateValidWithFormat(String dateToValidate, String dateFromat) {
        try {
            if (nullOrEmpty(dateToValidate) || nullOrEmpty(dateFromat)) {
                return false;
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat(dateFromat);
            dateFormat.setLenient(false);
            dateFormat.parse(dateToValidate);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    public static boolean isNumericValue(String value) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}
