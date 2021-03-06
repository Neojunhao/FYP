package com.iritech.iddk.demo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TextView;
import android.widget.Toast;

import com.iritech.driver.UsbNotification;
import com.iritech.iddk.android.HIRICAMM;
import com.iritech.iddk.android.Iddk2000Apis;
import com.iritech.iddk.android.IddkCaptureMode;
import com.iritech.iddk.android.IddkCaptureOperationMode;
import com.iritech.iddk.android.IddkCaptureStatus;
import com.iritech.iddk.android.IddkCommStd;
import com.iritech.iddk.android.IddkComparisonResult;
import com.iritech.iddk.android.IddkConfig;
import com.iritech.iddk.android.IddkDeviceConfig;
import com.iritech.iddk.android.IddkDeviceInfo;
import com.iritech.iddk.android.IddkEyeSubType;
import com.iritech.iddk.android.IddkFloat;
import com.iritech.iddk.android.IddkImage;
import com.iritech.iddk.android.IddkImageFormat;
import com.iritech.iddk.android.IddkImageKind;
import com.iritech.iddk.android.IddkInteger;
import com.iritech.iddk.android.IddkIrisQuality;
import com.iritech.iddk.android.IddkQualityMode;
import com.iritech.iddk.android.IddkResult;
import com.iritech.iddk.android.IddkSleepCode;
import com.iritech.iddk.android.IddkTemplateInfo;

@SuppressLint("HandlerLeak")
public class IriShieldDemo extends TabActivity implements OnClickListener, OnTabChangeListener, DialogInterface.OnClickListener
{
	//private static final String _FORMAT_REJECT_MSG = "The captured image's quality is not sufficient for %s.\nPlease capture another image with subject's eye opened widely and moved slowly towards the camera.";
	//private static final String STR_REJECT_ENROLLMENT = String.format(_FORMAT_REJECT_MSG, "enrollment");
	//private static final String STR_REJECT_IDENTIFICATION = String.format(_FORMAT_REJECT_MSG, "identification");
	//private static final String STR_REJECT_VERIFICATION = String.format(_FORMAT_REJECT_MSG, "verification");
	//private static final String STR_WARNING_QUALITY_ENROLLMENT = "The captured image is enrollable but is not in sufficient quality to warrant the best accuracy."
	//		+ "The subject is recommended to have his/her iris image recaptured with the eye opened widely and moved slowly towards the camera.\n" + "Do you want to proceed anyway?";

	private static final String OUT_DIR = "/iritech/output/";	
	private static final int TAB_CAPTURE_INDEX_ID = 0x00;
	private static final int ABOUT_DIALOG_ID = 0x00;
	private static final int IRIS_VERIFY = 1;
	private static final int IRIS_ENROLL = 2;
	private static final int IRIS_ENROLL_MORE = 3;
	private static final int IRIS_IDENTIFY = 4;

	private static Iddk2000Apis mApis = null;
	private HIRICAMM mDeviceHandle = null;
	private IddkCaptureStatus mCurrentStatus = null;
	private IddkResult mCaptureResult = null;
	private MediaData mMediaData = null;
	private IddkCaptureInfo mCaptureInfo = null;
	private DemoConfig mManiaConfig = null;
	private UsbNotification mUsbNotification = null;
	private Spinner mListOfDevices = null;

	private TabHost mTabHost = null;
	private Dialog mAboutDialog = null;
	private TextView mStatusTextView = null;
	private ImageView mCaptureView = null;
	private ImageView mCaptureViewLeft = null;
	private Bitmap mCurrentBitmap = null;

	private static int mCaptureCount = 0;
	private boolean mIspreviewing = false;
	private boolean mIsBadQualityImage = false;
	private boolean mIsRegFirstTime = true;
	private boolean mIsMatchFirstTime = true;
	private int irisRegCurrentAction = 0;
	private int mTotalScore = 0;
	private int mUsableArea = 0;
	private String mCurrentDeviceName = "";
	private String mCurrentOutputDir = "";

	private boolean mIsGalleryLoaded = false;
	private boolean mIsCameraReady = false;
	private boolean mIsPermissionDenied = false;
	private boolean mIsJustError = false;
	private boolean mIsCheckDedup = true;

	private int mScreenWidth = 0;

	private enum eIdentifyResult
	{
		IRI_IDENTIFY_DIFFERENT, IRI_IDENTIFY_LOOKLIKE, IRI_IDENTIFY_SAME, IRI_IDENTIFY_DUPLICATED
	};

	// ///////////////////////////////////////////////////////////////////////////////////////////

