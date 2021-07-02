package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.ACCESS_STORAGE_PERMISSION_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.app.Activity;
import android.content.Intent;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class RDToolkitSupportActivity {

	private final Activity ctx;
	private RDToolkitSupport rdToolkitSupport;
	private static String[] PERMISSIONS_STORAGE = { READ_EXTERNAL_STORAGE };
	private Intent lastIntent;

	RDToolkitSupportActivity(Activity ctx) {
		this.ctx = ctx;
		this.rdToolkitSupport = new RDToolkitSupport(this.ctx);
	}

	String processActivity(int requestCode, int resultCode, Intent intentData) {
		trace(this, "RDToolkitSupport :: process request code: %s", requestCode);

		if (resultCode != RESULT_OK) {
			throw new RuntimeException("RDToolkitSupport :: Bad result code: " + resultCode);
		}

		switch (requestCode) {
			case RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE:
				return this.rdToolkitSupport.processProvisionedTest(intentData);

			case RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE:
				return this.rdToolkitSupport.processCapturedResponse(intentData);

			default:
				throw new RuntimeException("RDToolkitSupport :: Unsupported request code: " + requestCode);
		}
	}

	void startProvisionIntent(String sessionId, String patientName, String patientId, String rdtFilter, String monitorApiURL) {
		Intent intent = this.rdToolkitSupport.provisionRDTest(sessionId, patientName, patientId, rdtFilter, monitorApiURL);

		if (intent.resolveActivity(this.ctx.getPackageManager()) != null) {
			this.ctx.startActivityForResult(intent, RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE);
		}
	}

	void startCaptureIntent(String sessionId) {
		Intent intent = this.rdToolkitSupport.captureRDTest(sessionId);

		if (intent.resolveActivity(ctx.getPackageManager()) == null) {
			return;
		}

		if (ContextCompat.checkSelfPermission(this.ctx, READ_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
			trace(this, "RDToolkitSupport :: Requesting storage permissions");
			this.lastIntent = intent; // Saving intent to start it when permission is granted.
			ActivityCompat.requestPermissions(this.ctx, PERMISSIONS_STORAGE, ACCESS_STORAGE_PERMISSION_REQUEST_CODE);
			return;
		}

		this.ctx.startActivityForResult(intent, RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE);
	}

	void resumeCaptureActivity() {
		if (this.lastIntent == null) {
			return;
		}

		trace(this, "RDToolkitSupport :: Resuming capture action.");
		this.ctx.startActivityForResult(this.lastIntent, RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE);
		this.lastIntent = null; // Cleaning as we don't need it anymore.
	}
}
