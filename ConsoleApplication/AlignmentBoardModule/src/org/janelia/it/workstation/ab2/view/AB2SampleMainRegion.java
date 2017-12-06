package org.janelia.it.workstation.ab2.view;

import javax.media.opengl.GL4;
import javax.media.opengl.GLAutoDrawable;

import antlr.collections.impl.Vector;
import org.janelia.geometry3d.Matrix4;
import org.janelia.geometry3d.Vector4;
import org.janelia.it.workstation.ab2.gl.GLRegion;
import org.janelia.it.workstation.ab2.renderer.AB2SampleRenderer;

public class AB2SampleMainRegion extends GLRegion {

    private AB2SampleRenderer sampleRenderer=new AB2SampleRenderer();

    public AB2SampleMainRegion() {
        renderers.add(sampleRenderer);
    }

    public AB2SampleRenderer getSampleRenderer() {
        return sampleRenderer;
    }

    int w0;
    int h0;
    int x0;
    int y0;
    int w1;
    int h1;
    int w2;
    int h2;
    float scale;
    int xCenter;
    int yCenter;
    float xCenterFraction;
    float yCenterFraction;
    float xTranslate;
    float yTranslate;

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height, int screenWidth, int screenHeight) {
        final GL4 gl=drawable.getGL().getGL4();
        // todo: sampleRenderer.reshape(gl, x, y, width, height, screenWidth, screenHeight);
        computeOffsetParameters(x, y, width, height, screenWidth, screenHeight);
        sampleRenderer.setVoxel3DActorPostRotationalMatrix(getPrMatrix(x, y, width, height, screenWidth, screenHeight));
        sampleRenderer.setVoxel3DxyBounds(getXYBounds(x, y, width, height, screenWidth, screenHeight));
    }

    private void computeOffsetParameters(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        // OpenGL will center and clip the smaller of the screen dimensions, so we need to find the size of the
        // virtual square we are working with.

        // Initially, assume screenWidth>screenHeight
        w0=screenWidth;
        h0=screenWidth;

        // We need to create virtual pixel position on the virtual square pixel field
        x0=x;
        int yDownFromMiddle=screenHeight/2-y;
        y0=screenWidth/2-yDownFromMiddle;

        // Deal with screenHeight>screenWidth
        if (screenHeight>screenWidth) {
            w0=screenHeight;
            h0=screenHeight;
            y0=y;
            int xDownFromMiddle=screenWidth/2-x;
            x0=screenHeight/2-xDownFromMiddle;
        }

        // The translation, to line up correctly, needs first to take into account scale, so we do scale first.

        // Because we want the dimensions for the main region to be square, we will take the smaller of the two
        w1=height;
        h1=height;
        w2=width;
        h2=width;
        if (height<width) {
            w1=width;
            h1=width;
            w2=height;
            h2=height;
        }

        // For scale, we want the fraction of total, using the result which is smallest
        scale=(float)((1.0*w2)/(1.0*w0));

        // Now base translation on virtual square coordinates
        xCenter=x0+w1/2;
        yCenter=y0+w1/2;

        xCenterFraction=(float)((1.0*xCenter)/(1.0*w0));
        yCenterFraction=(float)((1.0*yCenter)/(1.0*w0));

        xTranslate=2.0f*xCenterFraction-1.0f;
        yTranslate=2.0f*yCenterFraction-1.0f;
    }


    public Matrix4 getPrMatrix(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        Matrix4 translationMatrix = new Matrix4();
        translationMatrix.set(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                xTranslate, yTranslate, 0.0f, 1.0f);
        Matrix4 scaleMatrix = new Matrix4();
        scaleMatrix.set(
                scale, 0.0f, 0.0f, 0.0f,
                0.0f, scale, 0.0f, 0.0f,
                0.0f, 0.0f, scale, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f);
        Matrix4 modelMatrix=translationMatrix.multiply(scaleMatrix);

        return modelMatrix;
    }

    public Vector4 getXYBounds(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        int xBegin=x0;
        int xEnd=xBegin+w1;
        int yBegin=y0;
        int yEnd=yBegin+w1;
        float xB=(float)( ( (1.0*xBegin)/(1.0*w0) )*2.0-1.0 );
        float xE=(float)( ( (1.0*xEnd)/(1.0*w0) )*2.0-1.0);
        float yB=(float)( ( (1.0*yBegin)/(1.0*w0) )*2.0-1.0 );
        float yE=(float)( ( (1.0*yEnd)/(1.0*w0) )*2.0-1.0);
        return new Vector4(xB, xE, yB, yE);
    }

}
