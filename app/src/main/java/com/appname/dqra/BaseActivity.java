package com.appname.dqra;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.appname.dqra.maindashboards.UserDashboard;

public class BaseActivity extends AppCompatActivity {

    private View indicatorHome, indicatorProfile;
    private TextView tvProfileLabel;
    private ImageView imgNavHome, imgNavProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_base);
        applySystemBarSpacing();

        // Indicators (pill background behind icon)
        indicatorHome    = findViewById(R.id.indicatorHome);
        indicatorProfile = findViewById(R.id.indicatorProfile);

        // Nav icon ImageViews
        imgNavHome    = findNavIcon(R.id.btnHome);
        imgNavProfile = findNavIcon(R.id.btnProfile);

        // Labels
        tvProfileLabel = findViewById(R.id.tvProfileLabel);

        // Nav tab buttons
        LinearLayout btnHome    = findViewById(R.id.btnHome);
        LinearLayout btnProfile = findViewById(R.id.btnProfile);

        // Default active tab
        setActiveTab(Tab.HOME);

        btnHome.setOnClickListener(v -> {
            if (!getHomeActivityClass().isInstance(this)) {
                setActiveTab(Tab.HOME);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                startActivity(new Intent(this, getHomeActivityClass()));
                finish();
            }
        });

        btnProfile.setOnClickListener(v -> {
            if (!getProfileActivityClass().isInstance(this)) {
                setActiveTab(Tab.PROFILE);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                startActivity(new Intent(this, getProfileActivityClass()));
                finish();
            }
        });
    }

    private void applySystemBarSpacing() {
        View root = findViewById(R.id.clRoot);
        View bottomNav = findViewById(R.id.bottomNav);
        if (root == null || bottomNav == null) return;

        int rootPaddingLeft = root.getPaddingLeft();
        int rootPaddingTop = root.getPaddingTop();
        int rootPaddingRight = root.getPaddingRight();
        int rootPaddingBottom = root.getPaddingBottom();

        ViewGroup.MarginLayoutParams bottomNavParams =
                (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
        int navMarginLeft = bottomNavParams.leftMargin;
        int navMarginRight = bottomNavParams.rightMargin;
        int navMarginBottom = bottomNavParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    rootPaddingLeft + systemBars.left,
                    rootPaddingTop + systemBars.top,
                    rootPaddingRight + systemBars.right,
                    rootPaddingBottom
            );

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
            params.leftMargin = navMarginLeft;
            params.rightMargin = navMarginRight;
            params.bottomMargin = navMarginBottom + systemBars.bottom;
            bottomNav.setLayoutParams(params);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Helper — finds the ImageView inside a nav tab's FrameLayout child.
     */
    private ImageView findNavIcon(int tabLayoutId) {
        LinearLayout tab = findViewById(tabLayoutId);
        if (tab == null) return null;
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (child instanceof FrameLayout) {
                FrameLayout frame = (FrameLayout) child;
                for (int j = 0; j < frame.getChildCount(); j++) {
                    View fChild = frame.getChildAt(j);
                    if (fChild instanceof ImageView) return (ImageView) fChild;
                }
            }
        }
        return null;
    }

    /** Inflate child layout into the content FrameLayout */
    protected void setContentLayout(int layoutResID) {
        FrameLayout contentFrame = findViewById(R.id.contentFrame);
        contentFrame.removeAllViews();
        LayoutInflater.from(this).inflate(layoutResID, contentFrame, true);
    }

    /** Enum for bottom nav tabs */
    protected enum Tab {
        HOME,
        PROFILE
    }

    /**
     * Updates the pill indicator, icon tints, and label colors
     * to reflect the active tab.
     */
    protected void setActiveTab(Tab tab) {
        if (indicatorHome == null || indicatorProfile == null) return;

        switch (tab) {
            case HOME:
                indicatorHome.setVisibility(View.VISIBLE);
                indicatorProfile.setVisibility(View.GONE);

                if (imgNavHome != null)
                    imgNavHome.setColorFilter(
                            ContextCompat.getColor(this, android.R.color.white),
                            PorterDuff.Mode.SRC_IN);
                if (imgNavProfile != null)
                    imgNavProfile.setColorFilter(0xFFBBBBBB, PorterDuff.Mode.SRC_IN);

                if (tvProfileLabel != null)
                    tvProfileLabel.setTextColor(0xFFBBBBBB);
                break;

            case PROFILE:
                indicatorHome.setVisibility(View.GONE);
                indicatorProfile.setVisibility(View.VISIBLE);

                if (imgNavHome != null)
                    imgNavHome.setColorFilter(0xFFBBBBBB, PorterDuff.Mode.SRC_IN);
                if (imgNavProfile != null)
                    imgNavProfile.setColorFilter(
                            ContextCompat.getColor(this, android.R.color.white),
                            PorterDuff.Mode.SRC_IN);

                if (tvProfileLabel != null)
                    tvProfileLabel.setTextColor(0xFFFFFFFF);
                break;
        }
    }

    protected Class<?> getHomeActivityClass() {
        return UserDashboard.class;
    }

    protected Class<?> getProfileActivityClass() {
        return ProfileActivity.class;
    }
}
