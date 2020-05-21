package org.medicmobile.webapp.mobile;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.app.ActivityManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.ValueCallback;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;

import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.lang.Boolean.parseBoolean;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;
import static org.medicmobile.webapp.mobile.Utils.isUrlRelated;

@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public class EmbeddedBrowserActivity extends LockableActivity {
      /** Any activity result with all 3 low bits set is _not_ a simprints result. */
      private static final int NON_SIMPRINTS_FLAGS = 0x7;
      static final int GRAB_PHOTO = (0 << 3) | NON_SIMPRINTS_FLAGS;
      static final int GRAB_MRDT_PHOTO = (1 << 3) | NON_SIMPRINTS_FLAGS;

      private static final long FIVE_MINS = 5 * 60 * 1000;
      private static final float ANY_DISTANCE = 0f;

      private static final ValueCallback<String> IGNORE_RESULT = new ValueCallback<String>() {
          public void onReceiveValue(String result) { /* ignore */ }
      };
      static final String MY_LAST_ACTIVITY = "myLastActivity";
      private final ValueCallback<String> backButtonHandler = new ValueCallback<String>() {
          public void onReceiveValue(String result) {
              if(!"true".equals(result)) {
                  EmbeddedBrowserActivity.this.moveTaskToBack(false);
              }
          }
      };

      private XWalkView container;
      private SettingsStore settings;
      private String appUrl;
      private SimprintsSupport simprints;
      private MrdtSupport mrdt;
      private PhotoGrabber photoGrabber;
      private SmsSender smsSender;
      private SharedPreferences sharedPreferences;

//> ACTIVITY LIFECYCLE METHODS
      @Override public void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);

              trace(this, "Starting XWalk webview...");

              this.simprints = new SimprintsSupport(this);
              this.photoGrabber = new PhotoGrabber(this);
              this.mrdt = new MrdtSupport(this);
              try {
                  this.smsSender = new SmsSender(this);
              } catch(Exception ex) {
                  error(ex, "Failed to create SmsSender.");
              }

              this.settings = SettingsStore.in(this);
              this.appUrl = settings.getAppUrl();

              this.requestWindowFeature(Window.FEATURE_NO_TITLE);
              setContentView(R.layout.main);

              // Add an alarming red border if using configurable (i.e. dev)
              // app with a medic production server.
              if(settings.allowsConfiguration() &&
                      appUrl.contains("app.medicmobile.org")) {
                  View webviewContainer = findViewById(R.id.lytWebView);
                  webviewContainer.setPadding(10, 10, 10, 10);
                  webviewContainer.setBackgroundColor(R.drawable.warning_background);
              }

              container = (XWalkView) findViewById(R.id.wbvMain);

              configureUseragent();

              enableLocationUpdates();
              setUpUiClient(container);
              enableRemoteChromeDebugging();
              enableJavascript(container);
              enableStorage(container);

              enableUrlHandlers(container);

              browseToRoot();

              if(settings.allowsConfiguration()) {
                  toast(redactUrl(appUrl));
              }

              /*
              * Created SharedPreferences A Store Procedure in Android
              * In onStop() We are Storing the Current Path of the User
              * When we relaunch the application we are loading Stored URL in the container i.e XWalkView
              * */
              sharedPreferences = getSharedPreferences(MY_LAST_ACTIVITY, 0); // 0 - for private mode
              String recentNavigation = sharedPreferences.getString(MY_LAST_ACTIVITY, "");
              if (!(recentNavigation != null && recentNavigation.isEmpty())) {
                  container.loadUrl(recentNavigation);
              }
      }

      @Override protected void onStop() {
            super.onStop();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(MY_LAST_ACTIVITY, container.getUrl());
            editor.commit();
      }

      @Override public boolean onCreateOptionsMenu(Menu menu) {
              if(settings.allowsConfiguration()) {
                  getMenuInflater().inflate(R.menu.unbranded_web_menu, menu);
              } else {
                  getMenuInflater().inflate(R.menu.web_menu, menu);
              }
              return super.onCreateOptionsMenu(menu);
      }

      @Override public boolean onOptionsItemSelected(MenuItem item) {
              switch(item.getItemId()) {
                  case R.id.mnuGotoTestPages:
                      evaluateJavascript("window.location.href = 'https://medic.github.io/atp'");
                      return true;
                  case R.id.mnuSetUnlockCode:
                    changeCode();
                    return true;
                case R.id.mnuSettings:
                    openSettings();
                    return true;
                case R.id.mnuHardRefresh:
                    browseToRoot();
                    return true;
                case R.id.mnuLogout:
                    evaluateJavascript("angular.element(document.body).injector().get('AndroidApi').v1.logout()");
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
      }

      @Override public boolean dispatchKeyEvent(KeyEvent event) {
              if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                  // With standard android WebView, this would be handled by onBackPressed().  However, that
                  // method does not get called when using XWalkView, so we catch the back button here instead.
                  // TODO this causes issues with the Samsung long-back-press to trigger menu - the menu opens,
                  // but the app also handles the back press :¬/
                  if(event.getAction() == KeyEvent.ACTION_UP) {
                      container.evaluateJavascript(
                              "angular.element(document.body).injector().get('AndroidApi').v1.back()",
                              backButtonHandler);
                  }

                  return true;
              } else {
                  return super.dispatchKeyEvent(event);
              }
      }

            @Override protected void onActivityResult(int requestCode, int resultCode, Intent i) {
                    try {
                     trace(this, "onActivityResult() :: requestCode=%s, resultCode=%s", requestCode, resultCode);
                    if((requestCode & NON_SIMPRINTS_FLAGS) == NON_SIMPRINTS_FLAGS) {
                        switch(requestCode) {
                            case GRAB_PHOTO:
                                photoGrabber.process(requestCode, resultCode, i);
                                return;
                            case GRAB_MRDT_PHOTO:
                                String js = mrdt.process(requestCode, resultCode, i);
                                trace(this, "Execing JS: %s", js);
                                evaluateJavascript(js);
                                return;
                            default:
                                trace(this, "onActivityResult() :: no handling for requestCode=%s", requestCode);
                        }
                    } else {
                        String js = simprints.process(requestCode, i);
                        trace(this, "Execing JS: %s", js);
                        evaluateJavascript(js);
                    }
                } catch(Exception ex) {
                    String action = i == null ? null : i.getAction();
                    warn(ex, "Problem handling intent %s (%s) with requestCode=%s & resultCode=%s", i, action, requestCode, resultCode);
                }
      }

