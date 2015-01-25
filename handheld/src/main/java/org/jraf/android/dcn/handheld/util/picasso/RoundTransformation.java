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