
package com.drbeef.razexr;


import static android.system.Os.setenv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.KeyEvent;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.drbeef.externalhapticsservice.HapticServiceClient;

@SuppressLint("SdCardPath") public class GLES3JNIActivity extends Activity implements SurfaceHolder.Callback
{
	private static String manufacturer = "";
	
	// Load the gles3jni library right away to make sure JNI_OnLoad() gets called as the very first thing.
	static
	{
		manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
		if (manufacturer.contains("oculus")) // rename oculus to meta as this will probably happen in the future anyway
		{
			manufacturer = "meta";
		}

		try
		{
			//Load manufacturer specific loader
			System.loadLibrary("openxr_loader_" + manufacturer);
			setenv("OPENXR_HMD", manufacturer, true);
		} catch (Exception e)
		{}
		
		System.loadLibrary( "raze" );
	}

	private static final String TAG = "RazeXR";

	private int permissionCount = 0;
	private static final int READ_EXTERNAL_STORAGE_PERMISSION_ID = 1;
	private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_ID = 2;

	private HapticServiceClient externalHapticsServiceClient = null;

	String commandLineParams;

	private SurfaceView mView;
	private SurfaceHolder mSurfaceHolder;
	private long mNativeHandle;

	private final boolean m_asynchronousTracking = false;
	
	@Override protected void onCreate( Bundle icicle )
	{
		Log.v( TAG, "----------------------------------------------------------------" );
		Log.v( TAG, "GLES3JNIActivity::onCreate()" );
		super.onCreate( icicle );

		mView = new SurfaceView( this );
		setContentView( mView );
		mView.getHolder().addCallback( this );

		// Force the screen to stay on, rather than letting it dim and shut off
		// while the user is watching a movie.
		getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

		// Force screen brightness to stay at maximum
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.screenBrightness = 1.0f;
		getWindow().setAttributes( params );

		checkPermissionsAndInitialize();
	}

