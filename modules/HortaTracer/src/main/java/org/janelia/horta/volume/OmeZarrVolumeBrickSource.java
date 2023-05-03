package org.janelia.horta.volume;

import org.aind.omezarr.OmeZarrAxisUnit;
import org.aind.omezarr.OmeZarrDataset;
import org.aind.omezarr.OmeZarrGroup;
import org.aind.omezarr.image.AutoContrastParameters;
import org.aind.omezarr.image.TCZYXRasterZStack;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.horta.omezarr.JadeZarrStoreProvider;
import org.janelia.horta.omezarr.OmeZarrJadeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * StaticVolumeBrickSource for Ome-Zarr datasets.
 */
public class OmeZarrVolumeBrickSource implements StaticVolumeBrickSource {
    private final static Logger log = LoggerFactory.getLogger(OmeZarrVolumeBrickSource.class);

    private final ArrayList<Double> resolutionsMicrometers = new ArrayList<>();

    private final Map<Double, BrickInfoSet> brickInfoSets = new HashMap<>();

    private AutoContrastParameters autoContrast = null;

    private final String basePath;

    private OmeZarrJadeReader reader;

    public OmeZarrVolumeBrickSource(String path) {
        basePath = path;
    }

    public OmeZarrVolumeBrickSource init() {
        try {
            OmeZarrGroup fileset = OmeZarrGroup.open(Paths.get(basePath));

            this.reader = null;

            init(fileset);
        } catch (IOException ex) {
            log.info("failed to initialize ome-zarr volume source");
        }

        return this;
    }

    public OmeZarrVolumeBrickSource init(OmeZarrJadeReader reader, Consumer<Integer> progressUpdater) {
        try {
            this.reader = reader;

            OmeZarrGroup fileset = OmeZarrGroup.open(new JadeZarrStoreProvider("", reader));

            init(fileset);
        } catch (IOException ex) {
            log.info("failed to initialize ome-zarr volume source");
        }

        return this;
    }

    private void init(OmeZarrGroup fileset) {
        int datasetCount = fileset.getAttributes().getMultiscales()[0].getDatasets().size();

        for (int idx = 0; idx < datasetCount; idx++) {
            try {
                OmeZarrDataset dataset = fileset.getAttributes().getMultiscales()[0].getDatasets().get(idx);

                if (this.reader != null) {
                    dataset.setExternalZarrStore(new JadeZarrStoreProvider(dataset.getPath(), reader));
                }

                if (!dataset.isValid()) {
                    continue;
                }

                Pair<Double, BrickInfoSet> pair = createBricksetForDataset(dataset);

                if (pair.getRight() != null && !pair.getRight().isEmpty()) {
                    resolutionsMicrometers.add(pair.getLeft());

                    brickInfoSets.put(pair.getLeft(), pair.getRight());
                }
            } catch (Exception ex) {
                log.info("failed to initialize dataset at index " + idx);
            }
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
            log.info(String.format("requesting resolution %.1f", resolution.floatValue()));
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

        log.info(String.format("creating brickset for resolution: %.1f", resolutionMicrometers));

        BrickInfoSet brickInfoSet = new BrickInfoSet();

        List<BrainChunkInfo> chunks = createTilesForResolution(dataset);

        for (BrainChunkInfo chunk : chunks) {
            brickInfoSet.add(chunk);
        }

        return Pair.of(resolutionMicrometers, brickInfoSet);
    }

    /**
     * Only valid for tczyx OmeZarr datasets.
     *
     * @param dataset
     * @return
     * @throws IOException
     */
    private List<BrainChunkInfo> createTilesForResolution(OmeZarrDataset dataset) {
        List<BrainChunkInfo> brickInfoList = new ArrayList<>();

        try {
            // [t, c, z, y, x]
            int[] shape = dataset.getShape();

            // [x, y, z]
            int[] chunkSize = {shape[4], shape[3], shape[2]};

            if (autoContrast == null) {
                int[] autoContrastShape = {1, 1, 256, 256, 128};

                AutoContrastParameters parameters = TCZYXRasterZStack.computeAutoContrast(dataset, autoContrastShape);

                if (parameters != null) {
                    double existingMax = parameters.min + (65535.0 / parameters.slope);

                    double min = Math.max(100, parameters.min * 0.1);
                    double max = Math.min(65535.0, Math.max(min + 100, existingMax * 4));
                    double slope = 65535.0 / (max - min);

                    autoContrast = new AutoContrastParameters(min, slope);
                }
            }

            // [z, y, x]
            List<Double> spatialShape = dataset.getSpatialResolution(OmeZarrAxisUnit.MICROMETER);

            int chunkSegment = (int) Math.round(4e5 / chunkSize[2] / 1.0);

            int xChunkSegment = chunkSegment;
            int yChunkSegment = chunkSegment;
            int zChunkSegment = 512000000;

            log.info("chunkSegments for dataset path " + dataset.getPath() + ": " + xChunkSegment + "," + yChunkSegment + "," + zChunkSegment);

            int chunkCount = 0;

            for (int xIdx = 0; xIdx < shape[4]; xIdx += xChunkSegment) {
                for (int yIdx = 0; yIdx < shape[3]; yIdx += yChunkSegment) {
                    for (int zIdx = 0; zIdx < shape[2]; zIdx += zChunkSegment) {
                        // [x, y, z]
                        int[] offset = {xIdx, yIdx, zIdx};

                        chunkSize[0] = Math.min(shape[4] - xIdx, xChunkSegment);
                        chunkSize[1] = Math.min(shape[3] - yIdx, yChunkSegment);
                        chunkSize[2] = Math.min(shape[2] - zIdx, zChunkSegment);

                        // [x, y, z]
                        double[] voxelSize = {spatialShape.get(2), spatialShape.get(1), spatialShape.get(0)};

                        // All args [x, y, z]
                        brickInfoList.add(new BrainChunkInfo(dataset, chunkSize, offset, voxelSize, shape[1], autoContrast));

                        chunkCount++;
                    }
                }
            }

            log.info(chunkCount + " chunks for dataset path " + dataset.getPath());
        } catch (Exception ex) {
            log.info(ex.getMessage());
        }

        return brickInfoList;
    }
}
