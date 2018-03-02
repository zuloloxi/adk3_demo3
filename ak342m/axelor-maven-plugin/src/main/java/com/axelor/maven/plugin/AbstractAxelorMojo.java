package com.axelor.maven.plugin;

import java.io.File;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.traversal.CollectingDependencyNodeVisitor;

public abstract class AbstractAxelorMojo
  extends AbstractMojo
{
  private static final String AXELOR_CORE = "axelor-core";
  protected MavenProject project;
  protected File baseDirectory;
  protected File targetDirectory;
  protected ArtifactFactory artifactFactory;
  protected ArtifactMetadataSource artifactMetadataSource;
  protected ArtifactCollector artifactCollector;
  protected DependencyTreeBuilder treeBuilder;
  protected ArtifactRepository localRepository;
  
  protected boolean isAxelorModule()
  {
    if ("axelor-core".equals(this.project.getArtifactId())) return true;
    try {
      ArtifactFilter artifactFilter = new ScopeArtifactFilter(null);
      DependencyNode rootNode = this.treeBuilder.buildDependencyTree(this.project, this.localRepository, this.artifactFactory, this.artifactMetadataSource, artifactFilter, this.artifactCollector);
      
      CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
      rootNode.accept(visitor);
      
      List<DependencyNode> nodes = visitor.getNodes();
      for (DependencyNode node : nodes) {
        if ("axelor-core".equals(node.getArtifact().getArtifactId())) return true;
      }
    }
    catch (Exception e) {}
    return false;
  }
}
