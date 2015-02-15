/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2015 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jraf.android.dcn.handheld.model.contactinfo;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class ContactInfo implements Parcelable {
    public Uri uri;
    public Uri contentLookupUri;
    public String displayName;

    public ContactInfo() {}


    //region Parcelable implementation.

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, 0); dest.writeParcelable(contentLookupUri, 0); dest.writeString(displayName);
    }

    private ContactInfo(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader()); contentLookupUri = in.readParcelable(Uri.class.getClassLoader()); displayName = in.readString();
    }

    public static final Creator<ContactInfo> CREATOR = new Creator<ContactInfo>() {
        public ContactInfo createFromParcel(Parcel source) {
            return new ContactInfo(source);
        }

        public ContactInfo[] newArray(int size) {
            return new ContactInfo[size];
        }
    };

    //endregion
}
