package org.janelia.it.workstation.gui.browser.nodes;

import java.awt.Image;
import java.lang.ref.WeakReference;

import org.janelia.it.jacs.model.domain.sample.Sample;
import org.janelia.it.jacs.model.domain.sample.SamplePipelineRun;
import org.janelia.it.workstation.gui.browser.api.DomainUtils;
import org.janelia.it.workstation.gui.browser.nodes.children.ResultChildFactory;
import org.janelia.it.workstation.gui.util.Icons;
import org.openide.nodes.Children;

public class SamplePipelineRunNode extends InternalNode<SamplePipelineRun> {
    
    private final WeakReference<Sample> sampleRef;
    
    public SamplePipelineRunNode(Sample sample, SamplePipelineRun run) throws Exception {
        super(run);
        this.sampleRef = new WeakReference<Sample>(sample);
        if (DomainUtils.isEmpty(run.getResults())) {
            setChildren(Children.LEAF);
        }
        else {
            setChildren(Children.create(new ResultChildFactory(sample, run), true));       
        }
    }
    
    public SamplePipelineRun getSamplePipelineRun() {
        return (SamplePipelineRun)getBean();
    }
    
    @Override
    public String getPrimaryLabel() {
        return getSamplePipelineRun().getName();
    }
    
    @Override
    public String getSecondaryLabel() {
        return getSamplePipelineRun().getCreationDate()+"";
    }
    
    @Override
    public Image getIcon(int type) {
        return Icons.getIcon("folder_image.png").getImage();
    }
}
