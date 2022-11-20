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

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.homescreenwidgets.TextDrawable;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Slider;

public class SliderView extends ItemViewHolder<JiveItem> {

    SliderView(@NonNull JiveItemListActivity activity, @NonNull View view) {
        super(activity, view);
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);
        com.google.android.material.slider.Slider seekBar = itemView.findViewById(R.id.slider);
        final Slider slider = item.slider;
        seekBar.setValue(slider.initial);
        seekBar.setValueFrom(slider.min);
        seekBar.setValueTo(slider.max);

        ImageView downIcon = itemView.findViewById(R.id.slider_down_icon);
        ImageView upIcon = itemView.findViewById(R.id.slider_up_icon);
        downIcon.setVisibility("none".equals(slider.sliderIcons) ? View.INVISIBLE : View.VISIBLE);
        upIcon.setVisibility("none".equals(slider.sliderIcons) ? View.INVISIBLE : View.VISIBLE);
        if ("volume".equals(slider.sliderIcons)) {
            downIcon.setImageResource(R.drawable.ic_volume_down);
            upIcon.setImageResource(R.drawable.ic_volume_up);
        } else {
            Resources resources = itemView.getResources();
            downIcon.setImageDrawable(new TextDrawable(resources, "-", resources.getColor(R.color.white)));
            upIcon.setImageDrawable(new TextDrawable(resources, "+", resources.getColor(R.color.white)));
        }

        seekBar.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {

            @Override
            @SuppressLint("RestrictedApi")
            public void onStartTrackingTouch(@NonNull com.google.android.material.slider.Slider seekBar) {
            }

            @Override
            @SuppressLint("RestrictedApi")
            public void onStopTrackingTouch(@NonNull com.google.android.material.slider.Slider seekBar) {
                if (item.goAction != null) {
                    item.inputValue = String.valueOf((int)seekBar.getValue());
                    getActivity().action(item, item.goAction);
                }
            }
        });
    }

}
