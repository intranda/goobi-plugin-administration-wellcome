package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.goobi.beans.User;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.jfree.util.Log;

import de.intranda.counterscript.model.MetadataInformation;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.Helper;
import jakarta.faces.context.ExternalContext;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import lombok.Cleanup;
import lombok.Data;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public @Data class CounterscriptPlugin implements IAdministrationPlugin, IPlugin {

    private static final long serialVersionUID = 2320742556608952269L;
    private String restUrl = "http://localhost:8081/Counterscript/api/";
    private String token;

    private static final String TITLE = "Counterscript";
    private static final String PLUGIN_TITLE = "intranda_counterscript";
    private static final SimpleDateFormat dateConverter = new SimpleDateFormat("yyyy-MM-dd");

    private Date startDate = null;
    private Date endDate = null;
    private boolean includeOutdatedData = false;
    private String currentNumber;

    private transient List<MetadataInformation> dataList = null;
    private transient List<MetadataInformation> detailList = null;

    private int entriesPerPage = 10;

    private int pageNo = 0;

    private int imageIndex = 0;

    public CounterscriptPlugin() {
        User user = Helper.getCurrentUser();
        if (user != null) {
            entriesPerPage = user.getTabellengroesse();
        }
        restUrl = ConfigPlugins.getPluginConfig(PLUGIN_TITLE).getString("rest_url", "http://localhost:8080/Counterscript/api/");
        token = ConfigPlugins.getPluginConfig(PLUGIN_TITLE).getString("rest_token");

    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getGui() {
        return "/uii/administration_Counterscript.xhtml";
    }

    public void getData() {
        try (Client client = ClientBuilder.newClient()) {
            WebTarget base = client.target(restUrl);
            WebTarget xml = base.path("xml");
            if (includeOutdatedData) {
                xml = xml.path("withinactive");
            }

            if (startDate != null && endDate != null) {
                String start = dateConverter.format(startDate);
                String end = dateConverter.format(endDate);
                xml = xml.path(start).path(end);
            }
            dataList = xml.request().header("token", token).get(new GenericType<List<MetadataInformation>>() {
            });
        }
    }

    public void resetData() {

        detailList = null;
    }

    public void getDetails() {
        try (Client client = ClientBuilder.newClient()) {
            WebTarget base = client.target(restUrl);
            WebTarget xml = base.path("xml");
            xml = xml.path("bnumber").path(currentNumber);
            detailList = xml.request().header("token", token).get(new GenericType<List<MetadataInformation>>() {
            });
        }
    }

    public void download() {

        try (Client client = ClientBuilder.newClient()) {
            WebTarget base = client.target(restUrl);
            WebTarget csv = base.path("csv");
            if (includeOutdatedData) {
                csv = csv.path("withinactive");
            }

            if (startDate != null && endDate != null) {
                String start = dateConverter.format(startDate);
                String end = dateConverter.format(endDate);
                csv = csv.path(start).path(end);
            }
            csv = csv.queryParam("token", token);
            try {
                ExternalContext ec = FacesContextHelper.getCurrentFacesContext().getExternalContext();

                ec.responseReset(); // Some JSF component library or some Filter might have set some headers in the
                // buffer beforehand. We want to get rid of them, else it may collide.
                ec.setResponseContentType("text/csv"); // Check http://www.iana.org/assignments/media-types for all types.
                // Use if necessary ExternalContext#getMimeType() for auto-detection
                // based on filename.
                ec.setResponseHeader("Content-Disposition", "attachment; filename=\"counterscript.csv\""); // The Save As
                // popup magic
                // is done here.
                // You can give
                // it any file
                // name you
                // want, this
                // only won't
                // work in MSIE,
                // it will use
                // current
                // request URL
                // as file name
                // instead.

                OutputStream outStream = ec.getResponseOutputStream();

                @Cleanup
                InputStream inStream = csv.getUri().toURL().openStream();

                byte[] buffer = new byte[1024];

                int length;

                while ((length = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, length);
                }
                FacesContextHelper.getCurrentFacesContext().responseComplete(); // Important! Otherwise JSF will attempt to
                // render the response which obviously will
                // fail since it's already written with a
                // file and closed.

            } catch (IOException e) {
                Log.error(e);
            }
        }
    }

    public List<MetadataInformation> getPaginatorList() {
        List<MetadataInformation> subList = new ArrayList<>();
        if (dataList != null) {
            if (dataList.size() > (pageNo * entriesPerPage) + entriesPerPage) {
                subList = dataList.subList(pageNo * entriesPerPage, (pageNo * entriesPerPage) + entriesPerPage);
            } else {
                subList = dataList.subList(pageNo * entriesPerPage, dataList.size());
            }
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
        int ret = Double.valueOf(Math.floor(this.dataList.size() / entriesPerPage)).intValue();
        if (this.dataList.size() % entriesPerPage == 0) {
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
        return this.dataList.size() > entriesPerPage;
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

    public int getSizeOfDataList() {
        return dataList.size();
    }

}
