package com.alramz.globalconfigurationsettings.model;

import jakarta.persistence.*;

@Entity
@Table(name = "GLOBAL_CONFIGURATION_SETTINGS", indexes = {
    @Index(name = "idx_identifier", columnList = "IDENTIFIER"),
    @Index(name = "idx_identifier_type", columnList = "IDENTIFIER_TYPE"),
    @Index(name = "idx_status", columnList = "STATUS")
})
public class GlobalConfigurationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "IDENTIFIER", nullable = false, unique = true)
    private String identifier;

    @Column(name = "IDENTIFIER_TYPE", nullable = false)
    private String identifierType;

    @Column(name = "IDENTIFIER_TEXT", nullable = false, length = 500)
    private String identifierText;

    @Column(name = "STATUS")
    private String status;

    public GlobalConfigurationSettings() {
    }

    public GlobalConfigurationSettings(Long id, String identifier, String identifierType, String identifierText, String status) {
        this.id = id;
        this.identifier = identifier;
        this.identifierType = identifierType;
        this.identifierText = identifierText;
        this.status = status;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getIdentifierType() {
        return identifierType;
    }

    public String getIdentifierText() {
        return identifierText;
    }

    public String getStatus() {
        return status;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setIdentifierType(String identifierType) {
        this.identifierType = identifierType;
    }

    public void setIdentifierText(String identifierText) {
        this.identifierText = identifierText;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GlobalConfigurationSettings)) return false;
        GlobalConfigurationSettings other = (GlobalConfigurationSettings) obj;
        return java.util.Objects.equals(id, other.id) &&
               java.util.Objects.equals(identifier, other.identifier) &&
               java.util.Objects.equals(identifierType, other.identifierType) &&
               java.util.Objects.equals(identifierText, other.identifierText) &&
               java.util.Objects.equals(status, other.status);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, identifier, identifierType, identifierText, status);
    }

    @Override
    public String toString() {
        return "GlobalConfigurationSettings{" +
                "id=" + id +
                ", identifier='" + identifier + '\'' +
                ", identifierType='" + identifierType + '\'' +
                ", identifierText='" + identifierText + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
