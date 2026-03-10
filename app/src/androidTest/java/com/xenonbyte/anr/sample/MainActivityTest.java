package com.xenonbyte.anr.sample;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.isAssignableFrom;

import android.view.View;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MainActivity UI 测试
 *
 * 使用 Espresso 测试 UI 交互
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testActivityLaunches() {
        // 验证 Activity 能正常启动
        onView(withText("Falcon ANR Monitor Demo")).check(matches(isDisplayed()));
    }

    @Test
    public void testHealthCardIsDisplayed() {
        // 验证健康状态卡片显示
        onView(withId(R.id.health_card)).check(matches(isDisplayed()));
        onView(withId(R.id.health_status_text)).check(matches(isDisplayed()));
    }

    @Test
    public void testRefreshHealthButton() {
        // 点击刷新健康状态按钮
        onView(withId(R.id.refresh_health_btn)).perform(click());

        // 验证健康状态文本更新
        onView(withId(R.id.health_status_text)).check(matches(isDisplayed()));
    }

    @Test
    public void testCalculatorAddition() {
        // 输入数字
        onView(withId(R.id.num1_input)).perform(clearText(), typeText("10"), closeSoftKeyboard());
        onView(withId(R.id.num2_input)).perform(clearText(), typeText("5"), closeSoftKeyboard());

        // 点击加法按钮
        onView(withId(R.id.add_btn)).perform(click());

        // 验证结果
        onView(withId(R.id.result_text)).check(matches(withText("Result: 15.00")));
    }

    @Test
    public void testCalculatorSubtraction() {
        onView(withId(R.id.num1_input)).perform(clearText(), typeText("10"), closeSoftKeyboard());
        onView(withId(R.id.num2_input)).perform(clearText(), typeText("3"), closeSoftKeyboard());

        onView(withId(R.id.subtract_btn)).perform(click());

        onView(withId(R.id.result_text)).check(matches(withText("Result: 7.00")));
    }

    @Test
    public void testCalculatorMultiplication() {
        onView(withId(R.id.num1_input)).perform(clearText(), typeText("6"), closeSoftKeyboard());
        onView(withId(R.id.num2_input)).perform(clearText(), typeText("7"), closeSoftKeyboard());

        onView(withId(R.id.multiply_btn)).perform(click());

        onView(withId(R.id.result_text)).check(matches(withText("Result: 42.00")));
    }

    @Test
    public void testCalculatorDivision() {
        onView(withId(R.id.num1_input)).perform(clearText(), typeText("20"), closeSoftKeyboard());
        onView(withId(R.id.num2_input)).perform(clearText(), typeText("4"), closeSoftKeyboard());

        onView(withId(R.id.divide_btn)).perform(click());

        onView(withId(R.id.result_text)).check(matches(withText("Result: 5.00")));
    }

    @Test
    public void testDivisionByZero() {
        onView(withId(R.id.num1_input)).perform(clearText(), typeText("10"), closeSoftKeyboard());
        onView(withId(R.id.num2_input)).perform(clearText(), typeText("0"), closeSoftKeyboard());

        onView(withId(R.id.divide_btn)).perform(click());

        onView(withId(R.id.result_text)).check(matches(withText("Result: Error (division by zero)")));
    }

    @Test
    public void testButtonsAreDisplayed() {
        // 验证所有测试按钮都显示
        onView(withId(R.id.slow_task_btn)).check(matches(isDisplayed()));
        onView(withId(R.id.anr_task_btn)).check(matches(isDisplayed()));
        onView(withId(R.id.custom_block_btn)).check(matches(isDisplayed()));
    }

    @Test
    public void testLogSectionIsDisplayed() {
        onView(withText("Event Log")).check(matches(isDisplayed()));
        onView(withId(R.id.log_text)).check(matches(isDisplayed()));
    }

    /**
     * 清除 EditText 文本的辅助方法
     */
    private static ViewAction clearText() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return allOf(isDisplayed(), isAssignableFrom(android.widget.EditText.class));
            }

            @Override
            public String getDescription() {
                return "clear text";
            }

            @Override
            public void perform(UiController uiController, View view) {
                ((android.widget.EditText) view).getText().clear();
            }
        };
    }
}
