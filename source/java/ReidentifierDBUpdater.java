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
public class ReidentifierDBUpdater extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(ReidentifierDBUpdater.class);

	String objectCacheID;
	String reidentifierDBID;
	ObjectCache objectCache = null;
	ReidentifierDB db = null;

	/**
	 * Construct the ReidentifierDBUpdater PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ReidentifierDBUpdater(Element element) {
		super(element);
		objectCacheID = element.getAttribute("cacheID").trim();
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
		if (!objectCacheID.equals("")) {
			PipelineStage stage = config.getRegisteredStage(objectCacheID);
			if (stage != null) {
				if (stage instanceof ObjectCache) {
					objectCache = (ObjectCache)stage;
				}
				else logger.warn(name+": cacheID \""+objectCacheID+"\" does not reference an ObjectCache");
			}
			else logger.warn(name+": cacheID \""+objectCacheID+"\" does not reference any PipelineStage");
		}

		Plugin plugin = config.getRegisteredPlugin(reidentifierDBID);
		if ((plugin != null) && (plugin instanceof ReidentifierDB)) {
			db = (ReidentifierDB)plugin;
		}
		else logger.warn(name+": reidentifierDBID \""+reidentifierDBID+"\" does not reference a ReidentifierDB");
	}

	/**
	 * Insert objects into the ReidentifierDB as they are received by the stage.
	 * @param fileObject the object to enter into the db.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((db != null) && (fileObject instanceof DicomObject)) {
			
			DicomObject dob = (DicomObject)fileObject;

			//Get the cached object, if possible
			DicomObject cachedObject = null;
			if (objectCache != null) {
				FileObject fob = objectCache.getCachedObject();
				if ( (fob instanceof DicomObject) ) cachedObject = (DicomObject)fob;
			}

			if (cachedObject != null) db.insert(dob/*anon*/, cachedObject/*phi*/);
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

}