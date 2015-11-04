package de.intranda.goobi.plugins;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    private int NUMBER_OF_OBJECTS_PER_PAGE = 10;

    private int pageNo = 0;

    private int imageIndex = 0;

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

    public List<MetadataInformation> getPaginatorList() {
        List<MetadataInformation> subList = new ArrayList<MetadataInformation>();
        if (dataList.size() > (pageNo * NUMBER_OF_OBJECTS_PER_PAGE) + NUMBER_OF_OBJECTS_PER_PAGE) {
            subList = dataList.subList(pageNo * NUMBER_OF_OBJECTS_PER_PAGE, (pageNo * NUMBER_OF_OBJECTS_PER_PAGE) + NUMBER_OF_OBJECTS_PER_PAGE);
        } else {
            subList = dataList.subList(pageNo * NUMBER_OF_OBJECTS_PER_PAGE, dataList.size());
        }

        return subList;
    }

    public String cmdMoveFirst() {
        if (this.pageNo != 0) {
            this.pageNo = 0;
            getPaginatorList();
        }
        return "";
    }

    public String cmdMovePrevious() {
        if (!isFirstPage()) {
            this.pageNo--;
            getPaginatorList();
        }
        return "";
    }

    public String cmdMoveNext() {
        if (!isLastPage()) {
            this.pageNo++;
            getPaginatorList();
        }
        return "";
    }

    public String cmdMoveLast() {
        if (this.pageNo != getLastPageNumber()) {
            this.pageNo = getLastPageNumber();
            getPaginatorList();
        }
        return "";
    }

    public void setTxtMoveTo(int neueSeite) {
        if ((this.pageNo != neueSeite - 1) && neueSeite > 0 && neueSeite <= getLastPageNumber() + 1) {
            this.pageNo = neueSeite - 1;
            getPaginatorList();
        }
    }

    public int getTxtMoveTo() {
        return this.pageNo + 1;
    }

    public int getLastPageNumber() {
        int ret = new Double(Math.floor(this.dataList.size() / NUMBER_OF_OBJECTS_PER_PAGE)).intValue();
        if (this.dataList.size() % NUMBER_OF_OBJECTS_PER_PAGE == 0) {
            ret--;
        }
        return ret;
    }

    public boolean isFirstPage() {
        return this.pageNo == 0;
    }

    public boolean isLastPage() {
        return this.pageNo >= getLastPageNumber();
    }

    public boolean hasNextPage() {
        return this.dataList.size() > NUMBER_OF_OBJECTS_PER_PAGE;
    }

    public boolean hasPreviousPage() {
        return this.pageNo > 0;
    }

    public Long getPageNumberCurrent() {
        return Long.valueOf(this.pageNo + 1);
    }

    public Long getPageNumberLast() {
        return Long.valueOf(getLastPageNumber() + 1);
    }

    public int getSizeOfImageList() {
        return dataList.size();
    }

}
