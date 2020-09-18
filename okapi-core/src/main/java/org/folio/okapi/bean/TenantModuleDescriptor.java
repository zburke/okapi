package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Association of a module to a tenant. This encapsulates the id of the module.
 * Each tenant has a list of such associations, listing what modules have been
 * enabled for it.
 *
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantModuleDescriptor {

  private String id; // For practical reasons, the UI folks prefer this to be
  // called 'id'. It is the id of a module.
  private String from;

  // we really want these lowercase as they reflect the JSON property values
  // S00115: Constant names should comply with a naming convention
  @java.lang.SuppressWarnings({"squid:S00115"})
  public enum Action {
    enable, disable, uptodate, suggest, conflict
  }

  private Action action;

  private String message;

  @java.lang.SuppressWarnings({"squid:S00115"})
  public enum Stage {
    pending, deploy, call, undeploy, done
  }

  private Stage stage;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String id) {
    this.from = id;
  }

  public Action getAction() {
    return action;
  }

  public void setAction(Action action) {
    this.action = action;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Stage getStage() {
    return stage;
  }

  public void setStage(Stage stage) {
    this.stage = stage;
  }

  /**
   * Clone an entry without status and stage (for Install without async).
   * @return entry
   */
  @JsonIgnore
  public TenantModuleDescriptor cloneWithoutStatus() {
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.action = this.action;
    tm.id = this.id;
    tm.from = this.from;
    tm.message = this.message;
    return tm;
  }

}
