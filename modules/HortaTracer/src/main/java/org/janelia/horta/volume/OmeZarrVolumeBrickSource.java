package org.janelia.horta.volume;

import com.google.common.collect.ImmutableSet;
import org.aind.omezarr.OmeZarrDataset;
import org.aind.omezarr.OmeZarrGroup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * StaticVolumeBrickSource for Ome-Zarr datasets.
 */
public class OmeZarrVolumeBrickSource implements StaticVolumeBrickSource {
    private final Double resolutionMicrometers;

    private final BrickInfoSet brickInfoSet;

    public OmeZarrVolumeBrickSource(Path path) throws IOException {
        // TODO assumes 1um/voxel.
        resolutionMicrometers = 1.0;

        brickInfoSet = new BrickInfoSet();

        OmeZarrGroup fileset = OmeZarrGroup.open(path);

        // TODO only supports one resolution/brickset w/a single brick for the entire plane
        OmeZarrDataset dataset = fileset.getAttributes().getMultiscales()[0].getDatasets()[0];

        brickInfoSet.add(new BrainChunkInfo(dataset));
        // brickInfoSet.addAll(createTilesForResolution(dataset));
    }

    @Override
    public Collection<Double> getAvailableResolutions() {
        return ImmutableSet.of(resolutionMicrometers);
    }

    @Override
    public BrickInfoSet getAllBrickInfoForResolution(Double resolution) {
        return brickInfoSet;
    }

    @Override
    public FileType getFileType() {
        return FileType.ZARR;
    }

    private List<BrainChunkInfo> createTilesForResolution(OmeZarrDataset dataset) throws IOException {
        List<BrainChunkInfo> brickInfoList = new ArrayList<>();

        brickInfoList.add(new BrainChunkInfo(dataset));

        return brickInfoList;
    }
}
