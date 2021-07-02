package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceError;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static android.webkit.WebViewClient.ERROR_CONNECT;
import static android.webkit.WebViewClient.ERROR_HOST_LOOKUP;
import static android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION;
import static android.webkit.WebViewClient.ERROR_TIMEOUT;
import static org.medicmobile.webapp.mobile.BuildConfig.APPLICATION_ID;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.VERSION_NAME;

final class Utils {
	private Utils() {}

	static boolean isUrlRelated(String appUrl, Uri uriToTest) {
		// android.net.Uri doesn't give us a host for URLs like blob:https://some-project.dev.medicmobile.org/abc-123
		// so we might as well just regex the URL string
		return isUrlRelated(appUrl, uriToTest.toString());
	}

	static boolean isUrlRelated(String appUrl, String uriToTest) {
		// android.net.Uri doesn't give us a host for URLs like blob:https://some-project.dev.medicmobile.org/abc-123
		// so we might as well just regex the URL string
		return uriToTest.matches("^(blob:)?" + appUrl + "/.*$");
	}

	static JSONObject json(Object... keyVals) throws JSONException {
		if(DEBUG && keyVals.length % 2 != 0) throw new AssertionError();
		JSONObject o = new JSONObject();
		for(int i=keyVals.length-1; i>0; i-=2) {
			o.put(keyVals[i-1].toString(), keyVals[i]);
		}
		return o;
	}

	static boolean intentHandlerAvailableFor(Context ctx, Intent intent) {
		return intent.resolveActivity(ctx.getPackageManager()) != null;
	}

	static void startAppActivityChain(Activity a) {
		if(SettingsStore.in(a).hasWebappSettings()) {
			a.startActivity(new Intent(a, EmbeddedBrowserActivity.class));
		} else {
			a.startActivity(new Intent(a, SettingsDialogActivity.class));
		}
		a.finish();
	}

	public static ProgressDialog showSpinner(Context ctx, int messageId) {
		return showSpinner(ctx, ctx.getString(messageId));
	}

	public static ProgressDialog showSpinner(Context ctx, String message) {
		ProgressDialog p = new ProgressDialog(ctx);
		p.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		if(message != null) p.setMessage(message);
		p.setIndeterminate(true);
		p.setCanceledOnTouchOutside(false);
		p.show();
		return p;
	}

	static String createUseragentFrom(String current) {
		if(current.contains(APPLICATION_ID)) return current;

		return String.format("%s %s/%s",
				current, APPLICATION_ID, VERSION_NAME);
	}

	static void restartApp(Context context) {
		Intent intent = new Intent(context, StartupActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		context.startActivity(intent);
		Runtime.getRuntime().exit(0);
	}

	static boolean isConnectionError(WebResourceError error) {
		switch (error.getErrorCode()) {
			case ERROR_HOST_LOOKUP:
			case ERROR_PROXY_AUTHENTICATION:
			case ERROR_CONNECT:
			case ERROR_TIMEOUT:
				return true;
		}
		return false;
	}

	static String connectionErrorToString(WebResourceError error) {
		return String.format("%s [%s]", error.getDescription(), error.getErrorCode());
	}

	static String getUtcIsoDate(Date date) {
		if (date == null) {
			return null;
		}

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		return dateFormat.format(date);
	}

	/**
	 * The file path can be a regular file or a content ("content://" scheme)
	 * @param path {String} File path
	 * @return
	 */
	static Uri getUriFromFilePath(String path) {
		if (path == null) {
			return null;
		}

		if ("content".equals(Uri.parse(path).getScheme())) {
			return Uri.parse(path);
		}

		return Uri.fromFile(new File(path));
	}
}
