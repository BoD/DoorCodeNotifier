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
package org.jraf.android.dcn.handheld.util.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;

import com.squareup.picasso.Transformation;

public class RoundTransformation implements Transformation {
    private static final RoundTransformation INSTANCE = new RoundTransformation();

    public static RoundTransformation get() {
        return INSTANCE;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        Bitmap res = getRound(source);
        source.recycle();
        return res;
    }

    @Override
    public String key() {
        return RoundTransformation.class.getName();
    }

    private static Bitmap getRound(Bitmap source) {
        if (source == null) return null;
        // We want the resulting bitmap to be squared: use only one dimension (the smallest one)
        int width = Math.min(source.getWidth(), source.getHeight());
        int height = width;
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(shader);
        int radius = width / 2;
        canvas.drawCircle(radius, height / 2, radius, paint);
        return output;
    }
}