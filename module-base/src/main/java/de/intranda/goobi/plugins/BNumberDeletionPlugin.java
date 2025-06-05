package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.StepManager;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
public class BNumberDeletionPlugin implements IAdministrationPlugin, IPlugin {

    private static final long serialVersionUID = -4478973187665181921L;

    private String bnumber;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private BeanHelper bHelper = new BeanHelper();

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
        if (StringUtils.isNotBlank(bnumber)) {
            bnumber = bnumber.trim();
        }
        this.bnumber = bnumber;
    }

    private Process getProcessId() {

        List<Process> processlist = ProcessManager.getProcesses("prozesse.titel", "prozesse.titel like '%" + bnumber + "'", null);
        for (Process proc : processlist) {
            if (proc.getTitel().endsWith(bnumber)) {
                return proc;
            }
        }

        Process process = null;
        List<Integer> processIdList = MetadataManager.getProcessesWithMetadata("CatalogIDDigital", bnumber);
        Integer processId = null;
        if (processIdList.size() > 1) {
            for (Integer id : processIdList) {
                // found more than one, maybe we have an anchor identifier
                List<StringPair> spl = MetadataManager.getMetadata(id);
                for (StringPair sp : spl) {
                    if ("CatalogIDDigital".equals(sp.getOne().trim())) {
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
            } catch (SwapException | IOException e) {

            }

            ProcessManager.deleteProcess(process);
            Helper.setMeldung("Deleted process with b-number " + bnumber);
        }

        return "";
    }

    public String deleteFromPlayer() {
        String identifier = bnumber;
        String anchorIdentifier = null;
        boolean mmo = false;
        if (bnumber.contains("_")) {
            // MMO file
            anchorIdentifier = identifier.substring(0, identifier.lastIndexOf("_"));
            mmo = true;
        }

        // read bag information

        Process template = ProcessManager.getProcessByExactTitle("volume_deletion_from_storage_service");
        Process process = new Process();
        process.setTitel(dateFormat.format(new Date()) + "_" + bnumber + "_deletion");

        process.setIstTemplate(false);
        process.setInAuswahllisteAnzeigen(false);
        process.setProjekt(template.getProjekt());
        process.setRegelsatz(template.getRegelsatz());
        process.setDocket(template.getDocket());

        bHelper.SchritteKopieren(template, process);
        bHelper.EigenschaftenKopieren(template, process);

        // create simple mets file - monograph or mmo/monographic manifestation
        // add identifier fields

        try {
            ProcessManager.saveProcess(process);
        } catch (DAOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        try {
            Prefs prefs = process.getRegelsatz().getPreferences();
            MetsMods metsMods = new MetsMods(prefs);
            DigitalDocument digitalDocument = new DigitalDocument();
            metsMods.setDigitalDocument(digitalDocument);
            DocStruct physical = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
            digitalDocument.setPhysicalDocStruct(physical);
            MetadataType mdt = prefs.getMetadataTypeByName("CatalogIDDigital");
            if (mmo) {
                DocStruct anchor = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("MultipleManifestation"));
                DocStruct volume = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("MonographManifestation"));
                digitalDocument.setLogicalDocStruct(anchor);
                anchor.addChild(volume);

                Metadata identifierAnchor = new Metadata(mdt);
                identifierAnchor.setValue(anchorIdentifier);
                anchor.addMetadata(identifierAnchor);

                Metadata identifierVolume = new Metadata(mdt);
                identifierVolume.setValue(identifier);
                volume.addMetadata(identifierVolume);

            } else {
                DocStruct volume = digitalDocument.createDocStruct(prefs.getDocStrctTypeByName("Monograph"));
                digitalDocument.setLogicalDocStruct(volume);
                Metadata identifierVolume = new Metadata(mdt);
                identifierVolume.setValue(identifier);
                volume.addMetadata(identifierVolume);
            }

            try {
                process.writeMetadataFile(metsMods);
            } catch (WriteException | IOException | SwapException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } catch (PreferencesException | TypeNotAllowedForParentException | TypeNotAllowedAsChildException | MetadataTypeNotAllowedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        List<Step> steps = StepManager.getStepsForProcess(process.getId());
        for (Step s : steps) {
            if (StepStatus.OPEN.equals(s.getBearbeitungsstatusEnum()) && s.isTypAutomatisch()) {
                ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
                myThread.start();
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
