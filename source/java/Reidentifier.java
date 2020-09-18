package com.fourd;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.stdstages.ObjectCache;
import org.w3c.dom.Element;

/**
 * A Processor stage that enters DicomObjects into the reidentifier database.
 */
public class Reidentifier extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(Reidentifier.class);

	String reidentifierDBID;
	ReidentifierDB db = null;

	/**
	 * Construct the Reidentifier PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public Reidentifier(Element element) {
		super(element);
		reidentifierDBID = element.getAttribute("reidentifierDBID").trim();
	}

	/**
	 * Start the pipeline stage. When this method is called, all the
	 * stages have been instantiated. We have to get the ObjectCache
	 * stage and ReidentifierDB plugin here to ensure that the Configuration
	 * has been instantiated. (Note: The Configuration constructor has
	 * not finished when the stages are constructed.)
	 */
	public void start() {
		Configuration config = Configuration.getInstance();
		Plugin plugin = config.getRegisteredPlugin(reidentifierDBID);
		if ((plugin != null) && (plugin instanceof ReidentifierDB)) {
			db = (ReidentifierDB)plugin;
		}
		else logger.warn(name+": reidentifierDBID \""+reidentifierDBID+"\" does not reference a ReidentifierDB");
	}

	/**
	 * Reidentify DicomObjects using the ReidentifierDB.
	 * @param fileObject the object to reidentify.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((db != null) && (fileObject instanceof DicomObject)) {
			try {
				DicomObject dob = (DicomObject)fileObject;
				dob = db.reidentify(dob);
				fileObject = dob;
			}
			catch (Exception ex) {
				logger.warn("Unable to reidentify "+fileObject.getFile(), ex);
				if (quarantine != null) quarantine.insert(fileObject);
				return null;
			}
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

}