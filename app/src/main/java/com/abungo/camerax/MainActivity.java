package com.abungo.camerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.Metadata;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import android.util.Size;
import pub.devrel.easypermissions.EasyPermissions;
//import com.karan.churi.PermissionManager.PermissionManager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnTouchListener {
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	LifecycleCameraController cameraController;
	CaptureRequestOptions captureRequestOptions;
	CameraSelector cameraSelector;
	PreviewView previewView;
	Range<Integer>[] fps;
	Camera camera;
	CameraInfo cameraInfo;
	CameraControl cameraControl;
	Button flip_button;
	ImageButton shutter_btn;
	TextView tv1;
	Slider zoom_slider;
	private ImageCapture imageCapture;
	private VideoCapture videoCapture;
	GestureDetector gestureDetector;
	BottomNavigationView bnv;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		shutter_btn = findViewById(R.id.shutter_btn);
		previewView = findViewById(R.id.previewView);
		flip_button = findViewById(R.id.flipcam);
		tv1 = findViewById(R.id.tv1);
		zoom_slider = findViewById(R.id.zoom_slider);
		bnv = findViewById(R.id.bnv1);
		previewView.setOnTouchListener(this);
		shutter_btn.setOnClickListener(this);
		flip_button.setOnClickListener(this);

		//Camera Selector
		cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
		String[] perms = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
		fps = new Range[2];
		fps[0] = Range.create(60, 60);
		// shutter button tag setting
		shutter_btn.setTag("camera");
		flip_button.setTag("back");
		//Bottom Nav View
		bnv.inflateMenu(R.menu.bnv_menu);
		bnv.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
			@Override
			public boolean onNavigationItemSelected(MenuItem item) {
				switch (item.getItemId()) {
				case R.id.photo:
					shutter_btn.setTag("camera");
					shutter_btn.setImageResource(R.drawable.capture_button_video);
					startPhoto();
					zoom_slider.setValue(0);
					return true;
				case R.id.video:
					shutter_btn.setTag("video");
					shutter_btn.setImageResource(R.drawable.capture_button_video_background);
					startVideo();
					zoom_slider.setValue(0);
					return true;
				case R.id.qrcode:
					Toast.makeText(MainActivity.this, "This Feature is not Implememted yet.", Toast.LENGTH_SHORT)
							.show();
					return false;
				}

				return false;
			}

		});

		//CaptureRequestOptions
		captureRequestOptions = new CaptureRequestOptions.Builder()
				.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
						CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
				.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
						CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
				//	.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fps[0])
				.build();

		cameraController = new LifecycleCameraController(this);
		//Starts here
		startPhoto();
		//set PreviewScale
		previewView.setScaleType(PreviewView.ScaleType.FIT_START);
		
		previewView.setController(cameraController);
		cameraController.bindToLifecycle(this);

		//Runtime Permission Request
		EasyPermissions.requestPermissions(MainActivity.this, "This App needs Camera & Audio Record Permission", 101,
				perms);

		//  Zoom Slider		
		zoom_slider.addOnChangeListener(new Slider.OnChangeListener() {
			@Override
			public void onValueChange(Slider arg0, float arg1, boolean arg2) {
				cameraControl.setLinearZoom(arg1);
			}
		});
	}

	private Executor getExecutor() {
		return ContextCompat.getMainExecutor(this);
	}

	//Touch Event Listener to set the slider position to what the pinch zoom set
	@Override
	public boolean onTouch(View view, MotionEvent event) {
		Camera2CameraControl.from(camera.getCameraControl()).setCaptureRequestOptions(captureRequestOptions);
		Float zl = cameraInfo.getZoomState().getValue().getLinearZoom();
		zoom_slider.setValue(zl);
		return false;
	}

	@SuppressLint("RestrictedApi")
	private void startVideo() {
		cameraController = new LifecycleCameraController(this);
		cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		cameraProviderFuture.addListener(() -> {
			try {
				ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
				cameraProvider.unbindAll();
				Preview preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
						//	.setTargetResolution(new Size(1080,1920))
						.build();

				preview.setSurfaceProvider(previewView.getSurfaceProvider());
				videoCapture = new VideoCapture.Builder().setTargetResolution(new Size(1080, 1920))
						.setVideoFrameRate(60).build();
				camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
				cameraInfo = camera.getCameraInfo();
				cameraControl = camera.getCameraControl();
				//tv1.setText(cameraInfo.getZoomState().getValue().toString());
				Float z = cameraInfo.getZoomState().getValue().getLinearZoom();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}, getExecutor());

	}

	@SuppressLint("RestrictedApi")
	private void startPhoto() {
		cameraController = new LifecycleCameraController(this);
		cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		cameraProviderFuture.addListener(() -> {
			try {
				ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
				cameraProvider.unbindAll();
				Preview preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
				preview.setSurfaceProvider(previewView.getSurfaceProvider());
				imageCapture = new ImageCapture.Builder()
						.setTargetAspectRatio(AspectRatio.RATIO_4_3)
						.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
						.build();
				camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
				cameraInfo = camera.getCameraInfo();
				cameraControl = camera.getCameraControl();
				Float z = cameraInfo.getZoomState().getValue().getLinearZoom();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}, getExecutor());

	}

	@SuppressLint("RestrictedApi")
	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.flipcam:
			if (flip_button.getTag() == "back") {
				cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT)
						.build();
				flip_button.setTag("front");
			} else if (flip_button.getTag() == "front") {
				cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
						.build();
				flip_button.setTag("back");
			}
			if (shutter_btn.getTag() == "camera") {
				startPhoto();
			} else if (shutter_btn.getTag() == "video") {
				startVideo();
			}
			break;
		case R.id.shutter_btn: {
			if (shutter_btn.getTag() == "camera") {
				capturePhoto();
			} else if (shutter_btn.getTag() == "video") {
				if (videoCapture != null) {
					recordVideo();
					shutter_btn.setTag("video_recording");
					shutter_btn.setImageResource(R.drawable.capture_button_video_recording);
				}
			} else if (shutter_btn.getTag() == "video_recording") {
				videoCapture.stopRecording();
				shutter_btn.setTag("video");
				shutter_btn.setImageResource(R.drawable.capture_button_video_background);
			}
			break;
		}

		}

	}

	@SuppressLint("RestrictedApi")
	private void recordVideo() {
		if (videoCapture != null) {
			long timeStamp = System.currentTimeMillis();
			ContentValues contentValues = new ContentValues();
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + timeStamp);
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
			contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);
			//contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.getExternalStoragePublicDirectory("Camera"));
			videoCapture.startRecording(
					new VideoCapture.OutputFileOptions.Builder(getContentResolver(),
							MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues).build(),
					getExecutor(), new VideoCapture.OnVideoSavedCallback() {
						@Override
						public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
							Toast.makeText(MainActivity.this, "Saving...", Toast.LENGTH_SHORT).show();
						}

						@Override
						public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
							Toast.makeText(MainActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
						}
					}

			);

		}
	}

	private void capturePhoto() {
		long timeStamp = System.currentTimeMillis();
		ContentValues contentValues = new ContentValues();
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + timeStamp);
		contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
		contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);

		imageCapture.takePicture(
				new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build(),
				getExecutor(), new ImageCapture.OnImageSavedCallback() {
					@Override
					public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
						Toast.makeText(MainActivity.this, "Saving...", Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onError(@NonNull ImageCaptureException exception) {
						Toast.makeText(MainActivity.this, "Error: " + exception.getMessage(), Toast.LENGTH_SHORT)
								.show();
					}

				});
	}

	// Function that takes care of Opening Gallery
	public void openGallery(View view) {
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_VIEW);
		intent.setType("image/*");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}
}