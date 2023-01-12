package org.janelia.horta.volume;

import com.google.common.collect.ImmutableSet;
import org.aind.omezarr.OmeZarrAxisUnit;
import org.aind.omezarr.OmeZarrDataset;
import org.aind.omezarr.OmeZarrGroup;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * StaticVolumeBrickSource for Ome-Zarr datasets.
 */
public class OmeZarrVolumeBrickSource implements StaticVolumeBrickSource {
    private final ArrayList<Double> resolutionsMicrometers;

    private final HashMap<Double, BrickInfoSet> brickInfoSets;

    public OmeZarrVolumeBrickSource(Path path) throws IOException {
        OmeZarrGroup fileset = OmeZarrGroup.open(path);

        int datasetCount = fileset.getAttributes().getMultiscales()[0].getDatasets().size();

        resolutionsMicrometers = new ArrayList<>();

        brickInfoSets = new HashMap<>();

        for (int idx = datasetCount - 8; idx < datasetCount; idx++) {
            OmeZarrDataset dataset = fileset.getAttributes().getMultiscales()[0].getDatasets().get(idx);

            if (!dataset.isValid()) {
                continue;
            }

            Pair<Double, BrickInfoSet> pair = createBricksetForDataset(dataset);

            resolutionsMicrometers.add(pair.getLeft());

            brickInfoSets.put(pair.getLeft(), pair.getRight());
        }
    }

    @Override
    public Collection<Double> getAvailableResolutions() {
        return resolutionsMicrometers;
    }

    // TODO temp
    private Double lastResolution;

    @Override
    public BrickInfoSet getAllBrickInfoForResolution(Double resolution) {
        if (resolution != lastResolution) {
            System.out.println(String.format("requesting resolution %.1f", resolution.floatValue()));
            lastResolution = resolution;
        }

        return brickInfoSets.get(resolution);
    }

    @Override
    public FileType getFileType() {
        return FileType.ZARR;
    }

    private Pair<Double, BrickInfoSet> createBricksetForDataset(OmeZarrDataset dataset) throws IOException {

        double resolutionMicrometers = dataset.getMinSpatialResolution();

        System.out.println(String.format("creating brickset for resolution: %.1f", resolutionMicrometers));

        BrickInfoSet brickInfoSet = new BrickInfoSet();

        List<BrainChunkInfo> chunks = createTilesForResolution(dataset);

        for (BrainChunkInfo chunk : chunks) {
            brickInfoSet.add(chunk);
        }

        return Pair.of(resolutionMicrometers, brickInfoSet);
    }

    private final int chunkSegment = 256;

    /**
     * Only valid for tczyx OmeZarr datasets.
     *
     * @param dataset
     * @return
     * @throws IOException
     */
    private List<BrainChunkInfo> createTilesForResolution(OmeZarrDataset dataset) throws IOException {
        List<BrainChunkInfo> brickInfoList = new ArrayList<>();

        int[] shape = dataset.getShape();

        // TODO does not chunk bricks
        int[] chunkSize = {shape[2], shape[3], shape[4]};

        // BrainChunkInfo info = null;

        // Raw TIFF chunks are 1024 x 1536 x 251 for reference (~400M voxels) or 350 x 450 x 250 um (~150k um3).
        for (int xIdx = 0; xIdx < shape[4]; xIdx += chunkSegment) {
            for (int yIdx = 0; yIdx < shape[3]; yIdx += chunkSegment) {

                int[] offset = {0, yIdx, xIdx};

                List<Double> spatialShape = dataset.getSpatialResolution(OmeZarrAxisUnit.MICROMETER);

                chunkSize[1] = Math.min(shape[3] - yIdx, chunkSegment);
                chunkSize[2] = Math.min(shape[4] - xIdx, chunkSegment);

                // spatialShape is returned as ordered in OmeZarr attributes file which is zyx.  Should probably change in jomezarr.
                double[] voxelSize = {spatialShape.get(2), spatialShape.get(1), spatialShape.get(0)};

                brickInfoList.add(new BrainChunkInfo(dataset, chunkSize, offset, voxelSize, shape[1]));
                // info = new BrainChunkInfo(dataset, chunkSize, offset, voxelSize, shape[1]);
            }
        }

        // brickInfoList.add(info);

        return brickInfoList;
    }
}
