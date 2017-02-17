package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.location.*;
import android.net.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.webkit.CookieManager;
import android.widget.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.medicmobile.webapp.mobile.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.Utils.showProgressDialog;

public class StandardWebViewDataExtractionActivity extends Activity {
	private static final ValueCallback<String> IGNORE_RESULT = new ValueCallback<String>() {
		public void onReceiveValue(String result) {}
	};

	private WebView container;
	private SettingsStore settings;
	private FakeCouch fakeCouch;
	private boolean allowServerComms;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		trace("onCreate", "Starting old webview...");

		this.settings = SettingsStore.in(this);

		setContentView(R.layout.data_extraction_webview);

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if(settings.allowsConfiguration() &&
				settings.getAppUrl().contains("app.medicmobile.org")) {
			findViewById(R.id.lytWebView).setPadding(10, 10, 10, 10);
		}

		// TODO remove this asymmetric padding after we've finished testing
		findViewById(R.id.lytWebView).setPadding(10, 10, 30, 10);

		container = (WebView) findViewById(R.id.wbvMain);

		if(DEBUG) enableWebviewLoggingAndGeolocation(container);
		enableRemoteChromeDebugging(container);
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		browseToRoot();

		if(settings.allowsConfiguration()) {
			toast(settings.getAppUrl());
		}

		// TODO disableUserInteraction();

		allowServerComms = true; // TODO we need this for first run when debugging, so we
		// actually have some data to debug.  After that, this option can likely be removed
		// permanently.

		fakeCouch = new FakeCouch(settings);
		fakeCouch.start(this);
		// This seems to make things worse...
		//container.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
	}

	@Override public void onDestroy() {
		super.onDestroy();
		stopFakeCouch();
	}

	public void evaluateJavascript(final String js) {
		container.post(new Runnable() {
			public void run() {
				// `WebView.loadUrl()` seems to be significantly faster than
				// `WebView.evaluateJavascript()` on Tecno Y4.  We may find
				// confusing behaviour on Android 4.4+ when using `loadUrl()`
				// to run JS, in which case we should switch to the second
				// block.
				if(true) {
					container.loadUrl("javascript:" + js);
				} else {
					container.evaluateJavascript(js, IGNORE_RESULT);
				}
			}
		});
	}

//> JAVASCRIPT BINDINGS
	void replicationComplete() {
		String url = settings.getAppUrl();
		String cookies = getCookies(url);
		trace("replicationComplete", "Got cookies for %s: %s", url, cookies);

		stopFakeCouch();

		Intent xwalkStarter = new Intent(
				StandardWebViewDataExtractionActivity.this,
				EmbeddedBrowserActivity.class);
		xwalkStarter.putExtra(EmbeddedBrowserActivity.EXTRA_COOKIES, cookies);
		startActivity(xwalkStarter);

		// TODO this doesn't actually appear to destroy the webview - still visible in the chrome tools thingy
		this.finish();
	}

//> INTERNAL HELPERS
	/** @return the encoded cookie string for the given url */
	// TODO the reflection method below needs testing on the tecno and other in-use handsets
	String getCookies(String url) {
		try {
			CookieManager cm = CookieManager.getInstance();

			java.lang.reflect.Field ccm = cm.getClass().getDeclaredField("mChromeCookieManager");
			ccm.setAccessible(true);
			Object acm = ccm.get(cm);

			java.lang.reflect.Method getCookie = acm.getClass().getDeclaredMethod("nativeGetCookie", String.class);
			getCookie.setAccessible(true);

			Object cookie = getCookie.invoke(acm, url);

			trace("shouldInterceptRequest", "Cookie: [%s] %s", cookie.getClass(), cookie);

			return cookie.toString();
		} catch(Exception ex) {
			MedicLog.warn(ex, "getCookie");
		}

		return CookieManager.getInstance().getCookie(url);
	}

	private synchronized void stopFakeCouch() {
		if(fakeCouch == null) return;
		fakeCouch.stop();
		fakeCouch = null;
	}

	private void browseToRoot() {
		String url = settings.getAppUrl() + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
		if(DEBUG) trace("browseToRoot", "Pointing browser to %s", url);
		container.loadUrl(url);
	}

	private void enableRemoteChromeDebugging(WebView container) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			container.setWebContentsDebuggingEnabled(true);
		}
	}

	private void enableWebviewLoggingAndGeolocation(WebView container) {
		container.setWebChromeClient(new WebChromeClient() {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("MedicMobile", String.format("%s:%s | %s",
						cm.sourceId(),
						cm.lineNumber(),
						cm.message()));
				return true;
			}

			public void onGeolocationPermissionsShowPrompt(
					String origin,
					GeolocationPermissions.Callback callback) {
				// allow all location requests
				// TODO this should be restricted to the domain
				// set in Settings - issue #1603
				StandardWebViewDataExtractionActivity.this.trace(
						"onGeolocationPermissionsShowPrompt",
						"origin=%s, callback=%s",
						origin, callback);
				callback.invoke(origin, true, true);
			}
		});
	}

	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);

		WebViewDataExtractionJavascriptBinding wvDataExtractionJs =
				new WebViewDataExtractionJavascriptBinding(this);
		container.addJavascriptInterface(wvDataExtractionJs, "medicmobile_webview_data_extraction");

		//MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		//maj.setAlert(new Alert(this));

		//maj.setLocationManager((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));

		//container.addJavascriptInterface(maj, "medicmobile_android");
	}

	private void enableStorage(WebView container) {
		WebSettings webSettings = container.getSettings();
		webSettings.setDatabaseEnabled(true);
		webSettings.setDomStorageEnabled(true);
		File dir = getCacheDir();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		webSettings.setAppCachePath(dir.getPath());
		webSettings.setAppCacheEnabled(true);
	}

	private void enableUrlHandlers(WebView container) {
		container.setWebViewClient(new WebViewClient() {
			@Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
				// Enable SMS and call handling
				if(url.startsWith("tel:") || url.startsWith("sms:")) {
					Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					view.getContext().startActivity(i);
					return true;
				}
				return false;
			}

			@Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
				trace("shouldInterceptRequest", "[%s] %s", request.getMethod(), request.getUrl());
				for(Map.Entry e : request.getRequestHeaders().entrySet()) {
					trace("shouldInterceptRequest", "–%s: %s", e.getKey(), e.getValue());
				}
				trace("shouldInterceptRequest", "–––––––––––– headers end");
				if(allowServerComms) return null;

				Uri url = request.getUrl();
				if(url == null) return null;

				String host = url.getHost();
				if(host == null) return null;

				String configuredHost = Uri.parse(settings.getAppUrl()).getHost();

				//trace("shouldInterceptRequest", "comparing hosts: %s <-> %s", host, configuredHost);

				// TODO safer just to block anything non-localhost?
				if(host.equals(configuredHost)) {
					// don't let the webview talk to remote couchdb
					Map<String, String> headers = Collections.emptyMap();
					return new WebResourceResponse("text", "utf8", 503,
							"Server blocked.  Local db replication will begin shortly.",
							headers, emptyInputStream());
				}
				return null;
// TODO once loading has completed successfully (presumably we can detect this...somehow), then
// trigger replication from local pouch to local HTTP server.  For now, trigger this manually.
			}

			private InputStream emptyInputStream() {
				return new ByteArrayInputStream(new byte[0]);
			}
		});
	}

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}

	private void trace(String method, String message, Object...extras) {
		MedicLog.trace(this, method + "() :: " + message, extras);
	}
}