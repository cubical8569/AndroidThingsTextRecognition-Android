// ABBYY Real-Time Recognition SDK 1 © 2016 ABBYY Production LLC
// ABBYY is either a registered trademark or a trademark of ABBYY Software Ltd.

package com.example.azat.textcapturer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.abbyy.mobile.rtr.Engine;
import com.abbyy.mobile.rtr.IRecognitionService;
import com.abbyy.mobile.rtr.ITextCaptureService;
import com.abbyy.mobile.rtr.Language;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    // Licensing
    private static final String LICENSE_FILE_NAME = "AbbyyRtrSdk.license";

    ///////////////////////////////////////////////////////////////////////////////
    // Some application settings that can be changed to modify application behavior:
    // The default behavior in this sample is to start recognition when application is started or
    // resumed. You can turn off this behavior or remove it completely to simplify the application
    private static final boolean START_RECOGNITION_ON_APP_START = true;
    // A subset of available languages shown in the UI. See all available LANGUAGES in Language enum.
    // To show all languages in the UI you can substitute the list below with:
    // Language[] languages = Language.values();
    private static final Language[] LANGUAGES = {
            Language.English,
            Language.ChineseSimplified,
            Language.ChineseTraditional,
            Language.French,
            Language.German,
            Language.Italian,
            Language.Japanese,
            Language.Korean,
            Language.Polish,
            Language.PortugueseBrazilian,
            Language.Russian,
            Language.Spanish,
    };
    ///////////////////////////////////////////////////////////////////////////////

    // The 'Abbyy RTR SDK Engine' and 'Text Capture Service' to be used in this sample application
    private Engine mEngine;
    private ITextCaptureService mTextCaptureService;

    // The camera and the preview surface
    private Camera mCamera;
    private SurfaceViewWithOverlay mSurfaceViewWithOverlay;
    private SurfaceHolder mPreviewSurfaceHolder;

    // Actual preview size and orientation
    private Camera.Size mCameraPreviewSize;
    private int mOrientation;

    // Auxiliary variables
    private boolean mInPreview = false; // Camera preview is started
    private boolean mStartRecognitionWhenReady; // Start recognition next time when ready (and reset this flag)
    private Handler mHandler = new Handler(); // Posting some delayed actions;

    // UI components
    private Button mStartButton; // The start button
    private TextView mWarningTextView; // Show warnings from recognizer
    private TextView mErrorTextView; // Show errors from recognizer

    // Text displayed on start button
    private static final String BUTTON_TEXT_START = "Start";
    private static final String BUTTON_TEXT_STOP = "Stop";
    private static final String BUTTON_TEXT_STARTING = "Starting...";

    private final static Uploader mUploader = new Uploader();

    private IRecognitionService.ResultStabilityStatus previousResultStatus = null;

    private static boolean mIsUploading = false;

    public static class UploadTextTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            mUploader.uploadText(params[0]);
            return null;
        }

    }

    public static class UploadImageTask extends AsyncTask<byte[], Void, Void> {

        private int mCameraPreviewWidth;
        private int mCameraPreviewHeight;

        public UploadImageTask(int width, int height) {
            mCameraPreviewWidth = width;
            mCameraPreviewHeight = height;
        }

        @Override
        protected Void doInBackground(final byte[]... params) {
            byte[] jpegBytes = convertToJpegBytes(params[0]);
            if (jpegBytes != null) {
                mUploader.uploadImage(jpegBytes);
            }
            return null;
        }

        private byte[] convertToJpegBytes(byte[] rawBytes) {
            YuvImage yuvImage = new YuvImage(
                    rawBytes,
                    ImageFormat.NV21,
                    mCameraPreviewWidth,
                    mCameraPreviewHeight,
                    null
            );

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                yuvImage.compressToJpeg(
                        new Rect(0, 0, mCameraPreviewWidth, mCameraPreviewHeight),
                        40,
                        os
                );
                return os.toByteArray();
            } catch (IOException e) {
                Log.d(TAG, "compress error");
                return null;
            }
        }

        private static long startTime;
        private static float count = 0;

        @Override
        protected void onPostExecute(Void aVoid) {
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            } else {
                ++count;
                Log.d("FRAME_RATE", " " + count * 1000 / (float) (System.currentTimeMillis() - startTime));
            }

            mIsUploading = false;
        }
    }

    // To communicate with the Text Capture Service we will need this callback:
    private ITextCaptureService.Callback textCaptureCallback = new ITextCaptureService.Callback() {

        @Override
        public void onRequestLatestFrame(byte[] buffer) {
            // Метод хочет, чтобы мы заполнили полученный буфер новым кадром.
            // Мы делегируем это камере.
            mCamera.addCallbackBuffer(buffer);
        }

        @Override
        public void onFrameProcessed(
                ITextCaptureService.TextLine[] lines,
                ITextCaptureService.ResultStabilityStatus resultStatus, ITextCaptureService.Warning warning) {
            // Здесь мы получаем результаты обработки изображения, то есть текст

            if (resultStatus.ordinal() >= 3) {
                // Результаты достаточно стабильны, чтобы показать их пользователю
                mSurfaceViewWithOverlay.setLines(lines, resultStatus);
            } else {
                // Нестабильный результат, лучше ничего не показывать
                mSurfaceViewWithOverlay.setLines(null, ITextCaptureService.ResultStabilityStatus.NotReady);
            }

            // Показываем warnings
            mWarningTextView.setText(warning != null ? warning.name() : "");

            if (resultStatus == ITextCaptureService.ResultStabilityStatus.Stable
                    && previousResultStatus != ITextCaptureService.ResultStabilityStatus.Stable) {

                // mSurfaceViewWithOverlay.setFillBackground(false);
                StringBuilder sb = new StringBuilder();
                for (ITextCaptureService.TextLine line : lines) {
                    sb.append(line.Text + "\n");
                }

                // Отправляем результат на сервер
                new UploadTextTask().execute(sb.toString());
            }

            previousResultStatus = resultStatus;
        }

        @Override
        public void onError(Exception e) {
            // An error occurred while processing. Log it. Processing will continue
            Log.e(getString(R.string.app_name), "Error: " + e.getMessage());
            if (BuildConfig.DEBUG) {
                // Make the error easily visible to the developer
                String message = e.getMessage();
                if (message == null) {
                    message = "Unspecified error while creating the service. See logcat for details.";
                } else {
                    if (message.contains("ChineseJapanese.rom")) {
                        message =
                                "Chinese, Japanese and Korean are available in EXTENDED version only. Contact us for more information.";
                    }
                    if (message.contains("Russian.edc")) {
                        message =
                                "Cyrillic script languages are available in EXTENDED version only. Contact us for more information.";
                    } else if (message.contains(".trdic")) {
                        message = "Translation is available in EXTENDED version only. Contact us for more information.";
                    }
                }
                mErrorTextView.setText(message);
            }
        }
    };

    private Camera.PreviewCallback cameraPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            // Если пришло время отправлять (если уже ничего не отправляется)
            if (!mIsUploading) {
                mIsUploading = true;

                // Отправляем на сервер
                new UploadImageTask(mCameraPreviewSize.width, mCameraPreviewSize.height).execute(data);
            }


            // Заполняем полученный ранее буфер
            mTextCaptureService.submitRequestedFrame(data);
        }
    };

    // This callback is used to configure preview surface for the camera
    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // When surface is created, store the holder
            mPreviewSurfaceHolder = holder;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // When surface is changed (or created), attach it to the camera, configure mCamera and start preview
            if (mCamera != null) {
                setCameraPreviewDisplayAndStartPreview();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // When surface is destroyed, clear previewSurfaceHolder
            mPreviewSurfaceHolder = null;
        }
    };

    // Start recognition when autofocus completes (used when continuous autofocus is not enabled)
    private Camera.AutoFocusCallback startRecognitionCameraAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            onAutoFocusFinished(success, camera);
            startRecognition();
        }
    };

    // Simple autofocus callback
    private Camera.AutoFocusCallback simpleCameraAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            onAutoFocusFinished(success, camera);
        }
    };

    // Enable 'Start' button and switching to continuous focus mode (if possible) when autofocus completes
    private Camera.AutoFocusCallback finishCameraInitialisationAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            onAutoFocusFinished(success, camera);
            mStartButton.setText(BUTTON_TEXT_START);
            mStartButton.setEnabled(true);
            if (mStartRecognitionWhenReady) {
                startRecognition();
                mStartRecognitionWhenReady = false;
            }
        }
    };

    // Autofocus by tap
    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // if BUTTON_TEXT_STARTING autofocus is already in progress, it is incorrect to interrupt it
            if (!mStartButton.getText().equals(BUTTON_TEXT_STARTING)) {
                autoFocus(simpleCameraAutoFocusCallback);
            }
        }
    };

    private void onAutoFocusFinished(boolean success, Camera camera) {
        if (isContinuousVideoFocusModeEnabled(camera)) {
            setCameraFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else {
            if (!success) {
                autoFocus(simpleCameraAutoFocusCallback);
            }
        }
    }

    // Start autofocus (used when continuous autofocus is disabled)
    private void autoFocus(Camera.AutoFocusCallback callback) {
        if (mCamera != null) {
            try {
                setCameraFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                mCamera.autoFocus(callback);
            } catch (Exception e) {
                Log.e(getString(R.string.app_name), "Error: " + e.getMessage());
            }
        }
    }

    // Checks that FOCUS_MODE_CONTINUOUS_VIDEO supported
    private boolean isContinuousVideoFocusModeEnabled(Camera camera) {
        return camera.getParameters().getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    // Sets camera focus mode and focus area
    private void setCameraFocusMode(String mode) {
        mCamera.cancelAutoFocus();
    }

    // Attach the camera to the surface holder, configure the mCamera and start preview
    private void setCameraPreviewDisplayAndStartPreview() {
        try {
            mCamera.setPreviewDisplay(mPreviewSurfaceHolder);
        } catch (Throwable t) {
            Log.e(getString(R.string.app_name), "Exception in setPreviewDisplay()", t);
        }
        configureCameraAndStartPreview(mCamera);
    }

    // Stop preview and release the camera
    private void stopPreviewAndReleaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(null);
            stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // Stop preview if it is running
    private void stopPreview() {
        if (mInPreview) {
            mCamera.stopPreview();
            mInPreview = false;
        }
    }

    // Show error on startup if any
    private void showStartupError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("ABBYY RTR SDK")
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        MainActivity.this.finish();
                    }
                });
    }

    // Load ABBYY RTR SDK engine and configure the text capture service
    private boolean createTextCaptureService() {
        // Initialize the engine and text capture service
        try {
            mEngine = Engine.load(this, LICENSE_FILE_NAME);
            mTextCaptureService = mEngine.createTextCaptureService(textCaptureCallback);

            return true;
        } catch (java.io.IOException e) {
            // Troubleshooting for the developer
            Log.e(getString(R.string.app_name), "Error loading ABBYY RTR SDK:", e);
            showStartupError("Could not load some required resource files. Make sure to configure " +
                                     "'assets' directory in your application and specify correct 'license file name'. See logcat for details.");
        } catch (Engine.LicenseException e) {
            // Troubleshooting for the developer
            Log.e(getString(R.string.app_name), "Error loading ABBYY RTR SDK:", e);
            showStartupError("License not valid. Make sure you have a valid license file in the " +
                                     "'assets' directory and specify correct 'license file name' and 'application mFrameId'. See logcat for details.");
        } catch (Throwable e) {
            // Troubleshooting for the developer
            Log.e(getString(R.string.app_name), "Error loading ABBYY RTR SDK:", e);
            showStartupError("Unspecified error while loading the engine. See logcat for details.");
        }

        return false;
    }

    // Start recognition
    private void startRecognition() {
        // Do not switch off the screen while text capture service is running
        mPreviewSurfaceHolder.setKeepScreenOn(true);
        // Get area of interest (in coordinates of preview frames)
        Rect areaOfInterest = new Rect(mSurfaceViewWithOverlay.getAreaOfInterest());
        // Clear error message
        mErrorTextView.setText("");
        // Start the service
        mTextCaptureService.start(mCameraPreviewSize.width, mCameraPreviewSize.height, mOrientation, areaOfInterest);
        // Change the text on the start button to 'Stop'
        mStartButton.setText(BUTTON_TEXT_STOP);
        mStartButton.setEnabled(true);
    }

    // Stop recognition
    void stopRecognition() {
        // Disable the 'Stop' button
        mStartButton.setEnabled(false);

        // Stop the service asynchronously to make application more responsive. Stopping can take some time
        // waiting for all processing threads to stop
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                mTextCaptureService.stop();
                return null;
            }

            protected void onPostExecute(Void result) {
                if (mPreviewSurfaceHolder != null) {
                    // Restore normal power saving behaviour
                    mPreviewSurfaceHolder.setKeepScreenOn(false);
                }
                // Change the text on the stop button back to 'Start'
                mStartButton.setText(BUTTON_TEXT_START);
                mStartButton.setEnabled(true);
            }
        }.execute();
    }

    // Clear recognition results
    void clearRecognitionResults() {
        mSurfaceViewWithOverlay.setLines(null, ITextCaptureService.ResultStabilityStatus.NotReady);
        mSurfaceViewWithOverlay.setFillBackground(false);
    }

    // Returns orientation of camera
    private int getCameraOrientation() {
        Display display = getWindowManager().getDefaultDisplay();
        int orientation = 0;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
                orientation = 0;
                break;
            case Surface.ROTATION_90:
                orientation = 90;
                break;
            case Surface.ROTATION_180:
                orientation = 180;
                break;
            case Surface.ROTATION_270:
                orientation = 270;
                break;
        }
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return (cameraInfo.orientation - orientation + 360) % 360;
            }
        }
        // If Camera.open() succeed, this point of code never reached
        return -1;
    }

    private void configureCameraAndStartPreview(Camera camera) {
        // Setting camera parameters when preview is running can cause crashes on some android devices
        stopPreview();

        // Configure camera orientation. This is needed for both correct preview mOrientation
        // and recognition
        mOrientation = getCameraOrientation();
        camera.setDisplayOrientation(mOrientation);

        // Configure camera parameters
        Camera.Parameters parameters = camera.getParameters();

        // Select preview size. The preferred size for Text Capture scenario is 1080x720. In some scenarios you might
        // consider using higher resolution (small text, complex background) or lower resolution (better performance, less noise)
        mCameraPreviewSize = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.height <= 720 || size.width <= 720) {
                if (mCameraPreviewSize == null) {
                    mCameraPreviewSize = size;
                } else {
                    int resultArea = mCameraPreviewSize.width * mCameraPreviewSize.height;
                    int newArea = size.width * size.height;
                    if (newArea > resultArea) {
                        mCameraPreviewSize = size;
                    }
                }
            }
        }

        parameters.setPreviewSize(mCameraPreviewSize.width, mCameraPreviewSize.height);

        // Buffer format. The only currently supported format is NV21
        parameters.setPreviewFormat(ImageFormat.NV21);

        // Done
        camera.setParameters(parameters);

        // The camera will fill the buffers with image data and notify us through the callback.
        // The buffers will be sent to camera on requests from recognition service (see implementation
        // of ITextCaptureService.Callback.onRequestLatestFrame above)
        camera.setPreviewCallbackWithBuffer(cameraPreviewCallback);

        // Clear the previous recognition results if any
        clearRecognitionResults();

        // Width and height of the preview according to the current screen rotation
        int width = 0;
        int height = 0;
        switch (mOrientation) {
            case 0:
            case 180:
                width = mCameraPreviewSize.width;
                height = mCameraPreviewSize.height;
                break;
            case 90:
            case 270:
                width = mCameraPreviewSize.height;
                height = mCameraPreviewSize.width;
                break;
        }

        // Configure the view scale and area of interest (camera sees it as rotated 90 degrees, so
        // there's some confusion with what is width and what is height)
        mSurfaceViewWithOverlay.setScaleX(mSurfaceViewWithOverlay.getWidth(), width);
        mSurfaceViewWithOverlay.setScaleY(mSurfaceViewWithOverlay.getHeight(), height);

        mSurfaceViewWithOverlay.setAreaOfInterest(
                new Rect(0, 0, width, height));

        // Start preview
        camera.startPreview();

        setCameraFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        autoFocus(finishCameraInitialisationAutoFocusCallback);

        mInPreview = true;
    }

    // Initialize recognition language spinner in the UI with available languages
    private void initializeRecognitionLanguageSpinner() {
        final Spinner languageSpinner = findViewById(R.id.recognitionLanguageSpinner);

        // Make the collapsed spinner the size of the selected item
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, R.layout.spinner_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                view.setLayoutParams(params);
                return view;
            }
        };

        for (int i = 0; i < LANGUAGES.length; i++) {
            String name = LANGUAGES[i].name();
            adapter.add(name);
        }

        languageSpinner.setAdapter(adapter);
        languageSpinner.setSelection(0);

        // The callback to be called when a language is selected
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String recognitionLanguage = (String) parent.getItemAtPosition(position);
                if (mTextCaptureService != null) {
                    // Reconfigure the recognition service each time a new language is selected
                    // This is also called when the spinner is first shown
                    mTextCaptureService.setRecognitionLanguage(Language.valueOf(recognitionLanguage));
                    clearRecognitionResults();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // The 'Start' and 'Stop' button
    public void onStartButtonClick(View view) {
        if (mStartButton.getText().equals(BUTTON_TEXT_STOP)) {
            stopRecognition();
        } else {
            clearRecognitionResults();
            mStartButton.setEnabled(false);
            mStartButton.setText(BUTTON_TEXT_STARTING);
            if (!isContinuousVideoFocusModeEnabled(mCamera)) {
                autoFocus(startRecognitionCameraAutoFocusCallback);
            } else {
                startRecognition();
            }
        }
    }

    void init() {
        setContentView(R.layout.activity_main);
        // Retrieve some ui components
        mWarningTextView = findViewById(R.id.warningText);
        mErrorTextView = findViewById(R.id.errorText);
        mStartButton = findViewById(R.id.startButton);

        // Initialize the recognition language spinner
        initializeRecognitionLanguageSpinner();

        // Manually create preview surface. The only reason for this is to
        // avoid making it public top level class
        RelativeLayout layout = (RelativeLayout) mStartButton.getParent();

        mSurfaceViewWithOverlay = new SurfaceViewWithOverlay(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        mSurfaceViewWithOverlay.setLayoutParams(params);
        // Add the surface to the layout as the bottom-most view filling the parent
        layout.addView(mSurfaceViewWithOverlay, 0);

        // Create text capture service
        if (createTextCaptureService()) {
            // Set the callback to be called when the preview surface is ready.
            // We specify it as the last step as a safeguard so that if there are problems
            // loading the engine the preview will never start and we will never attempt calling the service
            mSurfaceViewWithOverlay.getHolder().addCallback(surfaceCallback);
        }

        layout.setOnClickListener(clickListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        init();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reinitialize the camera, restart the preview and recognition if required

        mStartButton.setEnabled(false);
        clearRecognitionResults();
        mStartRecognitionWhenReady = START_RECOGNITION_ON_APP_START;
        mCamera = Camera.open();
        if (mPreviewSurfaceHolder != null) {
            setCameraPreviewDisplayAndStartPreview();
        }
    }

    @Override
    public void onPause() {
        // Clear all pending actions
        mHandler.removeCallbacksAndMessages(null);
        // Stop the text capture service
        if (mTextCaptureService != null) {
            mTextCaptureService.stop();
        }
        mStartButton.setText(BUTTON_TEXT_START);
        // Clear recognition results
        clearRecognitionResults();
        stopPreviewAndReleaseCamera();
        super.onPause();
    }
}