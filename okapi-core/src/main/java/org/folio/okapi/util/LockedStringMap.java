package org.folio.okapi.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.shareddata.AsyncMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

public class LockedStringMap {

  static class StringMap {

    @JsonProperty
    final Map<String, String> strings = new LinkedHashMap<>();
  }

  private AsyncMap<String, String> list = null;
  private Vertx vertx = null;
  private static final int DELAY = 10; // ms in recursing for retry of map
  protected final Logger logger = OkapiLogger.get();
  private final Messages messages = Messages.getInstance();

  /**
   * Initialize a shared map.
   * @param vertx Vert.x handle
   * @param mapName name of shared map
   * @param fut async result
   */
  public void init(Vertx vertx, String mapName, Handler<ExtendedAsyncResult<Void>> fut) {
    init(vertx, mapName).onComplete(res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      }
    });
  }

  /**
   * Initialize a shared map.
   * @param vertx Vert.x handle
   * @param mapName name of shared map
   * @return Future
   */
  public Future<Void> init(Vertx vertx, String mapName) {
    this.vertx = vertx;
    return AsyncMapFactory.<String, String>create(vertx, mapName).compose(res -> {
      this.list = res;
      logger.info("initialized map {} ok", mapName);
      return Future.succeededFuture();
    });
  }

  public void size(Handler<AsyncResult<Integer>> fut) {
    list.size(fut);
  }

  /**
   * Get value from shared map - primary and secondary level keys.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param fut async result with value if successful
   */
  public void getString(String k, String k2, Handler<ExtendedAsyncResult<String>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (k2 == null) {
          if (val == null) {
            fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
          } else {
            fut.handle(new Success<>(val));
          }
        } else {
          if (val == null) {
            fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
          } else {
            StringMap stringMap = new StringMap();
            StringMap oldList = Json.decodeValue(val, StringMap.class);
            stringMap.strings.putAll(oldList.strings);
            if (stringMap.strings.containsKey(k2)) {
              fut.handle(new Success<>(stringMap.strings.get(k2)));
            } else {
              fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
            }
          }
        }
      }
    });
  }

  /**
   * Get values from shared map with primary key.
   * @param k primary-level key
   * @param fut async result with values if successful
   */
  public void getString(String k, Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        StringMap map;
        if (val != null) {
          map = Json.decodeValue(val, StringMap.class);
          fut.handle(new Success<>(map.strings.values()));
        } else {
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
        }
      }
    });
  }

  /**
   * Get all values from shared map.
   * @param fut async result with values if successful
   */
  public void getKeys(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    Future<Collection<String>> f = getKeys();
    f.onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      } else {
        fut.handle(new Success<>(res.result()));
      }
    });
  }

  /**
   * Get all keys from shared map (sorted).
   * @return Future with sorted keys
   */
  public Future<Collection<String>> getKeys() {
    return list.keys().compose(res -> {
      List<String> s = new ArrayList<>(res);
      java.util.Collections.sort(s);
      return Future.succeededFuture(s);
    });
  }

  /**
   * Update value in shared map.
   * @param allowReplace true: both insert and replace; false: insert only
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param value new value
   * @param fut async result
   */
  public void addOrReplace(boolean allowReplace, String k, String k2, String value,
                           Handler<ExtendedAsyncResult<Void>> fut) {
    addOrReplace(allowReplace, k, k2, value).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
        return;
      }
      fut.handle(new Success<>());
    });
  }

  /**
   * Update value in shared map.
   * @param allowReplace true: both insert and replace; false: insert only
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param value new value
   * @return fut async result
*/
  public Future<Void> addOrReplace(boolean allowReplace, String k, String k2, String value) {
    return list.get(k).compose(resGet -> {
      String oldVal = resGet;
      String newVal;
      if (k2 == null) {
        newVal = value;
      } else {
        StringMap smap = new StringMap();
        if (oldVal != null) {
          StringMap oldList = Json.decodeValue(oldVal, StringMap.class);
          smap.strings.putAll(oldList.strings);
        }
        if (!allowReplace && smap.strings.containsKey(k2)) {
          return Future.failedFuture(messages.getMessage("11400", k2));
        }
        smap.strings.put(k2, value);
        newVal = Json.encodePrettily(smap);
      }
      return addOrReplace2(allowReplace, k, k2, value, oldVal, newVal);
    });
  }

  private Future<Void> addOrReplace2(boolean allowReplace, String k, String k2, String value,
                                     String oldVal, String newVal) {

    if (oldVal == null) { // new entry
      return list.putIfAbsent(k, newVal).compose(resPut -> {
        if (resPut == null) {
          return Future.succeededFuture();
        }
        // Someone messed with it, try again
        Promise promise = Promise.promise();
        vertx.setTimer(DELAY, x -> addOrReplace(allowReplace, k, k2, value)
            .onComplete(promise::handle));
        return promise.future();
      });
    } else { // existing entry, put and retry if someone else messed with it
      return list.replaceIfPresent(k, oldVal, newVal).compose(resRepl -> {
        if (Boolean.TRUE.equals(resRepl)) {
          return Future.succeededFuture();
        }
        Promise promise = Promise.promise();
        vertx.setTimer(DELAY, x -> addOrReplace(allowReplace, k, k2, value)
            .onComplete(promise::handle));
        return promise.future();
      });
    }
  }

  public void remove(String k, Handler<ExtendedAsyncResult<Boolean>> fut) {
    remove(k, null, fut);
  }

  /**
   * Remove entry from shared map.
   * @param k primary-level key
   * @param k2 secondary-level key
   * @param fut async result
   */
  public void remove(String k, String k2,
                     Handler<ExtendedAsyncResult<Boolean>> fut) {

    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val == null) {
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
          return;
        }
        StringMap stringMap = new StringMap();
        if (k2 != null) {
          stringMap = Json.decodeValue(val, StringMap.class);
          if (!stringMap.strings.containsKey(k2)) {
            fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
            return;
          }
          stringMap.strings.remove(k2);
        }
        remove2(k, k2, stringMap, val, fut);
      }
    });
  }

  private void remove2(String k, String k2, StringMap stringMap, String val,
                       Handler<ExtendedAsyncResult<Boolean>> fut) {

    if (stringMap.strings.isEmpty()) {
      list.removeIfPresent(k, val, resDel -> {
        if (resDel.succeeded()) {
          if (Boolean.TRUE.equals(resDel.result())) {
            fut.handle(new Success<>(true));
          } else {
            vertx.setTimer(DELAY, res -> remove(k, k2, fut));
          }
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, resDel.cause()));
        }
      });
    } else { // list was not empty, remove value
      String newVal = Json.encodePrettily(stringMap);
      list.replaceIfPresent(k, val, newVal, resPut -> {
        if (resPut.succeeded()) {
          if (Boolean.TRUE.equals(resPut.result())) {
            fut.handle(new Success<>(false));
          } else {
            vertx.setTimer(DELAY, res -> remove(k, k2, fut));
          }
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, resPut.cause()));
        }
      });
    }
  }

}
