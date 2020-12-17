package com.platon.mtool.client.execute;

import com.alaya.contracts.ppos.dto.enums.StakingAmountType;
import com.beust.jcommander.JCommander;
import com.platon.mtool.client.options.IncreaseStakingOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.LogUtils;
import com.alaya.contracts.ppos.StakingContract;
import com.alaya.contracts.ppos.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alaya.crypto.Credentials;
import com.alaya.protocol.Web3j;
import com.alaya.protocol.core.methods.response.PlatonSendTransaction;
import com.alaya.tx.gas.GasProvider;

import static com.platon.mtool.client.tools.CliConfigUtils.CLIENT_CONFIG;

/**
 * 增持质押
 *
 * <p>Created by liyf.
 */
public class IncreaseStakingExecutor extends MtoolExecutor<IncreaseStakingOption> {

  private static final Logger logger = LoggerFactory.getLogger(IncreaseStakingExecutor.class);

  private BlockChainService blockChainService = BlockChainService.singleton();

  public IncreaseStakingExecutor(JCommander commander, IncreaseStakingOption commonOption) {
    super(commander, commonOption);
  }

  protected Web3j getWeb3j(ValidatorConfig validatorConfig) {
    return com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
  }

  protected StakingContract getStakingContract(
      Web3j web3j, Credentials credentials, Long chainId) {
    return StakingContract.load(web3j, credentials, chainId);
  }

  @Override
  public void execute(IncreaseStakingOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("Increasestaking").kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = getWeb3j(validatorConfig);

    // 检查当前金额是否满足链上的最小增持质押金额
    blockChainService.validAmount(web3j,"staking","operatingThreshold",option.getAmount().getAmount());

    Credentials credentials = option.getKeystore().getCredentials();

    StakingContract stakingContract =
        getStakingContract(web3j, credentials, CLIENT_CONFIG.getTargetChainId());
    GasProvider gasProvider =
        stakingContract.getAddStakingGasProvider(
            validatorConfig.getNodePublicKey(),
            option.getAmount().getAmountType(),
            option.getAmount().getAmount());
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), option.getAmount().getAmount(), gasProvider, web3j, option.getAmount().getAmountType());
    PlatonSendTransaction transaction =
        stakingContract
            .addStakingReturnTransaction(
                validatorConfig.getNodePublicKey(),
                option.getAmount().getAmountType(),
                option.getAmount().getAmount(),
                gasProvider)
            .send();

    TransactionResponse response = stakingContract.getTransactionResponse(transaction).send();
    LogUtils.info(logger, () -> Log.newBuilder().msg("Increasestaking").kv("response", response));
    ProgressBar.stop();
    echoResult(
        transaction,
        response,
        validatorConfig.getNodePublicKey(),
        blockChainService.getCostAmount(option.getAmount().getAmount(), gasProvider));
  }
}
