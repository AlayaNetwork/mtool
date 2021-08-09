package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.StakingContract;
import com.platon.contracts.ppos.dto.TransactionResponse;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.crypto.Credentials;
import com.platon.mtool.client.options.UnstakingOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.common.AllCommands;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.tx.gas.GasProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * 解质押
 *
 * <p>Created by liyf.
 */
public class UnstakingExecutor extends MtoolExecutor<UnstakingOption> {

  private static final Logger logger = LoggerFactory.getLogger(UnstakingExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public UnstakingExecutor(JCommander commander, UnstakingOption commonOption) {
    super(commander, commonOption);
  }

  protected Web3j getWeb3j(ValidatorConfig validatorConfig) {
    return com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
  }

  protected StakingContract getStakingContract(
      Web3j web3j, Credentials credentials) {
    return StakingContract.load(web3j, credentials);
  }

  @Override
  public void execute(UnstakingOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.UNSTAKING).kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = getWeb3j(validatorConfig);
    Credentials credentials = option.getKeystore().getCredentials();

    StakingContract stakingContract =
        getStakingContract(web3j, credentials);
    GasProvider gasProvider = checkGasPrice(stakingContract.getUnStakingGasProvider(validatorConfig.getNodePublicKey()));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);
    PlatonSendTransaction transaction =
        stakingContract
            .unStakingReturnTransaction(validatorConfig.getNodePublicKey(), gasProvider)
            .send();
    TransactionResponse response = stakingContract.getTransactionResponse(transaction).send();

    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.UNSTAKING).kv("transaction", transaction));
    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.UNSTAKING).kv("response", response));

    ProgressBar.stop();
    echoResult(
        transaction,
        response,
        validatorConfig.getNodePublicKey(),
        blockChainService.getCostAmount(null, gasProvider));
  }
}
