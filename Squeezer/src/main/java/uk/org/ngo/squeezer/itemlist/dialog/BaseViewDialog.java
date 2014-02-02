package uk.org.ngo.squeezer.itemlist.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.Item;
import uk.org.ngo.squeezer.menu.ViewMenuItemFragment;
import uk.org.ngo.squeezer.util.Reflection;

public abstract class BaseViewDialog<
        T extends Item,
        ListLayout extends Enum<ListLayout> & BaseViewDialog.EnumWithTextAndIcon,
        SortOrder extends Enum<SortOrder> & BaseViewDialog.EnumWithText> extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressWarnings("unchecked") final Class<ListLayout> listLayoutClass = (Class<ListLayout>) Reflection.getGenericClass(getClass(), BaseViewDialog.class, 1);
        @SuppressWarnings("unchecked") final Class<SortOrder> sortOrderClass = (Class<SortOrder>) Reflection.getGenericClass(getClass(), BaseViewDialog.class, 2);
        @SuppressWarnings("unchecked") final ViewMenuItemFragment.ListActivityWithViewMenu<T, ListLayout, SortOrder> activity = (ViewMenuItemFragment.ListActivityWithViewMenu<T, ListLayout, SortOrder>) getActivity();
        final int positionSortLabel = listLayoutClass.getEnumConstants().length;
        final int positionSortStart = positionSortLabel + 1;


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getTitle());
        builder.setAdapter(new BaseAdapter() {
                               @Override
                               public boolean areAllItemsEnabled() {
                                   return false;
                               }

                               @Override
                               public boolean isEnabled(int position) {
                                   return (position != positionSortLabel);
                               }

                               @Override
                               public int getCount() {
                                   return listLayoutClass.getEnumConstants().length + 1 + sortOrderClass
                                           .getEnumConstants().length;
                               }

                               @Override
                               public Object getItem(int i) {
                                   return null;
                               }

                               @Override
                               public long getItemId(int i) {
                                   return i;
                               }


                               @Override
                               public View getView(int position, View convertView,
                                                   ViewGroup parent) {
                                   if (position < positionSortLabel) {
                                       CheckedTextView textView = (CheckedTextView) getActivity()
                                               .getLayoutInflater()
                                               .inflate(android.R.layout.select_dialog_singlechoice,
                                                       parent, false);
                                       ListLayout listLayout = listLayoutClass.getEnumConstants()[position];
                                       textView.setCompoundDrawablesWithIntrinsicBounds(
                                               listLayout.getIcon(), 0, 0, 0);
                                       textView.setText(listLayout.getText(getActivity()));
                                       textView.setChecked(listLayout == activity.getListLayout());
                                       return textView;
                                   } else if (position > positionSortLabel) {
                                       CheckedTextView textView = (CheckedTextView) getActivity()
                                               .getLayoutInflater()
                                               .inflate(android.R.layout.select_dialog_singlechoice,
                                                       parent, false);
                                       position -= positionSortStart;
                                       SortOrder sortOrder = sortOrderClass.getEnumConstants()[position];
                                       textView.setText(sortOrder.getText(getActivity()));
                                       textView.setChecked(sortOrder == activity.getSortOrder());
                                       return textView;
                                   }

                                   TextView textView = new TextView(getActivity(), null,
                                           android.R.attr.listSeparatorTextViewStyle);
                                   textView.setText(getString(R.string.choose_sort_order,
                                           activity.getItemAdapter().getQuantityString(2)));
                                   return textView;
                               }
                           }, new DialogInterface.OnClickListener() {
                               public void onClick(DialogInterface dialog, int position) {
                                   if (position < positionSortLabel) {
                                       activity.setListLayout(listLayoutClass.getEnumConstants()[position]);
                                       dialog.dismiss();
                                   } else if (position > positionSortLabel) {
                                       position -= positionSortStart;
                                       activity.setSortOrder(sortOrderClass.getEnumConstants()[position]);
                                       dialog.dismiss();
                                   }
                               }
                           }
        );
        return builder.create();
    }

    protected abstract String getTitle();

    public interface EnumWithText {
        public String getText(Context context);
    }

    public interface EnumWithTextAndIcon extends EnumWithText {
        public int getIcon();
    }

}
