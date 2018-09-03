package com.xlythe.service.weather;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ParcelableUtils {
    @NonNull
    public static String toString(@NonNull Parcelable parcelable) {
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);
        try {
            return Base64.encodeToString(parcel.marshall(), Base64.DEFAULT);
        } finally {
            parcel.recycle();
        }
    }

    @Nullable
    public static <T extends Parcelable> T fromString(@Nullable String data, @NonNull Parcelable.Creator<T> creator) {
        if (data == null) {
            return null;
        }
        byte[] bytes = Base64.decode(data, Base64.DEFAULT);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        try {
            return creator.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    public static void fromString(@Nullable String data, @NonNull RestorableParcelable parcelable) {
        if (data == null) {
            return;
        }
        byte[] bytes = Base64.decode(data, Base64.DEFAULT);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        try {
            parcelable.readFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    public interface RestorableParcelable extends Parcelable {
        void readFromParcel(Parcel in);
    }
}
