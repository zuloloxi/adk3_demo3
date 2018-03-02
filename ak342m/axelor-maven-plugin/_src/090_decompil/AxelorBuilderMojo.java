package com.axelor.maven.plugin;

import com.axelor.tools.x2j.Extender;
import com.axelor.tools.x2j.Generator;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

public class AxelorBuilderMojo
  extends AbstractMojo
{
  private MavenProject project;
  private File baseDirectory;
  private File targetDirectory;
  private static final Set<String> ignoreNames = ;
  
  static { ignoreNames.add("axelor-cglib");
    ignoreNames.add("axelor-common");
  }
  
  public void execute() throws MojoExecutionException
  {
    try {
      genInfo();
    }
    catch (Exception e) {}
    try {
      genCode();
    }
    catch (IOException e) {}
  }
  
  private void genCode() throws IOException
  {
    String searchPath = this.baseDirectory.getPath() + "/src/main/resources/domains";
    
    File path = null;
    Generator gen = null;
    
    path = new File(searchPath);
    if (path.exists()) {
      gen = new Generator(this.baseDirectory, this.targetDirectory);
      generate(gen);
      return;
    }
    
    String aggregator = this.project.getProperties().getProperty("axelor.aggregator.module");
    if ((aggregator == null) || ("".equals(aggregator.trim()))) {
      aggregator = "axelor-objects";
    }
    
    path = new File(this.baseDirectory.getPath(), "/" + aggregator);
    if (path.exists()) {
      gen = new Extender(this.baseDirectory, this.targetDirectory, aggregator);
      generate(gen);
    }
  }
  
  private void generate(Generator gen) throws IOException
  {
    String outputPath = this.targetDirectory.getPath() + "/src-gen";
    
    getLog().info("Generating Models: " + this.baseDirectory);
    
    gen.getLog().addListener(new AxelorBuilderMojo.1(this));
    
    gen.start();
    
    getLog().info(String.format("Source directory: %s added.", new Object[] { outputPath }));
    
    File target = new File(outputPath);
    this.project.addCompileSourceRoot(target.getAbsolutePath());
  }
  
  private void genInfo() throws FileNotFoundException
  {
    String searchPath = this.baseDirectory.getPath() + "/src/main/resources/module.properties";
    String outputPath = this.targetDirectory.getPath() + "/src-gen/module.properties";
    
    File search = new File(searchPath);
    if (search.exists()) {
      return;
    }
    
    File output = new File(outputPath);
    if ((output.exists()) && (this.project.getFile().lastModified() < output.lastModified())) {
      return;
    }
    try
    {
      Files.createParentDirs(output);
    } catch (IOException e) {
      getLog().info("Error generating module.properties", e);
      return;
    }
    
    getLog().info("Generating: " + outputPath);
    
    BufferedWriter writer = Files.newWriter(output, Charsets.UTF_8);
    PrintWriter printer = new PrintWriter(writer);
    
    String description = this.project.getDescription();
    if ((this.project.getParent() != null) && (description.equals(this.project.getParent().getDescription()))) {
      description = "";
    }
    
    description = description.replace("\n", "\\\n");
    
    printer.println("name = " + this.project.getArtifactId());
    printer.println("version = " + this.project.getVersion());
    printer.println();
    printer.println("title = " + this.project.getName());
    printer.println("description = " + description);
    printer.println();
    
    if ("true".equals(this.project.getProperties().getProperty("axelor.module.removable"))) {
      printer.println("removable = true");
    } else {
      printer.println("removable = false");
    }
    
    List<String> deps = Lists.newArrayList();
    for (Object d : this.project.getDependencies()) {
      Dependency dependency = (Dependency)d;
      if ((dependency.getGroupId().startsWith("com.axelor")) && (!"test".equals(dependency.getScope())) && (!ignoreNames.contains(dependency.getArtifactId())))
      {
        deps.add("\t" + dependency.getArtifactId());
      }
    }
    
    printer.println();
    
    if (deps.size() > 0) {
      printer.println("depends = \\");
      printer.println(Joiner.on(" \\\n").join(deps));
    }
    
    printer.close();
  }
}
