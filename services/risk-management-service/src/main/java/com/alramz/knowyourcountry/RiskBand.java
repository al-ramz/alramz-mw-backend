package com.alramz.knowyourcountry;

import java.util.Optional;

/** The fixed 3-band mapping of the vendor's risk_level labels (exact, case-sensitive, untrimmed). */
public enum RiskBand {
    HIGH(20, "H"),
    MEDIUM(9, "M"),
    LOW(1, "L");

    private final int rating;
    private final String code;

    RiskBand(int rating, String code) {
        this.rating = rating;
        this.code = code;
    }

    public int rating() {
        return rating;
    }

    public String code() {
        return code;
    }

    public static Optional<RiskBand> fromVendorLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        return switch (label) {
            case "Higher Risk", "Medium-High Risk" -> Optional.of(HIGH);
            case "Medium Risk", "Medium-Low Risk" -> Optional.of(MEDIUM);
            case "Lower Risk" -> Optional.of(LOW);
            default -> Optional.empty();
        };
    }
}
