package org.jraf.android.digibod.handheld.model.addressinfo;

import android.net.Uri;
import android.support.annotation.Nullable;

import org.jraf.android.digibod.handheld.model.contactinfo.ContactInfo;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressInfo {
    public static final String SEPARATOR = "\n--\n";
    private static final Pattern PATTERN_CODE = Pattern.compile("Code( \\d+)?: (.+)");
    private static final Pattern PATTERN_COORDINATES = Pattern.compile("Coordinates: (\\d+), (\\d+)");

    public ContactInfo contactInfo;
    public Uri uri;

    /**
     * The original formatted address (without augmented data).
     */
    public String formattedAddress;
    public double latitude;
    public double longitude;
    public float radius;
    public List<String> codeList = new ArrayList<>();

    /**
     * Any other info (optional) present in the augmented data.
     */
    @Nullable
    public String otherInfo;

    public static AddressInfo parse(String sourceFormattedAddress) throws ParseException {
        AddressInfo res = new AddressInfo();
        res.contactInfo = new ContactInfo();
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
        }

        if (!foundCoordinates) {
            throw new ParseException("No coordinates found in the augmented info", 0);
        }

        return res;
    }

    public static boolean isAugmented(String formattedAddress) {
        return formattedAddress.contains(SEPARATOR);
    }
}
