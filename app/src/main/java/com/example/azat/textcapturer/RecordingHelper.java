package com.example.azat.textcapturer;

import java.util.Date;

public final class RecordingHelper {

    public static String generateFolderName() {
        Date now = new Date();
        return now.toString();
    }
}
