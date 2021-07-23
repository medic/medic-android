package org.medicmobile.webapp.mobile;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.web.webdriver.DriverAtoms;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Locale;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.web.assertion.WebViewAssertions.webContent;
import static androidx.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static androidx.test.espresso.web.matcher.DomMatchers.hasElementWithId;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.clearElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.getText;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;

@LargeTest
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LoginTests {

	private static final String ERROR_INCORRECT = "Incorrect user name or password. Please try again.";

	@Rule
	public ActivityScenarioRule<SettingsDialogActivity> mActivityTestRule =
			new ActivityScenarioRule<>(SettingsDialogActivity.class);

	@Test
	public void testLoginScreen() throws Exception {
		DataInteraction linearLayout = onData(anything())
				.inAdapterView(allOf(withId(R.id.lstServers),
						childAtPosition(
								withId(android.R.id.content),
								0)))
				.atPosition(2);
		linearLayout.perform(click());
		Thread.sleep(7000);//TODO: use better ways to handle delays

		ViewInteraction webView = onView(
				allOf(withId(R.id.wbvMain),
						withParent(allOf(withId(R.id.lytWebView),
								withParent(withId(android.R.id.content)))),
						isDisplayed()));
		webView.check(matches(isDisplayed()));
		onWebView()
				.withNoTimeout()
				.check(webContent(hasElementWithId("form")))
				.withElement(findElement(Locator.ID, "locale"))
				.check(webMatches(getText(), containsString("English")));
		String[] codes = {"es", "en", "fr", "sw"};
		for (String code : codes) {
			onWebView()
					.withNoTimeout()
					.withElement(findElement(Locator.NAME, code))
					.check(webMatches(getText(), containsString(getLanguage(code))));
		}

		//login form and errors
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.ID, "user"))
				.perform(clearElement())
				.perform(DriverAtoms.webKeys("fakeName"))    //to be created first
				.withElement(findElement(Locator.ID, "password"))
				.perform(clearElement())
				.perform(DriverAtoms.webKeys("fake_password"))
				.withElement(findElement(Locator.ID, "login"))
				.perform(webClick());
		Thread.sleep(4000);//TODO: use better ways to handle delays - takes longer with emulators
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.CSS_SELECTOR, "p.error.incorrect"))
				.check(webMatches(getText(), containsString(ERROR_INCORRECT)));
	}

	private String getLanguage(String code) {
		Locale aLocale = new Locale(code);
		return aLocale.getDisplayName();
	}

	private static Matcher<View> childAtPosition(
			final Matcher<View> parentMatcher, final int position) {

		return new TypeSafeMatcher<View>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("Child at position " + position + " in parent ");
				parentMatcher.describeTo(description);
			}

			@Override
			public boolean matchesSafely(View view) {
				ViewParent parent = view.getParent();
				return parent instanceof ViewGroup && parentMatcher.matches(parent)
						&& view.equals(((ViewGroup) parent).getChildAt(position));
			}
		};
	}
}
