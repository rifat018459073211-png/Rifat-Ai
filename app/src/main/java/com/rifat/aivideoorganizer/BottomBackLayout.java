package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Adds a persistent bottom navigation action and moves any page-level back button into it. */
public final class BottomBackLayout {
    private BottomBackLayout() {}

    private static int dp(Activity a, int value) {
        return (int) (value * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void install(Activity activity, ScrollView scroll, Runnable fallback) {
        Button back = null;
        View page = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (page instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) page;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof Button) {
                    CharSequence label = ((Button) child).getText();
                    if (label != null && (label.toString().trim().startsWith("←")
                            || label.toString().trim().startsWith("‹"))) {
                        back = (Button) child;
                        group.removeViewAt(i);
                        break;
                    }
                }
            }
            page.setPadding(page.getPaddingLeft(), page.getPaddingTop(),
                    page.getPaddingRight(), page.getPaddingBottom() + dp(activity, 88));
        }

        if (back == null) {
            back = new Button(activity);
            back.setText("←  ফিরে যাও");
            back.setAllCaps(false);
            back.setOnClickListener(v -> {
                if (fallback != null) fallback.run();
                else activity.finish();
            });
        }

        back.setText("←  ফিরে যাও");
        back.setAllCaps(false);
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(31, 105, 190));
        background.setCornerRadius(dp(activity, 15));
        back.setBackground(background);
        back.setMinHeight(dp(activity, 52));
        back.setElevation(dp(activity, 3));

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.rgb(246, 248, 252));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 12));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));
        FrameLayout.LayoutParams nav = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottom, nav);
        activity.setContentView(root);
    }
}
