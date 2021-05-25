/*
 * Copyright (c) 2019 Kurt Aaholst.  All Rights Reserved.
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

package uk.org.ngo.squeezer.itemlist.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.DialogFragment;

import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.Image;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.widget.OnSwipeListener;

public class SlideShow extends DialogFragment implements IServiceItemListCallback<JiveItem> {
    private static final String TAG = SlideShow.class.getSimpleName();
    private static final int DELAY = 10_000;
    private ImageView artwork;
    private Image[] images;
    private int currentImage;

    private final Handler handler = new Handler();
    private final Runnable nextSlideTask = new Runnable() {
        @Override
        public void run() {
            nextSlide();
            handler.postDelayed(this, DELAY);
        }
    };

    private GestureDetectorCompat detector;

    private void startSlideShow(int position) {
        currentImage = position > 0 ? --position : images.length - 1;
        nextSlide();
        handler.postDelayed(nextSlideTask, DELAY);
    }

    private void nextSlide() {
        currentImage = ++currentImage % images.length;
        ImageFetcher.getInstance(getContext()).loadImage(images[currentImage].artworkId, artwork);
    }

    private void prevSlide() {
        currentImage = currentImage > 0 ? --currentImage : images.length - 1;
        ImageFetcher.getInstance(getContext()).loadImage(images[currentImage].artworkId, artwork);
    }

    private void resetTimeout() {
        handler.removeCallbacks(nextSlideTask);
        handler.postDelayed(nextSlideTask, DELAY);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BaseActivity activity = (BaseActivity)getActivity();
        Action action = getArguments().getParcelable(Action.class.getName());
        images = (Image[]) getArguments().getParcelableArray(Image.class.getName());

        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.show_artwork);
        artwork = dialog.findViewById(R.id.artwork);

        detector = new GestureDetectorCompat(activity, new OnSwipeListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                forward();
                return true;
            }

            @Override
            public boolean onSwipeDown() {
                dismiss();
                return true;
            }

            @Override
            public boolean onSwipeRight() {
                backward();
                return true;
            }

            @Override
            public boolean onSwipeLeft() {
                forward();
                return true;
            }

            private void forward() {
                if (images != null && images.length > 0) {
                    nextSlide();
                    resetTimeout();
                }
            }

            private void backward() {
                if (images != null && images.length > 0) {
                    prevSlide();
                    resetTimeout();
                }
            }
        });
        artwork.setOnTouchListener((view, event) -> detector.onTouchEvent(event));


        Rect rect = new Rect();
        Window window = dialog.getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rect);
        int size = Math.min(rect.width(), rect.height());
        window.setLayout(size, size);

        if (images != null) {
            startSlideShow(getArguments().getInt("position"));
        } else {
            // FIXME Image wont get fetched (and thus not displayed) after orientation change
            if (activity.getService() != null) {
                activity.getService().pluginItems(action, this);
            }
        }

        return dialog;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        handler.removeCallbacks(nextSlideTask);
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        Object[] item_data = (Object[]) parameters.get("loop_loop");
        if (item_data != null && item_data.length > 0) {
            images = new Image[item_data.length];
            for (int i = 0; i < item_data.length; i++) {
                Object item_d = item_data[i];
                Map<String, Object> record = (Map<String, Object>) item_d;
                record.put("urlPrefix", parameters.get("urlPrefix"));
                images[i] = new Image(record);
            }
            startSlideShow(0);
        }
    }

    @Override
    public Object getClient() {
        return getActivity();
    }

    /**
     * Create a dialog to show artwork.
     * <p>
     * We call {@link ISqueezeService#pluginItems(Action, IServiceItemListCallback)} with the
     * supplied <code>action</code> to asynchronously order an artwork id or URL. When the response
     * arrives we load the artwork into the dialog.
     * <p>
     * See Slim/Control/Queries.pm in the slimserver code
     */
    public static SlideShow show(BaseActivity activity, Action action) {
        // Create and show the dialog
        SlideShow dialog = new SlideShow();

        Bundle args = new Bundle();
        args.putParcelable(Action.class.getName(), action);
        dialog.setArguments(args);

        dialog.show(activity.getSupportFragmentManager(), TAG);
        return dialog;
    }

    /**
     * Create a dialog to show the supplied images in a dialog.
     */
    public static SlideShow show(BaseActivity activity, int position, Image[] images) {
        // Create and show the dialog
        SlideShow dialog = new SlideShow();

        Bundle args = new Bundle();
        args.putInt("position", position);
        args.putParcelableArray(Image.class.getName(), images);
        dialog.setArguments(args);

        dialog.show(activity.getSupportFragmentManager(), TAG);
        return dialog;
    }
}
