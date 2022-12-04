package org.janelia.horta.volume;

import Jama.Matrix;
import org.aind.omezarr.OmeZarrDataset;
import org.aind.omezarr.image.OmeZarrImageStack;
import org.janelia.geometry3d.Box3;
import org.janelia.geometry3d.ConstVector3;
import org.janelia.geometry3d.Vector3;
import org.janelia.gltools.texture.Texture3d;
import org.janelia.horta.BrainTileInfo;

import java.awt.image.Raster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * In principle intended to implement BrickInfo.  There are substantial enough differences from BrainTileInfo that it
 * probably does not make sense to subclass.  Or at least refactor what bit is shared to a parent.  However, there
 * appear to be a lot of places that require BrainTileInfo rather than BrickInfo so this is the shortest path to get
 * Ome Zarr support for now.
 */
public class BrainChunkInfo extends BrainTileInfo {
    private final OmeZarrDataset dataset;

    private final int[] shapeMicrometers;

    private final int[] originMicrometers;

    private final int[] pixelDims;

    private final int bytesPerIntensity;

    private Matrix stageCoordToTexCoord;

    private int colorChannelIndex = 0;

    public BrainChunkInfo(OmeZarrDataset dataset) throws IOException {
        super();

        this.dataset = dataset;

        int[] shape = dataset.getShape();

        // TODO any translate from multiscale
        originMicrometers = new int[3];
        shapeMicrometers = new int[3];
        pixelDims = new int[4];

        // TODO assumes 1um per voxel.
        shapeMicrometers[0] = pixelDims[0] = shape[4];
        shapeMicrometers[1] = pixelDims[1] = shape[3];
        shapeMicrometers[2] = pixelDims[2] = shape[2];
        pixelDims[3] = shape[1];

        // TODO assumes 2 bytes per intensity.
        this.bytesPerIntensity = 2;
    }

    @Override
    public List<? extends ConstVector3> getCornerLocations() {
        List<ConstVector3> result = new ArrayList<>();
        for (int pz : new int[]{0, pixelDims[2]}) {
            for (int py : new int[]{0, pixelDims[1]}) {
                for (int px : new int[]{0, pixelDims[0]}) {
                    Matrix um = new Matrix(new double[]{px, py, pz, 0, 1}, 5);
                    ConstVector3 v = new Vector3(
                            (float) um.get(0, 0),
                            (float) um.get(1, 0),
                            (float) um.get(2, 0));
                    result.add(v);
                }
            }
        }
        return result;
    }

    @Override
    public VoxelIndex getRasterDimensions() {
        return new VoxelIndex(pixelDims[0], pixelDims[1], pixelDims[2]);
    }

    @Override
    public int getChannelCount() {
        return pixelDims[3];
    }

    @Override
    public int getBytesPerIntensity() {
        return bytesPerIntensity;
    }

    @Override
    public double getResolutionMicrometers() {
        float resolution = Float.MAX_VALUE;

        for (int xyz = 0; xyz < 3; ++xyz) {
            float res = shapeMicrometers[xyz] / (float) pixelDims[xyz];
            if (res < resolution)
                resolution = res;
        }
        return resolution;
    }

    @Override
    public Box3 getBoundingBox() {
        Box3 result = new Box3();

        Vector3 bbOrigin = new Vector3(originMicrometers[0], originMicrometers[1], originMicrometers[2]);
        Vector3 bbSize = new Vector3(shapeMicrometers[0], shapeMicrometers[1], shapeMicrometers[2]);

        result.include(bbOrigin);
        result.include(bbOrigin.add(bbSize));

        return result;
    }

    @Override
    public Texture3d loadBrick(double maxEdgePadWidth, String fileExtension) {
        Texture3d texture = new Texture3d();

        try {
            OmeZarrImageStack stack = new OmeZarrImageStack(dataset);

            Raster[] slices = stack.asSlices(0, colorChannelIndex, true);

            texture.loadRasterSlices(slices, stack.getColorModel());

            return texture;
        } catch (Exception e) {
        }

        return null;
    }

    @Override
    public boolean isSameBrick(BrickInfo other) {
        if (!(other instanceof BrainChunkInfo)) {
            return false;
        }

        return false;
    }

    @Override
    public Matrix getStageCoordToTexCoord() {
        // Compute matrix just-in-time
        if (stageCoordToTexCoord == null) {
            // For ray casting, convert from stageUm to texture coordinates (i.e. normalized voxels)
            stageCoordToTexCoord = new Matrix(new double[][]{
                    {1.0 / pixelDims[0], 0, 0, 0},
                    {0, 1.0 / pixelDims[1], 0, 0},
                    {0, 0, 1.0 / pixelDims[2], 0},
                    {0, 0, 0, 1}});
        }
        return stageCoordToTexCoord;
    }
}
