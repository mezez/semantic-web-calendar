package org.semanticwebproject.lib;

public class CalendarEvent {
    private String stampDate;
    private String startDate;
    private String endDate;
    private String summary;
    private String location;
    private String description;
    private String UID;
    private String createdAt;
    private String lastModified;
    private String sequence;

    public CalendarEvent(){};
    public CalendarEvent(String stampDate, String startDate,String endDate, String summary, String location,
                         String description, String UID, String createdAt, String lastModified,String sequence){
        this.stampDate = stampDate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.summary = summary;
        this.location = location;
        this.description = description;
        this.UID = UID;
        this.createdAt = createdAt;
        this.lastModified = lastModified;
        this.sequence = sequence;
    };

    public String getStampDate(){
        return this.stampDate;
    }
    public void setStampDate(String stampDate){
        this.stampDate = stampDate;
    }

    public String getStartDate(){
        return this.startDate;
    }
    public void setStartDate(String startDate){
        this.startDate = startDate;
    }

    public String getEndDate(){
        return this.endDate;
    }
    public void setEndDate(String endDate){
        this.endDate = endDate;
    }
    public String getSummary(){
        return this.summary;
    }
    public void setSummary(String summary){
        this.summary = summary;
    }
    public String getLocation(){
        return this.location;
    }
    public void setLocation(String location){
        this.location = location;
    }
    public String getDescription(){
        return this.description;
    }
    public void setDescription(String description){
        this.description = description;
    }
    public String getUID(){
        return this.UID;
    }
    public void setUID(String UID){
        this.UID = UID;
    }
    public String getCreatedAt(){
        return this.createdAt;
    }
    public void setCreatedAt(String createdAt){
        this.createdAt = createdAt;
    }
    public String getLastModified(){
        return this.lastModified;
    }
    public void setLastModified(String lastModified){
        this.lastModified = lastModified;
    }
    public String getSequence(){
        return this.sequence;
    }
    public void setSequence(String sequence){
        this.sequence = sequence;
    }
}
