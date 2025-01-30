package de.intranda.counterscript.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@XmlRootElement
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonAutoDetect
@JsonPropertyOrder({ "bnumber", "material", "accessStatus", "accessLicence", "playerPermissions", "status", "creationDate", "modificationDate",
        "deletionDate", "active", "filename", "id" })
public @Data class MetadataInformation {
    Integer id;
    String filename;
    String bnumber;
    String material;
    String accessStatus;
    String accessLicence;
    String playerPermissions;
    StatusType status = StatusType.newly;
    @JsonFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    Date creationDate;
    @JsonFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    Date modificationDate;
    @JsonFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    Date deletionDate;
    boolean active = false;

    public enum StatusType {
        newly,
        modified,
        deleted
    }

}
