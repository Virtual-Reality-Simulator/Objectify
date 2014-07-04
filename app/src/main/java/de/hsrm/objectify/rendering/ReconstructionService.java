package de.hsrm.objectify.rendering;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float3;
import android.renderscript.Float4;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.interfaces.decomposition.SingularValueDecomposition;
import org.ejml.ops.CommonOps;

import java.util.ArrayList;

import de.hsrm.objectify.camera.Constants;
import de.hsrm.objectify.rendering.compute_normals.ScriptC_compute_normals;
import de.hsrm.objectify.rendering.lh_integration.ScriptC_lh_integration;
import de.hsrm.objectify.utils.BitmapUtils;
import de.hsrm.objectify.utils.Storage;

public class ReconstructionService extends IntentService {

    public static final String IMAGE_PREFIX_NAME = "image_name";
    public static final String NOTIFICATION = "de.hsrm.objectify.android.service.receiver";
    public static final String NORMALMAP = "normalmap";
    private static final int LH_ITERATIONS = 3000;
    private int mWidth;
    private int mHeight;

    public ReconstructionService() {
        super("ReconstructionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String imagePrefix = intent.getStringExtra(IMAGE_PREFIX_NAME);

        /* read images */
        ArrayList<Bitmap> images = new ArrayList<Bitmap>();
        /* i from 0 to Constants.NUM_IMAGES + ambient image */
//        for (int i = 0; i <= Constants.NUM_IMAGES; i++) {
//            Bitmap img = BitmapUtils.openBitmap(Storage.getExternalRootDirectory() +
//                    "/" + imagePrefix + "_" + i + "." + Constants.IMAGE_FORMAT);
        for (int i = 0; i < Constants.NUM_IMAGES; i++) {
            Bitmap img = BitmapUtils.openBitmap(Storage.getExternalRootDirectory() +
                    "/kai_" + i + ".png");
            images.add(img);
        }

        mWidth = images.get(0).getWidth();
        mHeight = images.get(0).getHeight();
        /* subtract first ambient image from the remaining images */
//        Bitmap ambient = images.remove(0);
//        for (int i = 0; i < images.size(); i++) {
//            images.set(i, BitmapUtils.subtract(images.get(i), ambient));
//        }

        /* estimate threshold (otsu) */
        Bitmap Mask = BitmapUtils.convertToGrayscale(BitmapUtils.binarize(images.get(2)));

        ArrayList<Float4> normals = computeNormals(images, Mask);
        Bitmap Normals = BitmapUtils.convert(normals, mWidth, mHeight);
        BitmapUtils.saveBitmap(Normals, "normals.png");

//        float[] Z = localHeightfield(normals, mWidth, mHeight);
//
//        float min = Float.MAX_VALUE;
//        float max = Float.MIN_VALUE;
//        for (int row = 0; row < mHeight; row++) {
//            for (int col = 0; col < mWidth; col++) {
//                if (Z[index(col, row, mWidth)] < min) min = Z[index(col, row, mWidth)];
//                if (Z[index(col, row, mWidth)] > max) max = Z[index(col, row, mWidth)];
//            }
//        }
//
//        /* linear transformation of matrix valies from [min,max] -> [a,b] */
//        float a = 0.0f, b = 150.0f;
//        for (int i = 0; i < mHeight; i++) {
//            for (int j = 0; j < mWidth; j++) {
//                Z[index(j, i, mWidth)] = a + (b-a) * (Z[index(j, i, mWidth)] - min) / (max - min);
//            }
//        }
//
//        int[] heightPixels = new int[mWidth*mHeight];
//        int idx = 0;
//        for (int i = 0; i < mHeight; i++) {
//            for (int j = 0; j < mWidth; j++) {
//                int z = 255 - (int) Z[index(j, i, mWidth)];
//                heightPixels[idx++] = Color.rgb(z, z, z);
//            }
//        }
//
//        BitmapUtils.saveBitmap(Bitmap.createBitmap(heightPixels, mWidth, mHeight, Bitmap.Config.ARGB_8888), "heights.png");

        /* clean up and publish results */
        publishResult(Storage.getExternalRootDirectory() + "/normals.png");
    }

    private int index(int col, int row, int width) {
        return (row * width) + col;
    }

    private float[] localHeightfield(ArrayList<Float4> normals, int width, int height) {

//        float[] Z = new float[width*height];
//        for (int k = 0; k < LH_ITERATIONS; k++) {
//            for (int row = 1; row < mHeight-1; row++) {
//                for (int col = 1; col < mWidth-1; col++) {
//                    Float3 up = normals.get(index(col, row-1, mWidth));
//                    Float3 down = normals.get(index(col,  row+1, width));
//                    Float3 left = normals.get(index(col-1,  row, width));
//                    Float3 right = normals.get(index(col+1,  row, width));
//                    float zU = Z[index(col, row-1, width)];
//                    float zD = Z[index(col, row+1, width)];
//                    float zL = Z[index(col-1, row, width)];
//                    float zR = Z[index(col+1, row, width)];
//                    float nxC = normals.get(index(col,   row, width)).x;
//                    float nyC = normals.get(index(col, row, width)).y;
//                    float nxU = normals.get(index(col, row-1, width)).x;
//                    float nyL = normals.get(index(col-1, row, width)).y;
//                    if (!(up.x == 0.0f && up.y == 0.0f && up.z == 255.0f) &&
//                            !(down.x == 0.0f && down.y == 0.0f && down.z == 255.0f) &&
//                            !(left.x == 0.0f && left.y == 0.0f && left.z == 255.0f) &&
//                            !(right.x == 0.0f && right.y == 0.0f && right.z == 255.0f)) {
//                        Z[index(col, row, width)] = (float) (1.0f/4.0f * (zD + zU + zR + zL + nxU - nxC + nyL - nyC));
//                    }
//                }
//            }
//        }
//        return Z;

        /////////////////////////////////////////////////////////////
        /* create RenderScript context used to communicate with RenderScript. Afterwards create the
         * actual script, which will do the real work */
        RenderScript rs = RenderScript.create(getApplicationContext());
        ScriptC_lh_integration lhIntegration = new ScriptC_lh_integration(rs);

        /* set params for the generator */
        lhIntegration.set_width(width);
        lhIntegration.set_height(height);
        lhIntegration.set_iter(LH_ITERATIONS);

        /* create allocation input to RenderScript */
        Type myType = new Type.Builder(rs, Element.F32_4(rs)).setX(width).setY(height).create();
        Allocation allInNormals = Allocation.createTyped(rs, myType);

        /* Float3 types have the same amount of storage as Float4, although the last float is
         * always undefined. See: https://code.google.com/p/android/issues/detail?id=66182 */
        float[] floatnorms = new float[width*height*4];
        int idx = 0;
        for (int i = 0; i < floatnorms.length; i+=4) {
            floatnorms[i]   = normals.get(idx).x;
            floatnorms[i+1] = normals.get(idx).y;
            floatnorms[i+2] = normals.get(idx).z;
            floatnorms[i+3] = normals.get(idx).w;
            idx += 1;
        }
        allInNormals.copyFromUnchecked(floatnorms);

        Type myOtherType = new Type.Builder(rs, Element.F32(rs)).setX(width).setY(height).create();
        Allocation allOutHeights = Allocation.createTyped(rs, myOtherType);

        /* bind normals and heights data to pNormals and pHeights pointer inside RenderScript */
        lhIntegration.bind_pNormals(allInNormals);
        lhIntegration.bind_pHeights(allOutHeights);

        /* pass the input to RenderScript */
        for (int i = 0; i < LH_ITERATIONS; i++) {
            lhIntegration.forEach_integrate(allInNormals, allOutHeights);
        }

        /* save output from RenderScript */
        float[] heights = new float[width*height];
        allOutHeights.copyTo(heights);

        return heights;
    }

    private float[] toFloatArray(double[] arr) {
        float[] floatArray = new float[arr.length];
        for (int i = 0 ; i < arr.length; i++) {
            floatArray[i] = (float) arr[i];
        }
        return floatArray;
    }

    private ArrayList<Float4> computeNormals(ArrayList<Bitmap> images, Bitmap Mask) {

        ArrayList<Float4> normals = new ArrayList<Float4>();
        double[][] a = new double[mWidth*mHeight][Constants.NUM_IMAGES];

        /* populate A */
        for (int k = 0; k < Constants.NUM_IMAGES; k++) {
            int idx = 0;
            for (int i = 0; i < mHeight; i++) {
                for (int j = 0; j < mWidth; j++) {
                    int c = images.get(k).getPixel(j, i);
                    a[idx++][k] = Color.red(c) + Color.green(c) + Color.blue(c);
                }
            }
        }

        long start = System.currentTimeMillis();
        DenseMatrix64F A = new DenseMatrix64F(a);
        CommonOps.transpose(A);
        SingularValueDecomposition<DenseMatrix64F> svd =
                DecompositionFactory.svd(A.numRows, A.numCols, false, true, true);

        /* TODO: catch java.lang.OutOfMemoryError */
        if (!svd.decompose(A)) {
            throw new RuntimeException("Decomposition failed");
        }
        Log.i("ReconstructionService", " svd.decompose(A) took: " +
                (float) (System.currentTimeMillis() - start)/1000.0f + " seconds.");

        /* speeding up computation, SVD from A^TA instead of AA^T */
        DenseMatrix64F EV = svd.getV(null, false);
        Log.i("ReconstructionService", "EV: " + EV.getNumRows() + "," + EV.getNumCols());
        float[] data = toFloatArray(EV.data);
        Log.i("ReconstructionService", "data: " + data.length);

        start = System.currentTimeMillis();
        int[] mask = new int[mWidth*mHeight];
        Mask.getPixels(mask, 0, mWidth, 0, 0, mWidth, mHeight);

        /* create RenderScript context */
        RenderScript rs = RenderScript.create(getApplicationContext());
        ScriptC_compute_normals cmpNormals = new ScriptC_compute_normals(rs);

        /* set params for the generator */
        cmpNormals.set_width(mWidth);

        /* create allocation input to RenderScript */
        Type dataType = new Type.Builder(rs, Element.F32(rs)).setY(mWidth*mHeight).create();
        Allocation allInData = Allocation.createTyped(rs, dataType);
        allInData.copyFromUnchecked(data);

        /* create allocation for masked image */
        Type maskType = new Type.Builder(rs, Element.I32(rs)).setY(mWidth*mHeight).create();
        Allocation allMask = Allocation.createTyped(rs, maskType);
        allMask.copyFrom(mask);

        /* create allocation for output */
        Type normalsType = new Type.Builder(rs, Element.F32_4(rs)).setY(mWidth*mHeight).create();
        Allocation allOutNormals = Allocation.createTyped(rs, normalsType);

        /* bind pMask and pData pointer inside RenderScript */
        cmpNormals.bind_pData(allInData);
        cmpNormals.bind_pMask(allMask);

        cmpNormals.forEach_compute_normals(allInData, allOutNormals);

//        for (int idx = 0; idx < mWidth*mHeight; idx++) {
//            double rSxyz = 1.0f / Math.sqrt(Math.pow(EV.get(idx, 0), 2) +
//                                            Math.pow(EV.get(idx, 1), 2) +
//                                            Math.pow(EV.get(idx, 2), 2));
//            /* EV contains the eigenvectors of A^TA, which are as well the z,x,y components of
//             * the surface normals for each pixel */
//            float sz = (float) (128.0f + 127.0f * Math.signum(EV.get(idx, 0)) *
//                    Math.abs(EV.get(idx, 0)) * rSxyz);
//            float sx = (float) (128.0f + 127.0f * Math.signum(EV.get(idx, 1)) *
//                    Math.abs(EV.get(idx, 1)) * rSxyz);
//            float sy = (float) (128.0f + 127.0f * Math.signum(EV.get(idx, 2)) *
//                    Math.abs(EV.get(idx, 2)) * rSxyz);
//            float validFlag = mask[idx] == Color.WHITE ? 1.0f : 0.0f;
//            normals.add(new Float4(sx, sy, sz, validFlag));
//        }

        /* save output from RenderScript */
        float[] norms = new float[mWidth*mHeight*4];
        allOutNormals.copyTo(norms);
        Log.i("ReconstructionService", " rs took: " +
                (float) (System.currentTimeMillis() - start)/1000.0f + " seconds.");
        for (int i = 0; i < mWidth*mHeight; i++) {
            normals.add(new Float4(norms[4*i], norms[4*i+1], norms[4*i+2], norms[4*i+3]));
        }

        return normals;
    }

    private void publishResult(String normalmap) {
        Intent publish = new Intent(NOTIFICATION);
        publish.putExtra(NORMALMAP, normalmap);
        sendBroadcast(publish);
    }
}