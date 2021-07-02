package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.ACCESS_STORAGE_PERMISSION_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.Utils.json;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.rdtoolkit.support.interop.CaptureIntentBuilder;
import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtProvisioningIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.STATUS;
import org.rdtoolkit.support.model.session.TestSession;
import org.robolectric.annotation.Config;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@Config(sdk=28)
@PrepareForTest({
		RdtUtils.class,
		RdtIntentBuilder.class,
		MedicLog.class,
		Uri.class,
		ContextCompat.class,
		ActivityCompat.class
})
public class RDToolkitSupportActivityTest extends TestCase {
	@Mock
	Activity mockContext;

	@Test
	public void processActivity_provisionTestActivity_returnsScriptCorrectly() throws Exception {
		//> GIVEN
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		Intent intent = mock(Intent.class);

		TestSession session = PowerMockito.mock(TestSession.class);
		when(session.getSessionId()).thenReturn("session1");
		when(session.getState()).thenReturn(STATUS.RUNNING);

		Date resolvedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:20:05.000+0000");
		when(session.getTimeResolved()).thenReturn(resolvedDate);

		Date startedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:15:00.000+0000");
		when(session.getTimeStarted()).thenReturn(startedDate);

		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenReturn(session);

		JSONObject expectedResponse = json(
				"sessionId", "session1",
				"timeResolved", "2020-05-25T22:20:05.000+0000",
				"timeStarted", "2020-05-25T22:15:00.000+0000",
				"state", "RUNNING"
		);

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.rdToolkitProvisionedTestResponse) {" +
				"  api.rdToolkitProvisionedTestResponse(" + expectedResponse.toString() + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('RDToolkitSupport :: Error on sending provisioned RD Test data to CHT-Core - Webapp', error);" +
				"}";

		//> WHEN
		String script = rdToolkitSupportActivity.processActivity(RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE, RESULT_OK, intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processActivity_provisionTestActivity_catchesException() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenThrow(NullPointerException.class);

		//> WHEN
		String script = rdToolkitSupportActivity.processActivity(RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE, RESULT_OK, intent);

		//> THEN
		assertEquals("console.error('Problem serialising the provisioned RD Test: java.lang.NullPointerException')", script);
	}

	@Test
	public void processActivity_captureResponseActivity_returnsScriptCorrectly() throws Exception {
		//> GIVEN
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		Intent intent = mock(Intent.class);

		TestSession session = PowerMockito.mock(TestSession.class);
		when(session.getSessionId()).thenReturn("session1");
		when(session.getState()).thenReturn(STATUS.QUEUED);

		Date timeResolved = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:20:05.000+0000");
		when(session.getTimeResolved()).thenReturn(timeResolved);

		Date timeStarted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:15:00.000+0000");
		when(session.getTimeStarted()).thenReturn(timeStarted);

		TestSession.TestResult result = PowerMockito.mock(TestSession.TestResult.class);
		Date timeRead = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:25:10.000+0000");
		when(result.getTimeRead()).thenReturn(timeRead);
		when(result.getImages()).thenReturn(new HashMap<String, String>());

		Map<String, String> resultMap = new HashMap<>();
		resultMap.put("mal_pf", "mal_pf_neg");
		resultMap.put("mal_pv", "mal_pv_pos");
		when(result.getResults()).thenReturn(resultMap);

		when(session.getResult()).thenReturn(result);

		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenReturn(session);

		JSONArray expectedResults = new JSONArray();
		expectedResults.put(json("test", "mal_pf", "result", "mal_pf_neg"));
		expectedResults.put(json("test", "mal_pv", "result", "mal_pv_pos"));

		JSONObject expectedResponse = json(
				"sessionId", "session1",
				"timeResolved", "2020-05-25T22:20:05.000+0000",
				"timeStarted", "2020-05-25T22:15:00.000+0000",
				"state", "QUEUED",
				"timeRead", "2020-05-25T22:25:10.000+0000",
				"results", expectedResults
		);

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.rdToolkitCapturedTestResponse) {" +
				"  api.rdToolkitCapturedTestResponse(" + expectedResponse.toString() + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('RDToolkitSupport :: Error on sending captured results of RD Test to CHT-Core - Webapp', error);" +
				"}";

		//> WHEN
		String script = rdToolkitSupportActivity.processActivity(RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE, RESULT_OK, intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processActivity_captureResponseActivity_catchesException() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenThrow(NullPointerException.class);

		//> WHEN
		String script = rdToolkitSupportActivity.processActivity(RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE, RESULT_OK, intent);

		//> THEN
		assertEquals("console.error('Problem serialising the captured result: java.lang.NullPointerException')", script);
	}

	@Test
	public void processActivity_unknownActivity_throwsException() {
		//> GIVEN
		int unknownActivity = 99;
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenThrow(NullPointerException.class);

		//> WHEN
		try {
			String script = rdToolkitSupportActivity.processActivity(unknownActivity, RESULT_OK, intent);
			fail("expected exception did not occurred.");
		} catch (Exception exception) {
			assertEquals("RDToolkitSupport :: Unsupported request code: 99", exception.getMessage());
		}
	}

	@Test
	public void startProvisionIntent_startsIntent() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		doNothing().when(mockContext).startActivityForResult((Intent) any(), anyInt());
		when(mockContext.getPackageName()).thenReturn("medic");
		when(intent.resolveActivity(null)).thenReturn(mock(ComponentName.class));

		PowerMockito.mockStatic(RdtIntentBuilder.class);
		RdtProvisioningIntentBuilder builder = mock(RdtProvisioningIntentBuilder.class);

		when(RdtIntentBuilder.forProvisioning()).thenReturn(builder);
		when(builder.setCallingPackage(anyString())).thenReturn(builder);
		when(builder.setReturnApplication((Context) any())).thenReturn(builder);
		when(builder.requestProfileCriteria(anyString(), (ProvisionMode) any())).thenReturn(builder);
		when(builder.setSessionId(anyString())).thenReturn(builder);
		when(builder.setFlavorOne(anyString())).thenReturn(builder);
		when(builder.setFlavorTwo(anyString())).thenReturn(builder);
		when(builder.setCloudworksBackend(anyString(), anyString())).thenReturn(builder);
		when(builder.build()).thenReturn(intent);

		//> WHEN
		rdToolkitSupportActivity.startProvisionIntent("session1", "John", "patient1", "mal_pf", "https://monitorApi.org/api");

		//> THEN
		verify(mockContext).startActivityForResult(eq(intent), eq(RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE));
		verify(builder).setCallingPackage(eq("medic"));
		verify(builder).setReturnApplication(eq(mockContext));
		verify(builder).requestProfileCriteria(eq("mal_pf"), eq(ProvisionMode.CRITERIA_SET_OR));
		verify(builder).setSessionId(eq("session1"));
		verify(builder).setFlavorOne(eq("John"));
		verify(builder).setFlavorTwo(eq("patient1"));
		verify(builder).setCloudworksBackend(eq("https://monitorApi.org/api"), eq("patient1"));

		//> WHEN
		rdToolkitSupportActivity.startProvisionIntent("session1", "John", "patient1", "mal_pf mal_pv", "https://monitorApi.org/api");

		//> THEN
		verify(builder).requestProfileCriteria(eq("mal_pf mal_pv"), eq(ProvisionMode.CRITERIA_SET_AND));
	}

	@Test
	public void startCaptureIntent_startsIntent() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		doNothing().when(mockContext).startActivityForResult((Intent) any(), anyInt());
		when(mockContext.getPackageName()).thenReturn("medic");
		when(intent.resolveActivity(null)).thenReturn(mock(ComponentName.class));

		PowerMockito.mockStatic(ContextCompat.class);
		when(ContextCompat.checkSelfPermission((Context) any(), anyString())).thenReturn(PERMISSION_GRANTED);

		PowerMockito.mockStatic(RdtIntentBuilder.class);
		CaptureIntentBuilder builder = mock(CaptureIntentBuilder.class);

		when(RdtIntentBuilder.forCapture()).thenReturn(builder);
		when(builder.setSessionId(anyString())).thenReturn(builder);
		when(builder.build()).thenReturn(intent);

		//> WHEN
		rdToolkitSupportActivity.startCaptureIntent("session1");

		//> THEN
		verify(mockContext).startActivityForResult(eq(intent), eq(RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE));
		verify(builder).setSessionId(eq("session1"));
		PowerMockito.verifyStatic(ContextCompat.class, atLeast(1));
		ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE);
	}

	@Test
	public void startCaptureIntent_requestsPermission() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		when(mockContext.getPackageName()).thenReturn("medic");
		when(intent.resolveActivity(null)).thenReturn(mock(ComponentName.class));

		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(ActivityCompat.class);
		PowerMockito.mockStatic(ContextCompat.class);
		when(ContextCompat.checkSelfPermission((Context) any(), anyString())).thenReturn(PERMISSION_DENIED);

		PowerMockito.mockStatic(RdtIntentBuilder.class);
		CaptureIntentBuilder builder = mock(CaptureIntentBuilder.class);

		when(RdtIntentBuilder.forCapture()).thenReturn(builder);
		when(builder.setSessionId(anyString())).thenReturn(builder);
		when(builder.build()).thenReturn(intent);

		//> WHEN
		rdToolkitSupportActivity.startCaptureIntent("session1");

		//> THEN
		PowerMockito.verifyStatic(ContextCompat.class, atLeast(1));
		ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE);
		PowerMockito.verifyStatic(ActivityCompat.class, atLeast(1));
		ActivityCompat.requestPermissions(mockContext, new String[]{READ_EXTERNAL_STORAGE}, ACCESS_STORAGE_PERMISSION_REQUEST_CODE);
		verify(builder).setSessionId(eq("session1"));
	}

	@Test
	public void resumeCaptureActivity_startsPermission() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		when(mockContext.getPackageName()).thenReturn("medic");
		doNothing().when(mockContext).startActivityForResult((Intent) any(), anyInt());
		when(intent.resolveActivity(null)).thenReturn(mock(ComponentName.class));

		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(ActivityCompat.class);
		PowerMockito.mockStatic(ContextCompat.class);
		when(ContextCompat.checkSelfPermission((Context) any(), anyString())).thenReturn(PERMISSION_DENIED);

		PowerMockito.mockStatic(RdtIntentBuilder.class);
		CaptureIntentBuilder builder = mock(CaptureIntentBuilder.class);

		when(RdtIntentBuilder.forCapture()).thenReturn(builder);
		when(builder.setSessionId(anyString())).thenReturn(builder);
		when(builder.build()).thenReturn(intent);

		rdToolkitSupportActivity.startCaptureIntent("session1");

		//> WHEN
		rdToolkitSupportActivity.resumeCaptureActivity();

		//> THEN
		verify(mockContext).startActivityForResult(eq(intent), eq(RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE));
		verify(builder).setSessionId(eq("session1"));
		PowerMockito.verifyStatic(ContextCompat.class, atLeast(1));
		ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE);
		PowerMockito.verifyStatic(ActivityCompat.class, atLeast(1));
		ActivityCompat.requestPermissions(mockContext, new String[]{READ_EXTERNAL_STORAGE}, ACCESS_STORAGE_PERMISSION_REQUEST_CODE);
	}

	@Test
	public void resumeCaptureActivity_doesNothingIfNoIntent() {
		//> GIVEN
		RDToolkitSupportActivity rdToolkitSupportActivity = new RDToolkitSupportActivity(mockContext);
		doNothing().when(mockContext).startActivityForResult((Intent) any(), anyInt());

		//> WHEN
		rdToolkitSupportActivity.resumeCaptureActivity();

		//> THEN
		verify(mockContext, never()).startActivityForResult((Intent) any(), anyInt());
	}
}
