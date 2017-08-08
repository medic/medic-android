package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;

import com.simprints.libsimprints.Identification;
import com.simprints.libsimprints.Registration;
import com.simprints.libsimprints.SimHelper;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

import static com.simprints.libsimprints.Constants.SIMPRINTS_IDENTIFICATIONS;
import static com.simprints.libsimprints.Constants.SIMPRINTS_REGISTRATION;
import static org.medicmobile.webapp.mobile.BuildConfig.SIMPRINTS_API_KEY;
import static org.medicmobile.webapp.mobile.BuildConfig.SIMPRINTS_MODULE_ID;
import static org.medicmobile.webapp.mobile.BuildConfig.SIMPRINTS_USER_ID;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.intentHandlerAvailableFor;
import static org.medicmobile.webapp.mobile.Utils.json;

final class SimprintsSupport {
	private static final int INTENT_TYPE_MASK = 0x7;
	private static final int INTENT_ID_MASK = 0xFFFFF8;

	// future: private static final int INTENT_CONFIRM_IDENTITY = 1;
	private static final int INTENT_IDENTIFY = 2;
	private static final int INTENT_REGISTER = 3;
	// future: private static final int INTENT_UPDATE = 4;
	// future: private static final int INTENT_VERIFY = 5;

	private final Activity ctx;

	SimprintsSupport(Activity ctx) {
		this.ctx = ctx;
	}

	boolean isAppInstalled() {
		return
				intentHandlerAvailableFor(ctx, regIntent()) &&
				intentHandlerAvailableFor(ctx, identIntent());
	}

	void startIdent(int targetInputId) {
		checkValid(targetInputId);
		ctx.startActivityForResult(identIntent(), targetInputId | INTENT_IDENTIFY);
	}

	void startReg(int targetInputId) {
		checkValid(targetInputId);
		ctx.startActivityForResult(regIntent(), targetInputId | INTENT_REGISTER);
	}

	String process(int requestCode, Intent i) {
		int requestType = requestCode & INTENT_TYPE_MASK;
		int requestId = requestCode & INTENT_ID_MASK;

		trace(this, "process() :: requestType=%s, requestCode=%s", requestType, requestCode);

		try {
			switch(requestType) {
				case INTENT_IDENTIFY: {
						JSONArray result = new JSONArray();
						if(i != null && i.hasExtra(SIMPRINTS_IDENTIFICATIONS)) {
							List<Identification> ids = i.getParcelableArrayListExtra(SIMPRINTS_IDENTIFICATIONS);
							for(Identification id : ids) {
								result.put(json(
									"id", id.getGuid(),
									"confidence", id.getConfidence(),
									"tier", id.getTier()
								));
							}
						}

						log("Simprints ident returned IDs: " + result + "; requestId=" + requestId);

						return respond(requestId, result.toString());
				}

				case INTENT_REGISTER: {
					if(i == null || !i.hasExtra(SIMPRINTS_REGISTRATION)) return "console.log('No registration data returned from simprints app.')";
					Registration registration = i.getParcelableExtra(SIMPRINTS_REGISTRATION);
					String id = registration.getGuid();
					log("Simprints registration returned ID: " + id + "; requestId=" + requestCode);
					JSONObject result = json("id", id);
					return respond(requestId, result.toString());
				}

				default: throw new RuntimeException("Bad request type: " + requestType);
			}
		} catch(JSONException ex) {
			warn(ex, "Problem serialising simprints identifications.");
			return safeFormat("console.log('Problem serialising simprints identifications: %s')", ex);
		}
	}

//> PRIVATE HELPERS
	private Intent identIntent() {
		return simHelper().identify(SIMPRINTS_MODULE_ID);
	}

	private Intent regIntent() {
		return simHelper().register(SIMPRINTS_MODULE_ID);
	}

	private void respond(int requestId, String result) {
		return safeFormat("angular.element(document.body).injector().get('AndroidApi').v1.simprintsResponse('%s', '%s')", requestId, result);
	}

//> STATIC HELPERS
	private static String safeFormat(String js, Object... args) {
		Object[] escapedArgs = new Object[args.length];
		for(int i=0; i<args.length; ++i) {
			escapedArgs[i] = jsEscape(args[i]);
		}
		return String.format(js, escapedArgs);
	}

	private static String jsEscape(Object s) {
		return s.toString()
				.replaceAll("'",  "\\'")
				.replaceAll("\n", "\\n");
	}

	private static SimHelper simHelper() {
		return new SimHelper(SIMPRINTS_API_KEY, SIMPRINTS_USER_ID);
	}

	private static void checkValid(int targetInputId) {
		if(targetInputId != (targetInputId & INTENT_ID_MASK)) throw new RuntimeException("Bad targetInputId: " + targetInputId);
	}
}
