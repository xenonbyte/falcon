package com.xenonbyte.anr.sample;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testActivityLaunches() {
        onView(withId(R.id.title_text)).check(matches(withText("Falcon Monitor Sample")));
    }

    @Test
    public void testStatusCardIsDisplayed() {
        onView(withId(R.id.health_card)).check(matches(isDisplayed()));
        onView(withId(R.id.lifecycle_state_text)).check(matches(isDisplayed()));
        onView(withId(R.id.health_state_text)).check(matches(isDisplayed()));
        onView(withId(R.id.health_status_text)).check(matches(isDisplayed()));
    }

    @Test
    public void testRefreshHealthButton() {
        onView(withId(R.id.refresh_health_btn)).perform(click());
        onView(withId(R.id.health_status_text)).check(matches(isDisplayed()));
    }

    @Test
    public void testTriggerControlsAreDisplayed() {
        onView(withId(R.id.slow_task_btn)).check(matches(isDisplayed()));
        onView(withId(R.id.anr_task_btn)).check(matches(isDisplayed()));
        onView(withId(R.id.block_duration_input)).check(matches(isDisplayed()));
        onView(withId(R.id.custom_block_btn)).check(matches(isDisplayed()));
    }

    @Test
    public void testEventPanelsAreDisplayed() {
        onView(withId(R.id.event_section_title)).check(matches(isDisplayed()));
        onView(withId(R.id.latest_event_title_text)).check(matches(isDisplayed()));
        onView(withId(R.id.log_section_title)).check(matches(isDisplayed()));
        onView(withId(R.id.log_text)).check(matches(isDisplayed()));
    }
}
