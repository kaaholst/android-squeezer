
/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.itemlist;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.MenuCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.NowPlayingActivity;
import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.dialog.NetworkErrorDialogFragment;
import uk.org.ngo.squeezer.framework.BaseListActivity;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.util.ThemeManager;
import uk.org.ngo.squeezer.widget.DividerItemDecoration;
import uk.org.ngo.squeezer.widget.GridAutofitLayoutManager;

/*
 * The activity's content view scrolls in from the right, and disappear to the left, to provide a
 * spatial component to navigation.
 */
public class JiveItemListActivity extends BaseListActivity<ItemViewHolder<JiveItem>, JiveItem>
        implements NetworkErrorDialogFragment.NetworkErrorDialogListener {
    private static final int GO = 1;
    private static final String FINISH = "FINISH";
    private static final String RELOAD = "RELOAD";
    private static final String WINDOW = "WINDOW";
    public static final String WINDOW_EXTRA = "windowId";

    private JiveItemViewLogic pluginViewDelegate;
    private boolean register;
    protected JiveItem parent;
    private Action action;
    Window window = new Window();
    private int selectedIndex;

    private Menu viewMenu;
    private MenuItem menuItemLight;
    private MenuItem menuItemDark;
    private MenuItem menuItemList;
    private MenuItem menuItemGrid;
    private MenuItem menuItemOneLine;
    private MenuItem menuItemTwoLines;
    private MenuItem menuItemAllInfo;

    private ViewParamItemView<JiveItem> parentViewHolder;
    private DividerItemDecoration dividerItemDecoration;

    @Override
    protected ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> createItemListAdapter() {
        return new JiveItemAdapter(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        dividerItemDecoration = new DividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        super.onCreate(savedInstanceState);

        Bundle extras = Objects.requireNonNull(getIntent().getExtras(), "intent did not contain extras");
        register = extras.getBoolean("register");
        parent = extras.getParcelable(JiveItem.class.getName());
        action = extras.getParcelable(Action.class.getName());

        pluginViewDelegate = new JiveItemViewLogic(this);
        setParentViewHolder();

        // If initial setup is performed, use it
        if (savedInstanceState != null && savedInstanceState.containsKey("window")) {
            applyWindow(savedInstanceState.getParcelable("window"));
        } else {
            if (parent != null && parent.window != null) {
                applyWindow(parent.window);
            } else if (parent != null && "playlist".equals(parent.getType())) {
                // special case of playlist - override server based windowStyle to play_list
                applyWindowStyle(Window.WindowStyle.PLAY_LIST);
            } else
                applyWindowStyle(Window.WindowStyle.TEXT_ONLY);
        }

        findViewById(R.id.input_view).setVisibility((hasInputField()) ? View.VISIBLE : View.GONE);
        if (hasInputField()) {
            MaterialButton inputButton = findViewById(R.id.input_button);
            final EditText inputText = findViewById(R.id.plugin_input);
            TextInputLayout inputTextLayout = findViewById(R.id.plugin_input_til);
            int inputType = EditorInfo.TYPE_CLASS_TEXT;
            int inputImage = R.drawable.keyboard_return;

            switch (action.getInputType()) {
                case TEXT:
                    break;
                case SEARCH:
                    inputImage = R.drawable.ic_menu_search;
                    break;
                case EMAIL:
                    inputType |= EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                    break;
                case PASSWORD:
                    inputType |= EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
                    break;
            }
            inputText.setInputType(inputType);
            inputButton.setIconResource(inputImage);
            inputTextLayout.setHint(parent.input.title);
            inputText.setText(parent.input.initialText);
            parent.inputValue = parent.input.initialText;

            inputText.setOnKeyListener((v, keyCode, event) -> {
                if ((event.getAction() == KeyEvent.ACTION_DOWN)
                        && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    clearAndReOrderItems(inputText.getText().toString());
                    return true;
                }
                return false;
            });

            inputButton.setOnClickListener(v -> {
                if (getService() != null) {
                    clearAndReOrderItems(inputText.getText().toString());
                }
            });
        }
    }

    private void setParentViewHolder() {
        parentViewHolder = new ViewParamItemView<>(this, findViewById(R.id.parent_container));
        parentViewHolder.contextMenuButton.setOnClickListener(v -> pluginViewDelegate.showContextMenu(parentViewHolder, parent));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("window", window);
    }

    @Override
    public void onResume() {
        super.onResume();
        setupListView();
    }

    @Override
    public void onPause() {
        super.onPause();
        pluginViewDelegate.resetContextMenu();
        pluginViewDelegate.resetContextMenu();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        getListView().addItemDecoration(dividerItemDecoration);

        setupListView();
    }

    private void setupListView() {
        ArtworkListLayout listLayout = getListLayout();
        RecyclerView.LayoutManager layoutManager = getListView().getLayoutManager();
        if (listLayout == ArtworkListLayout.grid && !(layoutManager instanceof GridLayoutManager)) {
            getListView().setLayoutManager(new GridAutofitLayoutManager(this, R.dimen.grid_column_width));
            getListView().removeItemDecoration(dividerItemDecoration);
        }
        if (listLayout == ArtworkListLayout.list && (layoutManager instanceof GridLayoutManager)) {
            getListView().setLayoutManager(new LinearLayoutManager(this));
            getListView().addItemDecoration(dividerItemDecoration);
        }
    }

    void updateHeader(String windowTitle) {
        window.text = windowTitle;

        parentViewHolder.itemView.setVisibility(View.VISIBLE);
        parentViewHolder.text1.setText(windowTitle);
        parentViewHolder.icon.setVisibility(View.GONE);
        parentViewHolder.contextMenuButtonHolder.setVisibility(View.GONE);
    }

    void updateHeader(JiveItem parent) {
        updateHeader(parent.getName());

        if (!TextUtils.isEmpty(parent.text2)) {
            parentViewHolder.text2.setVisibility(View.VISIBLE);
            parentViewHolder.text2.setText(parent.text2);
        }

        if (parent.hasIcon() && window.windowStyle == Window.WindowStyle.TEXT_ONLY) {
            parentViewHolder.icon.setVisibility(View.VISIBLE);
            if (parent.hasIconUri()) {
                ImageFetcher.getInstance(this).loadImage(parent.getIcon(), parentViewHolder.icon);
            } else {
                parentViewHolder.icon.setImageDrawable(parent.getIconDrawable(this));
            }
        }

        if (parent.hasContextMenu()) {
            parentViewHolder.contextMenuButtonHolder.setVisibility(View.VISIBLE);
        }

    }

    private void updateHeader(@NonNull Window window) {
        if (!TextUtils.isEmpty(window.text)) {
            updateHeader(window.text);
        }
        if (!TextUtils.isEmpty(window.textarea)) {
            TextView header = findViewById(R.id.sub_header);
            header.setText(window.textarea);
            findViewById(R.id.sub_header_container).setVisibility(View.VISIBLE);
        }
    }

    private void applyWindow(@NonNull Window window) {
        applyWindowStyle(register ? Window.WindowStyle.TEXT_ONLY : window.windowStyle);
        updateHeader(window);

        window.titleStyle = this.window.titleStyle;
        window.text = this.window.text;
        this.window = window;
    }


    void applyWindowStyle(Window.WindowStyle windowStyle) {
        applyWindowStyle(windowStyle, getListLayout());
    }

    void applyWindowStyle(Window.WindowStyle windowStyle, ArtworkListLayout prevListLayout) {
        ArtworkListLayout listLayout = JiveItemView.listLayout(this, windowStyle);
        updateViewMenuItems(listLayout, windowStyle);
        if (windowStyle != window.windowStyle || listLayout != prevListLayout) {
            window.windowStyle = windowStyle;
            getItemAdapter().notifyDataSetChanged();
        }
        if (listLayout != prevListLayout) {
            setupListView();
        }
    }


    private void clearAndReOrderItems(String inputString) {
        if (getService() != null && !TextUtils.isEmpty(inputString)) {
            parent.inputValue = inputString;
            clearAndReOrderItems();
        }
    }

    private boolean hasInputField() {
        return parent != null && parent.hasInputField();
    }

    @Override
    protected boolean needPlayer() {
        // Most of the the times we actually do need a player, but if we need to register on SN,
        // it is before we can get the players
        return !register;
    }

    @Override
    protected void orderPage(@NonNull ISqueezeService service, int start) {
        if (parent != null) {
            if (action == null || (parent.hasInput() && !parent.isInputReady())) {
                showContent();
            } else
                service.pluginItems(start, parent, action, this);
        } else if (register) {
            service.register(this);
        }
    }

    // TODO: Adding Annotation will result in crash - @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(HandshakeComplete event) {
        super.onEventMainThread(event);
        if (parent != null && parent.hasSubItems()) {
            updateHeader(parent);
            getItemAdapter().update(parent.subItems.size(), 0, parent.subItems);
        }
    }

    @Override
    public void onItemsReceived(int count, int start, final Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        if (parameters.containsKey("goNow")) {
            Action.NextWindow nextWindow = Action.NextWindow.fromString(Util.getString(parameters, "goNow"));
            switch (nextWindow.nextWindow) {
                case nowPlaying:
                    NowPlayingActivity.show(this);
                    break;
                case playlist:
                    CurrentPlaylistActivity.show(this);
                    break;
                case home:
                    HomeActivity.show(this);
                    break;
            }
            finish();
            return;
        }

        final Window window = JiveItem.extractWindow(Util.getRecord(parameters, "window"), null);
        if (window != null) {
            // override server based icon_list style for playlist
            if (window.windowStyle == Window.WindowStyle.ICON_LIST && parent != null && "playlist".equals(parent.getType())) {
                window.windowStyle = Window.WindowStyle.PLAY_LIST;
            }
            runOnUiThread(() -> applyWindow(window));
        }

        if (this.window.text == null && parent != null) {
            runOnUiThread(() -> updateHeader(parent));
        }

        // The documentation says "Returned with value 1 if there was a network error accessing
        // the content source.". In practice (with at least the Napster and Pandora plugins) the
        // value is an error message suitable for displaying to the user.
        if (parameters.containsKey("networkerror")) {
            Resources resources = getResources();
            ISqueezeService service = getService();
            String playerName;

            if (service == null) {
                playerName = "Unknown";
            } else {
                playerName = service.getActivePlayer().getName();
            }

            String errorMsg = Util.getString(parameters, "networkerror");

            String errorMessage = String.format(resources.getString(R.string.server_error),
                    playerName, errorMsg);
            NetworkErrorDialogFragment networkErrorDialogFragment =
                    NetworkErrorDialogFragment.newInstance(errorMessage);
            networkErrorDialogFragment.show(getSupportFragmentManager(), "networkerror");
        }

        super.onItemsReceived(count, start, parameters, items, dataType);
    }

    @Override
    public void action(JiveItem item, Action action, int alreadyPopped) {
        if (getService() == null) {
            return;
        }

        if (action != null) {
            getService().action(item, action);
        }

        Action.JsonAction jAction = (action != null && action.action != null) ? action.action : null;
        Action.NextWindow nextWindow = (jAction != null ? jAction.nextWindow : item.nextWindow);
        nextWindow(nextWindow, alreadyPopped);
    }

    @Override
    public void action(Action.JsonAction action, int alreadyPopped) {
        if (getService() == null) {
            return;
        }

        getService().action(action);
        nextWindow(action.nextWindow, alreadyPopped);
    }

    private void nextWindow(Action.NextWindow nextWindow, int alreadyPopped) {
        while (alreadyPopped > 0 && nextWindow != null) {
            nextWindow = popNextWindow(nextWindow);
            alreadyPopped--;
        }
        if (nextWindow != null) {
            switch (nextWindow.nextWindow) {
                case nowPlaying:
                    // Do nothing as now playing is always available in Squeezer (maybe toast the action)
                    break;
                case playlist:
                    CurrentPlaylistActivity.show(this);
                    break;
                case home:
                    HomeActivity.show(this);
                    break;
                case parentNoRefresh:
                    finish();
                    break;
                case grandparent:
                    setResult(Activity.RESULT_OK, new Intent(FINISH));
                    finish();
                    break;
                case refresh:
                    clearAndReOrderItems();
                    break;
                case parent:
                case refreshOrigin:
                    setResult(Activity.RESULT_OK, new Intent(RELOAD));
                    finish();
                    break;
                case windowId:
                    setResult(Activity.RESULT_OK, new Intent(WINDOW).putExtra(WINDOW_EXTRA, nextWindow.windowId));
                    finish();
                    break;
            }
        }
    }

    private Action.NextWindow popNextWindow(Action.NextWindow nextWindow) {
        switch (nextWindow.nextWindow) {
            case parent:
            case parentNoRefresh:
                return null;
            case grandparent:
                return new Action.NextWindow(Action.NextWindowEnum.parentNoRefresh);
            case refreshOrigin:
                return new Action.NextWindow(Action.NextWindowEnum.refresh);
            default:
                return nextWindow;

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GO) {
            if (resultCode == RESULT_OK) {
                if (FINISH.equals(data.getAction())) {
                    finish();
                } else if (RELOAD.equals(data.getAction())) {
                    clearAndReOrderItems();
                } else if (WINDOW.equals(data.getAction())) {
                    String windowId = data.getStringExtra(WINDOW_EXTRA);
                    if (!(windowId.equals(parent.getId()) ||
                            (parent.window != null && windowId.equals(parent.window.windowId)) ||
                            JiveItem.HOME.getId().equals(parent.getId()))) {
                        setResult(Activity.RESULT_OK, new Intent(WINDOW).putExtra(WINDOW_EXTRA, windowId));
                        finish();
                    }
                }
            }
        }
    }

    /**
     * Save the supplied theme in preferences and restart activity to apply it.
     */
    private void setTheme(ThemeManager.Theme theme) {
        if (getThemeId() != theme.mThemeId) {
            new Preferences(this).setTheme(theme);

            Intent intent = getIntent();
            finish();
            overridePendingTransition(0, 0);
            startActivity(intent);
            overridePendingTransition(0, 0);
        }
    }

    public void setPreferredListLayout(ArtworkListLayout listLayout) {
        ArtworkListLayout prevListLayout = getListLayout();
        saveListLayout(listLayout);
        applyWindowStyle(window.windowStyle, prevListLayout);
    }

    ArtworkListLayout getListLayout() {
        return JiveItemView.listLayout(this, window.windowStyle);
    }

    protected void saveListLayout(ArtworkListLayout listLayout) {
        new Preferences(this).setAlbumListLayout(listLayout);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        selectedIndex = index;
    }

    /**
     * The user dismissed the network error dialog box. There's nothing more to do, so finish
     * the activity.
     */
    @Override
    public void onDialogDismissed(DialogInterface dialog) {
        runOnUiThread(this::finish);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.plugin_list_menu, menu);
        viewMenu = menu.findItem(R.id.menu_item_view).getSubMenu();
        MenuCompat.setGroupDividerEnabled(viewMenu, true);
        menuItemLight = viewMenu.findItem(R.id.menu_item_light);
        menuItemDark = viewMenu.findItem(R.id.menu_item_dark);
        menuItemList = viewMenu.findItem(R.id.menu_item_list);
        menuItemGrid = viewMenu.findItem(R.id.menu_item_grid);
        menuItemOneLine = viewMenu.findItem(R.id.menu_item_one_line);
        menuItemTwoLines = viewMenu.findItem(R.id.menu_item_two_lines);
        menuItemAllInfo = viewMenu.findItem(R.id.menu_item_all_lines);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        fixOverflowMenuIconColor(menu);
        updateViewMenuItems(getListLayout(), window.windowStyle);
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Work around an issue with Theme.MaterialComponents.Light.DarkActionBar.
     * <p>
     * Icon on the action bar is tinted correct to the theme of action bar. The overflow menu(s)
     * however are popped up in the theme of the main app, but tinted according to the action bar,
     * thus becoming invisible.
     */
    private void fixOverflowMenuIconColor(Menu menu) {
        if (getThemeId() == ThemeManager.Theme.LIGHT_DARKACTIONBAR.mThemeId) {
            fixOverflowMenuIconColor(menu, false);
        }
    }

    private void fixOverflowMenuIconColor(Menu menu, boolean isSubMenu) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (isSubMenu && item.getIcon() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    item.setIconTintList(getTint());
                } else {
                    Drawable icon = item.getIcon().mutate();
                    int color = R.attr.actionMenuTextColor;
                    icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    icon.setAlpha(item.isEnabled() ? 255 : 128);
                    item.setIcon(icon);
                }
            }
            if (item.hasSubMenu()) {
                fixOverflowMenuIconColor(item.getSubMenu(), true);
            }
        }
    }

    private ColorStateList getTint() {
        return AppCompatResources.getColorStateList(this, getAttributeValue(R.attr.colorControlNormal));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_item_light) {
            setTheme(ThemeManager.Theme.LIGHT_DARKACTIONBAR);
            return true;
        } else if (itemId == R.id.menu_item_dark) {
            setTheme(ThemeManager.Theme.DARK);
            return true;
        } else if (itemId == R.id.menu_item_list) {
            setPreferredListLayout(ArtworkListLayout.list);
            return true;
        } else if (itemId == R.id.menu_item_grid) {
            setPreferredListLayout(ArtworkListLayout.grid);
            return true;
        } else if (itemId == R.id.menu_item_one_line) {
            setMaxLines(1);
            return true;
        } else if (itemId == R.id.menu_item_two_lines) {
            setMaxLines(2);
            return true;
        } else if (itemId == R.id.menu_item_all_lines) {
            setMaxLines(0);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setMaxLines(int maxLines) {
        new Preferences(this).setMaxLines(getListLayout(), maxLines);
        updateViewMenuItems(getListLayout(), window.windowStyle);
        getListView().setAdapter(getListView().getAdapter());
    }

    private void updateViewMenuItems(ArtworkListLayout listLayout, Window.WindowStyle windowStyle) {
        if (menuItemList != null) {
            (getThemeId() ==  R.style.AppTheme ? menuItemDark : menuItemLight).setChecked(true);

            boolean canChangeListLayout = JiveItemView.canChangeListLayout(windowStyle);
            viewMenu.setGroupVisible(R.id.menu_group_artwork, canChangeListLayout);
            (listLayout == ArtworkListLayout.list ? menuItemList : menuItemGrid).setChecked(true);

            switch (new Preferences(this).getMaxLines(listLayout)) {
                case 1:
                    menuItemOneLine.setChecked(true);
                    break;
                case 2:
                    menuItemTwoLines.setChecked(true);
                    break;
                default:
                    menuItemAllInfo.setChecked(true);
                    break;
            }
        }
    }


    public static void register(Activity activity) {
        final Intent intent = new Intent(activity, JiveItemListActivity.class);
        intent.putExtra("register", true);
        activity.startActivity(intent);
    }

    /**
     * Start a new {@link JiveItemListActivity} to perform the supplied <code>action</code>.
     * <p>
     * If the action requires input, we initially get the input.
     * <p>
     * When input is ready or the action does not require input, items are ordered asynchronously
     * via {@link ISqueezeService#pluginItems(int, JiveItem, Action, IServiceItemListCallback)}
     *
     * @see #orderPage(ISqueezeService, int)
     */
    public static void show(Activity activity, JiveItem parent, Action action) {
        final Intent intent = getPluginListIntent(activity);
        intent.putExtra(JiveItem.class.getName(), parent);
        intent.putExtra(Action.class.getName(), action);
        activity.startActivityForResult(intent, GO);
    }

    public static void show(Activity activity, JiveItem item) {
        final Intent intent = getPluginListIntent(activity);
        intent.putExtra(JiveItem.class.getName(), item);
        activity.startActivityForResult(intent, GO);
    }

    @NonNull
    private static Intent getPluginListIntent(Activity activity) {
        Intent intent = new Intent(activity, JiveItemListActivity.class);
        if (activity instanceof JiveItemListActivity && ((JiveItemListActivity)activity).register) {
            intent.putExtra("register", true);
        }
        return intent;
    }

}
