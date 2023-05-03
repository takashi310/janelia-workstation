package org.janelia.horta.blocks;

public class OmeZarrBlockResolution implements BlockTileResolution {
    private final int depth;

    private final int[] shape;

    private final double resolutionMicrometers;

    private final int blockPowerScale;

    private OmeZarrBlockInfoSet blockInfoSet = new OmeZarrBlockInfoSet();

    public OmeZarrBlockResolution(int depth, int[] shape, double resolutionMicrometers, int blockPowerScale) {
        this.depth = depth;
        this.shape = shape;
        this.resolutionMicrometers = resolutionMicrometers;
        this.blockPowerScale = blockPowerScale;
    }

    @Override
    public int getResolution() {
        return depth;
    }

    public double getResolutionMicrometers() {
        return resolutionMicrometers;
    }

    public double getBlockPowerScale() {
        return blockPowerScale;
    }

    public OmeZarrBlockInfoSet getBlockInfoSet() {
        return blockInfoSet;
    }

    public float getBlockSizeScale() {
        return (float) Math.pow(2.0, getBlockPowerScale());
    }

    public int[] getChunkSize() {
        float scale = getBlockSizeScale();

        return new int[]{(int) (shape[4] / scale), (int) (shape[3] / scale), (int) (shape[2] / scale)};
    }

    @Override
    public int hashCode() {
        return depth;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final OmeZarrBlockResolution other = (OmeZarrBlockResolution) obj;
        if (this.depth != other.depth) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(BlockTileResolution o) {
        OmeZarrBlockResolution rhs = (OmeZarrBlockResolution) o;
        return depth < rhs.depth ? -1 : depth > rhs.depth ? 1 : 0;
    }

    @Override
    public String toString(){
        return String.format("depth: %d; um/voxel: %.1f; scaleFactor: %d", depth, resolutionMicrometers, blockPowerScale);
    }
}
