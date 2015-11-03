package de.intranda.goobi.plugins;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

import lombok.Data;
import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.intranda.counterscript.model.MetadataInformation;
import de.sub.goobi.helper.FacesContextHelper;

@PluginImplementation
public @Data class CounterscriptPlugin implements IAdministrationPlugin, IPlugin {

    private static final String TITLE = "Counterscript";

    private static final SimpleDateFormat dateConverter = new SimpleDateFormat("yyyy-MM-dd");

    private Date startDate = null;
    private Date endDate = null;
    private boolean includeOutdatedData = false;
    private String currentNumber;

    private List<MetadataInformation> dataList = null;
    private List<MetadataInformation> detailList = null;

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getDescription() {
        return TITLE;
    }

    @Override
    public String getGui() {
        return "/uii/administration_Counterscript.xhtml";
    }

    public void getData() {
        Client client = ClientBuilder.newClient();
        WebTarget base = client.target("http://localhost:8081/Counterscript/api/");
        WebTarget xml = base.path("xml");
        if (includeOutdatedData) {
            xml = xml.path("withinactive");
        }

        if (startDate != null && endDate != null) {
            String start = dateConverter.format(startDate);
            String end = dateConverter.format(endDate);
            xml = xml.path(start).path(end);
        }

        dataList = xml.request().get(new GenericType<List<MetadataInformation>>() {
        });

    }

    public void resetData() {

        detailList = null;
    }

    public void getDetails() {
        Client client = ClientBuilder.newClient();
        WebTarget base = client.target("http://localhost:8081/Counterscript/api");
        WebTarget xml = base.path("xml");
        xml = xml.path("bnumber").path(currentNumber);
        detailList = xml.request().get(new GenericType<List<MetadataInformation>>() {
        });
    }

    public void download() {

        Client client = ClientBuilder.newClient();
        WebTarget base = client.target("http://localhost:8081/Counterscript/api/");
        WebTarget csv = base.path("csv");
        if (includeOutdatedData) {
            csv = csv.path("withinactive");
        }

        if (startDate != null && endDate != null) {
            String start = dateConverter.format(startDate);
            String end = dateConverter.format(endDate);
            csv = csv.path(start).path(end);
        }
        HttpServletResponse response = (HttpServletResponse) FacesContextHelper.getCurrentFacesContext().getExternalContext().getResponse();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"counterscript.csv\"");
        try {
            response.sendRedirect(csv.getUri().toString());
        } catch (IOException e) {
        }
    }
    
    
    
    public void getJson() {
        Client client = ClientBuilder.newClient();
        WebTarget base = client.target("http://localhost:8081/Counterscript/api/");
        WebTarget json = base.path("json");
        if (includeOutdatedData) {
            json = json.path("withinactive");
        }

        if (startDate != null && endDate != null) {
            String start = dateConverter.format(startDate);
            String end = dateConverter.format(endDate);
            json = json.path(start).path(end);
        }

        HttpServletResponse response = (HttpServletResponse) FacesContextHelper.getCurrentFacesContext().getExternalContext().getResponse();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"counterscript.csv\"");
        try {
            response.sendRedirect(json.getUri().toString());
        } catch (IOException e) {
        }

    }
}
