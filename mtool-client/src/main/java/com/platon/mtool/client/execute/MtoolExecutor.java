package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.dto.BaseResponse;
import com.platon.mtool.client.CliExecutor;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.CommonOption;
import com.platon.mtool.client.tools.CliExceptionHelp;
import com.platon.mtool.client.tools.PrintUtils;
import com.platon.mtool.common.exception.MtoolPlatonException;
import com.platon.mtool.common.exception.MtoolPlatonExceptionCode;
import com.platon.mtool.common.utils.PlatOnUnit;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.tx.gas.ContractGasProvider;
import com.platon.tx.gas.GasProvider;

import java.math.BigInteger;

/**
 * mtool基础命令执行器适配器
 *
 * <p>Created by liyf.
 */
public abstract class MtoolExecutor<T extends CommonOption> extends CliExecutor<T> {

  public MtoolExecutor(JCommander commander, T commonOption) {
    super(commander, commonOption);
  }

  @Override
  protected void handleException(Exception e) {
    CliExceptionHelp.stopAndPrintError(e);
  }

  protected MtoolPlatonException platonException(int code, String nodeId, BigInteger balance,String... msgArgs) {
    if (code == 301111) {
      // E301111(301111, "账户的余额不足",
      return MtoolPlatonExceptionCode.E301111.create(PlatOnUnit.vonToLat(balance));
    } else if (code == 301115) {
      // E301115(301115, "验证人信息不存在",
      return MtoolPlatonExceptionCode.E301115.create(nodeId);
    } else {
      return MtoolPlatonExceptionCode.getFromCode(code,msgArgs).create();
    }
  }

  protected void echoResult(
      PlatonSendTransaction transaction, BaseResponse response, String nodeId, BigInteger balance,String... msgArgs) {
    PrintUtils.echo("transaction hash: " + transaction.getTransactionHash());
    if (response.isStatusOk()) {
      PrintUtils.echo(ClientConsts.SUCCESS);
    } else {
      throw platonException(response.getCode(), nodeId, balance,msgArgs);
    }
  }

  // 检查GasPrice
  protected GasProvider checkGasPrice(GasProvider origin){
    BigInteger gasPrice = origin.getGasPrice();
    if(gasPrice.compareTo(BigInteger.ZERO)<=0){
      // 如果gas price <= 0，则设置为默认值
      return new ContractGasProvider(BigInteger.valueOf(1000000000), origin.getGasLimit());
    }
    return origin;
  }
}
