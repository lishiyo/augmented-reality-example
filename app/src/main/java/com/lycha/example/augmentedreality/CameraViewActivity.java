package com.lycha.example.augmentedreality;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.location.Location;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by krzysztofjackowski on 24/09/15.
 */
public class CameraViewActivity extends Activity implements
		SurfaceHolder.Callback, OnLocationChangedListener, OnAzimuthChangedListener{

	private Camera mCamera;
	private SurfaceHolder mSurfaceHolder;
	private boolean isCameraviewOn = false;
	private AugmentedPOI mPoi;

	private double mAzimuthReal = 0;
	private double mAzimuthTeoretical = 0;
	private static double AZIMUTH_ACCURACY = 5;
	private double mMyLatitude = 0;
	private double mMyLongitude = 0;

	private MyCurrentAzimuth myCurrentAzimuth;
	private MyCurrentLocation myCurrentLocation;

	TextView descriptionTextView;
	ImageView pointerIcon;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_view);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		setupListeners();
		setupLayout();
		setAugmentedRealityPoint();
	}

	private void setAugmentedRealityPoint() {
		mPoi = new AugmentedPOI(
				"Kościół Marciacki",
				"Kościół Marciacki w Krakowie",
				50.06169631,
				19.93919566
		);
	}

	/**
	 * http://www.igismap.com/formula-to-find-bearing-or-heading-angle-between-two-points-latitude-longitude/
	 */
	private double calculateTheoreticalAzimuthArc() {
		// azimuth = Math.atan2(dX, dY)
		// me = point 1, poi = point 2
		final double poiLat = mPoi.getPoiLatitude();
		final double poiLong = mPoi.getPoiLongitude();

		double dX = Math.cos(poiLat) * Math.sin(poiLong - mMyLongitude);
		double dY = Math.cos(mMyLatitude) * Math.sin(poiLat) - Math.sin(mMyLatitude) * Math.cos(poiLat) *
				Math.cos(poiLong - mMyLongitude);

		return Math.atan2(dX, dY); // returns degree
	}

	public double calculateTheoreticalAzimuth() {
		// (diff in longitude) / (diff in latitude)
		// x = acos( {sin(lat2) - sin(lat1) * cos(d) } / {sin(d)*cos(lat1)} )
		double dY = mPoi.getPoiLongitude() - mMyLongitude;
		double dX = mPoi.getPoiLatitude() - mMyLatitude;

		double phiAngle;
		double tanPhi;
		double azimuth = 0;

//		tanPhi = Math.abs(dY / dX);
		tanPhi = Math.abs(dX / dY);
		// tan => toDegrees
		phiAngle = Math.atan(tanPhi);
		phiAngle = Math.toDegrees(phiAngle);

		if (dX > 0 && dY > 0) { // I quarter
			azimuth = phiAngle;
		} else if (dX < 0 && dY > 0) { // II
			azimuth = 180 - phiAngle;
		} else if (dX < 0 && dY < 0) { // III
			azimuth = 180 + phiAngle;
		} else if (dX > 0 && dY < 0) { // IV
			azimuth = 360 - phiAngle;
		}

		return azimuth;
	}

	/**
	 * Return List {min, max}.
	 *
	 * @param azimuth
	 * @return
	 */
	private List<Double> calculateAzimuthAccuracy(double azimuth) {
		double minAngle = azimuth - AZIMUTH_ACCURACY;
		double maxAngle = azimuth + AZIMUTH_ACCURACY;
		List<Double> minMax = new ArrayList<Double>();

		if (minAngle < 0)
			minAngle += 360;

		if (maxAngle >= 360)
			maxAngle -= 360;

		minMax.clear();
		minMax.add(minAngle);
		minMax.add(maxAngle);

		return minMax;
	}

	private boolean isBetween(double minAngle, double maxAngle, double azimuth) {
		if (minAngle > maxAngle) {
			if (isBetween(0, maxAngle, azimuth) && isBetween(minAngle, 360, azimuth))
				return true;
		} else {
			if (azimuth > minAngle && azimuth < maxAngle)
				return true;
		}
		return false;
	}

	private void updateDescription() {
		descriptionTextView.setText(mPoi.getPoiName() + " azimuthTheoretical "
				+ mAzimuthTeoretical + " azimuthReal " + mAzimuthReal + " latitude "
				+ mMyLatitude + " longitude " + mMyLongitude);
	}

	@Override
	public void onLocationChanged(Location location) {
		mMyLatitude = location.getLatitude();
		mMyLongitude = location.getLongitude();
		mAzimuthTeoretical = calculateTheoreticalAzimuth();
		Toast.makeText(this,"latitude: "+location.getLatitude()+" longitude: "+location.getLongitude(), Toast.LENGTH_SHORT).show();
		updateDescription();
	}

	@Override
	public void onAzimuthChanged(float azimuthChangedFrom, float azimuthChangedTo) {
		mAzimuthReal = azimuthChangedTo;
		mAzimuthTeoretical = calculateTheoreticalAzimuth();

		pointerIcon = (ImageView) findViewById(R.id.icon);

		double minAngle = calculateAzimuthAccuracy(mAzimuthTeoretical).get(0);
		double maxAngle = calculateAzimuthAccuracy(mAzimuthTeoretical).get(1);

		if (isBetween(minAngle, maxAngle, mAzimuthReal)) {
			pointerIcon.setVisibility(View.VISIBLE);
		} else {
			pointerIcon.setVisibility(View.INVISIBLE);
		}

		updateDescription();
	}

	@Override
	protected void onStop() {
		myCurrentAzimuth.stop();
		myCurrentLocation.stop();
		super.onStop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		myCurrentAzimuth.start();
		myCurrentLocation.start();
	}

	private void setupListeners() {
		myCurrentLocation = new MyCurrentLocation(this);
		myCurrentLocation.buildGoogleApiClient(this);
		myCurrentLocation.start();

		myCurrentAzimuth = new MyCurrentAzimuth(this, this);
		myCurrentAzimuth.start();
	}

	private void setupLayout() {
		descriptionTextView = (TextView) findViewById(R.id.cameraTextView);

		getWindow().setFormat(PixelFormat.UNKNOWN);
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.cameraview);
		mSurfaceHolder = surfaceView.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
							   int height) {
		if (isCameraviewOn) {
			mCamera.stopPreview();
			isCameraviewOn = false;
		}

		if (mCamera != null) {
			try {
				mCamera.setPreviewDisplay(mSurfaceHolder);
				mCamera.startPreview();
				isCameraviewOn = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mCamera = Camera.open();
		mCamera.setDisplayOrientation(90);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
		isCameraviewOn = false;
	}
}
