package com.xlythe.service.weather;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

public class ParcelableUtils {
    public static String toString(Parcelable parcelable) {
        Parcel parcel = Parcel.obtain();
        parcel.writeValue(parcelable);
        try {
            return new String(parcel.marshall());
        } finally {
            parcel.recycle();
        }
    }

    @Nullable
    public static <T extends Parcelable> T fromString(String data, Parcelable.Creator<T> creator) {
        if (data == null) {
            return null;
        }
        byte[] bytes = data.getBytes();
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        try {
            return creator.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
