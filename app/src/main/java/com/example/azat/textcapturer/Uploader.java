package com.example.azat.textcapturer;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Uploader {

    private static final String BASE_URL = "http://3.16.206.22:8000/";

    private static final String POST_TEXT_URL = String.format("%supload/upload_text/", BASE_URL);
    private static final String MESSAGE_PARAM = "message";

    private static final String POST_IMAGE_URL = String.format("%supload/upload_image/", BASE_URL);

    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_HEADER_VALUE = "form-data; name=\"file\"; filename=\"file.png\"";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final String CACHE_CONTROL_VALUE_HEADER = "no-cache";

    private static final String MEDIA_TYPE = "image/jpg";

    private static final String NAME = "file";
    private static final String FILENAME = "file.jpg";

    private final OkHttpClient mOkHttpClient;

    public Uploader() {
        mOkHttpClient = new OkHttpClient();
    }

    public void uploadText(@NonNull final String text) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        final String url = String.format("%s?%s=%s", POST_TEXT_URL, MESSAGE_PARAM, text);

        RequestBody formBody = formBuilder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        try {
            Response response = mOkHttpClient.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void uploadImage(@NonNull final byte[] bytes) {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(CONTENT_DISPOSITION_HEADER, CONTENT_DISPOSITION_HEADER_VALUE)
                .addFormDataPart(
                        NAME,
                        FILENAME,
                        RequestBody.create(MediaType.parse(MEDIA_TYPE), bytes)
                )
                .build();

        Request request = new Request.Builder()
                .url(POST_IMAGE_URL)
                .addHeader(CONTENT_TYPE_HEADER, MultipartBody.FORM.toString())
                .addHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE_HEADER)
                .put(body)
                .build();

        try {
            mOkHttpClient.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
