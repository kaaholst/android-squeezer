package uk.org.ngo.squeezer.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Map;

import uk.org.ngo.squeezer.Util;

public class Image implements Parcelable {
    public final Uri artworkId;
    public final String caption;

    public Image(Map<String, Object> record) {
        artworkId = Util.getImageUrl(record, "image");
        caption = Util.getString(record, "caption");
    }

    protected Image(Parcel in) {
        artworkId = Uri.parse(in.readString());
        caption = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(artworkId.toString());
        dest.writeString(caption);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Image> CREATOR = new Creator<Image>() {
        @Override
        public Image createFromParcel(Parcel in) {
            return new Image(in);
        }

        @Override
        public Image[] newArray(int size) {
            return new Image[size];
        }
    };
}
