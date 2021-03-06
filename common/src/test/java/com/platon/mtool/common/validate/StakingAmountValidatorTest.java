package com.platon.mtool.common.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.platon.mtool.common.entity.StakingAmount;
import com.platon.mtool.common.utils.PlatOnUnit;
import java.math.BigInteger;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Created by liyf */
class StakingAmountValidatorTest {
  private static Validator validator;

  @BeforeAll
  static void beforeAll() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  void shouldValid() {
    StakingAmount stakingAmount = new StakingAmount();
    stakingAmount.setAmount(PlatOnUnit.latToVon(BigInteger.valueOf(1000_000)));
    MockOption option = new MockOption(stakingAmount);

    Set<ConstraintViolation<MockOption>> constraintViolations = validator.validate(option);
    assertEquals(0, constraintViolations.size());
  }

  @Test
  void shouldNotValid() {

    StakingAmount stakingAmount = new StakingAmount();
    stakingAmount.setAmount(PlatOnUnit.latToVon(BigInteger.valueOf(999_000)));
    MockOption option = new MockOption(stakingAmount);

    Set<ConstraintViolation<MockOption>> constraintViolations = validator.validate(option);
    assertEquals(1, constraintViolations.size());
    assertEquals(
        "The stake amount cannot be lower than 1000000ATP",
        constraintViolations.iterator().next().getMessage());
  }

  static class MockOption {
    @StakingAmountMin(
        value = "1000000000000000000000000",
        message = "The stake amount cannot be lower than 1000000ATP")
    private StakingAmount amount;

    public MockOption(StakingAmount amount) {
      this.amount = amount;
    }
  }
}
