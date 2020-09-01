package org.folio.okapi.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;


public interface DeploymentStore {
  void insert(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<Void>> fut);

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  Future<Void> init(boolean reset);

  Future<List<DeploymentDescriptor>> getAll();
}
