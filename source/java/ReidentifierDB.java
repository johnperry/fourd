package com.fourd;

import java.io.File;
import java.util.Hashtable;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.VRs;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.plugin.AbstractPlugin;
import org.rsna.util.FileUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A Plugin to maintain a database of values for reidentifying de-identified DicomObjects.
 */
public class ReidentifierDB extends AbstractPlugin {
	
	static final Logger logger = Logger.getLogger(ReidentifierDB.class);
	
	static final String databaseName = "ReidentifierDB";
	
	static int patientIDTag = DicomObject.getElementTag("PatientID");
	static int patientNameTag = DicomObject.getElementTag("PatientName");
	static int studyInstanceUIDTag = DicomObject.getElementTag("StudyInstanceUID");
	
	RecordManager recman;
	HTree db;
	
	/**
	 * IMPORTANT: When the constructor is called, neither the
	 * pipelines nor the HttpServer have necessarily been
	 * instantiated. Any actions that depend on those objects
	 * must be deferred until the start method is called.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the plugin.
	 */
	public ReidentifierDB(Element element) {
		super(element);
		
		//Open the database
		try {
			File dbFile = new File(root, databaseName);
			recman = JdbmUtil.getRecordManager(dbFile.getAbsolutePath());
			db = JdbmUtil.getHTree(recman, "DB");
		}
		catch (Exception unable) { logger.warn("Unable to open the Reidentifier database."); }

		logger.info(id+" plugin instantiated");
	}

	/**
	 * Stop the plugin.
	 */
	public synchronized void shutdown() {
		if (recman != null) {
			try { recman.commit(); recman.close(); recman = null; }
			catch (Exception ignore) { }
		}
		stop = true;
		logger.info(id+" plugin stopped");
	}

	/**
	 * Insert PHI values from a DicomObject into the database, indexed by the anonymized PatientID.
	 * @param anonPatientID the anonymized value of the PatientID.
	 * @param phiDicomObject the original DicomObject, containing the values to be used for reidentification.
 	 */
	public void insert(DicomObject anonDicomObject, DicomObject phiDicomObject) {
		Hashtable<String,String> table = new Hashtable<String,String>();
		String anonPatientID = anonDicomObject.getElementValue(patientIDTag);
		String phiPatientID = phiDicomObject.getElementValue(patientIDTag);
		String phiPatientName = phiDicomObject.getElementValue(patientNameTag);
		String anonStudyInstanceUID = anonDicomObject.getElementValue(studyInstanceUIDTag);
		String phiStudyInstanceUID = phiDicomObject.getElementValue(studyInstanceUIDTag);
		try {
			table.put("PatientID", phiPatientID);
			table.put("PatientName", phiPatientName);
			table.put(anonStudyInstanceUID, phiStudyInstanceUID);
			db.put(anonPatientID, table);
		}
		catch (Exception ex) {
			logger.warn("Enable to insert "+anonPatientID+"/"+anonPatientID+" into the "+id+" database.");
		}
	}
	
	/**
	 * Modify a DicomObject, replacing anonymized values with the original PHI.
	 * @return the modified DicomObject.
	 */
	@SuppressWarnings("unchecked")
	public DicomObject reidentify(DicomObject dob) throws Exception {

		//Get the anonymized PatientID from the DicomObject
		String anonPatientID = dob.getElementValue(patientIDTag);
		
		//Check that there is an entry in the db for this patient
		Hashtable<String,String> table = (Hashtable<String,String>)db.get(anonPatientID);
		if (table == null) {
			//Not there; throw an Exception so the calling stage 
			//can log the error and quarantine the object.
			throw new Exception("Unable to find "+anonPatientID+" in the "+id+" database.");
		}
		
		//Parse the object and leave the file open so we can use the saveAs method
		File dobFile = dob.getFile();
		DicomObject xdob = new DicomObject(dobFile, true);
		String anonStudyInstanceUID = dob.getElementValue(studyInstanceUIDTag);
		
		//Get the dataset and modify it
		Dataset ds = xdob.getDataset();
		String ptid = table.get("PatientID");
		String ptname = table.get("PatientName");
		String siuid = table.get(anonStudyInstanceUID);
		ds.putXX(patientIDTag, VRs.valueOf("LO"), ptid);
		ds.putXX(patientNameTag, VRs.valueOf("PN"), ptname);
		ds.putXX(studyInstanceUIDTag, VRs.valueOf("UI"), siuid);
		
		//Replace the original file with the modified dataset
		File dir = dobFile.getParentFile();
		File temp = File.createTempFile("TEMP-", ".dcm", dir);
		xdob.saveAs(temp, false, false);
		xdob.close();
		dobFile.delete();
		temp.renameTo(dobFile);
		
		//Parse the modified file and return it.
		dob = new DicomObject(dobFile);
		return dob;		
	}
	
}