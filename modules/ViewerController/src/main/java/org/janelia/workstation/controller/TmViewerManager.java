package org.janelia.workstation.controller;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.janelia.console.viewerapi.dialogs.NeuronGroupsDialog;
import org.janelia.model.domain.DomainConstants;
import org.janelia.model.domain.tiledMicroscope.*;
import org.janelia.workstation.controller.access.ProjectInitFacade;
import org.janelia.workstation.controller.access.ProjectInitFacadeImpl;
import org.janelia.workstation.controller.access.RefreshHandler;
import org.janelia.workstation.controller.eventbus.*;
import org.janelia.model.domain.DomainObject;
import org.janelia.workstation.controller.model.TmModelManager;
import org.janelia.workstation.controller.access.TiledMicroscopeDomainMgr;
import org.janelia.workstation.controller.model.annotations.neuron.NeuronModel;
import org.janelia.workstation.core.api.ClientDomainUtils;
import org.janelia.workstation.core.util.ConsoleProperties;
import org.janelia.workstation.integration.util.FrameworkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.View;
import java.util.*;

public class TmViewerManager implements GlobalViewerController {
    private final Logger log = LoggerFactory.getLogger(TmViewerManager.class);
    private static final TmViewerManager instance = new TmViewerManager();
    private TmModelManager modelManager = new TmModelManager();
    private TiledMicroscopeDomainMgr tmDomainMgr;
    private NeuronManager neuronManager;
    private ProjectInitFacade projectInit;
    private DomainObject currProject;
    private int NUMBER_FRAGMENTS_THRESHOLD = 100;

    public enum ToolSet {
        NEURON
    }

    public static TmViewerManager getInstance() {
        return instance;
    }

    public TmViewerManager() {
        this.tmDomainMgr = TiledMicroscopeDomainMgr.getDomainMgr();
        setNeuronManager(NeuronManager.getInstance());
        ViewerEventBus.registerForEvents(this);
    }

    @Subscribe
    public void selectNeurons(SelectionNeuronsEvent selectionEvent) {
        if (selectionEvent.isClear()) {
            modelManager.getCurrentSelections().clearNeuronSelection();
            return;
        }
        if (selectionEvent.isSelect()) {
            List<Object> selections = selectionEvent.getItems();
            if (selections.get(0) instanceof TmNeuronMetadata)
                modelManager.getCurrentSelections().setCurrentNeuron(selections.get(0));
            else
                modelManager.getCurrentSelections().setCurrentNeuron(selections.get(0));
        } else {
            //
        }
    }

    @Subscribe
    public void selectAnnotations(SelectionAnnotationEvent selectionEvent) {
        if (selectionEvent.isSelect()) {
            List<Object> selections = selectionEvent.getItems();
            if (selections.get(0) instanceof TmGeoAnnotation)
                modelManager.getCurrentSelections().setCurrentVertex(selections.get(0));
            else
                modelManager.getCurrentSelections().setCurrentVertex(selections.get(0));
        } else {
            //
        }

        if (selectionEvent.isClear()) {
            modelManager.getCurrentSelections().clearVertexSelection();
        }

    }

    public NeuronManager getNeuronManager() {
        return neuronManager;
    }

    public void setNeuronManager(NeuronManager neuronManager) {
        this.neuronManager = neuronManager;
    }


    /**
     *  method for going through the full sequence of loading a TmWorkspace or TmSample.
     *  It's async since we'd like the UI to be responsive while loading goes on.
     *  The order of initialization for a regular workstation instance is to the following;
     *  1. Clear Viewers
     *  2. Load Annotation Data
     *  3. Load Tile Imagery Data/Stack Information
     *  4. Notify Viewers
     *
      */
    public void loadProject(DomainObject project) {
        projectInit = new ProjectInitFacadeImpl(project);
        projectInit.clearViewers();
        currProject = project;
        if (currProject instanceof TmWorkspace) {
            TmModelManager.getInstance().setCurrentWorkspace((TmWorkspace)currProject);
            projectInit.loadAnnotationData((TmWorkspace)currProject);
        } else {
            loadImagery((TmSample)currProject);
        }
    }

    // once the data has been loaded
    @Subscribe
    public void dataLoadComplete(LoadMetadataEvent dataEvent) {
        try {
            loadImagery(dataEvent.getSample());
        } catch (Exception error) {
            FrameworkAccess.handleException(error);
        }
    }

    private void loadImagery(TmSample sample) {
        projectInit.loadImagery(sample);
    }


