package net.java.nboglpack.joglproject;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.apache.tools.ant.module.api.support.ActionUtils;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;

public class ProjectWizardIterator implements WizardDescriptor.InstantiatingIterator
{
	
	private static final long serialVersionUID = 1L;
	private transient String createLabel="LBL_CreateProjectStep";
	private transient int index;
	private transient WizardDescriptor.Panel[] panels;
	private transient WizardDescriptor wiz;
	
	public ProjectWizardIterator()
	{}
	
	public static ProjectWizardIterator createIterator()
	{
		return new ProjectWizardIterator();
	}
	
	private WizardDescriptor.Panel[] createPanels()
	{
		return new WizardDescriptor.Panel[] {
			new ProjectWizardPanel(),
		};
	}
	
	private String[] createSteps()
	{
		return new String[] {
			NbBundle.getMessage(ProjectWizardIterator.class, createLabel)
		};
	}
	
	public Set/*<FileObject>*/ instantiate() throws IOException
	{
		FileObject template = Templates.getTemplate(wiz);
		String demo=(String)template.getAttribute("demo");
		createLabel="LBL_Create_"+demo;

		Set resultSet = new LinkedHashSet();
		File dirF = FileUtil.normalizeFile((File) wiz.getProperty("projdir"));
		dirF.mkdirs();
		
		FileObject dir = FileUtil.toFileObject(dirF);
		unZipFile(template.getInputStream(), dir);
		String[] opt= new String[] { "windows-i586", "linux-i586", "linux-amd64", "macosx-universal", "macosx-ppc", "solaris-sparc", "solaris-sparcv9", "solaris-i586" };
		callInitScript(
			dirF,
			(String)wiz.getProperty("name"),
			(String)wiz.getProperty("projpackage"),
			(String)wiz.getProperty("projclass"),
			demo,
			(String)template.getAttribute("archive"),
			(String)template.getAttribute("srcPath"),
			(String)template.getAttribute("extraFiles"),
			opt[((Integer)wiz.getProperty("platform")).intValue()],
			(String)template.getAttribute("includes"),
			(String)template.getAttribute("excludes")
		);
		// Always open top dir as a project:
		resultSet.add(dir);
		// Look for nested projects to open as well:
		Enumeration e = dir.getFolders(true);
		while (e.hasMoreElements())
		{
			FileObject subfolder = (FileObject) e.nextElement();
			if (ProjectManager.getDefault().isProject(subfolder))
			{
				resultSet.add(subfolder);
			}
		}
		
		File parent = dirF.getParentFile();
		if (parent != null && parent.exists())
		{
			ProjectChooser.setProjectsFolder(parent);
		}
		
		return resultSet;
	}
	
