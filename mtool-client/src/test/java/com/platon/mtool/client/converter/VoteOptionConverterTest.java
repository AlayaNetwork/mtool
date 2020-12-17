package com.platon.mtool.client.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beust.jcommander.ParameterException;
import com.platon.mtool.common.AllParams;
import com.alaya.contracts.ppos.dto.enums.VoteOption;
import org.junit.jupiter.api.Test;

/** Created by liyf. */
class VoteOptionConverterTest {

  private VoteOptionConverter converter = new VoteOptionConverter(AllParams.OPINION);

  @Test
  void convert() {
    VoteOption option = converter.convert("yes");
    assertEquals(VoteOption.YEAS, option);
  }

  @Test
  void convertException() {
    ParameterException exception =
        assertThrows(ParameterException.class, () -> converter.convert("unknow"));
    assertEquals(
        "\"--opinion\": unknow (no such opinion, support input:yes,no,abstain. )",
        exception.getMessage());
  }
}
