package org.janelia.horta.volume;

import Jama.Matrix;
import org.aind.omezarr.OmeZarrDataset;
import org.aind.omezarr.image.AutoContrastParameters;
import org.aind.omezarr.image.TCZYXRasterZStack;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.janelia.geometry3d.Box3;
import org.janelia.geometry3d.ConstVector3;
import org.janelia.geometry3d.Vector3;
import org.janelia.gltools.texture.Texture3d;
import org.janelia.horta.BrainTileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In principle intended to implement BrickInfo.  There are substantial enough differences from BrainTileInfo that it
 * probably does not make sense to subclass.  Or at least refactor what bit is shared to a parent.  However, there
 * appear to be a lot of places that require BrainTileInfo rather than BrickInfo so this is the shortest path to get
 * Ome Zarr support for now.
 */
public class BrainChunkInfo extends BrainTileInfo {
    private final static Logger log = LoggerFactory.getLogger(BrainChunkInfo.class);

    private final OmeZarrDataset dataset;

    private final int[] readShape;

    private final int[] readOffset;

    private final double[] voxelSize;

    private final double[] shapeMicrometers;

    private final double[] originMicrometers;

    private final int[] pixelDims;

    private final int bytesPerIntensity;

    private Matrix stageCoordToTexCoord;

    private final String tileRelativePath;

    private final AutoContrastParameters autoContrastParameters;

    private int colorChannelIndex = 0;

    private boolean haveShownBoundingBox = false;

    public BrainChunkInfo(OmeZarrDataset dataset, int[] shape, int[] offset, double[] voxelSize, int channelCount, AutoContrastParameters autoContrastParameters) {
        super();

        this.dataset = dataset;

        this.autoContrastParameters = autoContrastParameters;

        this.voxelSize = voxelSize;

        // TODO include any translate from multiscale/dataset coordinate transforms.
        originMicrometers = new double[3];
        shapeMicrometers = new double[3];
        pixelDims = new int[4];

        pixelDims[0] = shape[0];
        pixelDims[1] = shape[1];
        pixelDims[2] = shape[2];

        originMicrometers[0] = this.voxelSize[0] * offset[0];
        originMicrometers[1] = this.voxelSize[1] * offset[1];
        originMicrometers[2] = this.voxelSize[2] * offset[2];

        shapeMicrometers[0] = this.voxelSize[0] * pixelDims[0];
        shapeMicrometers[1] = this.voxelSize[1] * pixelDims[1];
        shapeMicrometers[2] = this.voxelSize[2] * pixelDims[2];

        pixelDims[3] = channelCount;

        // TODO assumes 2 bytes per intensity.
        this.bytesPerIntensity = 2;

        // switch to [z, y, x] for jomezarr
        this.readShape = new int[]{1, 1, shape[2], shape[1], shape[0]};

        // switch to [z, y, x] for jomezarr
        // Assumes tczyx dataset.  Default to time 0, channel 0.
        this.readOffset = new int[]{0, 0, offset[2], offset[1], offset[0]};

        tileRelativePath = String.format("[%s] [%.0f, %.0f, %.0f] [%.0f, %.0f, %.0f]", dataset.getPath(), originMicrometers[0], originMicrometers[1], originMicrometers[2], shapeMicrometers[0], shapeMicrometers[1], shapeMicrometers[2]);
    }

    @Override
    public String getTileRelativePath() {
        return tileRelativePath;
    }

    @Override
    public VoxelIndex getRasterDimensions() {
        return new VoxelIndex(pixelDims[0], pixelDims[1], pixelDims[2]);
    }

    @Override
    public int getChannelCount() {
        return pixelDims[4];
    }

    @Override
    public int getBytesPerIntensity() {
        return bytesPerIntensity;
    }

    @Override
    public double getResolutionMicrometers() {
        double resolution = Float.MAX_VALUE;

        for (int xyz = 0; xyz < 3; ++xyz) {
            double res = shapeMicrometers[xyz] / (double) pixelDims[xyz];
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

        if (!haveShownBoundingBox) {
            haveShownBoundingBox = true;
        }

        return result;
    }

    @Override
    public List<? extends ConstVector3> getCornerLocations() {
        List<ConstVector3> result = new ArrayList<>();
        for (double pz : new double[]{0, pixelDims[2]}) {
            for (double py : new double[]{0, pixelDims[1]}) {
                for (double px : new double[]{0, pixelDims[0]}) {
                    Matrix corner = new Matrix(new double[]{(px + readOffset[4]) * voxelSize[0], (py + readOffset[3]) * voxelSize[1], (pz + readOffset[2]) * voxelSize[2], 1}, 4);
                    ConstVector3 v = new Vector3(
                            (float) corner.get(0, 0),
                            (float) corner.get(1, 0),
                            (float) corner.get(2, 0));
                    result.add(v);
                }
            }
        }

        return result;
    }

    @Override
    public Matrix getStageCoordToTexCoord() {
        // Compute matrix just-in-time
        if (stageCoordToTexCoord == null) {

            // For ray casting, convert from stageUm to texture coordinates (i.e. normalized voxels)
            stageCoordToTexCoord = new Matrix(new double[][]{
                    {1.0 / shapeMicrometers[0], 0, 0, 0 /*readOffset[0]*/},
                    {0, 1.0 / shapeMicrometers[1], 0, 0 /*readOffset[1]*/},
                    {0, 0, 1.0 / shapeMicrometers[2], 0 /*readOffset[2]*/},
                    {0, 0, 0, 1}});
        }

        return stageCoordToTexCoord;
    }

    private static final ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, true, Transparency.OPAQUE, DataBuffer.TYPE_USHORT);

    @Override
    public Texture3d loadBrick(double maxEdgePadWidth, int colorChannel, String fileExtension) {
        // setColorChannelIndex(colorChannel);
        return loadBrick(maxEdgePadWidth, fileExtension);
    }

    @Override
    public Texture3d loadBrick(double maxEdgePadWidth, String fileExtension) {
        Texture3d texture = new Texture3d();

        try {
            WritableRaster[] slices = TCZYXRasterZStack.fromDataset(dataset, readShape, readOffset, 1, true, autoContrastParameters, null);

            texture.loadRasterSlices(slices, colorModel);

            return texture;
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return null;
    }

    @Override
    public boolean isSameBrick(BrickInfo other) {
        if (!(other instanceof BrainChunkInfo)) {
            return false;
        }

        return Objects.equals(((BrainChunkInfo) other).getTileRelativePath(), tileRelativePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        BrainChunkInfo that = (BrainChunkInfo) o;

        return new EqualsBuilder()
                .append(tileRelativePath, that.tileRelativePath)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(tileRelativePath)
                .toHashCode();
    }
}
