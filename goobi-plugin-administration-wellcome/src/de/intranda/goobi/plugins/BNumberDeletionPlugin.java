package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
//import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class BNumberDeletionPlugin implements IAdministrationPlugin, IPlugin {

    private String bnumber;

    private String destination = "/mnt/export/";

    private Namespace metsNamespace = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");

    private Namespace xlinkNamespace = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getTitle() {
        return "b-number deletion";
    }


    public String getDescription() {
        return "b-number deletion";
    }

    @Override
    public String getGui() {
        return "/uii/administration_ProcessDeletion.xhtml";
    }

    public String getBnumber() {
        return bnumber;
    }

    public void setBnumber(String bnumber) {
        this.bnumber = bnumber;
    }

    private Process getProcessId() {
        Process process = null;
        List<Integer> processIdList = MetadataManager.getProcessesWithMetadata("CatalogIDDigital", bnumber);
        Integer processId = null;
        if (processIdList.size() > 1) {
            for (Integer id : processIdList) {
                // found more than one, maybe we have an anchor identifier
                List<StringPair> spl = MetadataManager.getMetadata(id);
                for (StringPair sp : spl) {
                    if (sp.getOne().trim().equals("CatalogIDDigital")) {
                        String value = sp.getTwo();
                        String[] parts = value.split(";");
                        int maxLength = 0;
                        String longestString = null;
                        for (String s : parts) {
                            if (s.length() > maxLength) {
                                maxLength = s.length();
                                longestString = s;
                            }
                        }
                        if (longestString.equals(bnumber)) {
                            processId = id;
                        } else {
                            Helper.setFehlerMeldung(bnumber
                                    + " looks like the identifier of an anchor file. You have to delete the volumes. The anchor file gets removed autoamtically after the last volume was deleted.");
                        }

                    }
                }
            }
        }
        if (processIdList.size() == 1) {
            processId = processIdList.get(0);
        }
        if (processId != null) {
            process = ProcessManager.getProcessById(processId);
        }

        return process;
    }

    public String deleteFromGoobi() {
        Process process = getProcessId();
        if (process == null) {
            Helper.setFehlerMeldung("Cannot find process with identifier " + bnumber);
            return "";
        } else {
            try {
                StorageProvider.getInstance().deleteDir(Paths.get(process.getProcessDataDirectory()));

                //                Helper.deleteDir(new File(process.getProcessDataDirectory()));
            } catch (SwapException | DAOException | IOException | InterruptedException e) {

            }

            ProcessManager.deleteProcess(process);
            Helper.setMeldung("Deleted process with b-number " + bnumber);
        }

        return "";
    }

    public String deleteFromPlayer() {
        String identifier = bnumber;
        boolean mmo = false;
        if (bnumber.contains("_")) {
            // MMO file
            identifier = identifier.substring(0, identifier.lastIndexOf("_"));
            mmo = true;
        }

        String first = identifier.substring(identifier.length() - 1);
        String second = identifier.substring(identifier.length() - 2, identifier.length() - 1);
        String third = identifier.substring(identifier.length() - 3, identifier.length() - 2);
        String fourth = identifier.substring(identifier.length() - 4, identifier.length() - 3);

        File destinationFolder = new File(destination + first + File.separator + second + File.separator + third + File.separator + fourth);

        File fileToDelete = new File(destinationFolder, bnumber + ".xml");
        if (!fileToDelete.exists()) {

            Helper.setFehlerMeldung("File " + bnumber + ".xml was not found in player location.");
            return "";
        } else {
            try {
                Document doc = new SAXBuilder().build(fileToDelete);
                List<Element> structMaps = doc.getRootElement().getChildren("structMap", metsNamespace);

                if (structMaps.size() == 1) {
                    Helper.setFehlerMeldung("File " + bnumber + ".xml is an anchor file. "
                            + "You have to remove the volumes, the anchor file gets removed automatically after all volumes are deleted.");
                    return "";
                }

            } catch (JDOMException | IOException e) {

            }

            try {
                FileUtils.forceDelete(fileToDelete);
                Helper.setMeldung("File " + bnumber + ".xml was deleted from player location");
            } catch (IOException e) {
                Helper.setFehlerMeldung("Cannot delete file " + bnumber + ".xml", e);
            }
        }
        if (mmo) {
            // update anchor file or remove it
            File anchorFile = new File(destinationFolder, identifier + ".xml");

            try {
                Document doc = new SAXBuilder().build(anchorFile);
                Element structMap = doc.getRootElement().getChild("structMap", metsNamespace);
                Element mmoElement = structMap.getChild("div", metsNamespace);
                List<Element> volumes = mmoElement.getChildren("div", metsNamespace);
                if (volumes.size() == 1) {
                    // last volume, remove anchor as well
                    FileUtils.forceDelete(anchorFile);
                } else {
                    // remove volume from anchor
                    List<Element> volumesToKeep = new ArrayList<>();
                    for (Element volume : volumes) {
                        Element mptr = volume.getChild("mptr", metsNamespace);
                        String volumeIdentifier = mptr.getAttributeValue("href", xlinkNamespace);
                        if (!volumeIdentifier.equals(bnumber)) {

                            volumesToKeep.add(volume);
                        }
                    }

                    mmoElement.removeContent();
                    mmoElement.addContent(volumesToKeep);
                    // save
                    XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
                    FileOutputStream output = new FileOutputStream(anchorFile);
                    outputter.output(doc, output);
                }

            } catch (JDOMException | IOException e) {

            }

        }
        // delete alto as well
        File altoFolder = new File(destinationFolder, bnumber + "_alto");
        if (altoFolder.exists()) {
            try {
                FileUtils.deleteDirectory(altoFolder);
            } catch (IOException e) {
            }
        }
        return "";
    }

    public String deleteFromGoobiAndPlayer() {
        deleteFromPlayer();
        deleteFromGoobi();
        return "";
    }
}