//> ACCESSORS
      SimprintsSupport getSimprintsSupport() {
          return this.simprints;
      }

      MrdtSupport getMrdtSupport() {
        return this.mrdt;
      }

      SmsSender getSmsSender() {
          return this.smsSender;
      }

//> PUBLIC API
      public void evaluateJavascript(final String js) {
              container.post(new Runnable() {
                  public void run() {
                      // `WebView.loadUrl()` seems to be significantly faster than
                      // `WebView.evaluateJavascript()` on Tecno Y4.  We may find
                      // confusing behaviour on Android 4.4+ when using `loadUrl()`
                      // to run JS, in which case we should switch to the second
                      // block.
                      // On switching to XWalkView, we assume the same applies.
                      if(true) { // NOPMD
                          container.load("javascript:" + js, null);
                      } else {
                          container.evaluateJavascript(js, IGNORE_RESULT);
                      }
                  }
              });
      }

      public void errorToJsConsole(String message, Object... extras) {
              jsConsole("error", message, extras);
      }

      public void logToJsConsole(String message, Object... extras) {
              jsConsole("log", message, extras);
      }

      //> PRIVATE HELPERS
      private void jsConsole(String type, String message, Object... extras) {
              String formatted = String.format(message, extras);
              String escaped = formatted.replace("'", "\\'");
              evaluateJavascript("console." + type + "('" + escaped + "');");
      }

      private void configureUseragent() {
              String current = container.getUserAgentString();

              container.setUserAgentString(createUseragentFrom(current));
      }

      private void openSettings() {
              startActivity(new Intent(this,
                              SettingsDialogActivity.class));
              finish();
      }

      private String getRootUrl() {
              return appUrl;
      }

      private void browseToRoot() {
              String url = getRootUrl();
              if(DEBUG) trace(this, "Pointing browser to %s", redactUrl(url));
              container.load(url, null);
      }

      private void enableRemoteChromeDebugging() {
              XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
      }

    private void setUpUiClient(XWalkView container) {
            container.setUIClient(new XWalkUIClient(container) {
                    /** Not applicable for Crosswalk.  TODO find alternative and remove this
                     @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                     if(!DEBUG) {
                     return super.onConsoleMessage(cm);
                     }
                     trace(this, "onConsoleMessage() :: %s:%s | %s",
                     cm.sourceId(),
                     cm.lineNumber(),
                     cm.message());
                     return true;
                     } */

                     @Override public void openFileChooser(XWalkView view, ValueCallback<Uri> callback, String acceptType, String shouldCapture) {
                             if(DEBUG) trace(this, "openFileChooser() :: %s,%s,%s,%s", view, callback, acceptType, shouldCapture);

                              boolean capture = parseBoolean(shouldCapture);

                              if(photoGrabber.canHandle(acceptType, capture)) {
                                  photoGrabber.chooser(callback, capture);
                              } else {
                                  logToJsConsole("No file chooser is currently implemented for \"accept\" value: %s", acceptType);
                                  warn(this, "openFileChooser() :: No file chooser is currently implemented for \"accept\" value: %s", acceptType);
                              }
                     }

                     /*
                      * TODO Crosswalk: re-enable this if required
                     public void onGeolocationPermissionsShowPrompt(
                         String origin,
                         GeolocationPermissions.Callback callback) {
                       // allow all location requests
                       // TODO this should be restricted to the domain
                       // set in Settings - issue #1603
                       trace(this, "onGeolocationPermissionsShowPrompt() :: origin=%s, callback=%s",
                           origin, callback);
                       callback.invoke(origin, true, true);
                     }
                     */
            });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void enableJavascript(XWalkView container) {
            container.getSettings().setJavaScriptEnabled(true);

            MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
            maj.setAlert(new Alert(this));

            maj.setLocationManager((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));

            maj.setActivityManager((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE));

            maj.setConnectivityManager((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));

            container.addJavascriptInterface(maj, "medicmobile_android");
    }

    /**
     * Make the app poll the location providers periodically.  This should mean that calls
     * to MedicAndroidJavascript.getLocation() are reasonably up-to-date, and an initial
     * location is likely to have been resolved by the time the user first fills a form and
     * getLocation() is called.  However, longer-term we are likely to want a more explicit
     * async getLocation() implementation which is triggered only when needed.
     * @see https://github.com/medic/medic-projects/issues/2629
     * @deprecated @see https://github.com/medic/medic-webapp/issues/3781
     */
    @Deprecated
    private void enableLocationUpdates() {
            if(ContextCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
                log("EmbeddedBrowserActivity.enableLocationUpdates() :: Cannot enable location updates: permission ACCESS_FINE_LOCATION not granted.");
                return;
            }

            LocationManager m = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            if(m == null) {
                log("EmbeddedBrowserActivity.enableLocationUpdates() :: Cannot enable location updates: LOCATION_SERVICE could not be fetched.");
                return;
            }

            requestLocationUpdates(m, LocationManager.GPS_PROVIDER);
            requestLocationUpdates(m, LocationManager.NETWORK_PROVIDER);
    }

    private void requestLocationUpdates(LocationManager m, String locationProvider) {
            try {
                if(m.isProviderEnabled(locationProvider)) {
                        // Method bodies are empty because we need the location updates to be running constantly so
                        // that recent location can be requested when required from Javascript.
                        m.requestLocationUpdates(locationProvider, FIVE_MINS, ANY_DISTANCE, new LocationListener() {
                            @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
                            public void onLocationChanged(Location location) {}
                            @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
                            public void onProviderDisabled(String provider) {}
                            @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
                            public void onProviderEnabled(String provider) {}
                            @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
                            public void onStatusChanged(String provider, int status, Bundle extras) {}
                        });
                } else {
                        log("EmbeddedBrowserActivity.requestLocationUpdates(%s) :: Cannot get updates: not enabled or phone does not have this feature.",
                                locationProvider);
                }
            } catch(SecurityException ex) {
                    log(ex, "EmbeddedBrowserActivity.requestLocationUpdates(%s) :: Exception thrown while checking provider.",
                            locationProvider);
            }
    }

    private void enableStorage(XWalkView container) {
            XWalkSettings settings = container.getSettings();

            // N.B. in Crosswalk, database seems to be enabled by default

            settings.setDomStorageEnabled(true);

            // N.B. in Crosswalk, appcache seems to work by default, and
            // there is no option to set the storage path.
    }

    private void enableUrlHandlers(XWalkView container) {
            container.setResourceClient(new XWalkResourceClient(container) {
                    @Override public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
                            if(url.startsWith("tel:") || url.startsWith("sms:")) {
                                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                view.getContext().startActivity(i);
                                return true;
                            }
                            return false;
                    }
                    @Override public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
                            if(isUrlRelated(appUrl, request.getUrl())) {
                                return null; // load as normal
                            } else {
                                warn(this, "shouldInterceptLoadRequest() :: Denying access to URL outside of expected domain: %s", request.getUrl());

                                Map<String, String> noHeaders = Collections.<String, String>emptyMap();
                                ByteArrayInputStream emptyResponse = new ByteArrayInputStream(new byte[0]);

                                return createXWalkWebResourceResponse(
                                        "text/plain", "UTF-8", emptyResponse,
                                        403, "Read access forbidden.", noHeaders);
                            }
                    }
                    @Override public void onReceivedLoadError(XWalkView view, int errorCode, String description, String failingUrl) {
                            if(errorCode == XWalkResourceClient.ERROR_OK) return;

                            log("EmbeddedBrowserActivity.onReceivedLoadError() :: [%s] %s :: %s", errorCode, failingUrl, description);

                            if(!getRootUrl().equals(failingUrl)) {
                                log("EmbeddedBrowserActivity.onReceivedLoadError() :: ignoring for non-root URL");
                            }

                            evaluateJavascript(String.format(
                                    "var body = document.evaluate('/html/body', document);" +
                                            "body = body.iterateNext();" +
                                            "if(body) {" +
                                            "  var content = document.createElement('div');" +
                                            "  content.innerHTML = '" +
                                            "<h1>Error loading page</h1>" +
                                            "<p>[%s] %s</p>" +
                                            "<button onclick=\"window.location.reload()\">Retry</button>" +
                                            "';" +
                                            "  body.appendChild(content);" +
                                            "}", errorCode, description));
                    }
            });
    }

    private void toast(String message) {
            Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
    }
}