	/*****************************************************************************
	 * Called when the activity is first created.
	 *****************************************************************************/
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		// Initialize the application
		initApp();
	}

	/*****************************************************************************
	 * Initialize the application.
	 *****************************************************************************/
	private void initApp()
	{
		initGUI();

		// Get an instance of the IDDK library
		mApis = Iddk2000Apis.getInstance(this);

		// Application data initialization
		mDeviceHandle = new HIRICAMM();
		mCurrentStatus = new IddkCaptureStatus();
		mCaptureResult = new IddkResult();
		mManiaConfig = new DemoConfig();
		mCaptureInfo = new IddkCaptureInfo();

		// This is an opt. But we should do it as a hobby
		IddkResult ret = new IddkResult();
		IddkConfig iddkConfig = new IddkConfig();
		iddkConfig.setCommStd(IddkCommStd.IDDK_COMM_USB);
		iddkConfig.setEnableLog(false);
		ret = Iddk2000Apis.setSdkConfig(iddkConfig);
		if (ret.getValue() != IddkResult.IDDK_OK)
		{
			showDialog(getResources().getString(R.string.warning), getResources().getString(R.string.sdkinit_error));
		}

		// Get notification instance
		mUsbNotification = UsbNotification.getInstance(this);

		// Register detached event for the IriShield
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);
	}

	/*****************************************************************************
	 * Draw each tab menu item content.
	 *****************************************************************************/
	private static View createTabView(final Context context, final String text, int icon)
	{
		View view = LayoutInflater.from(context).inflate(R.layout.tab_background_layout, null);
		TextView tv = (TextView) view.findViewById(R.id.tabsText);
		ImageView tabImage = (ImageView) view.findViewById(R.id.tabImageView);
		tabImage.setImageResource(icon);
		tv.setText(text);
		return view;
	}

	/*****************************************************************************
	 * Create tab menu.
	 *****************************************************************************/
	private void setupTab(final View view, final String tag, int res, String name)
	{
		TabHost.TabSpec spec = mTabHost.newTabSpec(tag).setIndicator(createTabView(mTabHost.getContext(), name, res)).setContent(new TabContentFactory()
		{
			public View createTabContent(String tag)
			{
				return view;
			}
		});
		mTabHost.addTab(spec);
	}

	/*****************************************************************************
	 * Initialize GUI of the application.
	 *****************************************************************************/
	private void initGUI()
	{
		// Setup tab interface
		mTabHost = getTabHost();
		mTabHost.getTabWidget().setDividerDrawable(R.drawable.ic_tab_divider);

		View view = LayoutInflater.from(this).inflate(R.layout.capture_layout, null);
		setupTab(view, "capture", R.drawable.ic_tab_capture_grey, getResources().getString(R.string.tab_capture));

		view = LayoutInflater.from(this).inflate(R.layout.registration_layout, null);
		setupTab(view, "registration", R.drawable.ic_tab_registration_grey, getResources().getString(R.string.tab_registration));

		view = LayoutInflater.from(this).inflate(R.layout.matching_layout, null);
		setupTab(view, "matching", R.drawable.ic_tab_matching_grey, getResources().getString(R.string.tab_matching));

		// Set "Capture" tab as default always
		mTabHost.setCurrentTab(TAB_CAPTURE_INDEX_ID);

		// Register event "tab changed"
		mTabHost.setOnTabChangedListener(this);

		view = findViewById(R.id.menu_button_id);
		view.setOnClickListener(this);

		view = findViewById(R.id.start_button_id);
		view.setOnClickListener(this);

		// We just enable this button when the camera is ready ... so disable it
		view.setEnabled(false);

		view = findViewById(R.id.stop_button_id);
		view.setOnClickListener(this);
		view.setEnabled(false);

		// Get status view
		mStatusTextView = (TextView) findViewById(R.id.time_textView);

		// This view is used to get streaming image (right eye if Binocular device)
		mCaptureView = (ImageView) findViewById(R.id.captureview_id);

		// This view is used to get left eye streaming image (if Binocular device)
		mCaptureViewLeft = (ImageView) findViewById(R.id.captureview_left_id);

		// Prepare media data for capturing process
		mMediaData = new MediaData(getApplicationContext());

		// Change background color for the action bar
		getActionBar().setBackgroundDrawable(new ColorDrawable(Color.argb(0xFF, 0x1E, 0x67, 0xCC)));

		// Initialize spinner (list of devices)
		mListOfDevices = (Spinner) findViewById(R.id.list_of_devices_id);

		// Get width of the device
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		mScreenWidth = metrics.widthPixels;

		Log.d("Screen Display", mScreenWidth + "");
	}

	/*****************************************************************************
	 * This function is used to scan and open IriShield. In case there are multiple IriShields attached to the Android system, IriShield at index 0 is opened as default.
	 *****************************************************************************/
	private void openDevice()
	{
		// Clear any internal states
		IddkResult ret = new IddkResult();
		mCaptureView.setImageBitmap(null);
		mCaptureViewLeft.setImageBitmap(null);
		mTabHost.setCurrentTab(TAB_CAPTURE_INDEX_ID);
		mIsCameraReady = false;
		mIsGalleryLoaded = false;
		mCurrentStatus.setValue(IddkCaptureStatus.IDDK_IDLE);
		mIspreviewing = false;

		// Disable start and stop buttons
		View view = findViewById(R.id.start_button_id);
		view.setEnabled(false);
		view = findViewById(R.id.stop_button_id);
		view.setEnabled(false);

		// Scan and open IriShield again
		ArrayList<String> deviceDescs = new ArrayList<String>();
		ret = mApis.scanDevices(deviceDescs);
		if (ret.intValue() == IddkResult.IDDK_OK && deviceDescs.size() > 0)
		{
			// Show the list of IriShields attached to the Android system
			updateListOfDevices(deviceDescs);

			// We open the IriShield at index 0 as default
			ret = mApis.openDevice(deviceDescs.get(0), mDeviceHandle);
			if (ret.intValue() == IddkResult.IDDK_OK || ret.intValue() == IddkResult.IDDK_DEVICE_ALREADY_OPEN)
			{
				// Check device version
				// Our Android SDK not working well with IriShield device version <= 2.24
				IddkDeviceInfo deviceInfo = new IddkDeviceInfo();
				ret = mApis.getDeviceInfo(mDeviceHandle, deviceInfo);
				if (ret.getValue() == IddkResult.IDDK_OK)
				{
					int majorVersion = deviceInfo.getKernelVersion();
					int minorVersion = deviceInfo.getKernelRevision();

					if (majorVersion <= 2 && minorVersion <= 24)
					{
						showDialog(getResources().getString(R.string.error), getResources().getString(R.string.opendevice_version_incompatible));
						return;
					}
				}
				else
				{
					// Error occurs here
					handleError(ret);
					return;
				}

				updateCurrentStatus(getString(R.string.opendevice_connected));

				// We can enable the start button from now
				view = findViewById(R.id.start_button_id);
				view.setEnabled(true);

				// Reset error status
				mIsJustError = false;

				// Save the current device name
				mCurrentDeviceName = deviceDescs.get(0);
			}
			else
			{
				// Device not found or something wrong occurs
				if (ret.getValue() == IddkResult.IDDK_DEVICE_ACCESS_DENIED)
				{
					updateCurrentStatus(getResources().getString(R.string.opendevice_accessdenied));
				}
				else
				{
					updateCurrentStatus(getResources().getString(R.string.opendevice_failed));
				}
			}
		}
		else
		{
			// There is no IriShield attached to the Android system
			updateListOfDevices(null);
			updateCurrentStatus(getResources().getString(R.string.opendevice_notfound));
		}
	}

	/*****************************************************************************
	 * Update current camera status to the user via GUI.
	 *****************************************************************************/
	private void updateCurrentStatus(final String newStatus)
	{
		mStatusTextView.post(new Runnable()
		{
			public void run()
			{
				mStatusTextView.setText(newStatus);
			}
		});
	}

	/*****************************************************************************
	 * This function is used to get capture information from menu settings. Before a capturing process starts, this function is re-called to check updated capture information.
	 *****************************************************************************/
	private int setNativeConfig()
	{
		// Reset
		mTotalScore = 0;
		mUsableArea = 0;
		irisRegCurrentAction = 0;

		// Get the preferences in settings menu
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		String operationMode = sharedPref.getString("operation_mode_pref", getString(R.string.settings_autocapture));
		if (operationMode.equals("0"))
		{
			mCaptureInfo.setCaptureOperationMode(IddkCaptureOperationMode.IDDK_AUTO_CAPTURE);
		}
		else if (operationMode.equals("1"))
		{
			mCaptureInfo.setCaptureOperationMode(IddkCaptureOperationMode.IDDK_OPERATOR_INITIATED_AUTO_CAPTURE);
		}

		String countStr = sharedPref.getString("count_interval_pref", "3");
		int iCount = 3;
		try
		{
			iCount = Integer.parseInt(countStr);
		}
		catch (NumberFormatException ex)
		{
			ex.printStackTrace();
			showDialog(getString(R.string.error) + " !", getString(R.string.settings_correct_number));
			return -1;
		}
		mCaptureInfo.setCount(iCount);

		String captureModeStr = sharedPref.getString("capture_mode_pref", "0");
		if (captureModeStr.equals("0"))
		{
			mCaptureInfo.setCaptureMode(IddkCaptureMode.IDDK_TIMEBASED);
		}
		else if (captureModeStr.equals("1"))
		{
			mCaptureInfo.setCaptureMode(IddkCaptureMode.IDDK_FRAMEBASED);
		}

		String qualityMode = sharedPref.getString("quality_mode_pref", "0");
		if (qualityMode.equals("0"))
		{
			mCaptureInfo.setQualitymode(IddkQualityMode.IDDK_QUALITY_NORMAL);
		}
		else if (qualityMode.equals("1"))
		{
			mCaptureInfo.setQualitymode(IddkQualityMode.IDDK_QUALITY_HIGH);
		}
		else if (qualityMode.endsWith("2"))
		{
			mCaptureInfo.setQualitymode(IddkQualityMode.IDDK_QUALITY_VERY_HIGH);
		}

		// Save image settings
		String prefixName = sharedPref.getString("prefix_name_pref", getString(R.string.settings_pre_default));
		mCaptureInfo.setPrefixName(prefixName + "_" + mCaptureCount);
		mCaptureCount++;

		boolean isSaveBestImages = sharedPref.getBoolean("best_images_pref", true);
		mCaptureInfo.setSaveBest(isSaveBestImages);

		String outputDirStr = sharedPref.getString("output_dir_pref", Environment.getExternalStorageDirectory().getPath() + OUT_DIR);
		if (!outputDirStr.trim().endsWith("/"))
		{
			outputDirStr += "/";
		}
		mCurrentOutputDir = outputDirStr;

		// Set current device configuration
		IddkDeviceConfig deviceConfig = new IddkDeviceConfig();
		IddkResult iRet = mApis.getDeviceConfig(mDeviceHandle, deviceConfig);
		boolean isShowImages = (iRet.getValue() == IddkResult.IDDK_OK) ? deviceConfig.isEnableStream() : true;
		if (isShowImages == false)
		{
			showDialog(getResources().getString(R.string.warning), getResources().getString(R.string.stream_disable));
		}
		
		mCaptureInfo.setShowStream(isShowImages);
		if (!isShowImages)
		{
			mCaptureView.setImageBitmap(null);
			mCaptureViewLeft.setImageBitmap(null);
		}

		mIsCheckDedup = sharedPref.getBoolean("check_dedup_pref", true);

		return 0;
	}

	/*****************************************************************************
	 * Handle "click" event for notification dialogs.
	 *****************************************************************************/
	public void onClick(DialogInterface dialog, int which)
	{
		if (which == DialogInterface.BUTTON_POSITIVE)
		{
			if (irisRegCurrentAction == IRIS_VERIFY)
			{
				verify();
			}
			else if (irisRegCurrentAction == IRIS_ENROLL || irisRegCurrentAction == IRIS_ENROLL_MORE)
			{
				if (irisRegCurrentAction == IRIS_ENROLL)
				{
					if (!mIsBadQualityImage)
						irisRegCurrentAction = IRIS_ENROLL_MORE;
				}
				enroll();
			}
		}
		else if (which == DialogInterface.BUTTON_NEGATIVE)
		{
			// Do nothing
		}

		if (irisRegCurrentAction == IRIS_ENROLL && mIsBadQualityImage)
		{
			irisRegCurrentAction = IRIS_ENROLL_MORE;
		}
		else
		{
			irisRegCurrentAction = 0;
		}
	}

	/*****************************************************************************
	 * Show notification dialog.
	 *****************************************************************************/
	void showDialog(String title, String message)
	{
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle(title);
		alertDialog.setMessage(message);
		alertDialog.setIcon(R.drawable.ic_menu_notifications);
		alertDialog.setButton(getString(R.string.ok), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				// Do nothing
			}
		});
		alertDialog.show();
	}

	/*****************************************************************************
	 * Show notification dialog.
	 *****************************************************************************/
	void showQuestionDialog(String title, String message)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title).setMessage(message).setIcon(R.drawable.ic_menu_notifications).setPositiveButton(getResources().getString(R.string.yes), this).setNegativeButton(getResources().getString(R.string.no), this);
		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	/*****************************************************************************
	 * Called when application exits.
	 *****************************************************************************/
	@Override
	protected void onDestroy()
	{
		// If we are in previewing, stop it
		stopCamera(false);

		// Release the handle
		mApis.closeDevice(mDeviceHandle);

		mUsbNotification.cancelNofitications();

		if (mUsbReceiver != null)
			unregisterReceiver(mUsbReceiver);

		super.onDestroy();
	}

	/*****************************************************************************
	 * When IriShield attached to Android system, this function will be called.
	 *****************************************************************************/
	@Override
	protected void onResume()
	{
		super.onResume();	

		if (!mIsCameraReady && !mIsPermissionDenied)
			openDevice();
	}

	/*****************************************************************************
	 * Application wants to pause and resume later.
	 *****************************************************************************/
	@Override
	protected void onPause()
	{
		stopCamera(false);
		super.onPause();
	}

	/*****************************************************************************
	 * Override this function so as to stop camera when menu settings is opened.
	 *****************************************************************************/
	@Override
	public boolean onKeyDown(int keycode, KeyEvent event)
	{
		if (keycode == KeyEvent.KEYCODE_MENU)
		{
			stopCamera(false);
		}
		return super.onKeyDown(keycode, event);
	}

	/*****************************************************************************
	 * This functions check the quality and usable area of the captured iris.
	 *****************************************************************************/
	private void doIrisRegWithQualityCheck(int[] qtotal, int[] qusable)
	{
		boolean isBinocularDevice = false;
		boolean isGrayZone = false;
		int numAcceptableEyes = 0;
		IddkInteger isBino = new IddkInteger();
		mApis.Iddk_IsBinocular(mDeviceHandle, isBino);
		isBinocularDevice = (isBino.getValue() == 1) ? true : false;
		int RIGHT_EYE_INDEX = 0;
		int LEFT_EYE_INDEX = 1;

		ArrayList<IddkIrisQuality> qualities = new ArrayList<IddkIrisQuality>();
		IddkInteger maxEyeSubtypes = new IddkInteger();
		IddkResult ret = new IddkResult();
		ret = mApis.getResultQuality(mDeviceHandle, qualities, maxEyeSubtypes);
		if ((ret.intValue() != IddkResult.IDDK_OK && ret.intValue() != IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED && ret.intValue() != IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED) || qualities.size() <= 0)
		{
			handleError(ret);
			return;
		}
		else
		{
			mIsJustError = false;

			if (isBinocularDevice) // Binocular device
			{
				if (IRIS_ENROLL == irisRegCurrentAction)
				{
					isGrayZone = ((qualities.get(RIGHT_EYE_INDEX).getTotalScore() > qtotal[0] && qualities.get(RIGHT_EYE_INDEX).getUsableArea() > qusable[0] && (qualities.get(RIGHT_EYE_INDEX).getTotalScore() <= qtotal[1] || qualities.get(
							RIGHT_EYE_INDEX).getUsableArea() <= qusable[1])) || (qualities.get(LEFT_EYE_INDEX).getTotalScore() > qtotal[0] && qualities.get(LEFT_EYE_INDEX).getUsableArea() > qusable[0] && (qualities.get(LEFT_EYE_INDEX)
							.getTotalScore() <= qtotal[1] || qualities.get(LEFT_EYE_INDEX).getUsableArea() <= qusable[1])));
				}
				else
				{
					// For matching, no grayzone
					isGrayZone = false;
				}

				// number of eyes with acceptable quality (not bad)
				if (qualities.get(RIGHT_EYE_INDEX).getTotalScore() > qtotal[0] && qualities.get(RIGHT_EYE_INDEX).getUsableArea() > qusable[0])
					numAcceptableEyes++;

				if (qualities.get(LEFT_EYE_INDEX).getTotalScore() > qtotal[0] && qualities.get(LEFT_EYE_INDEX).getUsableArea() > qusable[0])
					numAcceptableEyes++;

				if (numAcceptableEyes == 0)
				{
					mIsBadQualityImage = true;

					// Clear all captured images
					// mApis.clearCapture(mDeviceHandle, new IddkEyeSubType(IddkEyeSubType.IDDK_BOTH_EYE));

					if (irisRegCurrentAction == IRIS_VERIFY)
					{
						showDialog(getString(R.string.information), getString(R.string.verify_reject) + "\n" + getString(R.string.capture_again_require));
					}
					else if (irisRegCurrentAction == IRIS_ENROLL)
					{
						showDialog(getString(R.string.information), getString(R.string.enroll_reject)+ "\n" + getString(R.string.capture_again_require));
					}
					else if (irisRegCurrentAction == IRIS_IDENTIFY)
					{
						showDialog(getString(R.string.information), getString(R.string.identify_reject)+ "\n" + getString(R.string.capture_again_require));
					}

					return;
				}

				// We should clear the captured image for each session
				if (ret.intValue() != IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED && (qualities.get(RIGHT_EYE_INDEX).getTotalScore() <= qtotal[0] || qualities.get(RIGHT_EYE_INDEX).getUsableArea() <= qusable[0]))
				{
					// clear right eye
					// mApis.clearCapture(mDeviceHandle, new IddkEyeSubType(IddkEyeSubType.IDDK_RIGHT_EYE));
				}
				if (ret.intValue() != IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED && (qualities.get(LEFT_EYE_INDEX).getTotalScore() <= qtotal[0] || qualities.get(LEFT_EYE_INDEX).getUsableArea() <= qusable[0]))
				{
					// clear left eye
					// mApis.clearCapture(mDeviceHandle, new IddkEyeSubType(IddkEyeSubType.IDDK_LEFT_EYE));
				}

				if (IRIS_ENROLL == irisRegCurrentAction)
				{
					String infor = "";
					if (numAcceptableEyes == 1)
					{
						infor = getString(R.string.enroll_warning_onequalify);
					}
					if (isGrayZone)
					{
						infor += getString(R.string.enroll_warning);
					}
					if (numAcceptableEyes == 1 || isGrayZone)
					{
						mIsBadQualityImage = true;
						if (numAcceptableEyes == 1 && !isGrayZone)
						{
							infor += getString(R.string.enroll_proceed_ask);
						}
						showQuestionDialog(getResources().getString(R.string.confirmation), infor);
						return;
					}
				}

				mIsBadQualityImage = false;
			}
			else
			// Monocular device
			{
				mTotalScore = (int) qualities.get(0).getTotalScore();
				mUsableArea = (int) qualities.get(0).getUsableArea();

				if (mTotalScore > qtotal[1] && mUsableArea > qusable[1])
				{
					mIsBadQualityImage = false;
				}
				else if (mTotalScore <= qtotal[0] || mUsableArea <= qusable[0])
				{
					mIsBadQualityImage = true;

					// Clear all captured images
					// mApis.clearCapture(mDeviceHandle, new IddkEyeSubType(IddkEyeSubType.IDDK_UNKNOWN_EYE));

					if (irisRegCurrentAction == IRIS_VERIFY)
					{
						showDialog(getString(R.string.information), getString(R.string.verify_reject));
					}
					else if (irisRegCurrentAction == IRIS_ENROLL)
					{
						showDialog(getString(R.string.information), getString(R.string.enroll_reject));
					}
					else if (irisRegCurrentAction == IRIS_IDENTIFY)
					{
						showDialog(getString(R.string.information), getString(R.string.identify_reject));
					}

					return;
				}
				else if (IRIS_ENROLL == irisRegCurrentAction)
				{
					mIsBadQualityImage = true;
					showQuestionDialog(getString(R.string.confirmation), getString(R.string.enroll_warning));
					return;
				}
			}
		}

		if (irisRegCurrentAction == IRIS_VERIFY)
		{
			verify();
		}
		else if (irisRegCurrentAction == IRIS_ENROLL)
		{
			enroll();
		}
		else if (irisRegCurrentAction == IRIS_IDENTIFY)
		{
			StringBuffer enrollId = new StringBuffer();
			eIdentifyResult identifyResult = identify(enrollId);
			if (eIdentifyResult.IRI_IDENTIFY_SAME == identifyResult || eIdentifyResult.IRI_IDENTIFY_DUPLICATED == identifyResult)
			{
				showDialog(getResources().getString(R.string.identification), getString(R.string.welcome) + " '" + enrollId.toString() + "'!\n" + getString(R.string.identify_successful));
			}
			else if (eIdentifyResult.IRI_IDENTIFY_DIFFERENT == identifyResult)
			{
				showDialog(getResources().getString(R.string.identification), getString(R.string.identify_no_match));
			}
			else
			// In greyzone, just looklike but not sure
			{
				showDialog(getResources().getString(R.string.identification), getString(R.string.identify_greyzone_suggest_retake_iris));
			}
		}
	}

	/*****************************************************************************
	 * This function is used to compare the current captured iris with a specified ID's iris.
	 *****************************************************************************/
	private void verify()
	{
		IddkResult ret = new IddkResult();
		EditText verifyEdit = (EditText) findViewById(R.id.verify_textedit_id);
		if (verifyEdit.getText().toString().trim().equals(""))
		{
			showDialog(getString(R.string.error), getString(R.string.verify_enter_enrollee_ID));
			return;
		}

		IddkFloat distance = new IddkFloat();
		float mindis = 4.0f;
		ret = mApis.compare11(mDeviceHandle, verifyEdit.getText().toString().trim(), distance);
		if (ret.intValue() == IddkResult.IDDK_OK)
		{
			if (mindis > distance.getValue())
				mindis = distance.getValue();
			mIsJustError = false;
		}
		else
		{
			handleError(ret);
			return;
		}

		if (mindis <= mManiaConfig.th_matching_distance[0])
		{
			showDialog(getString(R.string.verification), getString(R.string.welcome) + " '" + verifyEdit.getText().toString().trim() + "'.\n" + getString(R.string.identify_successful));

		}
		else if (mindis > mManiaConfig.th_matching_distance[1])
		{
			showDialog(getString(R.string.information), getString(R.string.verify_no_match) + verifyEdit.getText().toString().trim() + "'");
		}
		else
		{
			if (mTotalScore > mManiaConfig.th_enroll_totalscore[1] && mUsableArea > mManiaConfig.th_enroll_usablearea[1])
			{
				showDialog(getString(R.string.information), getString(R.string.verify_no_match) + verifyEdit.getText().toString().trim() + "'");
			}
			else
			{

				showDialog(getString(R.string.information), getString(R.string.identify_greyzone_suggest_retake_iris));
			}
		}
	}

	/*****************************************************************************
	 * Identify whether the specified ID's iris is enrolled before
	 *****************************************************************************/
	private eIdentifyResult identify(StringBuffer enrollId)
	{
		eIdentifyResult identifyResult = eIdentifyResult.IRI_IDENTIFY_DIFFERENT;
		IddkResult ret = new IddkResult();
		String result = "";

		ArrayList<IddkComparisonResult> comparisonResults = new ArrayList<IddkComparisonResult>();
		ret = mApis.compare1N(mDeviceHandle, 2.0f, comparisonResults);
		if (ret.getValue() == IddkResult.IDDK_OK && comparisonResults.size() > 0)
		{
			int i = 0;
			float mindis = 4.0f;
			for (i = 0; i < comparisonResults.size(); i++)
			{
				if (ret.intValue() == IddkResult.IDDK_OK)
				{
					if (mindis > comparisonResults.get(i).getDistance())
					{
						mindis = comparisonResults.get(i).getDistance();
						result = comparisonResults.get(i).getEnrollId();
					}
				}
			}

			if (mindis < mManiaConfig.th_dedup_distance)
			{
				identifyResult = eIdentifyResult.IRI_IDENTIFY_DUPLICATED;
			}
			else if (mindis <= mManiaConfig.th_matching_distance[0])
			{
				identifyResult = eIdentifyResult.IRI_IDENTIFY_SAME;
			}
			else if (mindis <= mManiaConfig.th_matching_distance[1])
			{
				identifyResult = eIdentifyResult.IRI_IDENTIFY_LOOKLIKE;
			}

			if (identifyResult != eIdentifyResult.IRI_IDENTIFY_DIFFERENT)
				enrollId.append(result);
		}

		if (ret.getValue() == IddkResult.IDDK_OK)
			mIsJustError = false;

		return identifyResult;
	}

	/*****************************************************************************
	 * Enroll current captured iris into the gallery.
	 *****************************************************************************/
	private void enroll()
	{
		EditText textEdit = (EditText) findViewById(R.id.textedit_id);
		IddkResult ret = new IddkResult();

		// Get the preferences
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mIsCheckDedup = sharedPref.getBoolean("check_dedup_pref", true);

		// Check deduplication before enrollment
		IddkDeviceConfig deviceConfig = new IddkDeviceConfig();
		ret = mApis.getDeviceConfig(mDeviceHandle, deviceConfig);
		if (ret.getValue() == IddkResult.IDDK_OK)
		{
			if (mIsCheckDedup != deviceConfig.isEnableDeduplication())
			{
				deviceConfig.setEnableDeduplication(mIsCheckDedup);
				ret = mApis.setDeviceConfig(mDeviceHandle, deviceConfig);
				if (ret.getValue() == IddkResult.IDDK_SEC_PRIVILEGE_RESTRICTED)
				{
					showDialog(getString(R.string.warning), (mIsCheckDedup ? getString(R.string.permission_warning_1) : getString(R.string.permission_warning_2)));
				}
				else if (ret.getValue() != IddkResult.IDDK_OK)
				{
					handleError(ret);
					return;
				}
			}
		}
		else
		{
			handleError(ret);
			return;
		}

		if (irisRegCurrentAction == IRIS_ENROLL_MORE)
		{
			ret = mApis.enrollCapture(mDeviceHandle, textEdit.getText().toString().trim());
		}
		else
		{
			// Check as if the specified ID can be enrolled more
			IddkTemplateInfo templateInfo = new IddkTemplateInfo();
			ret = mApis.getEnrolleeInfo(mDeviceHandle, textEdit.getText().toString().trim(), templateInfo);
			if (ret.intValue() == IddkResult.IDDK_OK)
			{
				// The maximum number of irises per ID is 8
				if (templateInfo.getTotalIrisCount() == 8)
				{
					showDialog(getString(R.string.information), getString(R.string.enroll_theID) + " '" + textEdit.getText().toString().trim() + "' "+getString(R.string.enroll_been_enrolled)+" " + templateInfo.getTotalIrisCount() + " "+getString(R.string.enroll_iris)+".\n" + getString(R.string.enroll_more_ask));
				}
				else
				{
					showQuestionDialog(getString(R.string.information), getString(R.string.enroll_theID) + " '" + textEdit.getText().toString().trim() + "' "+getString(R.string.enroll_been_enrolled)+" " + templateInfo.getTotalIrisCount() + " "+getString(R.string.enroll_iris));
				}
				mIsJustError = false;
				return;
			}
			else
			{
				ret = mApis.enrollCapture(mDeviceHandle, textEdit.getText().toString().trim());
			}
		}

		if (ret.intValue() == IddkResult.IDDK_OK)
		{
			ret = mApis.commitGallery(mDeviceHandle);
			if (ret.getValue() == IddkResult.IDDK_OK)
			{
				showDialog(getString(R.string.information), getString(R.string.enroll_succesful));
				mIsJustError = false;
			}
			else
			{
				handleError(ret);
			}
		}
		else if (ret.intValue() == IddkResult.IDDK_GAL_ENROLL_DUPLICATED)
		{
			showDialog(getString(R.string.error), getString(R.string.enroll_failed_already_enrolled));
		}
		else
		{
			handleError(ret);
		}
	}

	/*****************************************************************************
	 * Check as if the entered enroll identifier is valid or not
	 *****************************************************************************/
	public boolean checkEnrollID(String enrollID)
	{
		int i = 0;
		char[] ID = enrollID.toCharArray();
		for (i = 0; i < ID.length; i++)
		{
			if ((((ID[i] >= '0') && (ID[i] <= '9')) || ((ID[i] >= 'a') && (ID[i] <= 'z')) || ((ID[i] >= 'A') && (ID[i] <= 'Z'))) == false)
			{
				showDialog(getString(R.string.error), getString(R.string.enroll_id_wrong_format));
				return false;
			}
		}
		return true;
	}

	/*****************************************************************************
	 * Handle "click" event that may occur on any buttons in application graphical user interface.
	 *****************************************************************************/
	public void onClick(View v)
	{
		EditText textEdit = (EditText) findViewById(R.id.textedit_id);
		// An event occurs on start button
		if (v.getId() == R.id.start_button_id)
		{
			// Clear any images that appear in the previous session
			FrameLayout layout = (FrameLayout) findViewById(R.id.best_image_layout_id);
			layout.removeAllViews();
			layout.invalidate();

			// If we want to start a capturing process on another IriShield device, we have to check
			// the current selected device name on Spinner
			if (mListOfDevices.getSelectedItem().toString() == mCurrentDeviceName)
			{
				startCamera(true);
			}
			else
			{
				// User chooses another IriShield to start a capturing process. We must release any resources of the current IriShield device
				mApis.closeDevice(mDeviceHandle);

				// Reset any internal states of the application
				setInitState();

				// Get device handle and start a capturing process
				IddkResult ret = mApis.openDevice(mListOfDevices.getSelectedItem().toString(), mDeviceHandle);
				if (ret.intValue() == IddkResult.IDDK_OK || ret.intValue() == IddkResult.IDDK_DEVICE_ALREADY_OPEN)
				{
					updateCurrentStatus(getString(R.string.opendevice_connected));

					// We can enable the start button from now
					View view = findViewById(R.id.start_button_id);
					view.setEnabled(true);

					view = findViewById(R.id.stop_button_id);
					view.setEnabled(false);

					mIsJustError = false;
					mCurrentDeviceName = mListOfDevices.getSelectedItem().toString();

					startCamera(true);
				}
				else
				{
					if (ret.getValue() == IddkResult.IDDK_DEVICE_ACCESS_DENIED)
					{
						updateCurrentStatus(getString(R.string.opendevice_accessdenied));
					}
					else
					{
						updateCurrentStatus(getString(R.string.opendevice_failed));
					}
				}
			}
		}
		else if (v.getId() == R.id.stop_button_id)
		{
			// An event occurs on stop button
			mMediaData.captureAbortedPlayer.start();
			mCurrentStatus.setValue(IddkCaptureStatus.IDDK_ABORT);
			updateCurrentStatus(getString(R.string.capture_aborted));
			stopCamera(false);
		}
		else if (v.getId() == R.id.enroll_button_id)
		{
			// An event occurs on enroll button
			if (!textEdit.getText().toString().trim().equals(""))
			{
				if (checkEnrollID(textEdit.getText().toString().trim()) == false)
					return;
				irisRegCurrentAction = IRIS_ENROLL;
				doIrisRegWithQualityCheck(mManiaConfig.th_enroll_totalscore, mManiaConfig.th_enroll_usablearea);
			}
			else
			{
				showDialog(getString(R.string.error), getString(R.string.verify_enter_enrollee_ID));
			}
		}
		else if (v.getId() == R.id.unenroll_button_id)
		{
			// An event occurs on unenroll button
			if (!textEdit.getText().toString().trim().equals(""))
			{
				if (checkEnrollID(textEdit.getText().toString().trim()) == false)
					return;

				IddkResult ret = mApis.unenrollTemplate(mDeviceHandle, textEdit.getText().toString().trim());

				if (ret.intValue() == IddkResult.IDDK_OK)
				{
					showDialog(getString(R.string.information), getString(R.string.unenroll_succesfully));

					// Save the current changes to the gallery
					ret = mApis.commitGallery(mDeviceHandle);
					if (ret.getValue() != IddkResult.IDDK_OK)
					{
						handleError(ret);
					}
					else
					{
						mIsJustError = false;
					}
				}
				else if (ret.getValue() == IddkResult.IDDK_GAL_ID_NOT_EXIST)
				{
					showDialog(getString(R.string.information), getString(R.string.enroll_id_not_exist));
					mIsJustError = false;
				}
				else
				{
					handleError(ret);
				}
			}
			else
			{
				showDialog(getString(R.string.error), getString(R.string.verify_enter_enrollee_ID));
			}
		}
		else if (v.getId() == R.id.unenrollall_button_id)
		{
			// An event occurs on unenroll all button
			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(getString(R.string.warning));
			alertDialog.setMessage(getString(R.string.unenrollall_question));
			alertDialog.setIcon(R.drawable.ic_menu_notifications);
			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					IddkResult ret = mApis.unenrollTemplate(mDeviceHandle, null);
					if (ret.getValue() != IddkResult.IDDK_OK)
					{
						handleError(ret);
					}
					else
					{
						// Save the current changes to the gallery
						mApis.commitGallery(mDeviceHandle);
						if (ret.getValue() != IddkResult.IDDK_OK)
						{
							handleError(ret);
						}
						else
						{
							mIsJustError = false;
						}
					}
				}
			});
			alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					// do nothing
				}
			});
			alertDialog.show();
		}
		else if (v.getId() == R.id.verify_button_id)
		{
			// An event occurs on verify button
			EditText verifyEdit = (EditText) findViewById(R.id.verify_textedit_id);
			if (!verifyEdit.getText().toString().trim().equals(""))
			{
				if (checkEnrollID(verifyEdit.getText().toString().trim()) == false)
					return;
				irisRegCurrentAction = IRIS_VERIFY;

				doIrisRegWithQualityCheck(mManiaConfig.th_matching_totalscore, mManiaConfig.th_matching_usablearea);
			}
			else
			{
				showDialog(getString(R.string.error), getString(R.string.verify_enter_enrollee_ID));
			}
		}
		else if (v.getId() == R.id.identify_button_id)
		{
			// An event occurs on identify button
			irisRegCurrentAction = IRIS_IDENTIFY;
			doIrisRegWithQualityCheck(mManiaConfig.th_matching_totalscore, mManiaConfig.th_matching_usablearea);
		}
	}

	/*****************************************************************************
	 * Initialize camera and start a capturing process
	 *****************************************************************************/
	private void startCamera(boolean sound)
	{
		IddkResult ret = new IddkResult();
		if (!mIsCameraReady)
		{
			IddkInteger imageWidth = new IddkInteger();
			IddkInteger imageHeight = new IddkInteger();
			ret = mApis.initCamera(mDeviceHandle, imageWidth, imageHeight);
			if (ret.intValue() != IddkResult.IDDK_OK)
			{
				updateCurrentStatus(getString(R.string.cam_not_init));
				handleError(ret);
				return;
			}

			mIsCameraReady = true;

			updateCurrentStatus(getString(R.string.cam_ready));
		}
		if (!mIspreviewing)
		{
			// We disable the start button
			View button = findViewById(R.id.start_button_id);
			button.setEnabled(false);
			button = findViewById(R.id.stop_button_id);
			button.setEnabled(true);

			if (sound)
			{
				mMediaData.moveEyeClosePlayer.start();
			}
			mCurrentStatus.setValue(IddkCaptureStatus.IDDK_IDLE);

			// Get the current setting values
			if (setNativeConfig() < 0)
			{
				updateCurrentStatus(getString(R.string.config_get_failed));
				return;
			}

			// Start a capturing process
			CaptureTask captureTask = new CaptureTask(mCaptureView, mCaptureViewLeft);
			captureTask.execute(mApis, mCaptureResult, mCurrentStatus);

			mIspreviewing = true;
		}
	}

	/*****************************************************************************
	 * Stop the capture and deinit camera
	 *****************************************************************************/
	private IddkResult stopCamera(boolean sound)
	{
		IddkResult iRet = new IddkResult();
		iRet.setValue(IddkResult.IDDK_OK);
		if (mIspreviewing)
		{
			// We enable the start button
			View button = findViewById(R.id.start_button_id);
			button.setEnabled(true);
			button = findViewById(R.id.stop_button_id);
			button.setEnabled(false);

			if (sound)
			{
				mMediaData.captureFinishedPlayer.start();
				try
				{
					Thread.sleep(500);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}

			iRet = mApis.stopCapture(mDeviceHandle);
			if (iRet.getValue() != IddkResult.IDDK_OK)
			{
				handleError(iRet);
				return iRet;
			}

			mIspreviewing = false;
		}

		if (mIsCameraReady)
		{
			iRet = mApis.deinitCamera(mDeviceHandle);
			if (iRet.getValue() != IddkResult.IDDK_OK)
			{
				handleError(iRet);
				return iRet;
			}

			mIsCameraReady = false;
		}
		return iRet;
	}

	/*****************************************************************************
	 * Override function onOptionsItemSelected to render the menu bar's items. There are three menu items appearing on the menu bar: settings, exit and about.
	 *****************************************************************************/
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		new MenuInflater(this).inflate(R.menu.option, menu);

		return (super.onCreateOptionsMenu(menu));
	}

	/*****************************************************************************
	 * Handle click events appearing on the menu bar's item.
	 *****************************************************************************/
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == R.id.settings_menu)
		{
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;
		}
		else if (item.getItemId() == R.id.about_menu)
		{
			showDialog(ABOUT_DIALOG_ID);
			return true;
		}
		else if (item.getItemId() == R.id.exit_menu)
		{
			stopCamera(false);

			// Release the handle
			if (mApis != null)
			{
				mApis.closeDevice(mDeviceHandle);
			}

			finish();
			return true;
		} else if (item.getItemId() == R.id.sleep_menu){
			Dialog sleepDialog = new Dialog(this);
			sleepDialog.setContentView(R.layout.sleep_layout);
			sleepDialog.setTitle(getString(R.string.sleep_mode_title));
			Button stanbyButton = (Button) sleepDialog.findViewById(R.id.b_standby);
			Button sleepButton = (Button) sleepDialog.findViewById(R.id.b_sleep);
			Button deepSleepButton = (Button) sleepDialog.findViewById(R.id.b_deep_sleep);
			final Spinner spinner = (Spinner) sleepDialog.findViewById(R.id.sleep_spinner);
			final EditText sleepAfter = (EditText) sleepDialog.findViewById(R.id.sleep_after_input);

			Button sleepApply = (Button) sleepDialog.findViewById(R.id.b_sleep_apply);

			final IddkDeviceConfig deviceConfig = new IddkDeviceConfig();
			IddkResult ret = mApis.getDeviceConfig(mDeviceHandle, deviceConfig);
			byte sleepMode = -1;
			short sleepTimeout = -1;
			if (ret.getValue() == IddkResult.IDDK_OK){
				sleepMode = deviceConfig.getSleepMode();
				sleepTimeout = deviceConfig.getSleepTimeout();
				if(sleepMode == IddkSleepCode.IDDK_PM_STANDBY){
					spinner.setSelection(0);
				} else if (sleepMode == IddkSleepCode.IDDK_PM_SLEEP){
					spinner.setSelection(1);
				}else if (sleepMode == IddkSleepCode.IDDK_PM_DEEPSLEEP){
					spinner.setSelection(2);
				}

				sleepAfter.setText(String.valueOf(sleepTimeout));
			} else if (ret.getValue() == IddkResult.IDDK_INVALID_HANDLE){
				Toast.makeText(getApplicationContext(), "Device not found!", Toast.LENGTH_LONG).show();
				return false;
			}

			stanbyButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					IddkSleepCode sleepCode = new IddkSleepCode(IddkSleepCode.IDDK_PM_STANDBY);
					IddkResult ret = mApis.sleepDevice(mDeviceHandle, sleepCode, 0);
					if (ret.getValue() == IddkResult.IDDK_OK){
						Toast.makeText(getApplicationContext(), "Set sleep mode Standby successful.", Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(getApplicationContext(), "Set sleep mode Standby failed: "+ret.toString(), Toast.LENGTH_LONG).show();
					}
				}
			});
			sleepButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					IddkSleepCode sleepCode = new IddkSleepCode(IddkSleepCode.IDDK_PM_SLEEP);
					IddkResult ret = mApis.sleepDevice(mDeviceHandle, sleepCode, 0);
					if (ret.getValue() == IddkResult.IDDK_OK){
						Toast.makeText(getApplicationContext(), "Set sleep mode Sleep successful.", Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(getApplicationContext(), "Set sleep mode Sleep failed: "+ret.toString(), Toast.LENGTH_LONG).show();
					}
				}
			});
			deepSleepButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					IddkSleepCode sleepCode = new IddkSleepCode(IddkSleepCode.IDDK_PM_DEEPSLEEP);
					IddkResult ret = mApis.sleepDevice(mDeviceHandle, sleepCode, 0);
					if (ret.getValue() == IddkResult.IDDK_OK){
						Toast.makeText(getApplicationContext(), "Set sleep mode Deep Sleep successful.", Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(getApplicationContext(), "Set sleep mode Deep Sleep failed: "+ret.toString(), Toast.LENGTH_LONG).show();
					}
				}
			});

			sleepApply.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					String after = sleepAfter.getText().toString();
					if (after.isEmpty()){
						Toast.makeText(getApplicationContext(), "Sleep After is empty!: ", Toast.LENGTH_LONG).show();
						return;
					}
					byte sleepMode = (byte) (spinner.getSelectedItemPosition());
					if(sleepMode == 0){
						sleepMode = IddkSleepCode.IDDK_PM_STANDBY;
					} else if (sleepMode == 1){
						sleepMode = IddkSleepCode.IDDK_PM_SLEEP;
					}else if (sleepMode == 2){
						sleepMode = IddkSleepCode.IDDK_PM_DEEPSLEEP;
					}
					deviceConfig.setSleepMode(sleepMode);
					deviceConfig.setSleepTimeout(Short.valueOf(after));
					IddkResult ret = mApis.setDeviceConfig(mDeviceHandle, deviceConfig);
					if (ret.getValue() == IddkResult.IDDK_OK){
						Toast.makeText(getApplicationContext(), "Apply sleep mode successful.", Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(getApplicationContext(), "Apply sleep mode failed!: "+ret.toString(), Toast.LENGTH_LONG).show();
					}

				}
			});
			sleepDialog.show();
		}
		return false;
	}

	/*****************************************************************************
	 * This functions is used to create customized about dialog.
	 *****************************************************************************/
	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
		case ABOUT_DIALOG_ID:
			AlertDialog.Builder builder;

			LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.about_layout, (ViewGroup) findViewById(R.id.about_dialog_id));
			builder = new AlertDialog.Builder(this);
			builder.setView(layout);
			mAboutDialog = builder.create();
			return mAboutDialog;
		}
		return null;
	}

	/*****************************************************************************
	 * This function handles event "tab changed" when user leaves the current tab to others. There are 3 tabs in GUI: capture, registration and matching.
	 *****************************************************************************/
	public void onTabChanged(String tabId)
	{
		if (tabId.equals("capture"))
		{
			// If errors just occur, we need to reset the internal state of the application
			if (mIsJustError)
			{
				setInitState();

				updateCurrentStatus(getString(R.string.connection_failed));
			}
		}
		else
		{
			// If user clicks on tab "registration" or "matching", a capture process must be made in advance
			if (mCurrentStatus.getValue() == IddkCaptureStatus.IDDK_COMPLETE)
			{
				// We check as if the gallery is loaded or not
				if (!mIsGalleryLoaded)
				{
					ArrayList<String> enrollIds = new ArrayList<String>();
					IddkInteger numOfUsedSlots = new IddkInteger();
					IddkInteger numOfMaxSlots = new IddkInteger();
					IddkResult ret = mApis.loadGallery(mDeviceHandle, enrollIds, null, numOfUsedSlots, numOfMaxSlots);
					if (ret.intValue() != IddkResult.IDDK_OK)
					{
						handleError(ret);
						mTabHost.setCurrentTab(TAB_CAPTURE_INDEX_ID);
						return;
					}
					mIsGalleryLoaded = true;
				}

				View view;
				if (tabId.equals("registration"))
				{
					// If this is the first time user clicks on tab "registration", we register click event
					// on three buttons: enroll, unenroll and unenroll all.
					if (mIsRegFirstTime)
					{
						view = findViewById(R.id.enroll_button_id);
						view.setOnClickListener(this);
						view = findViewById(R.id.unenroll_button_id);
						view.setOnClickListener(this);
						view = findViewById(R.id.unenrollall_button_id);
						view.setOnClickListener(this);
						mIsRegFirstTime = false;
					}
				}
				else if (tabId.equals("matching"))
				{
					// If this is the first time user clicks on tab "matching", we register click event
					// on buttons: verify and identify.
					if (mIsMatchFirstTime)
					{
						view = findViewById(R.id.verify_button_id);
						view.setOnClickListener(this);
						view = findViewById(R.id.identify_button_id);
						view.setOnClickListener(this);
						mIsMatchFirstTime = false;
					}
				}
			}
			else
			{
				showDialog(getString(R.string.warning), getString(R.string.capture_finish));
				mTabHost.setCurrentTab(TAB_CAPTURE_INDEX_ID);
			}
		}
	}

	/*****************************************************************************
	 * This asynchronous task is run simultaneously with the main thread to update the current streaming images to captureView. A capturing process is also implemented in this class.
	 *****************************************************************************/
	private class CaptureTask extends AsyncTask<Object, Bitmap, Integer>
	{
		ImageView captureView = null; // Right eye
		ImageView captureViewLeft = null; // Left eye
		IddkResult iRet;
		boolean isBinocularDevice = false;

		public CaptureTask(View captureView, View captureViewLeft)
		{
			this.captureView = (ImageView) captureView;
			this.captureViewLeft = (ImageView) captureViewLeft;

			if (captureViewLeft != null)
			{
				IddkInteger isBino = new IddkInteger();
				mApis.Iddk_IsBinocular(mDeviceHandle, isBino);
				isBinocularDevice = (isBino.getValue() == 1) ? true : false;
				if (isBinocularDevice == false) // Monocular device
				{
					this.captureViewLeft.setImageBitmap(null);
					this.captureViewLeft.setVisibility(View.INVISIBLE);
					this.captureViewLeft.getLayoutParams().height = 1;
					this.captureViewLeft.getLayoutParams().width = 1;

					this.captureView.getLayoutParams().width = mScreenWidth - 10;
					this.captureView.getLayoutParams().height = (this.captureView.getLayoutParams().width / 4) * 3;
				}
				else
				{
					this.captureViewLeft.setVisibility(View.VISIBLE);
					this.captureViewLeft.getLayoutParams().width = mScreenWidth / 2 - 5;
					this.captureViewLeft.getLayoutParams().height = (this.captureViewLeft.getLayoutParams().width / 4) * 3;

					this.captureView.getLayoutParams().width = mScreenWidth / 2 - 5;
					this.captureView.getLayoutParams().height = (this.captureView.getLayoutParams().width / 4) * 3;
				}
			}
		}

		/*****************************************************************************
		 * Capturing process is implemented here. It runs simultaneously with the main thread and update streaming images to captureView. After the capturing process ends, we get the best image and
		 * save it in a default directory.
		 *****************************************************************************/
		protected Integer doInBackground(Object... params)
		{
			ArrayList<IddkImage> monoImages = new ArrayList<IddkImage>();
			IddkCaptureStatus captureStatus = new IddkCaptureStatus(IddkCaptureStatus.IDDK_IDLE);

			iRet = (IddkResult) params[1];
			Iddk2000Apis mApis = (Iddk2000Apis) params[0];

			boolean bRun = true;
			boolean eyeDetected = false;
			IddkEyeSubType subType = null;
			if (isBinocularDevice)
			{
				subType = new IddkEyeSubType(IddkEyeSubType.IDDK_BOTH_EYE);
			}
			else
			{
				subType = new IddkEyeSubType(IddkEyeSubType.IDDK_UNKNOWN_EYE);
			}
			IddkInteger maxEyeSubtypes = new IddkInteger();

			iRet = mApis.startCapture(mDeviceHandle, mCaptureInfo.getCaptureMode(), mCaptureInfo.getCount(), mCaptureInfo.getQualitymode(), mCaptureInfo.getCaptureOperationMode(), subType, true, null);

			if (iRet.intValue() != IddkResult.IDDK_OK)
			{
				mCaptureResult = iRet;
				return -1;
			}

			while (bRun)
			{
				if (mCaptureInfo.isShowStream())
				{
					iRet = mApis.getStreamImage(mDeviceHandle, monoImages, maxEyeSubtypes, captureStatus);

					if (iRet.intValue() == IddkResult.IDDK_OK)
					{
						if (isBinocularDevice)
						{
							Bitmap streamImageLeft = null;
							Bitmap streamImageRight = null;
							if (subType.getValue() == IddkEyeSubType.IDDK_LEFT_EYE)
							{
								streamImageLeft = convertBitmap(monoImages.get(0).getImageData(), monoImages.get(0).getImageWidth(), monoImages.get(0).getImageHeight());
							}
							else if (subType.getValue() == IddkEyeSubType.IDDK_RIGHT_EYE)
							{
								streamImageRight = convertBitmap(monoImages.get(0).getImageData(), monoImages.get(0).getImageWidth(), monoImages.get(0).getImageHeight());
							}
							else
							// both eye
							{
								streamImageRight = convertBitmap(monoImages.get(0).getImageData(), monoImages.get(0).getImageWidth(), monoImages.get(0).getImageHeight());
								streamImageLeft = convertBitmap(monoImages.get(1).getImageData(), monoImages.get(1).getImageWidth(), monoImages.get(1).getImageHeight());
							}
							publishProgress(streamImageRight, streamImageLeft);
						}
						else
						{
							Bitmap streamImage = convertBitmap(monoImages.get(0).getImageData(), monoImages.get(0).getImageWidth(), monoImages.get(0).getImageHeight());
							publishProgress(streamImage);
						}
					}
					else if (iRet.intValue() == IddkResult.IDDK_SE_NO_FRAME_AVAILABLE)
					{
						// when GetStreamImage returns IDDK_SE_NO_FRAME_AVAILABLE,
						// it does not always mean that capturing process has been finished or encountered problems.
						// It may be because new stream images are not available.
						// We need to query the current capture status to know what happens.
						iRet = mApis.getCaptureStatus(mDeviceHandle, captureStatus);
						mCurrentStatus.setValue(captureStatus.getValue());
					}
				}
				else
				{
					iRet = mApis.getCaptureStatus(mDeviceHandle, captureStatus);
					mCurrentStatus.setValue(captureStatus.getValue());
					DemoUtility.sleep(60);
				}

				// If GetStreamImage and GetCaptureStatus cause no error, process the capture status
				if (iRet.intValue() == IddkResult.IDDK_OK)
				{
					// Eye(s) is(are) detected
					if (captureStatus.intValue() == IddkCaptureStatus.IDDK_CAPTURING)
					{
						if (!eyeDetected)
						{
							updateCurrentStatus(getString(R.string.capture_eyey_detected));
							mMediaData.eyeDetectedPlayer.start();
							eyeDetected = true;
							mCurrentStatus.setValue(IddkCaptureStatus.IDDK_CAPTURING);
						}
					}
					else if (captureStatus.intValue() == IddkCaptureStatus.IDDK_COMPLETE)
					{
						// Capture has finished
						updateCurrentStatus(getString(R.string.capture_finished));
						mMediaData.captureFinishedPlayer.start();
						bRun = false;
						mCurrentStatus.setValue(IddkCaptureStatus.IDDK_COMPLETE);
					}
					else if (captureStatus.intValue() == IddkCaptureStatus.IDDK_ABORT)
					{
						// Capture has been aborted
						bRun = false;
						mCurrentStatus.setValue(IddkCaptureStatus.IDDK_ABORT);
					}
				}
				else
				{
					// Terminate the capture if errors occur
					bRun = false;
				}
			}

			mCaptureResult = iRet;
			if (mCurrentStatus.getValue() == IddkCaptureStatus.IDDK_COMPLETE)
			{
				// Get the best image
				ArrayList<IddkImage> monoBestImage = new ArrayList<IddkImage>();
				iRet = mApis.getResultImage(mDeviceHandle, new IddkImageKind(IddkImageKind.IDDK_IKIND_K1), new IddkImageFormat(IddkImageFormat.IDDK_IFORMAT_MONO_RAW), (byte) 1, monoBestImage, maxEyeSubtypes);
				if ((!isBinocularDevice && iRet.intValue() == IddkResult.IDDK_OK)
						|| (isBinocularDevice && (iRet.intValue() == IddkResult.IDDK_OK || iRet.intValue() == IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED || iRet.intValue() == IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED)))
				{
					// Showing the best image so that user can see it
					Bitmap bestImage = null;
					Bitmap bestImageRight = null;
					Bitmap bestImageLeft = null;
					if (isBinocularDevice)
					{
						if (iRet.intValue() == IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED)
						{
							bestImageLeft = convertBitmap(monoBestImage.get(1).getImageData(), monoBestImage.get(1).getImageWidth(), monoBestImage.get(1).getImageHeight());
						}
						else if (iRet.intValue() == IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED)
						{
							bestImageRight = convertBitmap(monoBestImage.get(0).getImageData(), monoBestImage.get(0).getImageWidth(), monoBestImage.get(0).getImageHeight());
						}
						else
						// both eye
						{
							bestImageRight = convertBitmap(monoBestImage.get(0).getImageData(), monoBestImage.get(0).getImageWidth(), monoBestImage.get(0).getImageHeight());
							bestImageLeft = convertBitmap(monoBestImage.get(1).getImageData(), monoBestImage.get(1).getImageWidth(), monoBestImage.get(1).getImageHeight());
						}
						publishProgress(bestImageRight, bestImageLeft);
					}
					else
					{
						bestImage = convertBitmap(monoBestImage.get(0).getImageData(), monoBestImage.get(0).getImageWidth(), monoBestImage.get(0).getImageHeight());
						publishProgress(bestImage);
					}

					// Print the total score and usable area
					ArrayList<IddkIrisQuality> quality = new ArrayList<IddkIrisQuality>();
					iRet = mApis.getResultQuality(mDeviceHandle, quality, maxEyeSubtypes);
					if (isBinocularDevice)
					{
						if (mManiaConfig.th_qmscore_show)
						{
							if (iRet.intValue() == IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED)
							{
								updateCurrentStatus(getString(R.string.right_eye) + ": "+getString(R.string.total_score)+" = " + quality.get(0).getTotalScore() + ", "+getString(R.string.usable_area)+" = " + quality.get(0).getUsableArea());
							}
							else if (iRet.intValue() == IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED)
							{
								updateCurrentStatus(getString(R.string.left_eye) + ": "+getString(R.string.total_score)+" = " + quality.get(1).getTotalScore() + ", "+getString(R.string.usable_area)+" = " + quality.get(1).getUsableArea());
							}
							else if (iRet.intValue() == IddkResult.IDDK_OK)
							{
								updateCurrentStatus(getString(R.string.right_eye) + ": "+getString(R.string.total_score)+" = " + quality.get(0).getTotalScore() + ", "+getString(R.string.usable_area)+" = " + quality.get(0).getUsableArea() + "\n" + getString(R.string.left_eye) + ": "+getString(R.string.total_score)+" = " + quality.get(1).getTotalScore()
										+ ", "+getString(R.string.usable_area)+" = " + quality.get(1).getUsableArea());
							}
						}
					}
					else
					{
						if (mManiaConfig.th_qmscore_show)
							updateCurrentStatus(getString(R.string.total_score) + " = " + quality.get(0).getTotalScore() + ", "+getString(R.string.usable_area)+" = " + quality.get(0).getUsableArea());
					}

					// Save the best image
					if (mCaptureInfo.isSaveBest())
					{
						Calendar c = Calendar.getInstance();
						int date = c.get(Calendar.DATE);
						int month = c.get(Calendar.MONTH);
						int year = c.get(Calendar.YEAR);

						StringBuilder outputDirStr = new StringBuilder();
						outputDirStr.append(mCurrentOutputDir).append("/");
						outputDirStr.append(year).append("-").append(month).append("-").append(date).append("/");

						File file = new File(outputDirStr.toString());
						if (!file.exists() && !file.mkdirs())
						{
							updateCurrentStatus(getString(R.string.cannot_create_best_folder));
							return -1;
						}

						FileOutputStream out = null;
						FileOutputStream outLeft = null;
						FileOutputStream outRight = null;
						try
						{
							outputDirStr.append(mCaptureInfo.getPrefixName()).append("_").append(c.get(Calendar.HOUR_OF_DAY)).append("_").append(c.get(Calendar.MINUTE)).append("_").append(c.get(Calendar.SECOND)).append("_s")
									.append(quality.get(0).getTotalScore()).append("_u").append(quality.get(0).getUsableArea());

							if (isBinocularDevice)
							{
								if (iRet.getValue() == IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED)
								{
									outLeft = new FileOutputStream(outputDirStr.toString() + "_left.jpg");
									if (outLeft != null)
									{
										bestImageLeft.compress(Bitmap.CompressFormat.JPEG, 100, outLeft);
									}
									DemoUtility.SaveBin(outputDirStr.toString() + "_left.raw", monoBestImage.get(1).getImageData());
								}
								else if (iRet.getValue() == IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED)
								{
									outRight = new FileOutputStream(outputDirStr.toString() + "_right.jpg");
									if (outRight != null)
									{
										bestImageRight.compress(Bitmap.CompressFormat.JPEG, 100, outRight);
									}
									DemoUtility.SaveBin(outputDirStr.toString() + "_right.raw", monoBestImage.get(0).getImageData());
								}
								else if (iRet.getValue() == IddkResult.IDDK_OK)
								{
									outLeft = new FileOutputStream(outputDirStr.toString() + "_left.jpg");
									if (outLeft != null)
									{
										bestImageLeft.compress(Bitmap.CompressFormat.JPEG, 100, outLeft);
									}
									DemoUtility.SaveBin(outputDirStr.toString() + "_left.raw", monoBestImage.get(1).getImageData());

									outRight = new FileOutputStream(outputDirStr.toString() + "_right.jpg");
									if (outRight != null)
									{
										bestImageRight.compress(Bitmap.CompressFormat.JPEG, 100, outRight);
									}
									DemoUtility.SaveBin(outputDirStr.toString() + "_right.raw", monoBestImage.get(0).getImageData());
								}
							}
							else
							{
								out = new FileOutputStream(outputDirStr.toString() + ".jpg");
								if (out != null)
								{
									bestImage.compress(Bitmap.CompressFormat.JPEG, 100, out);
								}
								DemoUtility.SaveBin(outputDirStr.toString() + ".raw", monoBestImage.get(0).getImageData());
							}
						}
						catch (FileNotFoundException e)
						{
							e.printStackTrace();
							iRet.setValue(IddkResult.IDDK_UNEXPECTED_ERROR);
						}
					}
				}
				if (iRet.intValue() == IddkResult.IDDK_SE_NO_QUALIFIED_FRAME)
				{
					// No qualified images
					iRet.setValue(IddkResult.IDDK_SE_NO_QUALIFIED_FRAME);
					updateCurrentStatus(getString(R.string.capture_noqualified));
					mMediaData.noEyeQualifiedPlayer.start();
				}
			}

			return 0;
		}

		/*****************************************************************************
		 * Convert the Grayscale image from the camera to bitmap format that can be used to show to the users.
		 *****************************************************************************/
		private Bitmap convertBitmap(byte[] rawImage, int imageWidth, int imageHeight)
		{
			byte[] Bits = new byte[rawImage.length * 4]; // That's where the RGBA array goes.

			int j;
			for (j = 0; j < rawImage.length; j++)
			{
				Bits[j * 4] = (byte) (rawImage[j]);
				Bits[j * 4 + 1] = (byte) (rawImage[j]);
				Bits[j * 4 + 2] = (byte) (rawImage[j]);
				Bits[j * 4 + 3] = -1; // That's the alpha
			}

			// Now put these nice RGBA pixels into a Bitmap object
			mCurrentBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
			mCurrentBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));

			return mCurrentBitmap;
		}

		/*****************************************************************************
		 * Update the current streaming image to the captureView
		 *****************************************************************************/
		protected void onProgressUpdate(Bitmap... bm)
		{
			if (isBinocularDevice)
			{
				captureView.setImageBitmap(bm[0]);
				captureViewLeft.setImageBitmap(bm[1]);
			}
			else
			{
				captureView.setImageBitmap(bm[0]);
			}
		}

		/*****************************************************************************
		 * Post processing after the capturing process ends
		 *****************************************************************************/
		protected void onPostExecute(Integer result)
		{
			IddkResult stopResult = stopCamera(false);
			if (iRet.getValue() != IddkResult.IDDK_OK && stopResult.getValue() != iRet.getValue())
			{
				handleError(iRet);
			}
		}
	}

	/*****************************************************************************
	 * Reset all the internal states of the application. This function is called whenever device connection has been changed.
	 *****************************************************************************/
	private void setInitState()
	{
		mIsCameraReady = false;
		mIsPermissionDenied = false;
		mIsGalleryLoaded = false;
		mIspreviewing = false;
		mCurrentStatus.setValue(IddkCaptureStatus.IDDK_IDLE);
		mIsJustError = false;

		DemoUtility.sleep(1000);

		mTabHost.setCurrentTab(TAB_CAPTURE_INDEX_ID);

		if (mTabHost.getCurrentTab() == TAB_CAPTURE_INDEX_ID)
		{
			mStatusTextView.setText(getString(R.string.opendevice_notfound));
			mCaptureView.setImageBitmap(null);
			mCaptureViewLeft.setImageBitmap(null);

			View view = findViewById(R.id.start_button_id);
			view.setEnabled(false);
			view = findViewById(R.id.stop_button_id);
			view.setEnabled(false);
		}
	}

	/*****************************************************************************
	 * This function handles any errors that may occur in the program. Notice to disable start and stop button to prevent other errors.
	 *****************************************************************************/
	public void handleError(IddkResult error)
	{
		mIsCameraReady = false;
		mIsPermissionDenied = false;

		// If there is a problem with the connection
		if ((error.getValue() == IddkResult.IDDK_DEVICE_IO_FAILED) || (error.getValue() == IddkResult.IDDK_DEVICE_IO_DATA_INVALID) || (error.getValue() == IddkResult.IDDK_DEVICE_IO_TIMEOUT))
		{
			showDialog(getString(R.string.error), getString(R.string.connection_restart_ask));

			if (mTabHost.getCurrentTab() == TAB_CAPTURE_INDEX_ID)
			{
				updateCurrentStatus(getString(R.string.connection_failed));

				View view = findViewById(R.id.start_button_id);
				view.setEnabled(false);
				view = findViewById(R.id.stop_button_id);
				view.setEnabled(false);

				mCaptureView.setImageBitmap(null);
				mCaptureViewLeft.setImageBitmap(null);
			}

			mIsJustError = true;
		}
		else
		{
			showDialog(getString(R.string.warning), DemoUtility.getErrorDesc(error));
		}
	}

	/*****************************************************************************
	 * Calling this function to invalidate the spinner that represents the list of IriShield(s) attached to the Android system.
	 *****************************************************************************/
	@SuppressWarnings("unchecked")
	private void updateListOfDevices(ArrayList<String> listOfDevices)
	{
		ArrayAdapter<String> adapter = null;
		if (listOfDevices == null || listOfDevices.size() == 0)
		{
			// Clear all the items in the spinner and notify it to refresh
			adapter = (ArrayAdapter<String>) mListOfDevices.getAdapter();
			if (adapter != null)
			{
				adapter.clear();
				adapter.notifyDataSetChanged();
			}
			return;
		}
		adapter = new ArrayAdapter<String>(this, R.xml.spinner_text_style, listOfDevices);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mListOfDevices.setAdapter(adapter);
	}

	/*****************************************************************************
	 * A broadcast receiver that is used to receive the detached events whenever a IriShield device is detached from the Android system. This function also sends a message to notify the main thread
	 * (GUI thread) to execute the post jobs.
	 *****************************************************************************/
	private BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
	{
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
			{
				// Make a notice to user
				mUsbNotification.cancelNofitications();
				mUsbNotification.createNotification(getString(R.string.device_disconnected));

				// Play a sound when a IriShield is detached from the Android system
				mMediaData.deviceDisconnected.start();

				// Send a message to main thread
				final Message msg = Message.obtain(mHandler, 0, null);
				mHandler.dispatchMessage(msg);
			}
		}
	};

	/*****************************************************************************
	 * A handler that is used to receive and handle the message sent from the broadcast receiver.
	 *****************************************************************************/
	final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			// Reset state of the application
			setInitState();

			// Scan and open device again
			openDevice();
		}
	};
}