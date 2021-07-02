package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.medicmobile.webapp.mobile.Utils.json;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

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
import org.rdtoolkit.support.model.session.TestSession.TestResult;
import org.robolectric.annotation.Config;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;
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
		BitmapFactory.class,
		Base64.class
})
public class RDToolkitSupportTest {

	@Mock
	Activity mockContext;

	@Test
	public void processProvisionedTest_returnsScriptCorrectly() throws Exception {
		//> GIVEN
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
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
		String script = rdToolkitSupport.processProvisionedTest(intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processProvisionedTest_catchesException() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenThrow(NullPointerException.class);

		//> WHEN
		String script = rdToolkitSupport.processProvisionedTest(intent);

		//> THEN
		assertEquals("console.error('Problem serialising the provisioned RD Test: java.lang.NullPointerException')", script);
	}

	@Test
	public void processCapturedResponse_returnsScriptCorrectly() throws Exception {
		//> GIVEN
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
		Intent intent = mock(Intent.class);

		TestSession session = PowerMockito.mock(TestSession.class);
		when(session.getSessionId()).thenReturn("session1");
		when(session.getState()).thenReturn(STATUS.QUEUED);

		Date timeResolved = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:20:05.000+0000");
		when(session.getTimeResolved()).thenReturn(timeResolved);

		Date timeStarted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).parse("2020-05-25T22:15:00.000+0000");
		when(session.getTimeStarted()).thenReturn(timeStarted);

		TestResult result = PowerMockito.mock(TestResult.class);
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
		String script = rdToolkitSupport.processCapturedResponse(intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processCapturedResponse_catchesException() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
		PowerMockito.mockStatic(MedicLog.class);
		PowerMockito.mockStatic(RdtUtils.class);
		when(RdtUtils.getRdtSession(intent)).thenThrow(NullPointerException.class);

		//> WHEN
		String script = rdToolkitSupport.processCapturedResponse(intent);

		//> THEN
		assertEquals("console.error('Problem serialising the captured result: java.lang.NullPointerException')", script);
	}

	@Test
	public void getImage_returnsBase64String() throws Exception {
		//> GIVEN
		String filePath = "content://img.png";
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
		PowerMockito.mockStatic(MedicLog.class);

		PowerMockito.mockStatic(Base64.class);
		when(Base64.encodeToString((byte[]) any(), anyInt())).thenReturn("bWF5IHRoZSBmb3JjZSBiZSB3aXRoIHlvdQ==");

		PowerMockito.mockStatic(Uri.class);
		Uri fileUri = mock(Uri.class);
		when(fileUri.getScheme()).thenReturn("content");
		when(Uri.parse(anyString())).thenReturn(fileUri);

		Bitmap bitmap = mock(Bitmap.class);
		when(bitmap.compress((Bitmap.CompressFormat) any(), anyInt(), (OutputStream) any())).thenReturn(true);

		PowerMockito.mockStatic(BitmapFactory.class);
		when(BitmapFactory.decodeStream((InputStream) any())).thenReturn(bitmap);

		ParcelFileDescriptor parcelFileDescriptor = mock(ParcelFileDescriptor.class);
		when(parcelFileDescriptor.getFileDescriptor()).thenReturn(mock(FileDescriptor.class));

		ContentResolver contentResolver = mock(ContentResolver.class);
		when(contentResolver.openFileDescriptor(fileUri, "r")).thenReturn(parcelFileDescriptor);

		when(mockContext.getContentResolver()).thenReturn(contentResolver);

		String expectedBase64 = "bWF5IHRoZSBmb3JjZSBiZSB3aXRoIHlvdQ==";

		//> WHEN
		String base64 = rdToolkitSupport.getImage(filePath);

		//> THEN
		assertEquals(expectedBase64, base64);

		verify(bitmap, times(1)).compress(eq(Bitmap.CompressFormat.JPEG), eq(75), (OutputStream) any());
		verify(contentResolver, times(1)).openFileDescriptor(fileUri, "r");

		PowerMockito.verifyStatic(Base64.class, atLeastOnce());
		Base64.encodeToString((byte[]) any(), eq(Base64.NO_WRAP));
		PowerMockito.verifyStatic(Uri.class, atLeast(2));
		Uri.parse(filePath);
	}

	@Test
	public void provisionRDTest_startsIntent() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
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
		Intent provission1 = rdToolkitSupport.provisionRDTest("session1", "John", "patient1", "mal_pf", "https://monitorApi.org/api");

		//> THEN
		assertEquals(provission1, intent);
		verify(builder).setCallingPackage(eq("medic"));
		verify(builder).setReturnApplication(eq(mockContext));
		verify(builder).requestProfileCriteria(eq("mal_pf"), eq(ProvisionMode.CRITERIA_SET_OR));
		verify(builder).setSessionId(eq("session1"));
		verify(builder).setFlavorOne(eq("John"));
		verify(builder).setFlavorTwo(eq("patient1"));
		verify(builder).setCloudworksBackend(eq("https://monitorApi.org/api"), eq("patient1"));

		//> WHEN
		Intent provission2 = rdToolkitSupport.provisionRDTest("session1", "John", "patient1", "mal_pf mal_pv", "https://monitorApi.org/api");

		//> THEN
		verify(builder).requestProfileCriteria(eq("mal_pf mal_pv"), eq(ProvisionMode.CRITERIA_SET_AND));
	}

	@Test
	public void captureRDTest_startsIntent() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		RDToolkitSupport rdToolkitSupport = new RDToolkitSupport(mockContext);
		when(mockContext.getPackageName()).thenReturn("medic");
		when(intent.resolveActivity(null)).thenReturn(mock(ComponentName.class));

		PowerMockito.mockStatic(RdtIntentBuilder.class);
		CaptureIntentBuilder builder = mock(CaptureIntentBuilder.class);

		when(RdtIntentBuilder.forCapture()).thenReturn(builder);
		when(builder.setSessionId(anyString())).thenReturn(builder);
		when(builder.build()).thenReturn(intent);

		//> WHEN
		Intent provission1 = rdToolkitSupport.captureRDTest("session1");

		//> THEN
		assertEquals(provission1, intent);
		verify(builder).setSessionId(eq("session1"));
	}
}
