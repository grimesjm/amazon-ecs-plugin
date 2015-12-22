/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.*;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSTaskTemplate extends AbstractDescribableImpl<ECSTaskTemplate> {

    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private final String label;
    /**
     * Docker image
     * @see ContainerDefinition#withImage(String)
     */
    @Nonnull
    private final String image;
    /**
     * Slave remote FS
     */
    @Nullable
    private final String remoteFSRoot;
    /**
     * The number of MiB of memory reserved for the Docker container. If your
     * container attempts to exceed the memory allocated here, the container
     * is killed by ECS.
     *
     * @see ContainerDefinition#withMemory(Integer)
     */
    private final int memory;
    /**
     * The number of <code>cpu</code> units reserved for the container. A
     * container instance has 1,024 <code>cpu</code> units for every CPU
     * core. This parameter specifies the minimum amount of CPU to reserve
     * for a container, and containers share unallocated CPU units with other
     * containers on the instance with the same ratio as their allocated
     * amount.
     *
     * @see ContainerDefinition#withCpu(Integer)
     */
    private final int cpu;
    /**
     * Space delimited list of Docker entry points
     *
     * @see ContainerDefinition#withEntryPoint(String...)
     */
    @CheckForNull
    private String entrypoint;
    /**
      JVM arguments to start slave.jar
     */
    @CheckForNull
    private String jvmArgs;

    private String taskDefinitionArn;
    /**
     * Whether or not to run the container in privileged mode
     */
    private final boolean privileged;

    /**
     * A comma separated list of mount points 'hostPath:containerPath, hostPath2:containerPath2'
     */
    private final String mountPoints;
    /**
     * A comma separated list of volumes 'volumeName:containerPath, volumeName2:containerPath2'
     */
    private final String volumes;


    @DataBoundConstructor
    public ECSTaskTemplate(@Nullable String label, @Nonnull String image, @Nullable String remoteFSRoot, int memory, int cpu, boolean privileged, @Nullable String mountPoints, @Nullable String volumes) {
        this.label = label;
        this.image = image;
        this.remoteFSRoot = remoteFSRoot;
        this.memory = memory;
        this.cpu = cpu;
        this.privileged = privileged;
        this.mountPoints = mountPoints;
        this.volumes = volumes;

    }

    @DataBoundSetter
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = StringUtils.trimToNull(entrypoint);
    }

    @DataBoundSetter
    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = StringUtils.trimToNull(jvmArgs);
    }

    public String getLabel() {
        return label;
    }

    public String getImage() {
        return image;
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    public int getMemory() {
        return memory;
    }

    public int getCpu() {
        return cpu;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public boolean getPrivileged(){
        return privileged;
    }

    public String getMountPoints(){
        return mountPoints;
    }

    public String getVolumes(){
        return volumes;
    }

    public List<MountPoint> getParsedMounts(){
        String mountPoints1 = getMountPoints();
        List<MountPoint> mounts = new ArrayList<MountPoint>();
        if(StringUtils.isEmpty(mountPoints1))
            return mounts;
        for(String mount: mountPoints1.split(",")){
            String[] paths = mount.split(":");
            mounts.add(new MountPoint().withContainerPath(paths[1]).withSourceVolume(paths[0]));
        }
        return mounts;
    }

    public List<Volume> getParsedVolumes(){
        String mountPoints1 = getVolumes();
        List<Volume> mounts = new ArrayList<Volume>();
        if(StringUtils.isEmpty(mountPoints1))
            return mounts;
        for(String mount: mountPoints1.split(",")){
            String[] paths = mount.split(":");
            mounts.add(new Volume().withHost(new HostVolumeProperties().withSourcePath(paths[1])).withName(paths[0]));
        }
        return mounts;
    }

    public String getTaskDefinitionArn() {
        return taskDefinitionArn;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "ECS Slave " + label;
    }

    public RegisterTaskDefinitionRequest asRegisterTaskDefinitionRequest() {
        final ContainerDefinition def = new ContainerDefinition()
                .withName("jenkins-slave")
                .withImage(image)
                .withMemory(memory)
                .withCpu(cpu)
                .withPrivileged(privileged)

                .withMountPoints(getParsedMounts());

        if (entrypoint != null)
            def.withEntryPoint(StringUtils.split(entrypoint));

        if (jvmArgs != null)
            def.withEnvironment(new KeyValuePair()
                .withName("JAVA_OPTS").withValue(jvmArgs))
                .withEssential(true);

        return new RegisterTaskDefinitionRequest()
            .withFamily("jenkins-slave")
            .withContainerDefinitions(def)
            .withVolumes(getParsedVolumes());

    }

    public void setOwer(ECSCloud owner) {
        final AmazonECSClient client = new AmazonECSClient(ECSCloud.getCredentials(owner.getCredentialsId()));
        client.setRegion(ECSCloud.getRegion(owner.getRegionName()));
        if (taskDefinitionArn == null) {
            final RegisterTaskDefinitionRequest req = asRegisterTaskDefinitionRequest();
            final RegisterTaskDefinitionResult result = client.registerTaskDefinition(req);
            taskDefinitionArn = result.getTaskDefinition().getTaskDefinitionArn();
            LOGGER.log(Level.FINE, "Slave {0} - Created Task Definition {1}: {2}", new Object[]{label, taskDefinitionArn, req});
            LOGGER.log(Level.INFO, "Slave {0} - Created Task Definition: {1}", new Object[] { label, taskDefinitionArn });
            getDescriptor().save();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ECSTaskTemplate.class.getName());

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSTaskTemplate> {

        @Override
        public String getDisplayName() {

            return Messages.Template();
        }
    }
}
