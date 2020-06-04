package org.janelia.workstation.controller.model;

import Jama.Matrix;
import org.janelia.model.domain.tiledMicroscope.TmNeuronTagMap;
import org.janelia.model.domain.tiledMicroscope.TmSample;
import org.janelia.model.domain.tiledMicroscope.TmWorkspace;
import org.janelia.model.util.MatrixUtilities;
import org.janelia.workstation.controller.SpatialIndexManager;
import org.janelia.workstation.controller.model.annotations.neuron.NeuronModel;
import org.janelia.workstation.controller.access.TiledMicroscopeDomainMgr;
import org.janelia.workstation.controller.tileimagery.TileLoader;

/**
 * This class is singleton to manage references to all the different model
 * data that TM viewers might need to access.  This includes stuff like Samples, Workspaces, Meshes,
 * Scripts, ViewState, SelectionState.  Having it in one place gives us an easy way to manage performance,
 * provide concurrent-safe data structures, load/unload data, and add integrations with plugins and 3rd party tools.
 *
 * There are assumptions to simplify things like 1 Sample/Workspace open,
 * @author schauderd
 */
public class TmModelManager {
    private TmSample currentSample;
    private TmWorkspace currentWorkspace;
    private TmNeuronTagMap currentTagMap;

    private TmViewState currentView;
    private TmSelectionState currentSelections;
    private NeuronModel neuronModel;
    private final TiledMicroscopeDomainMgr tmDomainMgr;
    private TileLoader tileLoader;
    private SpatialIndexManager spatialIndexManager;

    private static final TmModelManager instance = new TmModelManager();
    public static TmModelManager getInstance() {
        return instance;
    }

    public TmModelManager() {
        this.tmDomainMgr = TiledMicroscopeDomainMgr.getDomainMgr();
        neuronModel = NeuronModel.getInstance();
        currentView = new TmViewState();
    }

    public TmSample getCurrentSample() {
        return currentSample;
    }

    public SpatialIndexManager getSpatialIndexManager() {
        return spatialIndexManager;
    }

    public void setSpatialIndexManager(SpatialIndexManager spatialIndexManager) {
        this.spatialIndexManager = spatialIndexManager;
    }

    public void setSampleMatrices(Matrix micronToVoxMatrix, Matrix voxToMicronMatrix) throws Exception {
        if (currentSample==null) {
            throw new IllegalStateException("Sample is not loaded");
        }
        currentSample.setMicronToVoxMatrix(MatrixUtilities.serializeMatrix(micronToVoxMatrix, "micronToVoxMatrix"));
        currentSample.setVoxToMicronMatrix(MatrixUtilities.serializeMatrix(voxToMicronMatrix, "voxToMicronMatrix"));
        tmDomainMgr.save(currentSample);
    }

    public void setCurrentSample(TmSample currentSample) {
        this.currentSample = currentSample;
    }

    public TmWorkspace getCurrentWorkspace() {
        return currentWorkspace;
    }

    public void setCurrentWorkspace(TmWorkspace currentWorkspace) {
        this.currentWorkspace = currentWorkspace;
    }

    public TmViewState getCurrentView() {
        return currentView;
    }

    public void setCurrentView(TmViewState currentView) {
        this.currentView = currentView;
    }

    public TmSelectionState getCurrentSelections() {
        return TmSelectionState.getInstance();
    }

    public void setCurrentSelections(TmSelectionState currentSelections) {
        this.currentSelections = currentSelections;
    }

    public void saveWorkspace(TmWorkspace workspace) throws Exception {
        tmDomainMgr.save(workspace);
    }

    private Long getWsId() {
        if (currentWorkspace != null) {
            return currentWorkspace.getId();
        }
        else {
            return -1L;
        }
    }

    public NeuronModel getNeuronModel() {
        return neuronModel;
    }

    public void setNeuronModel(NeuronModel neuronModel) {
        this.neuronModel = neuronModel;
    }

    public TmNeuronTagMap getCurrentTagMap() {
        return currentTagMap;
    }

    public TmNeuronTagMap getAllTagMeta() {
        // if not set return an empty tag map
        return currentTagMap == null ? new TmNeuronTagMap() : currentTagMap;
    }

    public void setCurrentTagMap(TmNeuronTagMap currentTagMap) {
        this.currentTagMap = currentTagMap;
    }

    public TileLoader getTileLoader() {
        return tileLoader;
    }

    public void setTileLoader(TileLoader tileLoader) {
        this.tileLoader = tileLoader;
    }
}
