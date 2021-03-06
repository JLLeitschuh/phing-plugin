/*
 * The MIT License
 * 
 * Copyright (c) 2008-2013, Jenkins project, Seiji Sogabe
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.phing;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Phing Installation.
 *
 * @author Seiji Sogabe
 */
public final class PhingInstallation extends ToolInstallation
        implements EnvironmentSpecific<PhingInstallation>, NodeSpecific<PhingInstallation>, Serializable {

    private static final String PHING_EXEC_NAME_FOR_UNIX = "phing";

    private static final String PHING_EXEC_NAME_FOR_WINDOWS = "phing.bat";

    private static final long serialVersionUID = 1L;

    /**
     * PHING_HOME where phing has been installed.
     */
    @Deprecated
    private transient String phingHome;

    /**
     * PHP command.
     */
    private final String phpCommand;

    public static String getExecName(final Launcher launcher) {
        String execName;
        if (launcher.isUnix()) {
            execName = PHING_EXEC_NAME_FOR_UNIX;
        } else {
            execName = PHING_EXEC_NAME_FOR_WINDOWS;
        }
        return execName;
    }

    @Deprecated
    public String getPhingHome() {
        return phingHome;
    }

    public String getPhpCommand() {
        return phpCommand;
    }

    @DataBoundConstructor
    public PhingInstallation(String name, String home, String phpCommand, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
        this.phpCommand = Util.fixEmptyAndTrim(phpCommand);
    }

    public String getExecutable(final Launcher launcher) throws IOException, InterruptedException {
        final String execName = getExecName(launcher);
        return launcher.getChannel().call(new Callable<String, IOException>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String call() throws IOException {
                File exe = new File(new File(getHome(), "bin"), execName);
                if (exe.exists()) {
                    return exe.getPath();
                }
                exe = new File(new File(getHome()), execName);
                if (exe.exists()) {
                    return exe.getPath();
                }
                return execName;
            }

        });
    }

    @Override
    public PhingInstallation forEnvironment(EnvVars ev) {
        return new PhingInstallation(getName(), ev.expand(getHome()), getPhpCommand(), getProperties().toList());
    }

    @Override
    public PhingInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new PhingInstallation(getName(), translateFor(node, log), getPhpCommand(), getProperties().toList());
    }

    public EnvVars getEnvVars() {
        EnvVars env = new EnvVars();
        if (phpCommand != null) {
            env.put("PHP_COMMAND", phpCommand);
        }
        if (getHome() != null) {
            env.put("PHING_HOME", getHome());
            env.put("PHING_CLASSPATH", getHome() + File.separator + "classes");
        }
        return env;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(PhingInstallation.class);
    }

    public static PhingInstallation[] getInstallations() {
        return ((DescriptorImpl) Jenkins.getInstance().getDescriptor(PhingInstallation.class)).getInstallations();
    }

    /**
     * Backward compatibility. 
     */
    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void onLoaded() {
        PhingInstallation[] installations = getInstallations();
        if (installations != null && installations.length > 0) {
            return;
        }

        PhingDescriptor phingDescriptor = (PhingDescriptor) Jenkins.getInstance().getDescriptor(PhingBuilder.class);
        PhingInstallation[] olds = phingDescriptor.getOldInstallations();
        if (olds == null) {
            return;
        }
        phingDescriptor.clearOldInstallationsAndSave();

        PhingInstallation[] news = new PhingInstallation[olds.length];
        for (int i = 0; i < olds.length; i++) {
            PhingInstallation old = olds[i];
            news[i] = new PhingInstallation(old.getName(), old.getPhingHome(), old.getPhpCommand(), old.getProperties().toList());
        }
        DescriptorImpl descriptorImpl = (DescriptorImpl) Jenkins.getInstance().getDescriptor(PhingInstallation.class);
        descriptorImpl.setInstallations(news);
        descriptorImpl.save();
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<PhingInstallation> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Phing";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            super.configure(req, json);
            save();
            return true;
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.emptyList();
        }
    }
}
