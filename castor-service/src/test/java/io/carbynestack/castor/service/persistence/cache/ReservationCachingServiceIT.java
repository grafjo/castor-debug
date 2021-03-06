/*
 * Copyright (c) 2021 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/carbynestack/castor.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.carbynestack.castor.service.persistence.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import io.carbynestack.castor.common.entities.ActivationStatus;
import io.carbynestack.castor.common.entities.Reservation;
import io.carbynestack.castor.common.entities.ReservationElement;
import io.carbynestack.castor.common.entities.TupleType;
import io.carbynestack.castor.service.CastorServiceApplication;
import io.carbynestack.castor.service.config.CastorCacheProperties;
import io.carbynestack.castor.service.persistence.markerstore.TupleChunkMetaDataEntity;
import io.carbynestack.castor.service.persistence.markerstore.TupleChunkMetaDataStorageService;
import io.carbynestack.castor.service.testconfig.PersistenceTestEnvironment;
import io.carbynestack.castor.service.testconfig.ReusableMinioContainer;
import io.carbynestack.castor.service.testconfig.ReusablePostgreSQLContainer;
import io.carbynestack.castor.service.testconfig.ReusableRedisContainer;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    classes = {CastorServiceApplication.class})
@ActiveProfiles("test")
public class ReservationCachingServiceIT {

  @ClassRule
  public static ReusableRedisContainer reusableRedisContainer =
      ReusableRedisContainer.getInstance();

  @ClassRule
  public static ReusableMinioContainer reusableMinioContainer =
      ReusableMinioContainer.getInstance();

  @ClassRule
  public static ReusablePostgreSQLContainer reusablePostgreSQLContainer =
      ReusablePostgreSQLContainer.getInstance();

  @Autowired private ConsumptionCachingService consumptionCachingService;

  @Autowired private PersistenceTestEnvironment testEnvironment;

  @Autowired private CastorCacheProperties cacheProperties;

  @Autowired private CacheManager cacheManager;

  @Autowired private RedisTemplate<String, Object> redisTemplate;

  private ReservationCachingService reservationCachingService;

  private TupleChunkMetaDataStorageService tupleChunkMetaDataStorageServiceMock;

  private Cache reservationCache;

  private final UUID testRequestId = UUID.fromString("c8a0a467-16b0-4f03-b7d7-07cbe1b0e7e8");
  private final UUID testChunkId = UUID.fromString("80fbba1b-3da8-4b1e-8a2c-cebd65229fad");
  private final TupleType testTupleType = TupleType.MULTIPLICATION_TRIPLE_GFP;
  private final String testReservationId = testRequestId + "_" + testTupleType;
  private final long testNumberReservedTuples = 3;
  private final Reservation testReservation =
      new Reservation(
          testReservationId,
          testTupleType,
          Collections.singletonList(
              new ReservationElement(testChunkId, testNumberReservedTuples, 0)));

  @Before
  public void setUp() {
    if (reservationCache == null) {
      reservationCache = cacheManager.getCache(cacheProperties.getReservationStore());
    }
    tupleChunkMetaDataStorageServiceMock = mock(TupleChunkMetaDataStorageService.class);
    reservationCachingService =
        new ReservationCachingService(
            cacheProperties,
            consumptionCachingService,
            redisTemplate,
            tupleChunkMetaDataStorageServiceMock);
    testEnvironment.clearAllData();
  }

  @Test
  public void givenCacheIsEmpty_whenGetReservation_thenReturnNull() {
    assertNull(reservationCachingService.getReservation(testReservation.getReservationId()));
  }

  @Test
  public void givenSuccessfulRequest_whenKeepReservation_thenStoreInCacheAndUpdateConsumption() {
    TupleChunkMetaDataEntity metaDataEntityMock = mock(TupleChunkMetaDataEntity.class);
    when(tupleChunkMetaDataStorageServiceMock.getTupleChunkData(testChunkId))
        .thenReturn(metaDataEntityMock);
    when(metaDataEntityMock.getReservedMarker()).thenReturn(0L);
    when(metaDataEntityMock.getConsumedMarker()).thenReturn(0L);
    when(metaDataEntityMock.getNumberOfTuples()).thenReturn(Long.MAX_VALUE);
    when(metaDataEntityMock.getTupleType()).thenReturn(testReservation.getTupleType());
    when(metaDataEntityMock.getStatus()).thenReturn(ActivationStatus.UNLOCKED);
    doNothing()
        .when(tupleChunkMetaDataStorageServiceMock)
        .updateReservationForTupleChunkData(testChunkId, testNumberReservedTuples);

    reservationCachingService.keepReservation(testReservation);
    assertEquals(
        testReservation,
        reservationCache.get(testReservation.getReservationId(), Reservation.class));
    Set<String> keysInCache =
        redisTemplate.keys(
            CacheKeyPrefix.simple()
                    .compute(
                        cacheProperties.getConsumptionStorePrefix()
                            + testReservation.getTupleType())
                + "*");
    assertEquals(1, keysInCache.size());

    verify(tupleChunkMetaDataStorageServiceMock)
        .updateReservationForTupleChunkData(testChunkId, testNumberReservedTuples);
  }

  @Test
  public void givenReservationInCache_whenGetReservation_thenKeepReservationUntouchedInCache() {
    reservationCache.put(testReservation.getReservationId(), testReservation);
    assertEquals(
        testReservation,
        reservationCachingService.getReservation(testReservation.getReservationId()));
    assertEquals(
        testReservation,
        reservationCachingService.getReservation(testReservation.getReservationId()));
  }

  @Test
  public void givenSuccessfulRequest_whenForgetReservation_thenRemoveFromCache() {
    reservationCache.put(testReservation.getReservationId(), testReservation);
    assertEquals(
        testReservation,
        reservationCache.get(testReservation.getReservationId(), Reservation.class));
    reservationCachingService.forgetReservation(testReservation.getReservationId());
    assertNull(reservationCache.get(testReservation.getReservationId(), Reservation.class));
  }
}