	/** Initializes the Activity only if the permission has been granted. */
	private void checkPermissionsAndInitialize() {
		// Boilerplate for checking runtime permissions in Android.
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED){
			ActivityCompat.requestPermissions(
					GLES3JNIActivity.this,
					new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
					WRITE_EXTERNAL_STORAGE_PERMISSION_ID);
		}
		else
		{
			permissionCount++;
		}

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(
					GLES3JNIActivity.this,
					new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
					READ_EXTERNAL_STORAGE_PERMISSION_ID);
		}
		else
		{
			permissionCount++;
		}

		if (permissionCount == 2) {
			// Permissions have already been granted.
			create();
		}
	}

	/** Handles the user accepting the permission. */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
		if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_ID) {
			if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
				permissionCount++;
			}
			else
			{
				System.exit(0);
			}
		}

		if (requestCode == WRITE_EXTERNAL_STORAGE_PERMISSION_ID) {
			if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
				permissionCount++;
			}
			else
			{
				System.exit(0);
			}
		}

		checkPermissionsAndInitialize();
	}

	public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

	public void create()
	{
		copy_asset("/sdcard/RazeXR", "raze.pk3", true);
		copy_asset("/sdcard/RazeXR", "raze.sf2", false);
		copy_asset("/sdcard/RazeXR", "commandline.txt", false);
		copy_asset("/sdcard/RazeXR", "RazeXR.zip", true);
		unzip("/sdcard/RazeXR/RazeXR.zip", new File("/sdcard/RazeXR"));

		//Copy shareware duke if player doesn't own it
		copy_asset("/sdcard/RazeXR/raze/duke", "DUKE3D.GRP", false);

		//Now make paths for all the other games
		makePath("/sdcard/RazeXR/raze/blood");
		makePath("/sdcard/RazeXR/raze/shadowwarrior");
		makePath("/sdcard/RazeXR/raze/rampage");
		makePath("/sdcard/RazeXR/raze/ridesagain");
		makePath("/sdcard/RazeXR/raze/exhumed");
		makePath("/sdcard/RazeXR/raze/ww2gi");
		makePath("/sdcard/RazeXR/raze/nam");

		//Read these from a file and pass through
		commandLineParams = new String("raze");

		//See if user is trying to use command line params
		if(new File("/sdcard/RazeXR/commandline.txt").exists()) // should exist!
		{
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader("/sdcard/RazeXR/commandline.txt"));
				String s;
				StringBuilder sb=new StringBuilder(0);
				while ((s=br.readLine())!=null)
					sb.append(s + " ");
				br.close();

				commandLineParams = new String(sb.toString());
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		externalHapticsServiceClient = new HapticServiceClient(this, (state, desc) -> {
			Log.v(TAG, "ExternalHapticsService is:" + desc);
		});

		externalHapticsServiceClient.bindService();

		mNativeHandle = GLES3JNILib.onCreate( this, commandLineParams );
	}

	private void unzip(String fileZip, File destDir) {
		try {
			byte[] buffer = new byte[1024];
			ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				File newFile = newFile(destDir, zipEntry);
				if (zipEntry.isDirectory()) {
					if (!newFile.isDirectory() && !newFile.mkdirs()) {
						throw new IOException("Failed to create directory " + newFile);
					}
				} else {
					// fix for Windows-created archives
					File parent = newFile.getParentFile();
					if (!parent.isDirectory() && !parent.mkdirs()) {
						throw new IOException("Failed to create directory " + parent);
					}

					// write file content
					FileOutputStream fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
				zipEntry = zis.getNextEntry();
			}

			zis.closeEntry();
			zis.close();
		}
		catch (Exception e) {}
	}

	public void copy_asset(String path, String name, boolean force) {
		File f = new File(path + "/" + name);
		if (!f.exists() || force) {
			
			//Ensure we have an appropriate folder
			makePath(path);
			_copy_asset(name, path + "/" + name);
		}
	}

	private void makePath(String path) {
		new File(path).mkdirs();
	}

	public void _copy_asset(String name_in, String name_out) {
		AssetManager assets = this.getAssets();

		try {
			InputStream in = assets.open(name_in);
			OutputStream out = new FileOutputStream(name_out);

			copy_stream(in, out);

			out.close();
			in.close();

		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	public static void copy_stream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buf = new byte[1024];
		while (true) {
			int count = in.read(buf);
			if (count <= 0)
				break;
			out.write(buf, 0, count);
		}
	}

	public void shutdown() {
		System.exit(0);
	}

	@Override protected void onStart()
	{
		Log.v( TAG, "GLES3JNIActivity::onStart()" );
		super.onStart();

		GLES3JNILib.onStart( mNativeHandle, this );
	}

	@Override protected void onResume()
	{
		Log.v( TAG, "GLES3JNIActivity::onResume()" );
		super.onResume();

		GLES3JNILib.onResume( mNativeHandle );
	}

	@Override protected void onPause()
	{
		Log.v( TAG, "GLES3JNIActivity::onPause()" );
		GLES3JNILib.onPause( mNativeHandle );
		super.onPause();
	}

	@Override protected void onStop()
	{
		Log.v( TAG, "GLES3JNIActivity::onStop()" );
		GLES3JNILib.onStop( mNativeHandle );
		super.onStop();
	}

	@Override protected void onDestroy()
	{
		Log.v( TAG, "GLES3JNIActivity::onDestroy()" );

		if ( mSurfaceHolder != null )
		{
			GLES3JNILib.onSurfaceDestroyed( mNativeHandle );
		}

		GLES3JNILib.onDestroy( mNativeHandle );

		super.onDestroy();
		mNativeHandle = 0;
	}

	@Override public void surfaceCreated( SurfaceHolder holder )
	{
		Log.v( TAG, "GLES3JNIActivity::surfaceCreated()" );
		if ( mNativeHandle != 0 )
		{
			GLES3JNILib.onSurfaceCreated( mNativeHandle, holder.getSurface() );
			mSurfaceHolder = holder;
		}
	}

	@Override public void surfaceChanged( SurfaceHolder holder, int format, int width, int height )
	{
		Log.v( TAG, "GLES3JNIActivity::surfaceChanged()" );
		if ( mNativeHandle != 0 )
		{
			GLES3JNILib.onSurfaceChanged( mNativeHandle, holder.getSurface() );
			mSurfaceHolder = holder;
		}
	}
	
	@Override public void surfaceDestroyed( SurfaceHolder holder )
	{
		Log.v( TAG, "GLES3JNIActivity::surfaceDestroyed()" );
		if ( mNativeHandle != 0 )
		{
			GLES3JNILib.onSurfaceDestroyed( mNativeHandle );
			mSurfaceHolder = null;
		}
	}

	public void haptic_event(String event, int position, int intensity, float angle, float yHeight)  {

		if (externalHapticsServiceClient.hasService()) {
			try {
				//QuestZDoom doesn't use repeating patterns - set flags to 0
				int flags = 0;
				externalHapticsServiceClient.getHapticsService().hapticEvent(TAG, event, position, flags, intensity, angle, yHeight);
			}
			catch (RemoteException r)
			{
				Log.v(TAG, r.toString());
			}
		}
	}

	public void haptic_stopevent(String event) {

		if (externalHapticsServiceClient.hasService()) {
			try {
				externalHapticsServiceClient.getHapticsService().hapticStopEvent(TAG, event);
			}
			catch (RemoteException r)
			{
				Log.v(TAG, r.toString());
			}
		}
	}

	public void haptic_enable() {

		if (externalHapticsServiceClient.hasService()) {
			try {
				externalHapticsServiceClient.getHapticsService().hapticEnable();
			}
			catch (RemoteException r)
			{
				Log.v(TAG, r.toString());
			}
		}
	}

	public void haptic_disable() {

		if (externalHapticsServiceClient.hasService()) {
			try {
				externalHapticsServiceClient.getHapticsService().hapticDisable();
			}
			catch (RemoteException r)
			{
				Log.v(TAG, r.toString());
			}
		}
	}
}
