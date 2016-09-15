package org.janelia.it.workstation.gui.large_volume_viewer.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.it.jacs.model.domain.DomainConstants;
import org.janelia.it.jacs.model.domain.Reference;
import org.janelia.it.jacs.model.domain.tiledMicroscope.BulkNeuronStyleUpdate;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmNeuronMetadata;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmProtobufExchanger;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmSample;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmWorkspace;
import org.janelia.it.jacs.model.domain.workspace.TreeNode;
import org.janelia.it.workstation.gui.browser.api.DomainMgr;
import org.janelia.it.workstation.gui.browser.api.DomainModel;
import org.janelia.it.workstation.gui.browser.model.DomainObjectComparator;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton for managing the Tiled Microscope Domain Model and related data access.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class TiledMicroscopeDomainMgr {

    private static final Logger log = LoggerFactory.getLogger(TiledMicroscopeDomainMgr.class);

    // Singleton
    private static TiledMicroscopeDomainMgr instance;
    
    public static TiledMicroscopeDomainMgr getDomainMgr() {
        if (instance==null) {
            instance = new TiledMicroscopeDomainMgr();
        }
        return instance;
    }

    private final TiledMicroscopeRestClient tmFacade;
    
    private TiledMicroscopeDomainMgr() {
        tmFacade = new TiledMicroscopeRestClient();
    }
    
    private final DomainModel model = DomainMgr.getDomainMgr().getModel();

    public List<String> getTmSamplePaths() throws Exception {
        return tmFacade.getTmSamplePaths();
    }

    public void setTmSamplePaths(List<String> paths) throws Exception {
        tmFacade.updateSamplePaths(paths);
    }
    
    public TmSample getSample(Long sampleId) throws Exception {
        log.debug("getSample(sampleId={})",sampleId);
        TmSample sample = model.getDomainObject(TmSample.class, sampleId);
        if (sample==null) {
            throw new Exception("Sample with id="+sampleId+" does not exist");
        }
        return sample;
    }

    public TmSample getSample(TmWorkspace workspace) throws Exception {
        log.debug("getSample({})",workspace);
        return getSample(workspace.getSampleRef().getTargetId());
    }

    public TmSample createTiledMicroscopeSample(String name, String filepath) throws Exception {
        log.debug("createTiledMicroscopeSample(name={}, filepath={})", name, filepath);
        TmSample sample = new TmSample();
        sample.setOwnerKey(SessionMgr.getSubjectKey());
        sample.setName(name);
        sample.setFilepath(filepath);
        sample = save(sample);

        // Server should have put the sample in the Samples root folder. Refresh the Samples folder to show it in the explorer.
        TreeNode folder = model.getDefaultWorkspaceFolder(DomainConstants.NAME_TM_SAMPLE_FOLDER, true);
        model.invalidate(folder);
        
        return sample;
    }

    public TmSample save(TmSample sample) throws Exception {
        log.debug("save({})",sample);
        TmSample canonicalObject;
        synchronized (this) {
            canonicalObject = model.putOrUpdate(sample.getId()==null ? tmFacade.create(sample) : tmFacade.update(sample));
        }
        if (sample.getId()==null) {
            model.notifyDomainObjectCreated(canonicalObject);
        }
        else {
            model.notifyDomainObjectChanged(canonicalObject);
        }
        return canonicalObject;
    }

    public void remove(TmSample sample) throws Exception {
        log.debug("remove({})",sample);
        tmFacade.remove(sample);
        model.notifyDomainObjectRemoved(sample);
    }

    public List<TmWorkspace> getTmWorkspaces(Long sampleId) throws Exception {
        Collection<TmWorkspace> workspaces = tmFacade.getTmWorkspacesForSample(sampleId);
        List<TmWorkspace> canonicalObjects = DomainMgr.getDomainMgr().getModel().putOrUpdate(workspaces, false);
        Collections.sort(canonicalObjects, new DomainObjectComparator());
        return canonicalObjects;
    }

    public TmWorkspace createTiledMicroscopeWorkspace(Long sampleId, String name) throws Exception {
        log.debug("createTiledMicroscopeWorkspace(sampleId={}, name={})", sampleId, name);
        TmSample sample = getSample(sampleId);
        if (sample==null) {
            throw new IllegalArgumentException("TM sample does not exist: "+sampleId);
        }
        
        TmWorkspace workspace = new TmWorkspace();
        workspace.setOwnerKey(SessionMgr.getSubjectKey());
        workspace.setName(name);
        workspace.setSampleRef(Reference.createFor(TmSample.class, sampleId));
        workspace = save(workspace);
        
        // Server should have put the workspace in the Workspaces root folder. Refresh the Workspaces folder to show it in the explorer.
        TreeNode folder = model.getDefaultWorkspaceFolder(DomainConstants.NAME_TM_WORKSPACE_FOLDER, true);
        model.invalidate(folder);
        
        return workspace;
    }

    public TmWorkspace save(TmWorkspace workspace) throws Exception {
        log.debug("save({})", workspace);
        TmWorkspace canonicalObject;
        synchronized (this) {
            canonicalObject = model.putOrUpdate(workspace.getId()==null ? tmFacade.create(workspace) : tmFacade.update(workspace));
        }
        if (workspace.getId()==null) {
            model.notifyDomainObjectCreated(canonicalObject);
        }
        else {
            model.notifyDomainObjectChanged(canonicalObject);
        }
        return canonicalObject;
    }

    public void remove(TmWorkspace workspace) throws Exception {
        log.debug("remove({})", workspace);
        tmFacade.remove(workspace);
        model.notifyDomainObjectRemoved(workspace);
    }
    
    public List<TmNeuronMetadata> getWorkspaceNeurons(Long workspaceId) throws Exception {
        log.debug("getWorkspaceNeurons(workspaceId={})",workspaceId);
        TmProtobufExchanger exchanger = new TmProtobufExchanger();
        List<TmNeuronMetadata> neurons = new ArrayList<>();
        for(Pair<TmNeuronMetadata,InputStream> pair : tmFacade.getWorkspaceNeuronPairs(workspaceId)) {
            TmNeuronMetadata neuronMetadata = pair.getLeft();
            exchanger.deserializeNeuron(pair.getRight(), neuronMetadata);
            log.trace("Got neuron {} with payload '{}'", neuronMetadata.getId(), neuronMetadata);
            neurons.add(neuronMetadata);
        }

        log.info("Loaded {} neurons for workspace {}", neurons.size(), workspaceId);
        return neurons;
    }

    public TmNeuronMetadata saveMetadata(TmNeuronMetadata neuronMetadata) throws Exception {
        log.debug("save({})", neuronMetadata);
        TmNeuronMetadata savedMetadata;
        if (neuronMetadata.getId()==null) {
            savedMetadata = tmFacade.createMetadata(neuronMetadata);
            model.notifyDomainObjectCreated(savedMetadata);
        }
        else {
            savedMetadata = tmFacade.updateMetadata(neuronMetadata);
            model.notifyDomainObjectChanged(savedMetadata);
        }
        return savedMetadata;
    }

    public List<TmNeuronMetadata> saveMetadata(List<TmNeuronMetadata> neuronList) throws Exception {
        log.debug("save({})", neuronList);
        for(TmNeuronMetadata tmNeuronMetadata : neuronList) {
            if (tmNeuronMetadata.getId()==null) {
                throw new IllegalArgumentException("Bulk neuron creation is currently unsupported");
            }
        }
        List<TmNeuronMetadata> updatedMetadata = tmFacade.updateMetadata(neuronList);
        for(TmNeuronMetadata tmNeuronMetadata : updatedMetadata) {
            model.notifyDomainObjectChanged(tmNeuronMetadata);
        }
        return updatedMetadata;
    }
    
    public TmNeuronMetadata save(TmNeuronMetadata neuronMetadata) throws Exception {
        log.debug("save({})", neuronMetadata);
        TmProtobufExchanger exchanger = new TmProtobufExchanger();
        InputStream protobufStream = new ByteArrayInputStream(exchanger.serializeNeuron(neuronMetadata));
        TmNeuronMetadata savedMetadata;
        if (neuronMetadata.getId()==null) {
            savedMetadata = tmFacade.create(neuronMetadata, protobufStream);
            model.notifyDomainObjectCreated(savedMetadata);
        }
        else {
            savedMetadata = tmFacade.update(neuronMetadata, protobufStream);
            model.notifyDomainObjectChanged(savedMetadata);
        }
        // We assume that the neuron data was saved on the server, but it only returns metadata for efficiency. We
        // already have the data, so let's copy it over into the new object.
        exchanger.copyNeuronData(neuronMetadata, savedMetadata);
        return savedMetadata;
    }
    
    public void updateNeuronStyles(BulkNeuronStyleUpdate bulkNeuronStyleUpdate) throws Exception {
        tmFacade.updateNeuronStyles(bulkNeuronStyleUpdate);
    }
    
    public void remove(TmNeuronMetadata tmNeuron) throws Exception {
        log.debug("remove({})", tmNeuron);
        TmNeuronMetadata neuronMetadata = new TmNeuronMetadata();
        neuronMetadata.setId(tmNeuron.getId());
        tmFacade.remove(neuronMetadata);
        model.notifyDomainObjectRemoved(neuronMetadata);
    }

    public void bulkEditTags(List<TmNeuronMetadata> neurons, List<String> tags, boolean tagState) throws Exception {
        log.debug("bulkEditTags({})", neurons);
        tmFacade.changeTags(neurons, tags, tagState);
    }
}
