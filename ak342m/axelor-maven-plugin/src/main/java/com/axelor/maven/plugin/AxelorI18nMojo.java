package com.axelor.maven.plugin;

import com.axelor.tools.i18n.I18nExtractor;
import java.io.File;
import java.nio.file.Paths;
import org.apache.maven.plugin.MojoExecutionException;

public class AxelorI18nMojo
  extends AbstractAxelorMojo
{
  private I18nExtractor extractor = new I18nExtractor();
  
  public void execute() throws MojoExecutionException
  {
    if (isAxelorModule()) {
      this.extractor.extract(Paths.get(this.baseDirectory.getPath(), new String[0]),false,false);
    }
  }
}
