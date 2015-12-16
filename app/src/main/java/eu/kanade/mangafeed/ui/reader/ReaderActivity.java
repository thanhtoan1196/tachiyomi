package eu.kanade.mangafeed.ui.reader;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.base.activity.BaseRxActivity;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import eu.kanade.mangafeed.ui.reader.viewer.horizontal.LeftToRightReader;
import eu.kanade.mangafeed.ui.reader.viewer.horizontal.RightToLeftReader;
import eu.kanade.mangafeed.ui.reader.viewer.vertical.VerticalReader;
import eu.kanade.mangafeed.ui.reader.viewer.webtoon.WebtoonReader;
import eu.kanade.mangafeed.util.ToastUtil;
import icepick.Icepick;
import nucleus.factory.RequiresPresenter;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;

@RequiresPresenter(ReaderPresenter.class)
public class ReaderActivity extends BaseRxActivity<ReaderPresenter> {

    @Bind(R.id.page_number) TextView pageNumber;
    @Bind(R.id.reader) FrameLayout container;
    @Bind(R.id.toolbar) Toolbar toolbar;

    @Inject PreferencesHelper preferences;

    private BaseReader viewer;
    private ReaderMenu readerMenu;

    private int uiFlags;
    private int readerTheme;
    protected CompositeSubscription subscriptions;
    private Subscription customBrightnessSubscription;

    private static final int LEFT_TO_RIGHT = 1;
    private static final int RIGHT_TO_LEFT = 2;
    private static final int VERTICAL = 3;
    private static final int WEBTOON = 4;

    public static final int BLACK_THEME = 1;

    public static Intent newIntent(Context context) {
        return new Intent(context, ReaderActivity.class);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        App.get(this).getComponent().inject(this);
        setContentView(R.layout.activity_reader);
        ButterKnife.bind(this);

        setupToolbar(toolbar);
        subscriptions = new CompositeSubscription();

        readerMenu = new ReaderMenu(this);
        Icepick.restoreInstanceState(readerMenu, savedState);
        if (savedState != null && readerMenu.showing)
            readerMenu.show(false);

        readerTheme = preferences.getReaderTheme();
        if (readerTheme == BLACK_THEME)
            setBlackTheme();

        initializeSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setSystemUiVisibility();
    }

    @Override
    protected void onPause() {
        if (viewer != null)
            getPresenter().setCurrentPage(viewer.getCurrentPosition());
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        subscriptions.unsubscribe();
        if (viewer != null)
            viewer.destroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Icepick.saveInstanceState(readerMenu, outState);
        super.onSaveInstanceState(outState);
    }

    public void onChapterError() {
        finish();
        ToastUtil.showShort(this, R.string.page_list_error);
    }

    public void onChapterReady(List<Page> pages, Manga manga, Chapter chapter) {
        if (viewer != null)
            viewer.destroy();
        viewer = createViewer(manga);
        viewer.onPageListReady(pages);
        viewer.updatePageNumber();
        readerMenu.onChapterReady(pages.size(), manga, chapter);
    }

    private BaseReader createViewer(Manga manga) {
        int mangaViewer = manga.viewer == 0 ? preferences.getDefaultViewer() : manga.viewer;

        switch (mangaViewer) {
            case LEFT_TO_RIGHT: default:
                return new LeftToRightReader(this);
            case RIGHT_TO_LEFT:
                return new RightToLeftReader(this);
            case VERTICAL:
                return new VerticalReader(this);
            case WEBTOON:
                return new WebtoonReader(this);
        }
    }

    public void onPageChanged(int currentPageIndex, int totalPages) {
        String page = (currentPageIndex + 1) + "/" + totalPages;
        pageNumber.setText(page);
        readerMenu.onPageChanged(currentPageIndex);
    }

    public void setSelectedPage(int pageIndex) {
        viewer.setSelectedPage(pageIndex);
    }

    public void onCenterSingleTap() {
        readerMenu.toggle();
    }

    private void initializeSettings() {
        subscriptions.add(preferences.showPageNumber()
                .asObservable()
                .subscribe(this::setPageNumberVisibility));

        subscriptions.add(preferences.lockOrientation()
                .asObservable()
                .subscribe(this::setOrientation));

        subscriptions.add(preferences.hideStatusBar()
                .asObservable()
                .subscribe(this::setStatusBarVisibility));

        subscriptions.add(preferences.keepScreenOn()
                .asObservable()
                .subscribe(this::setKeepScreenOn));

        subscriptions.add(preferences.customBrightness()
                .asObservable()
                .subscribe(this::setCustomBrightness));
    }

    private void setOrientation(boolean locked) {
        if (locked) {
            int orientation;
            int rotation = ((WindowManager) getSystemService(
                    Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
            }
            setRequestedOrientation(orientation);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void setPageNumberVisibility(boolean visible) {
        pageNumber.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    private void setKeepScreenOn(boolean enabled) {
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setCustomBrightness(boolean enabled) {
        if (enabled) {
            subscriptions.add(customBrightnessSubscription = preferences.customBrightnessValue()
                    .asObservable()
                    .subscribe(this::setCustomBrightnessValue));
        } else {
            if (customBrightnessSubscription != null)
                subscriptions.remove(customBrightnessSubscription);
            setCustomBrightnessValue(-1);
        }
    }

    private void setCustomBrightnessValue(float value) {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = value;
        getWindow().setAttributes(layout);
    }

    private void setStatusBarVisibility(boolean hidden) {
        createUiHideFlags(hidden);
        setSystemUiVisibility();
    }

    private void createUiHideFlags(boolean statusBarHidden) {
        uiFlags = 0;
        uiFlags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (statusBarHidden)
            uiFlags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            uiFlags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }

    public void setSystemUiVisibility() {
        getWindow().getDecorView().setSystemUiVisibility(uiFlags);
    }

    protected void setMangaDefaultViewer(int viewer) {
        getPresenter().updateMangaViewer(viewer);
        recreate();
    }

    private void setBlackTheme() {
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);
        pageNumber.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
        pageNumber.setBackgroundColor(ContextCompat.getColor(this, R.color.page_number_background_black));
    }

    public int getReaderTheme() {
        return readerTheme;
    }

    public ViewGroup getContainer() {
        return container;
    }

    public PreferencesHelper getPreferences() {
        return preferences;
    }

    public BaseReader getViewer() {
        return viewer;
    }

}