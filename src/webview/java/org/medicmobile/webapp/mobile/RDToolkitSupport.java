package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.getUriFromFilePath;
import static org.medicmobile.webapp.mobile.Utils.getUtcIsoDate;
import static org.medicmobile.webapp.mobile.Utils.json;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.support.model.session.TestSession.TestResult;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

public class RDToolkitSupport {

	private final Activity ctx;

	RDToolkitSupport(Activity ctx) {
		this.ctx = ctx;
	}

	Intent provisionRDTest(String sessionId, String patientName, String patientId, String rdtFilter, String monitorApiURL) {
		ProvisionMode provisionMode = !rdtFilter.trim().matches("\\S+") ? ProvisionMode.CRITERIA_SET_AND : ProvisionMode.CRITERIA_SET_OR;

		return RdtIntentBuilder
				.forProvisioning()
				.setCallingPackage(ctx.getPackageName())
				.setReturnApplication(ctx)
				// Type of RD Test to choose from
				.requestProfileCriteria(rdtFilter, provisionMode)
				// Unique ID for RD Test
				.setSessionId(sessionId)
				// First line text to display in RDToolkit App and to differentiate running tests
				.setFlavorOne(patientName)
				// Second line text to display in RDToolkit App and to differentiate running tests
				.setFlavorTwo(patientId)
				.setCloudworksBackend(monitorApiURL, patientId)
				.build();
	}

	Intent captureRDTest(String sessionId) {
		return RdtIntentBuilder
				.forCapture()
				// Unique ID for RD Test
				.setSessionId(sessionId)
				.build();
	}

	String getImage(String path) {
		if (path == null || path.length() == 0) {
			return null;
		}

		try {
			trace(this, "RDToolkitSupport :: Retrieving image file");
			Uri filePath = getUriFromFilePath(path);
			ParcelFileDescriptor parcelFileDescriptor = ctx
					.getContentResolver()
					.openFileDescriptor(filePath, "r");

			InputStream file = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
			Bitmap imgBitmap = BitmapFactory.decodeStream(file);
			file.close();

			trace(this, "RDToolkitSupport :: Compressing image file");
			ByteArrayOutputStream outputFile = new ByteArrayOutputStream();
			imgBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputFile);

			trace(this, "RDToolkitSupport :: Encoding image file to Base64");
			byte[] imageBytes = outputFile.toByteArray();

			return Base64.encodeToString(imageBytes, Base64.NO_WRAP);

		} catch (Exception exception) {
			error(exception, "RDToolkitSupport :: Failed to process image file from path: %s", path);
		}

		return null;
	}

	String processCapturedResponse(Intent intentData) {
		try {
			JSONObject response = parseCapturedResponseToJson(intentData);
			return makeCapturedResponseJavaScript(response);

		} catch (Exception exception) {
			error(exception, "RDToolkitSupport :: Problem serialising the captured result");
			return safeFormat("console.error('Problem serialising the captured result: %s')", exception);
		}
	}

	String processProvisionedTest(Intent intentData) {
		try {
			JSONObject response = parseProvisionedTestToJson(intentData);
			return makeProvisionedTestJavaScript(response);

		} catch (Exception exception) {
			error(exception, "RDToolkitSupport :: Problem serialising the provisioned RD Test");
			return safeFormat("console.error('Problem serialising the provisioned RD Test: %s')", exception);
		}
	}

	private String makeProvisionedTestJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.rdToolkitProvisionedTestResponse) {" +
				"  api.rdToolkitProvisionedTestResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('RDToolkitSupport :: Error on sending provisioned RD Test data to CHT-Core - Webapp', error);" +
				"}";

		return safeFormat(javaScript, response);
	}

	private String makeCapturedResponseJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.rdToolkitCapturedTestResponse) {" +
				"  api.rdToolkitCapturedTestResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('RDToolkitSupport :: Error on sending captured results of RD Test to CHT-Core - Webapp', error);" +
				"}";

		return safeFormat(javaScript, response, response);
	}

	private JSONObject parseProvisionedTestToJson(Intent intentData) throws NullPointerException, JSONException {
		TestSession session = RdtUtils.getRdtSession(intentData);
		trace(this, "RDToolkitSupport :: RD Test started, see session: %s", session);

		return json(
				"sessionId", session.getSessionId(),
				"timeResolved", getUtcIsoDate(session.getTimeResolved()),
				"timeStarted", getUtcIsoDate(session.getTimeStarted()),
				"state", session.getState()
		);
	}

	private JSONObject parseCapturedResponseToJson(Intent intentData) throws NullPointerException, JSONException {
		TestSession session = RdtUtils.getRdtSession(intentData);
		TestResult result = session.getResult();
		trace(this, "RDToolkitSupport :: RD Test completed, session: %s, results: %s", session, result);

		return json(
				"sessionId", session.getSessionId(),
				"state", session.getState(),
				"timeResolved", getUtcIsoDate(session.getTimeResolved()),
				"timeStarted", getUtcIsoDate(session.getTimeStarted()),
				"timeRead", result == null ? null : getUtcIsoDate(result.getTimeRead()),
				"croppedImage", result == null ? null : getImage(result.getImages().get("cropped")),
				"results", parseResultsToJson(result)
		);
	}

	private JSONArray parseResultsToJson(TestResult result) throws JSONException {
		JSONArray jsonResult = new JSONArray();

		if (result != null) {
			Map<String, String> resultMap = result.getResults();

			for (Map.Entry<String, String> entry : resultMap.entrySet()) {
				jsonResult.put(json(
						"test", entry.getKey(),
						"result", entry.getValue()
				));
			}
		}

		return jsonResult;
	}
}