    public boolean editsAllowed() {
        if (modelManager.getCurrentWorkspace()==null) return false;
        return ClientDomainUtils.hasWriteAccess(modelManager.getCurrentWorkspace());
    }

    public void loadUserPreferences() throws Exception {
        TmNeuronTagMap currentTagMap = modelManager.getCurrentTagMap();
        if (modelManager.getCurrentSample()==null || modelManager.getCurrentSample().getId()==null) return;
        Map<String,Map<String,Object>> tagGroupMappings = FrameworkAccess.getRemotePreferenceValue(DomainConstants.PREFERENCE_CATEGORY_MOUSELIGHT, modelManager.getCurrentSample().getId().toString(), null);
        if (tagGroupMappings!=null && currentTagMap!=null) {
            currentTagMap.saveTagGroupMappings(tagGroupMappings);
            //   if (neuronSetAdapter!=null && neuronSetAdapter.getMetaWorkspace()!=null) {
            //     neuronSetAdapter.getMetaWorkspace().setTagMetadata(currentTagMap);
            //   }

            // set toggled group properties on load-up
            Iterator<String> groupTags = tagGroupMappings.keySet().iterator();
            while (groupTags.hasNext()) {
                String groupKey = groupTags.next();
                Set<TmNeuronMetadata> neurons = getNeuronManager().getNeuronsForTag(groupKey);
                List<TmNeuronMetadata> neuronList = new ArrayList<TmNeuronMetadata>(neurons);
                Map<String,Object> groupMapping = currentTagMap.geTagGroupMapping(groupKey);
                if (groupMapping!=null && groupMapping.get("toggled")!=null && ((Boolean)groupMapping.get("toggled"))) {
                    String property = (String)groupMapping.get("toggleprop");
                    // these two prop changes ought to be in annmodel, not annmgr, and annmgr should call into model;
                    //  fixed for visiblity, but not for others yet
                    if (property.equals(NeuronGroupsDialog.PROPERTY_RADIUS)) {
                        //LargeVolumeViewerTopComponent.getInstance().getAnnotationMgr().setNeuronUserToggleRadius(neuronList, true);
                    } else if (property.equals(NeuronGroupsDialog.PROPERTY_VISIBILITY)) {
                        //setNeuronVisibility(neuronList, false);
                    } else if (property.equals(NeuronGroupsDialog.PROPERTY_READONLY)) {
                        //LargeVolumeViewerTopComponent.getInstance().getAnnotationMgr().setNeuronNonInteractable(neuronList, true);
                    }
                }
            }
        }
    }

    @Subscribe
    public void loadComplete(LoadProjectEvent event) {
        final TmWorkspace workspace = modelManager.getCurrentWorkspace();
        modelManager.setCurrentSample(event.getSample());
        if (workspace==null) {
            // this is a sample

        }

        String systemNeuron = ConsoleProperties.getInstance().getProperty("console.LVVHorta.tracersgroup").trim();
        modelManager.getCurrentView().setFilter(false);
        int nFragments = 0;
        for (TmNeuronMetadata neuron: modelManager.getNeuronModel().getNeurons()) {
            if (neuron.getOwnerKey().equals(systemNeuron)) {
                nFragments += 1;
                if (nFragments >= NUMBER_FRAGMENTS_THRESHOLD) {
                    modelManager.getCurrentView().setFilter(true);

                    // fire event
                    NeuronSpatialFilterUpdateEvent spatialEvent = new NeuronSpatialFilterUpdateEvent(true);
                    ViewerEventBus.postEvent(spatialEvent);
                    break;
                }
            }
        }

        try {
            loadUserPreferences();
            RefreshHandler.getInstance().ifPresent(rh -> rh.setAnnotationModel(getNeuronManager()));
            //TaskWorkflowViewTopComponent.getInstance().loadHistory();
        } catch (Exception error) {
            FrameworkAccess.handleException(error);
        }

        TmNeuronTagMap currentTagMap = new TmNeuronTagMap();
        for (TmNeuronMetadata tmNeuronMetadata : NeuronModel.getInstance().getNeurons()) {
            for(String tag : tmNeuronMetadata.getTags()) {
                currentTagMap.addTag(tag, tmNeuronMetadata);
            }
        }
        modelManager.setCurrentTagMap(currentTagMap);

        SelectionEvent evt = new SelectionEvent();
        evt.setClear(true);
        ViewerEventBus.postEvent(evt);
    }
}
