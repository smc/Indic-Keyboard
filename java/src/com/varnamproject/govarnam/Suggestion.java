package com.varnamproject.govarnam;

import android.os.Parcel;
import android.os.Parcelable;

public final class Suggestion implements Parcelable {
    public String Word;
    public int Weight;

    protected Suggestion(Parcel in) {
        Word = in.readString();
        Weight = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(Word);
        dest.writeInt(Weight);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Suggestion> CREATOR = new Creator<Suggestion>() {
        @Override
        public Suggestion createFromParcel(Parcel in) {
            return new Suggestion(in);
        }

        @Override
        public Suggestion[] newArray(int size) {
            return new Suggestion[size];
        }
    };
}

