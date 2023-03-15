package uk.org.ngo.squeezer.framework;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Map;
import java.util.Stack;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.itemlist.JiveItemListActivity;
import uk.org.ngo.squeezer.itemlist.JiveItemViewLogic;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;

public class ContextMenu extends BottomSheetDialogFragmentWithService implements IServiceItemListCallback<JiveItem>, ItemAdapter.PageOrderer {
    public static final String TAG = ContextMenu.class.getSimpleName();

    private Stack<Pair<JiveItem, Action>> contextStack;
    private ContextMenuAdapter adapter;
    private View divider;
    private ProgressBar progress;
    private ImageView icon;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.context_menu, container, false);

        Bundle arguments = requireArguments();
        contextStack = new Stack<>();
        contextStack.push(new Pair<>(arguments.getParcelable(JiveItem.class.getName()), arguments.getParcelable(Action.class.getName())));

        divider = view.findViewById(R.id.divider);
        progress = view.findViewById(R.id.progress);
        icon = view.findViewById(R.id.icon);
        updateHeader(view);

        RecyclerView items = view.findViewById(R.id.items);
        adapter = new ContextMenuAdapter(activity());
        items.setAdapter(adapter);

        return view;
    }

    public void show(JiveItem item, Action action) {
        contextStack.push(new Pair<>(item ,action));
        show();
    }

    private void show() {
        updateHeader(requireView());
        adapter.clear();
        maybeOrderPage(0);
    }

    private void updateHeader(View view) {
        TextView text1 = view.findViewById(R.id.text1);
        TextView text2 = view.findViewById(R.id.text2);

        JiveItem item = contextStack.peek().first;
        text1.setText(item.getName());
        text2.setText(item.text2);
        if (contextStack.size() > 1) {
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(R.drawable.ic_keyboard_arrow_left);
            view.setOnClickListener(v -> {
                contextStack.pop();
                show();
            });
        } else {
            icon.setVisibility(item.hasIcon() ? View.VISIBLE : View.GONE);
            JiveItemViewLogic.icon(icon, item, this::updateHeaderIcon);
            view.setClickable(false);
        }
    }

    public BaseActivity activity() {
        return (BaseActivity) super.requireActivity();
    }


    private void updateHeaderIcon() {
        JiveItemViewLogic.addLogo(icon, contextStack.peek().first);
    }

    public static void show(BaseActivity activity, JiveItem item) {
        show(activity, item, item.moreAction);
    }

    public static void show(BaseActivity activity, JiveItem item, Action action) {
        ContextMenu contextMenu = new ContextMenu();

        Bundle args = new Bundle();
        args.putParcelable(JiveItem.class.getName(), item);
        args.putParcelable(Action.class.getName(), action);
        contextMenu.setArguments(args);

        contextMenu.show(activity.getSupportFragmentManager(), TAG);
    }

    private void doItemContext(JiveItem item) {
        Action.NextWindow nextWindow = (item.goAction != null ? item.goAction.action.nextWindow : item.nextWindow);
        JiveItem contextItem = contextStack.peek().first;
        if (JiveItem.PLAY_NOW.equals(item)) {
            activity().action(contextItem, contextItem.playAction);
        } else if (JiveItem.ADD_TO_END.equals(item)) {
            activity().action(contextItem, contextItem.addAction);
        } else if (JiveItem.PLAY_NEXT.equals(item)) {
            activity().action(contextItem, contextItem.insertAction);
        } else if (JiveItem.MORE.equals(item)) {
            JiveItemListActivity.show(activity(), contextItem, contextItem.moreAction);
        } else if (JiveItem.DOWNLOAD.equals(item)) {
            activity().downloadItem(contextItem);
        } else if (JiveItem.RANDOM_PLAY.equals(item)) {
            activity().randomPlayFolder(contextItem);
        } else if (nextWindow != null) {
            activity().action(item, item.goAction, contextStack.size());
        } else {
            JiveItemViewLogic.execGoAction(activity(), this, item, contextStack.size());
            return;
        }
        dismiss();
    }

    private boolean canRandomPlay(JiveItem contextMenuItem) {
        // Do not set Random Play in the context menu of the headline item of the folder if
        // Nullobject. It works fine on first level.
        // TODO: Maybe make this ignore work for all
        //  folders. See JiveItem.randomPlayFolderCommand()
        return (contextMenuItem != null) &&
                (contextMenuItem.moreAction != null) &&
                contextMenuItem.moreAction.action.cmd.contains("folderinfo") &&
                (contextMenuItem.randomPlayFolderCommand() != null);
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        Preferences preferences = Squeezer.getPreferences();
        JiveItem item = contextStack.peek().first;
        activity().runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            divider.setVisibility(View.VISIBLE);
            adapter.update(count, start, items);
            if (canRandomPlay(item)) {
                adapter.insertItem(0, JiveItem.RANDOM_PLAY);
            }
            if (preferences.isDownloadEnabled() && item != null && item.canDownload()) {
                adapter.insertItem(0, JiveItem.DOWNLOAD);
            }
        });
    }

    @Override
    public Object getClient() {
        return this;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(@SuppressWarnings("unused") HandshakeComplete event) {
        maybeOrderPage(0);
    }

    @Override
    public void maybeOrderPage(int pagePosition) {
        Pair<JiveItem, Action> pair = contextStack.peek();
        if (pair.second != null) {
            divider.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            service.pluginItems(pagePosition, pair.first, pair.second, this);
        } else {
            JiveItem item = pair.first;
            activity().runOnUiThread(() -> {
                if (item.moreAction != null) {
                    adapter.insertItem(0, JiveItem.MORE);
                }
                if (item.insertAction != null) {
                    adapter.insertItem(0, JiveItem.PLAY_NEXT);
                }
                if (item.addAction != null) {
                    adapter.insertItem(0, JiveItem.ADD_TO_END);
                }
                if (item.playAction != null) {
                    adapter.insertItem(0, JiveItem.PLAY_NOW);
                }
            });
        }
    }

    private class ContextMenuAdapter extends ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> {
        public ContextMenuAdapter(BaseActivity activity) {
            super(activity, ContextMenu.this, false);
        }

        @Override
        public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
            return new ContextItemViewHolder(getActivity(), view);
        }

        @Override
        protected int getItemViewType(JiveItem item) {
            return R.layout.context_menu_item;
        }
    }

    private class ContextItemViewHolder extends ItemViewHolder<JiveItem> {
        TextView text;

        public ContextItemViewHolder(@NonNull BaseActivity activity, @NonNull View itemView) {
            super(activity, itemView);
            text = itemView.findViewById(R.id.text);
        }

        private float getAlpha(JiveItem item) {
            return item.isSelectable() ? 1.0f : (item.checkbox != null || item.radio != null) ? 0.25f : 0.75f;
        }

        @Override
        public void bindView(JiveItem item) {
            super.bindView(item);
            text.setAlpha(getAlpha(item));
            text.setText(item.getName());
            itemView.setEnabled(item.isSelectable());
            itemView.setOnClickListener(view -> doItemContext(item));
        }
    }
}
