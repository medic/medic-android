package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.DatePickerDialog;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Process;
import android.widget.DatePicker;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import static java.util.Locale.UK;
import static org.medicmobile.webapp.mobile.MedicLog.log;

public class MedicAndroidJavascript {
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private final EmbeddedBrowserActivity parent;
	private final SimprintsSupport simprints;
	private final MrdtSupport mrdt;
	private final SmsSender smsSender;
	private final RDToolkitSupportActivity rdToolkitSupportActivity;

	private ActivityManager activityManager;
	private ConnectivityManager connectivityManager;
	private Alert soundAlert;

	public MedicAndroidJavascript(EmbeddedBrowserActivity parent) {
		this.parent = parent;
		this.simprints = parent.getSimprintsSupport();
		this.mrdt = parent.getMrdtSupport();
		this.smsSender = parent.getSmsSender();
		this.rdToolkitSupportActivity = parent.getRdToolkitSupportActivity();
	}

	public void setAlert(Alert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setActivityManager(ActivityManager activityManager) {
		this.activityManager = activityManager;
	}

	public void setConnectivityManager(ConnectivityManager connectivityManager) {
		this.connectivityManager = connectivityManager;
	}

//> JavascriptInterface METHODS
	@android.webkit.JavascriptInterface
	public String getAppVersion() {
		try {
			return parent.getPackageManager()
					.getPackageInfo(parent.getPackageName(), 0)
					.versionName;
		} catch(Exception ex) {
			return jsonError("Error fetching app version: ", ex);
		}
	}

	@android.webkit.JavascriptInterface
	public void playAlert() {
		try {
			if(soundAlert != null) soundAlert.trigger();
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public String getDataUsage() {
		try {
			int uid = Process.myUid();
			return new JSONObject()
					.put("system", getDataUsage(
							TrafficStats.getTotalRxBytes(),
							TrafficStats.getTotalTxBytes()))
					.put("app", getDataUsage(
							TrafficStats.getUidRxBytes(uid),
							TrafficStats.getUidTxBytes(uid)))
					.toString();
		} catch(Exception ex) {
			return jsonError("Problem fetching data usage stats.");
		}
	}

	private JSONObject getDataUsage(long rx, long tx) throws JSONException {
		return new JSONObject()
				.put("rx", rx)
				.put("tx", tx);
	}

	@android.webkit.JavascriptInterface
	public boolean getLocationPermissions() {
		return this.parent.getLocationPermissions();
	}

	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement) {
		try {
			datePicker(targetElement, Calendar.getInstance());
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement, String initialDate) {
		try {
			DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, UK);
			Calendar c = Calendar.getInstance();
			c.setTime(dateFormat.parse(initialDate));
			datePicker(targetElement, c);
		} catch(ParseException ex) {
			datePicker(targetElement);
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public boolean mrdt_available() {
		try {
			return mrdt.isAppInstalled();
		} catch(Exception ex) {
			logException(ex);
			return false;
		}
	}

	@android.webkit.JavascriptInterface
	public void mrdt_verify() {
		try {
			mrdt.startVerify();
		} catch(Exception ex) {
			logException(ex);
		}
	}

	/**
	 * @return {@code true} iff an app is available to handle supported simprints {@code Intent}s
	 */
	@android.webkit.JavascriptInterface
	public boolean simprints_available() {
		try {
			return simprints.isAppInstalled();
		} catch(Exception ex) {
			logException(ex);
			return false;
		}
	}

	@android.webkit.JavascriptInterface
	public void simprints_ident(int targetInputId) {
		try {
			simprints.startIdent(targetInputId);
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public void simprints_reg(int targetInputId) {
		try {
			simprints.startReg(targetInputId);
		} catch(Exception ex) {
			logException(ex);
		}
	}


	@android.webkit.JavascriptInterface
	public boolean sms_available() {
		return smsSender != null;
	}

	/**
	 * @param id id associated with this message, e.g. a pouchdb docId
	 * @param destination the recipient phone number for this message
	 * @param content the text content of the SMS to be sent
	 */
	@android.webkit.JavascriptInterface
	public void sms_send(String id, String destination, String content) throws Exception {
		try {
			// TODO we may need to do this on a background thread to avoid the browser UI from blocking while the SMS is being sent.  Check.
			smsSender.send(id, destination, content);
		} catch(Exception ex) {
			logException(ex);
			throw ex;
		}
	}

	@android.webkit.JavascriptInterface
	public void rdToolkit_provisionRDTest(String sessionId, String patientName, String patientId, String rdtFilter, String monitorApiURL) {
		try {
			this.rdToolkitSupportActivity.startProvisionIntent(sessionId, patientName, patientId, rdtFilter, monitorApiURL);
		} catch (Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public void rdToolkit_captureRDTest(String sessionId) {
		try {
			this.rdToolkitSupportActivity.startCaptureIntent(sessionId);
		} catch (Exception ex) {
			logException(ex);
		}
	}

	@SuppressLint("ObsoleteSdkInt")
	@android.webkit.JavascriptInterface
	public String getDeviceInfo() {
		try {
			if (activityManager == null) {
				return jsonError("ActivityManager not set. Cannot retrieve RAM info.");
			}

			if (connectivityManager == null) {
				return jsonError("ConnectivityManager not set. Cannot retrieve network info.");
			}

			PackageInfo packageInfo = parent
					.getPackageManager()
					.getPackageInfo(parent.getPackageName(), 0);
			long versionCode = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ? (long)packageInfo.versionCode : packageInfo.getLongVersionCode();

			JSONObject appObject = new JSONObject();
			appObject.put("version", packageInfo.versionName);
			appObject.put("packageName", packageInfo.packageName);
			appObject.put("versionCode", versionCode);

			String androidVersion = Build.VERSION.RELEASE;
			int osApiLevel = Build.VERSION.SDK_INT;
			String osVersion = System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")";
			JSONObject softwareObject = new JSONObject();
			softwareObject
					.put("androidVersion", androidVersion)
					.put("osApiLevel", osApiLevel)
					.put("osVersion", osVersion);

			String device = Build.DEVICE;
			String model = Build.MODEL;
			String manufacturer = Build.BRAND;
			String hardware = Build.HARDWARE;
			Map<String, String> cpuInfo = getCPUInfo();
			JSONObject hardwareObject = new JSONObject();
			hardwareObject
					.put("device", device)
					.put("model", model)
					.put("manufacturer", manufacturer)
					.put("hardware", hardware)
					.put("cpuInfo", new JSONObject(cpuInfo));

			File dataDirectory = Environment.getDataDirectory();
			StatFs dataDirectoryStat = new StatFs(dataDirectory.getPath());
			long dataDirectoryBlockSize = dataDirectoryStat.getBlockSizeLong();
			long dataDirectoryAvailableBlocks = dataDirectoryStat.getAvailableBlocksLong();
			long dataDirectoryTotalBlocks = dataDirectoryStat.getBlockCountLong();
			long freeMemorySize = dataDirectoryAvailableBlocks * dataDirectoryBlockSize;
			long totalMemorySize = dataDirectoryTotalBlocks * dataDirectoryBlockSize;
			JSONObject storageObject = new JSONObject();
			storageObject
					.put("free", freeMemorySize)
					.put("total", totalMemorySize);

			MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
			activityManager.getMemoryInfo(memoryInfo);
			long totalRAMSize = memoryInfo.totalMem;
			long freeRAMSize = memoryInfo.availMem;
			long thresholdRAM = memoryInfo.threshold;
			JSONObject ramObject = new JSONObject();
			ramObject
					.put("free", freeRAMSize)
					.put("total", totalRAMSize)
					.put("threshold", thresholdRAM);

			NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
			JSONObject networkObject = new JSONObject();
			if (netInfo != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
				NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
				int downSpeed = networkCapabilities.getLinkDownstreamBandwidthKbps();
				int upSpeed = networkCapabilities.getLinkUpstreamBandwidthKbps();
				networkObject
						.put("downSpeed", downSpeed)
						.put("upSpeed", upSpeed);
			}

			return new JSONObject()
					.put("app", appObject)
					.put("software", softwareObject)
					.put("hardware", hardwareObject)
					.put("storage", storageObject)
					.put("ram", ramObject)
					.put("network", networkObject)
					.toString();
		} catch(Exception ex) {
			return jsonError("Problem fetching device info: ", ex);
		}
	}

//> PRIVATE HELPER METHODS
	private void datePicker(String targetElement, Calendar initialDate) {
		// Remove single-quotes from the `targetElement` CSS selecter, as
		// we'll be using these to enclose the entire string in JS.  We
		// are not trying to properly escape these characters, just prevent
		// suprises from JS injection.
		final String safeTargetElement = targetElement.replace('\'', '_');

		DatePickerDialog.OnDateSetListener listener = new DatePickerDialog.OnDateSetListener() {
			public void onDateSet(DatePicker view, int year, int month, int day) {
				++month;
				String dateString = String.format(UK, "%04d-%02d-%02d", year, month, day);
				String setJs = String.format("$('%s').val('%s').trigger('change')",
						safeTargetElement, dateString);
				parent.evaluateJavascript(setJs);
			}
		};

		// Make sure that the datepicker uses spinners instead of calendars.  Material design
		// does not support non-calendar view, so we explicitly use the Holo theme here.
		// Rumours suggest this may still show a calendar view on Android 24.  This has not been confirmed.
		// https://stackoverflow.com/questions/28740657/datepicker-dialog-without-calendar-visualization-in-lollipop-spinner-mode
		DatePickerDialog dialog = new DatePickerDialog(parent, android.R.style.Theme_Holo_Dialog, listener,
				initialDate.get(YEAR), initialDate.get(MONTH), initialDate.get(DAY_OF_MONTH));

		DatePicker picker = dialog.getDatePicker();
		picker.setCalendarViewShown(false);
		picker.setSpinnersShown(true);

		dialog.show();
	}

	private static HashMap getCPUInfo() throws IOException {
		BufferedReader bufferedReader = new BufferedReader(new FileReader("/proc/cpuinfo"));
		String line;
		HashMap output = new HashMap();
		while ((line = bufferedReader.readLine()) != null) {
			String[] data = line.split(":");
			if (data.length > 1) {
				String key = data[0].trim();
				if (key.equals("model name")) {
					output.put(key, data[1].trim());
					break;
				}
			}
		}
		bufferedReader.close();

		int cores = Runtime.getRuntime().availableProcessors();
		output.put("cores", cores);

		String arch = System.getProperty("os.arch");
		output.put("arch", arch);

		return output;
	}

	private void logException(Exception ex) {
		log(ex, "Exception thrown in JavascriptInterface function.");

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String stacktrace = sw.toString()
				.replace("\n", "; ")
				.replace("\t", " ");

		parent.errorToJsConsole("Exception thrown in JavascriptInterface function: %s", stacktrace);
	}

//> STATIC HELPERS
	private static String jsonError(String message, Exception ex) {
		return jsonError(message + ex.getClass() + ": " + ex.getMessage());
	}

	private static String jsonError(String message) {
		return "{ \"error\": true, \"message\":\"" +
				jsonEscape(message) +
				"\" }";
	}

	private static String jsonEscape(String s) {
		return s.replaceAll("\"", "'");
	}
}
