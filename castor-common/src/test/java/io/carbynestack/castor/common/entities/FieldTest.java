/*
 * Copyright (c) 2021 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/carbynestack/castor.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.carbynestack.castor.common.entities;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.SneakyThrows;
import org.junit.Test;

public class FieldTest {
  @SneakyThrows
  @Test
  public void givenFieldJsonStringForTypeGfp_whenDeserializingData_thenRecreateOrigin() {
    Field expectedField = Field.GFP;
    String fieldJsonString =
        String.format(
            "{\"@type\":\"Gfp\",\"name\":\"%s\",\"elementSize\":%d}",
            expectedField.getName(), expectedField.getElementSize());
    ObjectMapper om = new ObjectMapper();
    Field actualField = om.readerFor(Field.class).readValue(fieldJsonString);
    assertEquals(expectedField, actualField);
  }

  @SneakyThrows
  @Test
  public void givenFieldJsonStringForTypeGf2n_whenDeserializingData_thenRecreateOrigin() {
    Field expectedField = Field.GF2N;
    String invalidGfpFieldJsonString =
        String.format(
            "{\"@type\":\"Gf2n\",\"name\":\"%s\",\"elementSize\":%d}",
            expectedField.getName(), expectedField.getElementSize());
    ObjectMapper om = new ObjectMapper();
    Field actualField = om.readerFor(Field.class).readValue(invalidGfpFieldJsonString);
    assertEquals(expectedField, actualField);
  }

  @SneakyThrows
  @Test
  public void givenGf2nFieldJsonStringWithMissingName_whenDeserializingData_thenThrowException() {
    String invalidGfpFieldJsonString = "{\"@type\":\"Gf2n\",\"elementSize\":16}";
    ObjectMapper om = new ObjectMapper();
    MismatchedInputException mie =
        assertThrows(
            MismatchedInputException.class,
            () -> om.readerFor(Field.class).readValue(invalidGfpFieldJsonString));
    assertThat(mie.getMessage(), startsWith("Missing required creator property 'name'"));
  }

  @SneakyThrows
  @Test
  public void
      givenGfpFieldJsonStringWithMissingElementSize_whenDeserializingData_thenThrowException() {
    String invalidGfpFieldJsonString = "{\"@type\":\"Gfp\",\"name\":\"gf2n\"}";
    ObjectMapper om = new ObjectMapper();
    MismatchedInputException mie =
        assertThrows(
            MismatchedInputException.class,
            () -> om.readerFor(Field.class).readValue(invalidGfpFieldJsonString));
    assertThat(mie.getMessage(), startsWith("Missing required creator property 'elementSize'"));
  }
}
