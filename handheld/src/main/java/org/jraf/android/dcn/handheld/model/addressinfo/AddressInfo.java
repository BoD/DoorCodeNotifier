package org.jraf.android.dcn.handheld.model.addressinfo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.jraf.android.dcn.handheld.model.contactinfo.ContactInfo;
import org.jraf.android.util.annotation.Background;
import org.jraf.android.util.log.wrapper.Log;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressInfo {
    public static final String SEPARATOR = "\n--\n";
    private static final Pattern PATTERN_CODE = Pattern.compile("Code( \\d+)?: (.+)");
    private static final Pattern PATTERN_COORDINATES = Pattern.compile("Coordinates: (-?\\d+), (-?\\d+)");

    public ContactInfo contactInfo = new ContactInfo();
    public Uri uri;

    /**
     * The original formatted address (without augmented data).
     */
    public String formattedAddress;
    public double latitude;
    public double longitude;
    public List<String> codeList = new ArrayList<>();

    /**
     * Any other info (optional) present in the augmented data.
     */
    @Nullable
    public String otherInfo;

    public static AddressInfo parseAugmented(String sourceFormattedAddress) throws ParseException {
        AddressInfo res = new AddressInfo();
        String[] sourceElements = sourceFormattedAddress.split(SEPARATOR);

        if (sourceElements.length < 2) {
            throw new ParseException("Separator not found", 0);
        }

        // Formatted address
        res.formattedAddress = sourceElements[0];

        String augmentedData = sourceElements[1];
        String[] augmentedDataElements = augmentedData.split("\n");

        boolean foundCoordinates = false;
        for (String elem : augmentedDataElements) {
            Matcher codeMatcher = PATTERN_CODE.matcher(elem);
            // Code
            if (codeMatcher.matches()) {
                String code = codeMatcher.group(2);
                res.codeList.add(code);
                continue;
            }

            // Coordinates
            Matcher coordinatesMatcher = PATTERN_COORDINATES.matcher(elem);
            if (coordinatesMatcher.matches()) {
                foundCoordinates = true;

                String latE6Str = coordinatesMatcher.group(1);
                int latE6 = Integer.valueOf(latE6Str);
                res.latitude = latE6 / 1e6d;

                String lonE6Str = coordinatesMatcher.group(2);
                int lonE6 = Integer.valueOf(lonE6Str);
                res.longitude = lonE6 / 1e6d;
            }
        }

        // Other info (optional)
        if (sourceElements.length > 2) {
            res.otherInfo = sourceElements[2];
            if (res.otherInfo.trim().isEmpty()) res.otherInfo = null;
        }

        if (!foundCoordinates) {
            throw new ParseException("No coordinates found in the augmented info", 0);
        }

        return res;
    }

    public static boolean isAugmented(String formattedAddress) {
        return formattedAddress.contains(SEPARATOR);
    }

    @Background(Background.Type.DISK)
    public void persist(Context context) {
        Log.d();
        // Formatted address
        StringBuilder resultFormattedAddress = new StringBuilder(formattedAddress);
        resultFormattedAddress.append(SEPARATOR);

        // Code(s)
        boolean numbering = codeList.size() > 1;
        int i = 1;
        for (String code : codeList) {
            resultFormattedAddress.append("Code");
            if (numbering) {
                resultFormattedAddress.append(" ");
                resultFormattedAddress.append(String.valueOf(i));
            }
            resultFormattedAddress.append(": ");
            resultFormattedAddress.append(code);
            resultFormattedAddress.append("\n");
            i++;
        }

        // Coordinates
        resultFormattedAddress.append("Coordinates: ");
        resultFormattedAddress.append(String.valueOf((int) (latitude * 1e6d)));
        resultFormattedAddress.append(", ");
        resultFormattedAddress.append(String.valueOf((int) (longitude * 1e6d)));

        // Other info (optional)
        if (!TextUtils.isEmpty(otherInfo)) {
            resultFormattedAddress.append(SEPARATOR);
            resultFormattedAddress.append(otherInfo);
        }

        Log.d("resultFormattedAddress=" + resultFormattedAddress);

        // Persist
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, resultFormattedAddress.toString());

        int res = context.getContentResolver().update(uri, contentValues, null, null);
        Log.d("res=" + res);
    }

    public void delete(Context context) {
        Log.d();
        // Persist
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, formattedAddress);

        int res = context.getContentResolver().update(uri, contentValues, null, null);
        Log.d("res=" + res);
    }

    @Nullable
    @Background(Background.Type.DISK)
    public Bitmap getContactPhoto(Context context) {
        Uri photoUri = Uri.withAppendedPath(contactInfo.uri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        String[] projection = {ContactsContract.Contacts.Photo.PHOTO};
        Cursor cursor = context.getContentResolver().query(photoUri, projection, null, null, null);
        if (cursor == null) return null;
        try {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    @Nullable
    @Background(Background.Type.DISK)
    public String getContactPhoneNumber(Context context) {
        Uri dataUri = Uri.withAppendedPath(contactInfo.uri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY);
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        String sort = ContactsContract.Contacts.Data.IS_SUPER_PRIMARY + " DESC," + ContactsContract.Contacts.Data.IS_PRIMARY + " DESC";
        String selection = ContactsContract.Contacts.Data.MIMETYPE + "=?";
        String[] selectionArgs = {ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE};
        Cursor cursor = context.getContentResolver().query(dataUri, projection, selection, selectionArgs, sort);
        if (cursor == null) return null;
        try {
            if (cursor.moveToFirst()) return cursor.getString(0);
        } finally {
            cursor.close();
        }
        return null;
    }
}
