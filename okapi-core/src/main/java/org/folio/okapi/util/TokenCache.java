package org.folio.okapi.util;

import java.util.HashMap;
import java.util.Map;

public class TokenCache {

  private Map<String, String> cache = new HashMap<>();
  
  public void put(String method, String path, String id, String token) {
    cache.put(genKey(method, path, id), token);
  }
  
  public String get(String method, String path, String id) {
    return cache.get(genKey(method, path, id));
  }
  
  private String genKey(String method, String path, String id) {
    return method + "|" + path + "|" + id;
  }
  
}
