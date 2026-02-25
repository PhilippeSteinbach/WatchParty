package com.watchparty.dto;

public record SyncCorrectionMessage(
        double targetTimeSeconds,
        double playbackRate,
        String correctionType) {

    public static SyncCorrectionMessage rateAdjust(double targetTime, double rate) {
        return new SyncCorrectionMessage(targetTime, rate, "RATE_ADJUST");
    }

    public static SyncCorrectionMessage seek(double targetTime) {
        return new SyncCorrectionMessage(targetTime, 1.0, "SEEK");
    }

    public static SyncCorrectionMessage resetRate() {
        return new SyncCorrectionMessage(0, 1.0, "RATE_RESET");
    }
}