	public void initialize(WizardDescriptor wiz)
	{
		this.wiz = wiz;
		FileObject template = Templates.getTemplate(wiz);
		String projectPackage= (String) template.getAttribute("projectPackage");
		if(projectPackage!=null && !"".equals(projectPackage.trim()))
			this.wiz.putProperty("projpackage",projectPackage);
		this.wiz.putProperty("name",template.getAttribute("projectName"));
		index = 0;
		panels = createPanels();
		// Make sure list of steps is accurate.
		String[] steps = createSteps();
		for (int i = 0; i < panels.length; i++)
		{
			Component c = panels[i].getComponent();
			if (steps[i] == null)
			{
				// Default step name to component name of panel.
				// Mainly useful for getting the name of the target
				// chooser to appear in the list of steps.
				steps[i] = c.getName();
			}
			if (c instanceof JComponent)
			{ // assume Swing components
				JComponent jc = (JComponent) c;
				// Step #.
				jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i));
				// Step name (actually the whole list for reference).
				jc.putClientProperty("WizardPanel_contentData", steps);
			}
		}
	}
	
	public void uninitialize(WizardDescriptor wiz)
	{
		this.wiz.putProperty("projdir",null);
		this.wiz.putProperty("name",null);
		this.wiz.putProperty("projpackage",null);
		this.wiz.putProperty("projclass",null);
		this.wiz = null;
		panels = null;
	}
	
	public String name()
	{
		return MessageFormat.format("{0} of {1}",
			new Object[] {new Integer(index + 1), new Integer(panels.length)});
	}
	
	public boolean hasNext()
	{
		return index < panels.length - 1;
	}
	
	public boolean hasPrevious()
	{
		return index > 0;
	}
	
	public void nextPanel()
	{
		if (!hasNext())
		{
			throw new NoSuchElementException();
		}
		index++;
	}
	
	public void previousPanel()
	{
		if (!hasPrevious())
		{
			throw new NoSuchElementException();
		}
		index--;
	}
	
	public WizardDescriptor.Panel current()
	{
		return panels[index];
	}
	
	// If nothing unusual changes in the middle of the wizard, simply:
	public final void addChangeListener(ChangeListener l)
	{}
	public final void removeChangeListener(ChangeListener l)
	{}
	
	private static void unZipFile(InputStream source, FileObject projectRoot) throws IOException
	{
		try
		{
			ZipInputStream str = new ZipInputStream(source);
			ZipEntry entry;
			while ((entry = str.getNextEntry()) != null)
			{
				if (entry.isDirectory())
				{
					FileUtil.createFolder(projectRoot, entry.getName());
				}
				else
				{
					FileObject fo = FileUtil.createData(projectRoot, entry.getName());
					FileLock lock = fo.lock();
					try
					{
						OutputStream out = fo.getOutputStream(lock);
						try
						{
							FileUtil.copy(str, out);
						}
						finally
						{
							out.close();
						}
					}
					finally
					{
						lock.releaseLock();
					}
				}
			}
		}
		finally
		{
			source.close();
		}
	}
	
	private void callInitScript(File dir,String projectName,String packageName,String mainClass,String demo,String archive, String srcPath, String extraFiles, String platform, String includes, String excludes)
	{
		if(srcPath==null) srcPath="";
		srcPath.trim();
		if(!(srcPath.length()==0) && !srcPath.endsWith("/"))
		{
			srcPath+="/";
		}
		if(includes==null) includes="";
		if(excludes==null) excludes="";
		File demos=InstalledFileLocator.getDefault().locate("jogl-project/jogl-demos-src.zip",null,false);
		File archiveFile=InstalledFileLocator.getDefault().locate("jogl-project/"+archive,null,false);
		Properties props=new Properties();
		props.setProperty("project.root",dir.getAbsolutePath());
		props.setProperty("project.name",projectName);
		props.setProperty("project.packageName",packageName);
		props.setProperty("project.packagePath",packageName.replace('.','/'));
		props.setProperty("project.mainClass",mainClass);

		String demoPackage= demo.replaceAll("^(.*?)\\.[^\\.]+$","$1");
		String demoPath= demoPackage.replace('.','/');
		String demoClass= demo.replaceAll("^.*?\\.([^\\.]+)$","$1");
		props.setProperty("template.demoPath",demoPath);
		props.setProperty("template.demoPackage",demoPackage);
		props.setProperty("template.demoClass",demoClass);
		props.setProperty("template.demoPath.regex",demoPath.replaceAll("([\\.\\/])","\\\\$1"));
		props.setProperty("template.demoPackage.regex",demoPackage.replaceAll("([\\.\\/])","\\\\$1"));
		props.setProperty("template.demoClass.regex",demoClass.replaceAll("([\\.\\/])","\\\\$1"));
		props.setProperty("template.archive",archiveFile.getAbsolutePath());
		props.setProperty("template.srcPath",srcPath);
		props.setProperty("template.includes",includes);
		props.setProperty("template.excludes",excludes);
		props.setProperty("natives.platform",platform);
		if(extraFiles!=null && !extraFiles.trim().equals(""))
		{
			String mergedExtraFiles=null;
			StringTokenizer tokenizer= new StringTokenizer(extraFiles,", ");
			while (tokenizer.hasMoreTokens())
			{
				String token= tokenizer.nextToken();
				if(mergedExtraFiles==null)
					mergedExtraFiles=srcPath+token;
				else
					mergedExtraFiles+=","+srcPath+token;
			}
			props.setProperty("template.extraFiles",mergedExtraFiles);
		}
		try
		{
			File scriptFile=InstalledFileLocator.getDefault().locate("jogl-project/initJoglProject.xml",null,false);
			FileObject script= FileUtil.toFileObject(scriptFile);
			ExecutorTask task= ActionUtils.runTarget(script,new String[]{"initProject"},props);
			task.waitFinished();
			Thread.sleep(1000);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
}