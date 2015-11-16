package org.janelia.it.workstation.gui.browser.model.search;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.*;

import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.ontology.Annotation;
import org.janelia.it.workstation.gui.browser.model.AnnotatedDomainObjectList;

/**
 * One page of annotated results, treated as a unit for performance reasons.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class ResultPage implements AnnotatedDomainObjectList {

    private final List<DomainObject> domainObjects = new ArrayList<>();
    private final ListMultimap<Long,Annotation> annotationsByDomainObjectId = ArrayListMultimap.<Long,Annotation>create();
    private final int numTotalResults;
    
    // TODO: might want to use References here for consistency, even if it's less efficient
    private Map<Long, DomainObject> domainObjectById;
    
    public ResultPage(List<DomainObject> domainObjects, List<Annotation> annotations, int totalNumResults) {
        
        for(DomainObject domainObject : domainObjects) {
            // Filter out null objects, in case some references could not be resolved
            if (domainObject!=null) {
                this.domainObjects.add(domainObject);
            }
        }
        
        for(Annotation annotation : annotations) {
            annotationsByDomainObjectId.put(annotation.getTarget().getTargetId(), annotation);
        }
        this.numTotalResults = totalNumResults;
    }

    public int getNumTotalResults() {
        return numTotalResults;
    }
    
    public int getNumPageResults() {
        return domainObjects.size();
    }
    
    @Override
    public List<DomainObject> getDomainObjects() {
        return domainObjects;
    }
    
    @Override
    public List<Annotation> getAnnotations(Long domainObjectId) {
        return annotationsByDomainObjectId.get(domainObjectId);
    }
    
    @Override
    public DomainObject getDomainObject(Long domainObjectId) {
        if (domainObjectById==null) {
            this.domainObjectById = new HashMap<>();
            for(DomainObject domainObject : domainObjects) {
                domainObjectById.put(domainObject.getId(), domainObject);
            }
        }
        return domainObjectById.get(domainObjectId);
    }

    @Override
    public void updateAnnotations(Long domainObjectId, List<Annotation> annotations) {
        annotationsByDomainObjectId.removeAll(domainObjectId);
        annotationsByDomainObjectId.putAll(domainObjectId, annotations);
    }
}
