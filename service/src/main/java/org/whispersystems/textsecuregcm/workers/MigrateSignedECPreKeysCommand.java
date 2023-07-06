/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.workers;

import io.micrometer.core.instrument.Metrics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.whispersystems.textsecuregcm.entities.ECSignedPreKey;
import org.whispersystems.textsecuregcm.metrics.MetricsUtil;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.KeysManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public class MigrateSignedECPreKeysCommand extends AbstractSinglePassCrawlAccountsCommand {

  private static final String STORE_KEY_ATTEMPT_COUNTER_NAME =
      MetricsUtil.name(MigrateSignedECPreKeysCommand.class, "storeKeyAttempt");

  // It's tricky to find, but this is the default connection count for the AWS SDK's async DynamoDB client. We'll have
  // multiple workers using the max number of connections, but that's okay because the client allows for (by default)
  // 10,000 pending requests. As long as we don't have more than 200(!) workers, we'll be fine with this concurrency
  // level.
  private static final int MAX_CONCURRENCY = 50;

  public MigrateSignedECPreKeysCommand() {
    super("migrate-signed-ec-pre-keys", "Migrate signed EC pre-keys from Account records to a dedicated table");
  }

  @Override
  protected void crawlAccounts(final ParallelFlux<Account> accounts) {
    final KeysManager keysManager = getCommandDependencies().keysManager();

    accounts.flatMap(account -> Flux.fromIterable(account.getDevices())
            .flatMap(device -> {
              final List<Tuple3<UUID, Long, ECSignedPreKey>> keys = new ArrayList<>(2);

              if (device.getSignedPreKey() != null) {
                keys.add(Tuples.of(account.getUuid(), device.getId(), device.getSignedPreKey()));
              }

              if (device.getPhoneNumberIdentitySignedPreKey() != null) {
                keys.add(Tuples.of(account.getPhoneNumberIdentifier(), device.getId(), device.getPhoneNumberIdentitySignedPreKey()));
              }

              return Flux.fromIterable(keys);
            }))
        .flatMap(keyTuple -> Mono.fromFuture(
            keysManager.storeEcSignedPreKeyIfAbsent(keyTuple.getT1(), keyTuple.getT2(), keyTuple.getT3())), false, MAX_CONCURRENCY)
        .doOnNext(keyStored -> Metrics.counter(STORE_KEY_ATTEMPT_COUNTER_NAME, "stored", String.valueOf(keyStored)).increment())
        .then()
        .block();
  }
}